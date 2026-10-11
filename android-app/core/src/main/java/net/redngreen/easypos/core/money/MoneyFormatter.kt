package net.redngreen.easypos.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The only place money touches decimals (design D3): parse at the input edge,
 * format at the display edge. All computation stays in Long cents.
 */
object MoneyFormatter {

    /** "12.30", "RM 12.30", "1,234.50", "(9.50)" → cents. Throws on garbage. */
    fun parseCents(raw: String): Long {
        var text = raw.trim()
        if (text.isEmpty()) throw NumberFormatException("empty amount")

        var negative = false
        if (text.startsWith("(") && text.endsWith(")")) {
            negative = true
            text = text.removeSurrounding("(", ")").trim()
        }
        // Tolerate a currency prefix: "RM", "rm", "MYR", "RM$"
        text = text.replace(Regex("^(rm\\$|rm|myr)\\s*", RegexOption.IGNORE_CASE), "")
        // Strip thousands separators: 1,234.50 / 1.234,50 (Malaysian files use the former)
        text = if (text.contains('.') && text.contains(',')) {
            if (text.lastIndexOf('.') > text.lastIndexOf(',')) text.replace(",", "") // 1,234.50
            else text.replace(".", "").replace(',', '.') // 1.234,50
        } else {
            text.replace(",", "")
        }

        val value = BigDecimal(text)
            .setScale(2, RoundingMode.HALF_UP) // 3+ decimal inputs (rare CSVs) round to sen
            .movePointRight(2)
            .longValueExact()
        return if (negative) -value else value
    }

    /** 1230 → "12.30" (no currency marker). Locale-stable digits. */
    fun formatPlain(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = kotlin.math.abs(cents)
        return "%s%d.%02d".format(java.util.Locale.ROOT, sign, abs / 100, abs % 100)
    }

    /** 1230 → "RM 12.30" — the app/receipt display form (shop-settings spec). */
    fun format(cents: Long): String = "RM ${formatPlain(cents)}"

    fun add(a: Long, b: Long): Long = Math.addExact(a, b)

    fun multiply(qty: Int, unitCents: Long): Long = Math.multiplyExact(qty.toLong(), unitCents)

    /** Sales tax on an amount at basis-point rate (600 = 6%), HALF_UP per unit. */
    fun tax(amountCents: Long, rateBps: Int): Long =
        BigDecimal(amountCents)
            .multiply(BigDecimal(rateBps))
            .divide(BigDecimal(10_000), 0, RoundingMode.HALF_UP)
            .longValueExact()

    /** Change due; negative result means under-tender (caller blocks the sale). */
    fun change(tenderedCents: Long, totalCents: Long): Long =
        Math.subtractExact(tenderedCents, totalCents)

    /**
     * Gross margin in basis points (2083 = 20.83% → displays as 20.8%),
     * HALF_UP; null when there is no positive price to divide by.
     */
    fun marginBps(costCents: Long, priceCents: Long): Int? =
        if (priceCents <= 0) null
        else BigDecimal(Math.multiplyExact(priceCents - costCents, 10_000L))
            .divide(BigDecimal(priceCents), 0, RoundingMode.HALF_UP)
            .intValueExact()

    /** Quick-tender round-ups for a total: exact + next whole-ringgit steps. */
    fun quickTenders(totalCents: Long): List<Long> {
        val exactRinggit = (totalCents + 99) / 100 // next whole RM ≥ total
        val build = linkedSetOf(totalCents)
        if (exactRinggit * 100 > totalCents) build += exactRinggit * 100
        var step = exactRinggit + 1
        while (build.size < 4) {
            build += step * 100
            step++
        }
        return build.toList()
    }
}
