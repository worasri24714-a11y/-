package app.copyruler.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device text recognition with ML Kit. The bundled model only reads Latin script
 * (English letters and digits); Thai characters are skipped or come out garbled, which
 * the UI tells the user before sending the result to text mode.
 */
class TextRecognizerService(private val context: Context) {

    suspend fun recognize(imageFile: File): String {
        val image = withContext(Dispatchers.IO) {
            InputImage.fromFilePath(context, Uri.fromFile(imageFile))
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = suspendCancellableCoroutine<Text> { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
                    .addOnCanceledListener { continuation.cancel() }
            }
            return result.textBlocks
                .joinToString("\n\n") { block -> block.lines.joinToString("\n") { it.text } }
                .trim()
        } finally {
            recognizer.close()
        }
    }
}
