package app.copyruler.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import app.copyruler.ruler.LineDetector
import app.copyruler.ruler.LineSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.abs
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
        val bitmap = straighten(decodeUpright(source) ?: throw IOException("Cannot decode $source"))
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

    /** Text lines in [bitmap] as fractions of its height; empty when none are found. */
    suspend fun findLines(bitmap: Bitmap): List<LineSpan> = withContext(Dispatchers.Default) {
        val small = Luminance.of(bitmap, ANALYSIS_WIDTH)
        LineDetector.detect(small.values, small.width, small.height)
    }

    /** Rotates a slightly tilted page photo so its text lines are level. */
    private fun straighten(bitmap: Bitmap): Bitmap {
        val small = Luminance.of(bitmap, ANALYSIS_WIDTH)
        val ink = LineDetector.inkMap(small.values, small.width, small.height)
        val angle = LineDetector.skewDegrees(ink, small.width, small.height)
        if (abs(angle) < MIN_SKEW_DEGREES) return bitmap
        val level = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(level).apply {
            drawColor(Color.WHITE)
            rotate(-angle, bitmap.width / 2f, bitmap.height / 2f)
            drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        bitmap.recycle()
        return level
    }

    private class Luminance(val values: IntArray, val width: Int, val height: Int) {
        companion object {
            fun of(bitmap: Bitmap, targetWidth: Int): Luminance {
                val width = min(targetWidth, bitmap.width)
                val height = (bitmap.height.toLong() * width / bitmap.width).toInt().coerceAtLeast(1)
                val small = Bitmap.createScaledBitmap(bitmap, width, height, true)
                val pixels = IntArray(width * height)
                small.getPixels(pixels, 0, width, 0, 0, width, height)
                if (small !== bitmap) small.recycle()
                for (i in pixels.indices) {
                    val c = pixels[i]
                    pixels[i] = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                }
                return Luminance(pixels, width, height)
            }
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

        /** Width of the grayscale copy used to find lines; enough to separate lines of small print. */
        const val ANALYSIS_WIDTH = 480

        /** Tilts smaller than this are left alone. */
        const val MIN_SKEW_DEGREES = 0.3f

        /** Longest side we keep; larger bitmaps fail to draw on some GPUs. */
        const val MAX_SIDE = 4096

        /** About 12.6 MP, roughly 50 MB as an ARGB bitmap. */
        const val MAX_PIXELS = 4096 * 3072

        /** Largest bitmap decoded before the final scale-down (about 100 MB). */
        const val DECODE_PIXEL_BUDGET = 2L * MAX_PIXELS
    }
}
