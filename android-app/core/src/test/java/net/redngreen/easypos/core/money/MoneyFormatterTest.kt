package net.redngreen.easypos.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyFormatterTest {

    // ── parse ────────────────────────────────────────────────────────────────

    @Test
    fun parsePlainDecimals() {
        assertEquals(1230L, MoneyFormatter.parseCents("12.30"))
        assertEquals(30L, MoneyFormatter.parseCents("0.30"))
        assertEquals(0L, MoneyFormatter.parseCents("0"))
        assertEquals(5L, MoneyFormatter.parseCents(".05"))
    }

    @Test
    fun parseTolerantForms() {
        assertEquals(1230L, MoneyFormatter.parseCents("RM 12.30"))
        assertEquals(1230L, MoneyFormatter.parseCents("rm12.30"))
        assertEquals(1230L, MoneyFormatter.parseCents("MYR 12.30"))
        assertEquals(123450L, MoneyFormatter.parseCents("1,234.50"))
        assertEquals(123450L, MoneyFormatter.parseCents("1.234,50")) // European CSV surprise
        assertEquals(-950L, MoneyFormatter.parseCents("(9.50)"))
    }

    @Test
    fun parseRejectsGarbage() {
        assertThrows(NumberFormatException::class.java) { MoneyFormatter.parseCents("") }
        assertThrows(NumberFormatException::class.java) { MoneyFormatter.parseCents("abc") }
        assertThrows(ArithmeticException::class.java) { MoneyFormatter.parseCents("92233720368547758.08") } // one cent past Long.MAX
    }

    // ── format ───────────────────────────────────────────────────────────────

    @Test
    fun formatTwoDecimals() {
        assertEquals("RM 12.00", MoneyFormatter.format(1200))
        assertEquals("RM 8.50", MoneyFormatter.format(850))
        assertEquals("RM 0.30", MoneyFormatter.format(30))
        assertEquals("RM 2.50", MoneyFormatter.format(250))
    }

    @Test
    fun formatNegativeForVarianceDisplay() {
        assertEquals("-2.50", MoneyFormatter.formatPlain(-250))
    }

    // ── arithmetic ───────────────────────────────────────────────────────────

    @Test
    fun additionAndLineTotals() {
        assertEquals(850L, MoneyFormatter.add(300L, 550L))
        assertEquals(2400L, MoneyFormatter.multiply(2, 1200L))
        // 3 × RM 0.30 = RM 0.90 — the classic float failure case
        assertEquals(90L, MoneyFormatter.multiply(3, 30L))
    }

    @Test
    fun taxHalfUp() {
        assertEquals(51L, MoneyFormatter.tax(850L, 600)) // 8.50 × 6% = 0.51
        assertEquals(1L, MoneyFormatter.tax(17L, 600)) // 0.17 × 6% = 0.0102 → 0.01
        assertEquals(0L, MoneyFormatter.tax(8L, 600)) // 0.048 → 0.00
    }

    @Test
    fun changeMath() {
        assertEquals(150L, MoneyFormatter.change(1000L, 850L))
        assertEquals(0L, MoneyFormatter.change(850L, 850L))
        assertEquals(-500L, MoneyFormatter.change(500L, 1000L)) // under-tender signal
    }

    @Test
    fun valuationSums() {
        val stock = listOf(18L to 950L, 24L to 420L) // qty to costCents
        val atCost = stock.sumOf { (qty, cost) -> MoneyFormatter.multiply(qty.toInt(), cost) }
        // 18 × RM 9.50 = 171.00; 24 × RM 4.20 = 100.80; total RM 271.80
        assertEquals(27180L, atCost)
    }

    @Test
    fun quickTendersForSeventeenRinggit() {
        // Spec: total RM 17.00 → exact, RM 18, RM 19, RM 20
        assertEquals(listOf(1700L, 1800L, 1900L, 2000L), MoneyFormatter.quickTenders(1700L))
        // Exact ringgit total: no fake "round up to self" duplicate
        assertEquals(listOf(2000L, 2100L, 2200L, 2300L), MoneyFormatter.quickTenders(2000L))
        // Sen amounts round to the next ringgit first
        assertEquals(listOf(1750L, 1800L, 1900L, 2000L), MoneyFormatter.quickTenders(1750L))
    }

    // ── margin preview (product-catalog spec) ───────────────────────────────

    @Test
    fun marginBpsForMilo() {
        // cost 9.50, sale 12.00 → margin RM 2.50, 20.83% → 2083 bps → "20.8%"
        assertEquals(2083, MoneyFormatter.marginBps(950L, 1200L))
    }

    @Test
    fun marginBpsEdgeCases() {
        assertEquals(null, MoneyFormatter.marginBps(0L, 0L)) // no price to divide by
        assertEquals(0, MoneyFormatter.marginBps(1200L, 1200L)) // break-even
        assertEquals(10_000, MoneyFormatter.marginBps(0L, 300L)) // free goods, 100%
    }
}
