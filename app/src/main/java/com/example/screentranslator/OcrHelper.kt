package com.example.screentranslator

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object OcrHelper {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    suspend fun ocr(bmp: Bitmap): String {
        val image = InputImage.fromBitmap(bmp, 0)
        return recognizer.process(image).await().text ?: ""
    }
}
