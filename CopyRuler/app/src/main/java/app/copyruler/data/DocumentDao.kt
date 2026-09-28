package app.copyruler.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY last_opened_at DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observe(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: Long): DocumentEntity?

    @Insert
    suspend fun insert(document: DocumentEntity): Long

    @Query("UPDATE documents SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query(
        "UPDATE documents SET title = :title, text = :text, segment_count = :segmentCount, " +
            "position = :position WHERE id = :id",
    )
    suspend fun updateText(id: Long, title: String, text: String, segmentCount: Int, position: Int)

    @Query("UPDATE documents SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Query(
        "UPDATE documents SET unit = :unit, position = :position, segment_count = :segmentCount " +
            "WHERE id = :id",
    )
    suspend fun updateUnit(id: Long, unit: String, position: Int, segmentCount: Int)

    @Query("UPDATE documents SET band_top = :top, band_height = :height WHERE id = :id")
    suspend fun updateBand(id: Long, top: Float, height: Float)

    @Query("UPDATE documents SET last_opened_at = :time WHERE id = :id")
    suspend fun markOpened(id: Long, time: Long)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)
}
