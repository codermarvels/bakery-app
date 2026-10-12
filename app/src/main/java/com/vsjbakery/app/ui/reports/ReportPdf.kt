package com.vsjbakery.app.ui.reports

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.vsjbakery.app.domain.model.BAKERY_ZONE
import com.vsjbakery.app.domain.model.ReportLayout
import com.vsjbakery.app.domain.model.ReportTable
import com.vsjbakery.app.domain.model.SalesReport
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Draws a sales report onto A4 pages with Android's built-in PDF writer (no extra library),
 * using the same tables as the screen so the two always match.
 */
object ReportPdf {
    private const val PAGE_W = 595 // A4 in points
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val LINE = 16f

    private val BROWN = Color.rgb(0x5D, 0x40, 0x37)
    private val GOLD = Color.rgb(0xB8, 0x86, 0x0B)
    private val GREY = Color.rgb(0x75, 0x75, 0x75)

    fun write(context: Context, report: SalesReport): File {
        val doc = PdfDocument()
        val writer = PageWriter(doc)
        try {
            writer.header(report)
            ReportLayout.tables(report).forEach { writer.table(it) }
            writer.finish()
            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // keep only the latest export
            val file = File(dir, "VSJ-Bakery-sales-${report.range.from}_to_${report.range.to}.pdf")
            file.outputStream().use { doc.writeTo(it) }
            return file
        } finally {
            doc.close()
        }
    }

    /** Opens Android's share sheet (WhatsApp, Gmail, Drive, Files, …) for the PDF. */
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "VSJ BAKERY sales report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Share sales report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private class PageWriter(private val doc: PdfDocument) {
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var y = 0f
        private val canvas: Canvas get() = page!!.canvas

        private val title = paint(18f, BROWN, bold = true)
        private val heading = paint(12.5f, BROWN, bold = true)
        private val headerCell = paint(9.5f, GREY, bold = true)
        private val cell = paint(10f, Color.BLACK)
        private val small = paint(8.5f, GREY)
        private val rule = Paint().apply { color = GOLD; strokeWidth = 1f }

        init { newPage() }

        private fun newPage() {
            page?.let { finishPage(it) }
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            y = MARGIN
        }

        private fun finishPage(p: PdfDocument.Page) {
            p.canvas.drawText("VSJ BAKERY · page $pageNumber", MARGIN, PAGE_H - 20f, small)
            doc.finishPage(p)
        }

        private fun ensure(space: Float) {
            if (y + space > PAGE_H - MARGIN) newPage()
        }

        fun header(r: SalesReport) {
            y += 10f
            canvas.drawText("VSJ BAKERY — Sales report", MARGIN, y, title)
            y += LINE + 4f
            canvas.drawText("Period: ${r.range.label}", MARGIN, y, cell)
            y += LINE
            val generated = ZonedDateTime.now(BAKERY_ZONE)
                .format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH))
            canvas.drawText("Generated: $generated (IST) · amounts in ₹", MARGIN, y, small)
            y += 8f
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
            y += 18f
        }

        fun table(t: ReportTable) {
            ensure(LINE * 4)
            canvas.drawText(t.title, MARGIN, y, heading)
            y += LINE
            val columns = columnStarts(t.headers.size)
            drawRow(t.headers, columns, headerCell)
            canvas.drawLine(MARGIN, y - 11f, PAGE_W - MARGIN, y - 11f, rule.apply { alpha = 90 })
            rule.alpha = 255
            if (t.rows.isEmpty()) {
                canvas.drawText(t.emptyText, MARGIN, y, small)
                y += LINE
            }
            t.rows.forEach { row ->
                ensure(LINE)
                drawRow(row, columns, cell)
            }
            t.note?.let {
                ensure(LINE)
                canvas.drawText(it, MARGIN, y, small)
                y += LINE
            }
            y += 10f
        }

        private var firstWidth = 0f

        /**
         * The first column is wide (names) and left-aligned; for it the list holds its left
         * edge. The other columns are right-aligned numbers; for them it holds the right edge.
         */
        private fun columnStarts(count: Int): List<Float> {
            val width = PAGE_W - 2 * MARGIN
            firstWidth = if (count == 2) width * 0.55f else width * 0.40f
            if (count <= 1) return listOf(MARGIN)
            val rest = (width - firstWidth) / (count - 1)
            return List(count) { i -> if (i == 0) MARGIN else MARGIN + firstWidth + rest * i }
        }

        private fun drawRow(values: List<String>, columns: List<Float>, paint: Paint) {
            values.forEachIndexed { i, value ->
                if (i == 0) {
                    val maxWidth = if (columns.size > 1) firstWidth - 8f else PAGE_W - 2 * MARGIN
                    canvas.drawText(ellipsize(value, paint, maxWidth), MARGIN, y, paint)
                } else {
                    canvas.drawText(value, columns[i] - paint.measureText(value), y, paint)
                }
            }
            y += LINE
        }

        private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(text) <= maxWidth) return text
            var end = text.length
            while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
            return text.substring(0, end) + "…"
        }

        fun finish() {
            page?.let { finishPage(it) }
            page = null
        }

        private fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }
}
