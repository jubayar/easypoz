package net.redngreen.easypos.core.print

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import net.redngreen.easypos.core.money.MoneyFormatter
import net.redngreen.easypos.core.print.Receipt

/**
 * Renders a [Receipt] to a shareable PDF (task 10.4) — the printerless path
 * (receipts spec). The file lands in the cache and is handed to the system
 * share sheet via a FileProvider content URI.
 */
@Singleton
class PdfReceiptWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private enum class Align { LEFT, CENTER, RIGHT }
    private data class Row(val text: String, val bold: Boolean = false, val align: Align = Align.LEFT)

    fun write(receipt: Receipt, widthMm: Int = 80): File {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "receipt-${receipt.saleNo}-${System.currentTimeMillis()}.pdf")

        val pageWidth = widthMm * 72f / 25.4f
        val margin = 18f
        val leading = 12f
        val rows = buildRows(receipt)
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
                Align.CENTER -> (pageWidth - paint.measureText(row.text)) / 2f
                Align.RIGHT -> pageWidth - margin - paint.measureText(row.text)
                Align.LEFT -> margin
            }
            canvas.drawText(row.text, x, y, paint)
            y += leading
        }
        doc.finishPage(page)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** ACTION_SEND intent carrying the PDF as a content URI. */
    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun buildRows(receipt: Receipt): List<Row> {
        val rows = mutableListOf<Row>()
        val width = 48
        fun twoCol(left: String, right: String, bold: Boolean = false) {
            val gap = (width - left.length - right.length).coerceAtLeast(1)
            rows += Row(left + " ".repeat(gap) + right, bold)
        }

        rows += Row(receipt.shopName, bold = true, align = Align.CENTER)
        if (receipt.shopAddress.isNotBlank()) rows += Row(receipt.shopAddress, align = Align.CENTER)
        if (receipt.shopPhone.isNotBlank()) rows += Row(receipt.shopPhone, align = Align.CENTER)
        receipt.headerLine?.takeIf { it.isNotBlank() }?.let { rows += Row(it, align = Align.CENTER) }
        if (receipt.isReprint) rows += Row("*** REPRINT ***", align = Align.CENTER)
        rows += Row("-".repeat(width))

        val time = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .format(Instant.ofEpochMilli(receipt.timestamp).atZone(ZoneId.systemDefault()))
        twoCol("Sale #${receipt.saleNo}", time)
        receipt.cashierName?.takeIf { it.isNotBlank() }?.let { rows += Row("Cashier: $it") }
        rows += Row("-".repeat(width))

        receipt.lines.forEach { line ->
            rows += Row(line.name)
            twoCol("  ${line.qty} x ${MoneyFormatter.formatPlain(line.unitCents)}", MoneyFormatter.formatPlain(line.lineCents))
        }

        rows += Row("-".repeat(width))
        twoCol("Subtotal", MoneyFormatter.formatPlain(receipt.subtotalCents))
        if (receipt.taxCents != 0L) twoCol("Tax", MoneyFormatter.formatPlain(receipt.taxCents))
        twoCol("TOTAL", MoneyFormatter.formatPlain(receipt.totalCents), bold = true)
        receipt.paymentTypeName?.takeIf { it.isNotBlank() }?.let { rows += Row("Payment: $it") }
        if (receipt.isCash) {
            receipt.tenderedCents?.let { twoCol("Tendered", MoneyFormatter.formatPlain(it)) }
            receipt.changeCents?.let { twoCol("Change", MoneyFormatter.formatPlain(it)) }
        }

        rows += Row("")
        receipt.footer?.takeIf { it.isNotBlank() }?.let { rows += Row(it, align = Align.CENTER) }
        return rows
    }
}
