package net.redngreen.easypos.core.print

import net.redngreen.easypos.core.money.MoneyFormatter

/** Data for the end-of-day summary slip (task 9.3). */
data class DaySummaryPrint(
    val shopName: String,
    val dateLabel: String,
    val salesCount: Int,
    val salesTotalCents: Long,
    val byPaymentType: List<Pair<String, Long>>,
    val cashInCents: Long,
    val cashOutCents: Long,
    val netCashCents: Long,
    val averageBasketCents: Long,
    val topSellers: List<Pair<String, Long>>,
    val expectedDrawerCents: Long,
)

/** Renders the day summary to ESC/POS bytes for thermal print (task 9.3). */
object DaySummaryEscPos {
    fun render(summary: DaySummaryPrint, widthCols: Int): ByteArray {
        val b = EscPosBuilder()
        b.init()
        b.align(EscPosBuilder.Align.CENTER)
        b.bold(true).line(summary.shopName).bold(false)
        b.line("DAY SUMMARY")
        b.line(summary.dateLabel)

        b.align(EscPosBuilder.Align.LEFT)
        b.divider(widthCols)
        b.twoColumn("Sales", summary.salesCount.toString(), widthCols)
        b.twoColumn("Total", MoneyFormatter.formatPlain(summary.salesTotalCents), widthCols)
        b.twoColumn("Average basket", MoneyFormatter.formatPlain(summary.averageBasketCents), widthCols)

        if (summary.byPaymentType.isNotEmpty()) {
            b.divider(widthCols)
            b.line("By payment type")
            summary.byPaymentType.forEach { (name, total) ->
                b.twoColumn("  $name", MoneyFormatter.formatPlain(total), widthCols)
            }
        }

        b.divider(widthCols)
        b.twoColumn("Cash in", MoneyFormatter.formatPlain(summary.cashInCents), widthCols)
        b.twoColumn("Cash out", MoneyFormatter.formatPlain(summary.cashOutCents), widthCols)
        b.twoColumn("Net cash", MoneyFormatter.formatPlain(summary.netCashCents), widthCols)
        b.twoColumn("Expected drawer", MoneyFormatter.formatPlain(summary.expectedDrawerCents), widthCols)

        if (summary.topSellers.isNotEmpty()) {
            b.divider(widthCols)
            b.line("Top sellers")
            summary.topSellers.forEach { (name, units) ->
                b.twoColumn("  $name", units.toString(), widthCols)
            }
        }

        b.newline()
        b.align(EscPosBuilder.Align.CENTER)
        b.line("*** DAY SUMMARY ***")
        b.cut()
        return b.build()
    }
}
