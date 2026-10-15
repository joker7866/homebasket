package com.homebasket.core.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File

data class PdfTable(
    val headers: List<String>,
    /** Relative column widths. */
    val weights: List<Float>,
    val rows: List<List<String>>,
    /** Column drawn as an empty tick box for handwriting (e.g. "Bought"). */
    val checkboxColumn: Int? = null,
    /** Extra empty rows at the end for handwritten items. */
    val blankRows: Int = 0,
)

data class PdfSection(val title: String?, val paragraphs: List<String> = emptyList(), val table: PdfTable? = null)

data class PdfSpec(
    val title: String,
    val subtitleLines: List<String>,
    val sections: List<PdfSection>,
    val footerLines: List<String> = emptyList(),
    val footnote: String? = null,
)

/**
 * Renders a simple, printer-friendly A4 portrait document in black and white.
 * Text is laid out with StaticLayout, which shapes complex scripts (Tamil, Devanagari) using the
 * device's system fonts, so local-language product names print correctly.
 */
object PdfRenderer {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 36f
    private const val CELL_PAD = 4f

    fun render(spec: PdfSpec, out: File) {
        val doc = PdfDocument()
        val ctx = State(doc)
        try {
            ctx.newPage()
            ctx.text(spec.title, size = 18f, bold = true)
            spec.subtitleLines.forEach { ctx.text(it, size = 10f) }
            ctx.y += 6f
            ctx.rule()
            spec.sections.forEach { section ->
                section.title?.let { ctx.y += 8f; ctx.ensure(40f); ctx.text(it, size = 13f, bold = true) }
                section.paragraphs.forEach { ctx.text(it, size = 10f) }
                section.table?.let { ctx.table(it) }
            }
            if (spec.footerLines.isNotEmpty()) {
                ctx.y += 8f
                ctx.ensure(20f * spec.footerLines.size)
                ctx.rule()
                spec.footerLines.forEach { ctx.text(it, size = 11f, bold = true) }
            }
            spec.footnote?.let { ctx.y += 6f; ctx.text(it, size = 8f) }
            ctx.finishPage()
            out.parentFile?.mkdirs()
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    private class State(val doc: PdfDocument) {
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var pageNo = 0
        var y = MARGIN
        val width = PAGE_W - 2 * MARGIN
        val line = Paint().apply { color = Color.BLACK; strokeWidth = 0.6f; style = Paint.Style.STROKE }
        var currentTable: PdfTable? = null

        fun paint(size: Float, bold: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        fun newPage() {
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            canvas = page!!.canvas
            y = MARGIN
        }

        fun finishPage() {
            val c = canvas ?: return
            val p = paint(8f, false)
            c.drawText("HomeBasket  •  $pageNo", MARGIN, PAGE_H - 18f, p)
            doc.finishPage(page)
            page = null
            canvas = null
        }

        fun ensure(height: Float) {
            if (y + height > PAGE_H - MARGIN - 10f) {
                finishPage()
                newPage()
                currentTable?.let { header(it) }
            }
        }

        fun layout(text: String, width: Float, size: Float, bold: Boolean): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint(size, bold), width.toInt().coerceAtLeast(10))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()

        fun text(t: String, size: Float, bold: Boolean = false) {
            val l = layout(t, width, size, bold)
            ensure(l.height + 4f)
            val c = canvas!!
            c.save(); c.translate(MARGIN, y); l.draw(c); c.restore()
            y += l.height + 4f
        }

        fun rule() {
            canvas!!.drawLine(MARGIN, y, PAGE_W - MARGIN, y, line)
            y += 6f
        }

        fun columnWidths(t: PdfTable): List<Float> {
            val total = t.weights.sum()
            return t.weights.map { it / total * width }
        }

        fun header(t: PdfTable) {
            row(t, t.headers, bold = true, isHeader = true)
        }

        fun row(t: PdfTable, cells: List<String>, bold: Boolean = false, isHeader: Boolean = false, minHeight: Float = 0f) {
            val widths = columnWidths(t)
            val layouts = cells.mapIndexed { i, cell ->
                if (!isHeader && i == t.checkboxColumn) null else layout(cell, widths[i] - 2 * CELL_PAD, 10f, bold)
            }
            val h = maxOf(minHeight, (layouts.maxOfOrNull { it?.height ?: 0 } ?: 0) + 2 * CELL_PAD, 18f)
            if (!isHeader) ensure(h)
            val c = canvas!!
            var x = MARGIN
            widths.forEachIndexed { i, w ->
                c.drawRect(x, y, x + w, y + h, line)
                val l = layouts.getOrNull(i)
                if (l != null) {
                    c.save(); c.translate(x + CELL_PAD, y + CELL_PAD); l.draw(c); c.restore()
                } else if (i == t.checkboxColumn) {
                    val box = 10f
                    val bx = x + (w - box) / 2
                    val by = y + (h - box) / 2
                    c.drawRect(bx, by, bx + box, by + box, line)
                }
                x += w
            }
            y += h
        }

        fun table(t: PdfTable) {
            ensure(40f)
            currentTable = t
            header(t)
            t.rows.forEach { row(t, it) }
            repeat(t.blankRows) { row(t, List(t.headers.size) { "" }, minHeight = 20f) }
            currentTable = null
            y += 6f
        }
    }
}
