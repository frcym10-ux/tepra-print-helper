package com.reagent.tepraprint

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * [TepraLabel] の内容を印字用 Bitmap に描画するレンダラー。
 *
 * Bitmap レイアウト（TepraPrinter.sendTepraRaster に合わせる）:
 *   - Width  = テープを横切る方向（テープ幅のドット数）
 *   - Height = テープ送り方向（ラベル長のドット数）
 *
 * 解像度: TEPRA SR5500P の印字解像度 180 dpi ≈ 8 dots/mm（切り上げ）
 */
object TepraLabelRenderer {

    /** 印字解像度: 180 dpi ≈ 8 dots/mm */
    private const val DOTS_PER_MM = 8

    /** mm → ドット数変換 */
    fun mmToDots(mm: Int): Int = mm * DOTS_PER_MM

    /**
     * ラベルデータを Bitmap に描画して返す。
     * Bitmap の Width がテープ幅方向、Height がテープ送り方向となる。
     */
    fun render(label: TepraLabel): Bitmap {
        val widthDots  = mmToDots(label.tapeWidthMm)
        val heightDots = mmToDots(60) // ラベル長 60mm 固定

        val bitmap = Bitmap.createBitmap(widthDots, heightDots, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val margin    = widthDots * 0.05f
        val maxWidth  = widthDots - margin * 2

        // 管理番号（大きめ）
        val bigPaint = buildPaint((widthDots * 0.16f).coerceIn(10f, 24f))
        var y = bigPaint.textSize + margin
        canvas.drawText(label.controlNumber, margin, y, bigPaint)
        y += bigPaint.textSize * 0.4f

        // 区切り線
        val linePaint = Paint().apply { color = Color.BLACK; strokeWidth = 1f }
        canvas.drawLine(margin, y, widthDots - margin, y, linePaint)
        y += linePaint.strokeWidth + bigPaint.textSize * 0.4f

        // 試薬名（折り返し対応）
        val normalPaint = buildPaint((widthDots * 0.12f).coerceIn(8f, 18f))
        y = drawWrapped(canvas, label.reagentName, margin, y, maxWidth, normalPaint)
        y += normalPaint.textSize * 0.3f

        // ロット番号
        canvas.drawText("Lot: ${label.lotNumber}", margin, y, normalPaint)
        y += normalPaint.textSize * 1.4f

        // 有効期限
        canvas.drawText("Exp: ${label.expiryDate}", margin, y, normalPaint)

        return bitmap
    }

    // -------------------------------------------------------------------------

    private fun buildPaint(textSize: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.BLACK
        typeface  = Typeface.MONOSPACE
        this.textSize = textSize
    }

    /** テキストを maxWidth で折り返して描画し、描画後の y 座標を返す。 */
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
