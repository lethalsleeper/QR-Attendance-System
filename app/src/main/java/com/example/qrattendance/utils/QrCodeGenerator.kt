package com.example.qrattendance.utils

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import javax.inject.Inject
import javax.inject.Singleton

interface QrCodeGenerator {
    /** Renders [content] as a square QR code bitmap of [sizePx] × [sizePx]. */
    fun generate(content: String, sizePx: Int): ImageBitmap
}

/** ZXing-based generator. CPU bound: call it from a background dispatcher. */
@Singleton
class ZxingQrCodeGenerator @Inject constructor() : QrCodeGenerator {

    override fun generate(content: String, sizePx: Int): ImageBitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix[x, y]) QR_DARK else QR_LIGHT
            }
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return bitmap.asImageBitmap()
    }

    private companion object {
        const val QR_DARK = 0xFF0F172A.toInt()
        const val QR_LIGHT = 0xFFFFFFFF.toInt()
    }
}
