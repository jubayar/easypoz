package net.redngreen.easypos.core.print

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Receipt rendering invariants (tasks 10.1/10.3): init, content, width, drawer kick. */
class ReceiptEscPosTest {

    private fun receipt(isCash: Boolean = true) = Receipt(
        shopName = "Kedai Jaya",
        saleNo = 42L,
        timestamp = 0L,
        cashierName = "Saliman",
        lines = listOf(ReceiptLine("Milo 1kg", 2, 1200, 2400)),
        subtotalCents = 2400,
        totalCents = 2400,
        paymentTypeName = if (isCash) "Cash" else "DuitNow QR",
        isCash = isCash,
        tenderedCents = if (isCash) 3000 else null,
        changeCents = if (isCash) 600 else null,
    )

    private fun text(bytes: ByteArray) = String(bytes, Charsets.ISO_8859_1)

    @Test
    fun startsWithInitAndRendersContent() {
        val bytes = ReceiptEscPos.render(receipt(), widthCols = 42, kickDrawer = false)
        assertTrue(bytes[0] == 0x1B.toByte() && bytes[1] == 0x40.toByte())
        assertTrue(text(bytes).contains("Kedai Jaya"))
        assertTrue(text(bytes).contains("TOTAL"))
        assertTrue(text(bytes).contains("Change"))
    }

    @Test
    fun drawerKickOnlyForCashWhenEnabled() {
        assertTrue(containsKick(ReceiptEscPos.render(receipt(isCash = true), 42, kickDrawer = true)))
        assertFalse(containsKick(ReceiptEscPos.render(receipt(isCash = false), 42, kickDrawer = true)))
        assertFalse(containsKick(ReceiptEscPos.render(receipt(isCash = true), 42, kickDrawer = false)))
    }

    @Test
    fun columnCountFollowsPaperWidth() {
        assertTrue(text(ReceiptEscPos.render(receipt(), 42, false)).contains("-".repeat(42)))
        assertTrue(text(ReceiptEscPos.render(receipt(), 32, false)).contains("-".repeat(32)))
    }

    private fun containsKick(bytes: ByteArray): Boolean =
        (0 until bytes.size - 1).any { bytes[it] == 0x1B.toByte() && bytes[it + 1] == 0x70.toByte() }
}
