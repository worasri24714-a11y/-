package app.copyruler.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.copyruler.ruler.ImageBand
import app.copyruler.ruler.SegmentUnit
import app.copyruler.ruler.Segmenter

@Database(entities = [DocumentEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "copy_ruler.db")
                .addCallback(SeedSampleDocument)
                .build()
    }
}

/** Adds the chemistry sample the first time the database is created, i.e. on first launch. */
private object SeedSampleDocument : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        val unit = SegmentUnit.PHRASE
        val values = ContentValues().apply {
            put("title", SampleDocument.TITLE)
            put("kind", DocumentKind.TEXT.name)
            put("text", SampleDocument.TEXT)
            putNull("image_file")
            put("unit", unit.name)
            put("position", 0)
            put("segment_count", Segmenter.split(SampleDocument.TEXT, unit).size)
            put("band_top", 0f)
            put("band_height", ImageBand.DEFAULT_HEIGHT)
            put("created_at", now)
            put("last_opened_at", now)
        }
        db.insert("documents", SQLiteDatabase.CONFLICT_ABORT, values)
    }
}
