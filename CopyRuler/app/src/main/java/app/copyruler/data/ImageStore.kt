package app.copyruler.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Keeps copies of source photos in the app's private files directory, so they are still
 * there after the app restarts even if the original is deleted from the gallery.
 */
class ImageStore(private val context: Context) {

    fun file(relativePath: String): File = File(context.filesDir, relativePath)

    /** An empty file in the cache the camera app can write into, plus the Uri to hand it. */
    fun newCameraTarget(): Pair<File, Uri> {
        val dir = File(context.cacheDir, CAMERA_DIR).apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    /**
     * Copies the image at [source] into internal storage, turned upright and scaled down to a
     * size the screen can draw. Returns the stored path relative to the files directory.
     */
    suspend fun import(source: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = decodeUpright(source) ?: throw IOException("Cannot decode $source")
        val relativePath = "$IMAGES_DIR/${UUID.randomUUID()}.jpg"
        val target = file(relativePath)
        target.parentFile?.mkdirs()
        try {
            FileOutputStream(target).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    throw IOException("Cannot write $target")
                }
            }
        } catch (e: IOException) {
            target.delete()
            throw e
        } finally {
            bitmap.recycle()
        }
        relativePath
    }

    suspend fun loadBitmap(relativePath: String): Bitmap? = withContext(Dispatchers.IO) {
        BitmapFactory.decodeFile(file(relativePath).path)?.apply {
            // Mipmaps keep small print sharp when a large photo is drawn at screen width.
            setHasMipMap(true)
            prepareToDraw()
        }
    }

    fun delete(relativePath: String) {
        file(relativePath).delete()
    }

    private fun decodeUpright(source: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Decode at a power-of-two reduction that stays within a memory budget, then scale exactly.
        var sampleSize = 1
        while (
            (bounds.outWidth / sampleSize).toLong() * (bounds.outHeight / sampleSize) > DECODE_PIXEL_BUDGET ||
            max(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_SIDE * 2
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val orientation = try {
            resolver.openInputStream(source)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val scale = min(
            1f,
            min(
                MAX_SIDE.toFloat() / max(decoded.width, decoded.height),
                sqrt(MAX_PIXELS.toFloat() / (decoded.width.toFloat() * decoded.height)),
            ),
        )
        val matrix = orientationMatrix(orientation).apply { postScale(scale, scale) }
        if (matrix.isIdentity) return decoded
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (upright !== decoded) decoded.recycle()
        return upright
    }

    private fun orientationMatrix(orientation: Int): Matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                postRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                postRotate(270f)
                postScale(-1f, 1f)
            }
        }
    }

    private companion object {
        const val IMAGES_DIR = "images"
        const val CAMERA_DIR = "camera"
        const val JPEG_QUALITY = 90

        /** Longest side we keep; larger bitmaps fail to draw on some GPUs. */
        const val MAX_SIDE = 4096

        /** About 12.6 MP, roughly 50 MB as an ARGB bitmap. */
        const val MAX_PIXELS = 4096 * 3072

        /** Largest bitmap decoded before the final scale-down (about 100 MB). */
        const val DECODE_PIXEL_BUDGET = 2L * MAX_PIXELS
    }
}
