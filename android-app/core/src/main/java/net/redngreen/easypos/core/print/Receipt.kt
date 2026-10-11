package net.redngreen.easypos.core.print

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import net.redngreen.easypos.core.money.MoneyFormatter

/** One printable receipt line (already resolved — no domain references). */
data class ReceiptLine(
    val name: String,
    val qty: Int,
    val unitCents: Long,
    val lineCents: Long,
)

/**
 * Everything a receipt needs to print (receipts spec): shop identity, sale
 * attribution, lines, totals, tender, branding, reprint flag, optional logo.
 * Built by ReceiptRepository from Room; rendered to ESC/POS bytes here.
 */
data class Receipt(
    val shopName: String,
    val shopAddress: String = "",
    val shopPhone: String = "",
    val headerLine: String? = null,
    val footer: String? = null,
    val saleNo: Long,
    val timestamp: Long,
    val cashierName: String? = null,
    val lines: List<ReceiptLine>,
    val subtotalCents: Long,
    val taxCents: Long = 0L,
    val totalCents: Long,
    val paymentTypeName: String? = null,
    val isCash: Boolean = false,
    val tenderedCents: Long? = null,
    val changeCents: Long? = null,
    val isReprint: Boolean = false,
    val logo: MonoBitmap? = null,
)

/** Renders a [Receipt] to ESC/POS bytes for 58mm (32 cols) or 80mm (42 cols). */
object ReceiptEscPos {
    private val TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun render(receipt: Receipt, widthCols: Int, kickDrawer: Boolean): ByteArray {
        val b = EscPosBuilder()
        b.init()

        b.align(EscPosBuilder.Align.CENTER)
        receipt.logo?.let { b.logo(it); b.newline() }
        b.bold(true).line(receipt.shopName).bold(false)
        if (receipt.shopAddress.isNotBlank()) b.line(receipt.shopAddress)
        if (receipt.shopPhone.isNotBlank()) b.line(receipt.shopPhone)
        receipt.headerLine?.takeIf { it.isNotBlank() }?.let { b.line(it) }
        if (receipt.isReprint) b.line("*** REPRINT ***")

        b.align(EscPosBuilder.Align.LEFT)
        b.divider(widthCols)
        b.twoColumn("Sale #${receipt.saleNo}", time(receipt.timestamp), widthCols)
        receipt.cashierName?.takeIf { it.isNotBlank() }?.let { b.line("Cashier: $it") }
        b.divider(widthCols)

        for (line in receipt.lines) {
            b.line(line.name)
            b.twoColumn(
                "  ${line.qty} x ${MoneyFormatter.formatPlain(line.unitCents)}",
                MoneyFormatter.formatPlain(line.lineCents),
                widthCols,
            )
        }

        b.divider(widthCols)
        b.twoColumn("Subtotal", MoneyFormatter.formatPlain(receipt.subtotalCents), widthCols)
        if (receipt.taxCents != 0L) {
            b.twoColumn("Tax", MoneyFormatter.formatPlain(receipt.taxCents), widthCols)
        }
        b.bold(true)
            .twoColumn("TOTAL", MoneyFormatter.formatPlain(receipt.totalCents), widthCols)
            .bold(false)
        receipt.paymentTypeName?.takeIf { it.isNotBlank() }?.let { b.line("Payment: $it") }
        if (receipt.isCash) {
            receipt.tenderedCents?.let {
                b.twoColumn("Tendered", MoneyFormatter.formatPlain(it), widthCols)
            }
            receipt.changeCents?.let {
                b.twoColumn("Change", MoneyFormatter.formatPlain(it), widthCols)
            }
        }

        b.newline()
        b.align(EscPosBuilder.Align.CENTER)
        receipt.footer?.takeIf { it.isNotBlank() }?.let { b.line(it) }

        if (kickDrawer && receipt.isCash) b.kick()
        b.cut()
        return b.build()
    }

    private fun time(epochMillis: Long): String =
        TIME.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
}
