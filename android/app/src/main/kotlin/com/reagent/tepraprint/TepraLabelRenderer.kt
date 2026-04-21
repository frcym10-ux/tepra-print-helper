package com.reagent.tepraprint

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object TepraLabelRenderer {

    private const val DOTS_PER_MM = 8

    fun mmToDots(mm: Int): Int = mm * DOTS_PER_MM

    fun render(label: TepraLabel): Bitmap {
        val widthDots  = mmToDots(label.tapeWidthMm)  // 18mm → 144 dots
        val heightDots = mmToDots(40)                   // 40mm → 320 dots

        val bitmap = Bitmap.createBitmap(widthDots, heightDots, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val margin   = widthDots * 0.05f
        val maxWidth = widthDots - margin * 2

        // 試薬名（上部・折り返しあり）
        val namePaint = buildPaint((widthDots * 0.12f).coerceIn(8f, 16f))
        var y = namePaint.textSize + margin
        y = drawWrapped(canvas, label.reagentName, margin, y, maxWidth, namePaint)
        y += namePaint.textSize * 0.2f

        // 区切り線
        val linePaint = Paint().apply { color = Color.BLACK; strokeWidth = 1f }
        canvas.drawLine(margin, y, widthDots - margin, y, linePaint)
        y += 6f

        // QRコード（管理番号をエンコード・中央寄せ）
        val qrSize = (widthDots * 0.55f).toInt().coerceIn(60, 110)
        val qrBitmap = generateQrCode(label.controlNumber, qrSize)
        val qrX = (widthDots - qrSize) / 2f
        canvas.drawBitmap(qrBitmap, qrX, y, null)
        y += qrSize + 6f

        // テキスト情報（管理番号 / Lot / 期限）
        val infoPaint = buildPaint((widthDots * 0.11f).coerceIn(7f, 14f))
        canvas.drawText("管理: ${label.controlNumber}", margin, y, infoPaint)
        y += infoPaint.textSize * 1.3f
        canvas.drawText("Lot: ${label.lotNumber}", margin, y, infoPaint)
        y += infoPaint.textSize * 1.3f
        canvas.drawText("期限: ${label.expiryDate}", margin, y, infoPaint)

        return bitmap
    }

    private fun generateQrCode(content: String, size: Int): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 0)
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    private fun buildPaint(textSize: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color    = Color.BLACK
        typeface = Typeface.MONOSPACE
        this.textSize = textSize
    }

    private fun drawWrapped(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
    ): Float {
        if (text.isEmpty()) return startY
        var y = startY
        var start = 0
        while (start < text.length) {
            val count = paint.breakText(text, start, text.length, true, maxWidth, null)
            if (count <= 0) break
            canvas.drawText(text, start, start + count, x, y, paint)
            start += count
            y += paint.textSize * 1.4f
        }
        return y
    }
}
