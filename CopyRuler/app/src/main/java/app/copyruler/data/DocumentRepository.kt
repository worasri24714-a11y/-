package app.copyruler.data

import app.copyruler.ruler.SegmentUnit
import app.copyruler.ruler.Segmenter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single entry point for document data.
 *
 * All writes go through one queue that runs on the application scope, so rapid button
 * presses are saved in order and a write still finishes if the screen that started it
 * is closed a moment later.
 */
class DocumentRepository(
    private val dao: DocumentDao,
    private val imageStore: ImageStore,
    scope: CoroutineScope,
) {
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (task in writes) task()
        }
    }

    fun observeAll(): Flow<List<DocumentEntity>> = dao.observeAll()

    fun observe(id: Long): Flow<DocumentEntity?> = dao.observe(id)

    suspend fun get(id: Long): DocumentEntity? = dao.get(id)

    suspend fun createText(title: String, text: String, unit: SegmentUnit = SegmentUnit.LINE): Long {
        val count = countSegments(text, unit)
        val now = System.currentTimeMillis()
        return write {
            dao.insert(
                DocumentEntity(
                    title = title,
                    kind = DocumentKind.TEXT,
                    text = text,
                    unit = unit,
                    segmentCount = count,
                    createdAt = now,
                    lastOpenedAt = now,
                ),
            )
        }
    }

    suspend fun createImage(title: String, imageFile: String): Long {
        val now = System.currentTimeMillis()
        return write {
            dao.insert(
                DocumentEntity(
                    title = title,
                    kind = DocumentKind.IMAGE,
                    imageFile = imageFile,
                    createdAt = now,
                    lastOpenedAt = now,
                ),
            )
        }
    }

    /** Saves edited text; the reading position is kept but clamped to the new length. */
    suspend fun updateText(id: Long, title: String, text: String) {
        write {
            val document = dao.get(id) ?: return@write
            val count = countSegments(text, document.unit)
            val position = document.position.coerceIn(0, (count - 1).coerceAtLeast(0))
            dao.updateText(id, title, text, count, position)
        }
    }

    suspend fun rename(id: Long, title: String) {
        write { dao.rename(id, title) }
    }

    suspend fun delete(document: DocumentEntity) {
        write {
            dao.delete(document.id)
            document.imageFile?.let { imageStore.delete(it) }
        }
    }

    fun savePosition(id: Long, position: Int) = writeLater { dao.updatePosition(id, position) }

    fun saveUnit(id: Long, unit: SegmentUnit, position: Int, segmentCount: Int) =
        writeLater { dao.updateUnit(id, unit.name, position, segmentCount) }

    fun saveBand(id: Long, top: Float, height: Float) = writeLater { dao.updateBand(id, top, height) }

    fun markOpened(id: Long) = writeLater { dao.markOpened(id, System.currentTimeMillis()) }

    private suspend fun countSegments(text: String, unit: SegmentUnit): Int =
        withContext(Dispatchers.Default) { Segmenter.split(text, unit).size }

    /** Queues [block] and waits for its result. */
    private suspend fun <T> write(block: suspend () -> T): T {
        val result = CompletableDeferred<T>()
        writes.send { result.completeWith(runCatching { block() }) }
        return result.await()
    }

    /** Queues [block] without waiting; failures are ignored because the next write retries the state. */
    private fun writeLater(block: suspend () -> Unit) {
        writes.trySend { runCatching { block() } }
    }
}
