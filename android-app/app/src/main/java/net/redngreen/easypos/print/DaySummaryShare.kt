package net.redngreen.easypos.print

import android.content.Intent
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import net.redngreen.easypos.core.print.PdfAlign
import net.redngreen.easypos.core.print.PdfRow
import net.redngreen.easypos.core.print.PdfTextWriter
import javax.inject.Singleton
import net.redngreen.easypos.core.data.repo.ReportsRepository
import net.redngreen.easypos.core.money.MoneyFormatter

/**
 * Renders the day summary (Z-report) to a shareable PDF (task 9.3) via the
 * shared [PdfTextWriter] — the printerless path for owner reports.
 */
@Singleton
class DaySummaryShare @Inject constructor(
    private val textWriter: PdfTextWriter,
) {
    private val dateLabel = DateTimeFormatter.ofPattern("d MMM yyyy")

    fun write(summary: ReportsRepository.DaySummary, shopName: String, epochDay: Long): File {
        val label = dateLabel.format(LocalDate.ofEpochDay(epochDay))
        val rows = buildList {
            add(PdfRow(shopName, bold = true, align = PdfAlign.CENTER))
            add(PdfRow("DAY SUMMARY", bold = true, align = PdfAlign.CENTER))
            add(PdfRow(label, align = PdfAlign.CENTER))
            add(PdfRow(""))
            two("Sales", summary.salesCount.toString())
            two("Total", MoneyFormatter.format(summary.salesTotalCents))
            two("Average basket", MoneyFormatter.format(summary.averageBasketCents))
            if (summary.byPaymentType.isNotEmpty()) {
                add(PdfRow(""))
                add(PdfRow("By payment type", bold = true))
                summary.byPaymentType.forEach {
                    two("  ${it.paymentTypeName} (${it.saleCount})", MoneyFormatter.format(it.totalCents))
                }
            }
            add(PdfRow(""))
            two("Cash sales", MoneyFormatter.format(summary.cashSalesCents))
            two("Cash in", MoneyFormatter.format(summary.cashInCents))
            two("Cash out", MoneyFormatter.format(summary.cashOutCents))
            two("Expected drawer", MoneyFormatter.format(summary.expectedDrawerCents))
            summary.drawerCounts.firstOrNull()?.let {
                add(PdfRow(""))
                two("Counted", MoneyFormatter.format(it.countedCents))
                two("Variance", MoneyFormatter.format(it.varianceCents))
            }
            if (summary.topSellers.isNotEmpty()) {
                add(PdfRow(""))
                add(PdfRow("Top sellers", bold = true))
                summary.topSellers.take(5).forEachIndexed { index, seller ->
                    two("  ${index + 1}. ${seller.name}", "${seller.qty} · ${MoneyFormatter.format(seller.totalCents)}")
                }
            }
            add(PdfRow(""))
            add(PdfRow("*** DAY SUMMARY ***", align = PdfAlign.CENTER))
        }
        return textWriter.write(rows, "day-summary-$epochDay.pdf")
    }

    fun shareIntent(file: File): Intent = textWriter.shareIntent(file)

    /** Monospace two-column line — mirrors EscPosBuilder.twoColumn at ~40 cols. */
    private fun MutableList<PdfRow>.two(label: String, value: String) {
        val cols = 40
        val clippedLabel = label.take((cols - value.length - 1).coerceAtLeast(1))
        add(PdfRow(clippedLabel.padEnd(cols - value.length) + value))
    }
}
