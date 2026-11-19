package com.novastream.studio

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeGenerator {
    fun create(content: String, size: Int = 384): Bitmap {
        require(content.isNotBlank()) { "Informe um endereço ou texto para o QR Code." }
        val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
        val matrix = MultiFormatWriter().encode(content.trim(), BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
