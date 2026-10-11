package net.redngreen.easypos.feature.catalog.data

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import net.redngreen.easypos.core.money.MoneyFormatter
import net.redngreen.easypos.feature.catalog.domain.CatalogCsvRow

/**
 * Tolerant product-CSV parser per `docs/csv-import-format.md`:
 * - encodings: UTF-8 BOM, UTF-16 LE/BE BOM, then Windows-1252 fallback
 * - RFC 4180 quoting (quoted commas, doubled quotes), CRLF tolerated
 * - header required, any column order, unknown columns ignored
 * - rows are independent: a malformed row fails alone, never the file
 *
 * Pure Kotlin, no Android deps — fixture-driven unit tested (design D11).
 */
object CsvCatalogParser {

    data class RowFailure(
        val rowNumber: Int,
        val reason: String,
    )

    data class Result(
        val rows: List<CatalogCsvRow>,
        val failures: List<RowFailure>,
    ) {
        val okCount: Int get() = rows.size
        val failedCount: Int get() = failures.size
    }

    /** Whole-file failure (bad header / undecodable bytes). */
    class FormatException(message: String) : Exception(message)

    /** Decodes import bytes: BOM detection, then strict UTF-8, then Windows-1252. */
    fun decode(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        else -> runCatching {
            // Strict decode: malformed input must REPORT so the 1252 fallback
            // can fire (plain String() would silently emit U+FFFD).
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }.getOrElse { String(bytes, Charset.forName("Windows-1252")) }
    }

    /** Parses the full CSV text into good rows + per-row failures. */
    fun parse(text: String): Result {
        // A BOM that survived as a decoded char would poison the first header cell.
        val clean = text.removePrefix("\uFEFF")
        val records = parseRecords(clean)
        if (records.isEmpty()) throw FormatException("file is empty")

        val header = records.first()
            .map { it.trim().lowercase().replace(Regex("\\s+"), " ") }
        val col = { canonical: String -> indexOfColumn(header, canonical) }
        val nameIdx = col("name")
        val priceIdx = col("price")
        if (nameIdx < 0 || priceIdx < 0) {
            throw FormatException("header must contain 'name' and 'price' columns")
        }
        val barcodeIdx = col("barcode")
        val skuIdx = col("sku")
        val categoryIdx = col("category")
        val costIdx = col("cost")
        val activeIdx = col("active")
        val stockIdx = col("stock")
        val locationIdx = col("location")

        val rows = mutableListOf<CatalogCsvRow>()
        val failures = mutableListOf<RowFailure>()

        records.drop(1).forEachIndexed { index, record ->
            val rowNumber = index + 1
            val cell = { i: Int -> if (i in record.indices) record[i].trim() else "" }
            val blank = record.all { it.isBlank() }
            if (blank) return@forEachIndexed

            val name = cell(nameIdx)
            if (name.isEmpty()) {
                failures += RowFailure(rowNumber, "name is empty")
                return@forEachIndexed
            }

            val priceText = cell(priceIdx)
            val priceCents = try {
                MoneyFormatter.parseCents(priceText)
            } catch (e: NumberFormatException) {
                failures += RowFailure(rowNumber, "price '$priceText' is not a valid amount")
                return@forEachIndexed
            }
            if (priceCents < 0) {
                failures += RowFailure(rowNumber, "price must not be negative")
                return@forEachIndexed
            }

            val costCents = if (costIdx >= 0 && cell(costIdx).isNotEmpty()) {
                try {
                    MoneyFormatter.parseCents(cell(costIdx))
                } catch (e: NumberFormatException) {
                    failures += RowFailure(rowNumber, "cost '${cell(costIdx)}' is not a valid amount")
                    return@forEachIndexed
                }
            } else {
                0L
            }

            val active = if (activeIdx >= 0 && cell(activeIdx).isNotEmpty()) {
                when (cell(activeIdx).lowercase()) {
                    "true", "yes", "1" -> true
                    "false", "no", "0" -> false
                    else -> {
                        failures += RowFailure(rowNumber, "active '${cell(activeIdx)}' is not true/false")
                        return@forEachIndexed
                    }
                }
            } else {
                true
            }

            val stock = if (stockIdx >= 0 && cell(stockIdx).isNotEmpty()) {
                val parsed = cell(stockIdx).toLongOrNull()
                if (parsed == null) {
                    failures += RowFailure(rowNumber, "stock '${cell(stockIdx)}' is not a whole number")
                    return@forEachIndexed
                }
                parsed
            } else {
                null
            }

            rows += CatalogCsvRow(
                rowNumber = rowNumber,
                name = name,
                barcode = cell(barcodeIdx).ifEmpty { null },
                sku = cell(skuIdx).ifEmpty { null },
                category = cell(categoryIdx).ifEmpty { DEFAULT_CATEGORY },
                costCents = costCents,
                priceCents = priceCents,
                active = active,
                stock = stock,
                location = cell(locationIdx).ifEmpty { null },
            )
        }

        return Result(rows, failures)
    }

    /** RFC 4180 record splitter: quotes shield commas and newlines; "" is a quote. */
    internal fun parseRecords(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var field = StringBuilder()
        var record = mutableListOf<String>()
        var inQuotes = false

        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < text.length && text[i + 1] == '"') {
                            field.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        field.append(c)
                    }
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    record += field.toString()
                    field = StringBuilder()
                }
                c == '\r' -> {
                    // swallow; the following \n (if any) ends the record
                }
                c == '\n' -> {
                    record += field.toString()
                    field = StringBuilder()
                    records += record
                    record = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || record.isNotEmpty()) {
            record += field.toString()
            records += record
        }
        return records
    }

    const val DEFAULT_CATEGORY = "Uncategorized"

    /**
     * Header aliases — canonical column → accepted spellings (lowercased,
     * whitespace-collapsed). Covers Aronium's export names per the docs
     * ("Product Name", "Group", "Sell Price", "Purchase Price", …).
     */
    private val columnAliases: Map<String, List<String>> = mapOf(
        "name" to listOf("name", "product name", "item name", "product", "item"),
        "barcode" to listOf("barcode", "bar code", "upc", "ean"),
        "sku" to listOf("sku", "code", "product code"),
        "category" to listOf("category", "group", "product group", "department"),
        "cost" to listOf("cost", "cost price", "purchase price", "buy price"),
        "price" to listOf("price", "sell price", "sale price", "selling price", "retail price", "unit price"),
        "active" to listOf("active", "is active", "enabled"),
        "stock" to listOf("stock", "qty", "quantity", "on hand", "stock on hand", "inventory"),
        "location" to listOf("location", "shelf", "shelf location"),
    )

    private fun indexOfColumn(header: List<String>, canonical: String): Int =
        columnAliases[canonical].orEmpty().firstNotNullOfOrNull { alias ->
            header.indexOf(alias).takeIf { it >= 0 }
        } ?: -1
}
