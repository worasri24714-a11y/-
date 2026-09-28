package app.copyruler.ui.copy

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.copyruler.data.DocumentEntity
import app.copyruler.data.DocumentKind
import app.copyruler.data.DocumentRepository
import app.copyruler.data.ImageStore
import app.copyruler.data.SettingsRepository
import app.copyruler.ocr.TextRecognizerService
import app.copyruler.ruler.ImageBand
import app.copyruler.ruler.Segment
import app.copyruler.ruler.SegmentUnit
import app.copyruler.ruler.Segmenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CopyUiState {
    data object Loading : CopyUiState
    data object Missing : CopyUiState

    data class Text(
        val title: String,
        val unit: SegmentUnit,
        val segments: List<Segment>,
        val index: Int,
    ) : CopyUiState {
        val progress: Float get() = if (segments.isEmpty()) 0f else (index + 1f) / segments.size
    }

    data class Image(
        val title: String,
        val bitmap: ImageBitmap?,
        val loadFailed: Boolean,
        val bandTop: Float,
        val bandHeight: Float,
    ) : CopyUiState {
        val progress: Float get() = ImageBand.progress(bandTop, bandHeight)
    }
}

/** What happened when the user pressed next/previous; drives haptics and messages. */
sealed interface MoveResult {
    data object Moved : MoveResult
    data class BandMoved(val top: Float) : MoveResult
    data object AtEdge : MoveResult
    data object Ignored : MoveResult
}

sealed interface OcrState {
    data object Idle : OcrState
    data object Running : OcrState
    data class Done(val text: String) : OcrState
    data object Failed : OcrState
}

sealed interface CopyEvent {
    data class OpenDocument(val id: Long) : CopyEvent
}

/**
 * Drives the copy screen for one document.
 *
 * The reading position (segment index or band position) lives here and is updated
 * synchronously, so quick presses of the volume keys never read a stale value; each
 * change is also queued for saving so the place is kept after the app closes.
 */
class CopyViewModel(
    private val documentId: Long,
    private val repository: DocumentRepository,
    private val imageStore: ImageStore,
    private val textRecognizer: TextRecognizerService,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private data class Cursor(val unit: SegmentUnit, val index: Int)
    private data class Band(val top: Float, val height: Float)

    private sealed interface ImageLoad {
        data object Loading : ImageLoad
        data class Ready(val bitmap: ImageBitmap) : ImageLoad
        data object Failed : ImageLoad
    }

    private val cursor = MutableStateFlow<Cursor?>(null)
    private val band = MutableStateFlow<Band?>(null)
    private val image = MutableStateFlow<ImageLoad>(ImageLoad.Loading)

    private var document: DocumentEntity? = null

    // Segmenting is only redone when the text or unit changes.
    private var cachedKey: Pair<String, SegmentUnit>? = null
    private var cachedSegments: List<Segment> = emptyList()

    /** False in builds that ship without the ML Kit model. */
    val ocrAvailable: Boolean = textRecognizer.isAvailable

    private val _ocrState = MutableStateFlow<OcrState>(OcrState.Idle)
    val ocrState: StateFlow<OcrState> = _ocrState.asStateFlow()
    private var ocrJob: Job? = null

    private val _events = Channel<CopyEvent>(Channel.BUFFERED)
    val events: Flow<CopyEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<CopyUiState> = combine(
        repository.observe(documentId).onEach { document = it },
        cursor,
        band,
        image,
    ) { doc, cursor, band, image ->
        when {
            // Room answers null only when the row does not exist (e.g. it was deleted).
            doc == null -> CopyUiState.Missing
            doc.kind == DocumentKind.TEXT && cursor != null -> {
                val segments = segmentsFor(doc.text, cursor.unit)
                CopyUiState.Text(doc.title, cursor.unit, segments, clampIndex(cursor.index, segments.size))
            }
            doc.kind == DocumentKind.IMAGE && band != null -> CopyUiState.Image(
                title = doc.title,
                bitmap = (image as? ImageLoad.Ready)?.bitmap,
                loadFailed = image is ImageLoad.Failed,
                bandTop = band.top,
                bandHeight = band.height,
            )
            else -> CopyUiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CopyUiState.Loading)

    init {
        viewModelScope.launch {
            val doc = repository.get(documentId) ?: return@launch
            val height = ImageBand.clampHeight(doc.bandHeight)
            band.value = Band(ImageBand.clampTop(doc.bandTop, height), height)
            cursor.value = Cursor(doc.unit, doc.position)
            repository.markOpened(documentId)
            if (doc.kind == DocumentKind.IMAGE) loadImage(doc.imageFile)
        }
    }

    fun next(): MoveResult = move(forward = true)

    fun previous(): MoveResult = move(forward = false)

    /** Jumps to a tapped segment; returns true if the position changed. */
    fun jumpTo(index: Int): Boolean {
        val doc = document ?: return false
        val current = cursor.value ?: return false
        val segments = segmentsFor(doc.text, current.unit)
        if (index !in segments.indices || index == clampIndex(current.index, segments.size)) return false
        setIndex(current.unit, index)
        return true
    }

    /** Switches between line and phrase mode, staying on the same spot in the text. */
    fun setUnit(unit: SegmentUnit) {
        val doc = document ?: return
        val current = cursor.value ?: return
        if (doc.kind != DocumentKind.TEXT || current.unit == unit) return
        val oldSegments = segmentsFor(doc.text, current.unit)
        val offset = oldSegments.getOrNull(clampIndex(current.index, oldSegments.size))?.start ?: 0
        val newSegments = segmentsFor(doc.text, unit)
        val newIndex = Segmenter.indexForOffset(newSegments, offset)
        cursor.value = Cursor(unit, newIndex)
        repository.saveUnit(documentId, unit, newIndex, newSegments.size)
    }

    /** Moves the band while the user drags it; saved by [commitBand] when the finger lifts. */
    fun dragBandTo(top: Float) {
        val current = band.value ?: return
        band.value = current.copy(top = ImageBand.clampTop(top, current.height))
    }

    /** Centres the band on a tapped spot, given as a fraction of the image height. */
    fun placeBandAt(fraction: Float) {
        val current = band.value ?: return
        band.value = current.copy(top = ImageBand.centeredAt(fraction, current.height))
        commitBand()
    }

    /** Resizes the band while the slider moves; saved by [commitBand] when it is released. */
    fun setBandHeight(height: Float) {
        val current = band.value ?: return
        val clamped = ImageBand.clampHeight(height)
        band.value = Band(ImageBand.clampTop(current.top, clamped), clamped)
    }

    fun commitBand() {
        val current = band.value ?: return
        repository.saveBand(documentId, current.top, current.height)
    }

    fun setFontSize(sizeSp: Int) {
        viewModelScope.launch { settingsRepository.setFontSize(sizeSp) }
    }

    fun recognizeText() {
        val file = document?.imageFile ?: return
        if (ocrJob?.isActive == true) return
        _ocrState.value = OcrState.Running
        ocrJob = viewModelScope.launch {
            _ocrState.value = try {
                OcrState.Done(textRecognizer.recognize(imageStore.file(file)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Missing model, unsupported device, unreadable file…
                OcrState.Failed
            }
        }
    }

    fun dismissOcr() {
        ocrJob?.cancel()
        ocrJob = null
        _ocrState.value = OcrState.Idle
    }

    /** Creates a text document from the recognised text and opens it. */
    fun sendRecognizedText(title: String, text: String) {
        dismissOcr()
        viewModelScope.launch {
            val id = repository.createText(title, text, SegmentUnit.LINE)
            _events.send(CopyEvent.OpenDocument(id))
        }
    }

    private fun move(forward: Boolean): MoveResult {
        val doc = document ?: return MoveResult.Ignored
        return when (doc.kind) {
            DocumentKind.TEXT -> {
                val current = cursor.value ?: return MoveResult.Ignored
                val segments = segmentsFor(doc.text, current.unit)
                if (segments.isEmpty()) return MoveResult.Ignored
                val target = clampIndex(current.index, segments.size) + if (forward) 1 else -1
                if (target !in segments.indices) {
                    MoveResult.AtEdge
                } else {
                    setIndex(current.unit, target)
                    MoveResult.Moved
                }
            }
            DocumentKind.IMAGE -> {
                val current = band.value ?: return MoveResult.Ignored
                val top = if (forward) {
                    ImageBand.next(current.top, current.height)
                } else {
                    ImageBand.previous(current.top, current.height)
                }
                if (ImageBand.sameTop(top, current.top)) {
                    MoveResult.AtEdge
                } else {
                    band.value = current.copy(top = top)
                    commitBand()
                    MoveResult.BandMoved(top)
                }
            }
        }
    }

    private fun setIndex(unit: SegmentUnit, index: Int) {
        cursor.value = Cursor(unit, index)
        repository.savePosition(documentId, index)
    }

    private suspend fun loadImage(relativePath: String?) {
        val bitmap = try {
            relativePath?.let { imageStore.loadBitmap(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
        image.value = if (bitmap != null) ImageLoad.Ready(bitmap.asImageBitmap()) else ImageLoad.Failed
    }

    private fun segmentsFor(text: String, unit: SegmentUnit): List<Segment> {
        val key = text to unit
        if (key != cachedKey) {
            cachedSegments = Segmenter.split(text, unit)
            cachedKey = key
        }
        return cachedSegments
    }

    private fun clampIndex(index: Int, size: Int): Int = index.coerceIn(0, (size - 1).coerceAtLeast(0))
}
