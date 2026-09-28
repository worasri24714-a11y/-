package app.copyruler.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.copyruler.ruler.ImageBand
import app.copyruler.ruler.SegmentUnit

enum class DocumentKind { TEXT, IMAGE }

/**
 * A source the user copies from. Text documents remember the current segment index,
 * image documents remember where the highlight band sits (as fractions of image height).
 */
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: DocumentKind,
    val text: String = "",
    /** Path relative to the app's files directory, e.g. `images/<uuid>.jpg`. */
    @ColumnInfo(name = "image_file") val imageFile: String? = null,
    val unit: SegmentUnit = SegmentUnit.LINE,
    val position: Int = 0,
    @ColumnInfo(name = "segment_count") val segmentCount: Int = 0,
    @ColumnInfo(name = "band_top") val bandTop: Float = 0f,
    @ColumnInfo(name = "band_height") val bandHeight: Float = ImageBand.DEFAULT_HEIGHT,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long,
)

/** How far the ruler has moved through the document, 0..1. */
fun DocumentEntity.progress(): Float = when (kind) {
    DocumentKind.TEXT ->
        if (segmentCount <= 0) 0f else ((position + 1).toFloat() / segmentCount).coerceIn(0f, 1f)
    DocumentKind.IMAGE -> ImageBand.progress(bandTop, bandHeight)
}
