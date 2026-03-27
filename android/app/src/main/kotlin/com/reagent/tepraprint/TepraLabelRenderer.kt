package com.reagent.tepraprint

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * ラベルデータからBitmapを生成する。
 *
 * SR5500P (180dpi) のテープ別印刷可能領域:
 *   24mm → 128dot
 *   18mm → 108dot
 *   12mm →  72dot
 *    9mm →  54dot
 *    6mm →  36dot
 *    4mm →  22dot
 */
object TepraLabelRenderer {

    /** SR5500P の解像度 (180dpi ≈ 7.09 dots/mm) */
    private const val DPI = 180
    private const val DOTS_PER_MM = DPI.toFloat() / 25.4f

    /** ラベル長さ 40mm 固定 */
    private const val LABEL_LENGTH_MM = 40

    fun mmToDots(mm: Int): Int = (mm * DOTS_PER_MM).toInt()

    private fun printableDotsForTape(tapeWidthMm: Int): Int = when (tapeWidthMm) {
        24 -> 128
        18 -> 108
        12 -> 72
        9  -> 54
        6  -> 36
        4  -> 22
        else -> 108
    }

    /**
     * ラベルをBitmapとして描画する。
     *
     * レイアウト:
     * ┌────────────────────────────────┐
     * │ 試薬名（縮小して1行に収める）       │
     * │ ┌──────┐ 期限：YYYY/MM/DD     │
     * │ │QRコード│ Lot：XXXXX          │
     * │ │      │ 管理番号：XXXXXXXX    │
     * │ └──────┘                      │
     * └────────────────────────────────┘
     */
    fun render(label: TepraLabel): Bitmap {
        val tapeHeight = printableDotsForTape(label.tapeWidthMm)
        val tapeLength = mmToDots(LABEL_LENGTH_MM)

        val bitmap = Bitmap.createBitmap(tapeLength, tapeHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val margin = 4f

        // === 上部エリア: 試薬名 (高さの約35%) ===
        val topAreaHeight = (tapeHeight * 0.35f)
        val reagentName = label.reagentName
        val maxNameWidth = tapeLength - margin * 2

        // フォントサイズを自動縮小（収まるまで）
        var nameSize = topAreaHeight * 0.75f
        val namePaint = buildPaint(nameSize)
        while (nameSize > 6f && namePaint.measureText(reagentName) > maxNameWidth) {
            nameSize -= 0.5f
            namePaint.textSize = nameSize
        }

        // 試薬名を上部エリアの縦中央に描画
        val nameY = (topAreaHeight + nameSize) / 2f
        canvas.drawText(reagentName, margin, nameY, namePaint)

        // 区切り線
        val lineY = topAreaHeight
        val linePaint = Paint().apply { color = Color.BLACK; strokeWidth = 1f }
        canvas.drawLine(margin, lineY, tapeLength - margin, lineY, linePaint)

        // === 下部エリア ===
        val bottomTop = lineY + 2f
        val bottomHeight = tapeHeight - bottomTop - margin

        // === 下部左: QRコード ===
        val qrSize = bottomHeight.toInt().coerceAtLeast(1)
        val qrBitmap = generateQrCode(label.controlNumber, qrSize)
        if (qrBitmap != null) {
            canvas.drawBitmap(qrBitmap, margin, bottomTop, null)
        }

        // === 下部右: 情報3行 ===
        val textLeft = margin + qrSize + margin * 2
        val textAreaWidth = tapeLength - textLeft - margin
        val lineSpacing = bottomHeight / 3f

        // フォントサイズ: 行間に収まるサイズ
        var infoSize = (lineSpacing * 0.75f).coerceAtMost(16f)
        val infoPaint = buildPaint(infoSize)

        val lines = listOf(
            "期限:${label.expiryDate}",
            "Lot:${label.lotNumber}",
            "管理番号:${label.controlNumber}"
        )

        // テキストが幅に収まるようサイズ調整
        val maxTextWidth = lines.maxOf { infoPaint.measureText(it) }
        if (maxTextWidth > textAreaWidth && textAreaWidth > 0) {
            infoSize *= (textAreaWidth / maxTextWidth)
            infoPaint.textSize = infoSize
        }

        for (i in lines.indices) {
            val y = bottomTop + lineSpacing * (i + 0.75f)
            canvas.drawText(lines[i], textLeft, y, infoPaint)
        }

        return bitmap
    }

    private fun buildPaint(textSize: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = Typeface.DEFAULT_BOLD
        this.textSize = textSize
    }

    /**
     * ZXing で管理番号からQRコードBitmapを生成する。
     */
    private fun generateQrCode(data: String, size: Int): Bitmap? {
        if (data.isBlank() || size <= 0) return null
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 0,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size, hints)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}
