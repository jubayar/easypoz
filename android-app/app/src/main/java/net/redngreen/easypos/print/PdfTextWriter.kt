package net.redngreen.easypos.print

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class PdfAlign { LEFT, CENTER, RIGHT }

/** One line of a generated PDF (monospace, optional emphasis + alignment). */
data class PdfRow(
    val text: String,
    val bold: Boolean = false,
    val align: PdfAlign = PdfAlign.LEFT,
)

/**
 * Writes a list of text rows into a single-page PDF (task 10.4 / 9.3) and
 * exposes it via a FileProvider content URI. Shared by receipts and day
 * summaries — the only difference between them is the row list.
 */
@Singleton
class PdfTextWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun write(rows: List<PdfRow>, fileName: String, widthMm: Int = 80): File {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, fileName)

        val pageWidth = widthMm * 72f / 25.4f
        val margin = 18f
        val leading = 12f
        val pageHeight = margin * 2 + rows.size * leading + 8f

        val doc = PdfDocument()
        val page = doc.startPage(
            PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), 1).create(),
        )
        val canvas = page.canvas
        val regular = Paint().apply {
            typeface = Typeface.MONOSPACE
            textSize = 9f
            color = android.graphics.Color.BLACK
        }
        val bold = Paint(regular).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        var y = margin + 10f
        for (row in rows) {
            val paint = if (row.bold) bold else regular
            val x = when (row.align) {
                PdfAlign.CENTER -> (pageWidth - paint.measureText(row.text)) / 2f
                PdfAlign.RIGHT -> pageWidth - margin - paint.measureText(row.text)
                PdfAlign.LEFT -> margin
            }
            canvas.drawText(row.text, x, y, paint)
            y += leading
        }
        doc.finishPage(page)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
