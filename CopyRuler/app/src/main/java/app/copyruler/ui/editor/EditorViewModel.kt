package app.copyruler.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.copyruler.data.DocumentRepository
import app.copyruler.ruler.SegmentUnit
import app.copyruler.ruler.Segmenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SegmentCounts(val lines: Int, val phrases: Int)

/** Edits a text document; [documentId] is null when creating a new one. */
class EditorViewModel(
    private val documentId: Long?,
    private val repository: DocumentRepository,
) : ViewModel() {

    // Compose state rather than StateFlow keeps the text field's cursor in sync while typing.
    var title by mutableStateOf("")
        private set
    var text by mutableStateOf("")
        private set
    var loading by mutableStateOf(documentId != null)
        private set
    var saving by mutableStateOf(false)
        private set

    /** Set once saving finishes; the screen navigates on and then calls [onSaveHandled]. */
    var savedDocumentId by mutableStateOf<Long?>(null)
        private set

    private var savedTitle by mutableStateOf("")
    private var savedText by mutableStateOf("")

    val isNew: Boolean get() = documentId == null
    val hasChanges: Boolean get() = title != savedTitle || text != savedText

    @OptIn(FlowPreview::class)
    val counts: StateFlow<SegmentCounts> = snapshotFlow { text }
        .debounce(250)
        .map { SegmentCounts(Segmenter.split(it, SegmentUnit.LINE).size, Segmenter.split(it, SegmentUnit.PHRASE).size) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SegmentCounts(0, 0))

    init {
        if (documentId != null) {
            viewModelScope.launch {
                repository.get(documentId)?.let {
                    title = it.title
                    text = it.text
                    savedTitle = it.title
                    savedText = it.text
                }
                loading = false
            }
        }
    }

    fun onTitleChange(value: String) {
        title = value
    }

    fun onTextChange(value: String) {
        text = value
    }

    fun appendPasted(pasted: String) {
        text = when {
            text.isBlank() -> pasted
            text.endsWith('\n') -> text + pasted
            else -> text + "\n" + pasted
        }
    }

    fun save(untitled: String) {
        if (saving || loading || text.isBlank()) return
        saving = true
        viewModelScope.launch {
            val finalTitle = title.trim().ifEmpty { titleFromText(text) ?: untitled }
            val id = if (documentId == null) {
                repository.createText(finalTitle, text)
            } else {
                repository.updateText(documentId, finalTitle, text)
                documentId
            }
            savedTitle = title
            savedText = text
            saving = false
            savedDocumentId = id
        }
    }

    fun onSaveHandled() {
        savedDocumentId = null
    }

    private fun titleFromText(text: String): String? {
        val firstLine = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        return if (firstLine.length <= MAX_TITLE_LENGTH) firstLine else firstLine.take(MAX_TITLE_LENGTH).trimEnd() + "…"
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 40
    }
}
