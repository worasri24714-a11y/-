package app.copyruler.ui.list

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.copyruler.data.DocumentEntity
import app.copyruler.data.DocumentRepository
import app.copyruler.data.ImageStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface ListEvent {
    data class OpenDocument(val id: Long) : ListEvent
    data object ImportFailed : ListEvent
}

class DocumentListViewModel(
    private val repository: DocumentRepository,
    private val imageStore: ImageStore,
) : ViewModel() {

    /** Most recently opened first; null until the database has answered. */
    val documents: StateFlow<List<DocumentEntity>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** True while a photo is being copied into the app. */
    var importing by mutableStateOf(false)
        private set

    private val _events = Channel<ListEvent>(Channel.BUFFERED)
    val events: Flow<ListEvent> = _events.receiveAsFlow()

    fun rename(id: Long, title: String) {
        viewModelScope.launch { repository.rename(id, title.trim()) }
    }

    fun delete(document: DocumentEntity) {
        viewModelScope.launch { repository.delete(document) }
    }

    fun newCameraTarget(): Pair<File, Uri> = imageStore.newCameraTarget()

    /**
     * Copies the picked or captured photo into the app and opens it.
     * [tempFile] (the camera's scratch file) is deleted afterwards.
     */
    fun importImage(source: Uri, title: String, tempFile: File? = null) {
        if (importing) return
        importing = true
        viewModelScope.launch {
            try {
                val path = imageStore.import(source)
                val id = repository.createImage(title, path)
                _events.send(ListEvent.OpenDocument(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Includes OutOfMemoryError for huge images; the user can simply pick another.
                _events.send(ListEvent.ImportFailed)
            } finally {
                tempFile?.delete()
                importing = false
            }
        }
    }
}
