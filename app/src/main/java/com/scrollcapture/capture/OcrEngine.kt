package com.scrollcapture.capture

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrEngine {

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Run OCR on a bitmap, returning an ordered list of text lines.
     */
    suspend fun recognize(bitmap: Bitmap): List<String> = suspendCancellableCoroutine { cont ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val lines = mutableListOf<String>()
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val text = line.text.trim()
                        if (text.isNotEmpty()) {
                            lines.add(text)
                        }
                    }
                }
                if (cont.isActive) cont.resume(lines)
            }
            .addOnFailureListener { e ->
                if (cont.isActive) cont.resumeWithException(e)
            }
    }

    fun close() {
        recognizer.close()
    }
}
