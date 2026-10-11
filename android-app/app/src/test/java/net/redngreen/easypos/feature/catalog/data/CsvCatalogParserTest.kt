package net.redngreen.easypos.feature.catalog.data

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures follow `docs/csv-import-format.md` (incl. its worked example). */
class CsvCatalogParserTest {

    private val canonicalCsv = """
        name,barcode,category,cost,price,active,stock,location
        Milo Activ-Go 1kg,8886467920123,DRINKS,9.50,12.00,true,18,Rak A-03
        Plastic bag (kecil),,UMUM,0.10,0.30,yes,500,
        Dabur Red Paste 200g,8901012000123,TOOTHPASTE,4.20,5.90,true,24,Rak B-11
        ,,BROKEN,1,2,true,,
    """.trimIndent()

    @Test
    fun `parses the documented example`() {
        val result = CsvCatalogParser.parse(canonicalCsv)

        assertEquals(3, result.okCount)
        assertEquals(1, result.failedCount)
        assertEquals(4, result.failures.first().rowNumber)
        assertEquals("name is empty", result.failures.first().reason)

        val milo = result.rows[0]
        assertEquals("Milo Activ-Go 1kg", milo.name)
        assertEquals("8886467920123", milo.barcode)
        assertEquals("DRINKS", milo.category)
        assertEquals(950L, milo.costCents)
        assertEquals(1200L, milo.priceCents)
        assertEquals(true, milo.active)
        assertEquals(18L, milo.stock)
        assertEquals("Rak A-03", milo.location)

        val bag = result.rows[1]
        assertEquals(null, bag.barcode)
        assertEquals(10L, bag.costCents)
        assertEquals(30L, bag.priceCents)
        assertEquals(500L, bag.stock)
        assertEquals(null, bag.location)
    }

    @Test
    fun `header order is free and unknown columns are ignored`() {
        val csv = """
            Product Name,Group,Extra1,Sell Price
            Kari Ayam 50g,GROCERIES,whatever,3.90
        """.trimIndent()
        val result = CsvCatalogParser.parse(csv)
        assertEquals(1, result.okCount)
        val row = result.rows.first()
        assertEquals("Kari Ayam 50g", row.name)
        assertEquals("GROCERIES", row.category)
        assertEquals(390L, row.priceCents)
    }

    @Test
    fun `header without name or price is a format error`() {
        assertThrows(CsvCatalogParser.FormatException::class.java) {
            CsvCatalogParser.parse("barcode,category\n1,FOO")
        }
    }

    @Test
    fun `money forms tolerated per docs`() {
        val csv = """
            name,price,cost
            A,"RM 12.30",0
            B,"1,234.50",0
            C,"myr 5",0
        """.trimIndent()
        val result = CsvCatalogParser.parse(csv)
        assertEquals(3, result.okCount)
        assertEquals(1230L, result.rows[0].priceCents)
        assertEquals(123450L, result.rows[1].priceCents)
        assertEquals(500L, result.rows[2].priceCents)
    }

    @Test
    fun `negative price fails its row`() {
        val csv = "name,price\nA,(9.50)"
        val result = CsvCatalogParser.parse(csv)
        assertEquals(0, result.okCount)
        assertEquals("price must not be negative", result.failures.first().reason)
    }

    @Test
    fun `row with unparseable price fails alone`() {
        val csv = """
            name,price
            Good,2.00
            Bad,free
            Also good,3.00
        """.trimIndent()
        val result = CsvCatalogParser.parse(csv)
        assertEquals(2, result.okCount)
        assertEquals(listOf(2), result.failures.map { it.rowNumber })
        assertTrue(result.failures.first().reason.contains("free"))
    }

    @Test
    fun `active tolerance`() {
        val csv = """
            name,price,active
            A,1.00,TRUE
            B,1.00,no
            C,1.00,0
            D,1.00,
            E,1.00,maybe
        """.trimIndent()
        val result = CsvCatalogParser.parse(csv)
        assertEquals(4, result.okCount)
        assertEquals(true, result.rows[0].active)
        assertEquals(false, result.rows[1].active)
        assertEquals(false, result.rows[2].active)
        assertEquals(true, result.rows[3].active) // blank → true
        assertEquals(5, result.failures.first().rowNumber) // unknown token fails
    }

    @Test
    fun `stock blank means unchanged null`() {
        val csv = "name,price,stock\nA,1.00,\nB,1.00,7\nC,1.00,x"
        val result = CsvCatalogParser.parse(csv)
        assertEquals(null, result.rows[0].stock)
        assertEquals(7L, result.rows[1].stock)
        assertEquals(3, result.failures.first().rowNumber)
    }

    @Test
    fun `blank category maps to uncategorized`() {
        val csv = "name,price,category\nA,1.00,"
        assertEquals("Uncategorized", CsvCatalogParser.parse(csv).rows.first().category)
    }

    @Test
    fun `quoted commas and doubled quotes parse per rfc4180`() {
        val csv = """
            name,price,location
            "Milo, 1kg big pack",12.00,"Rak ""A"", belakang"
        """.trimIndent()
        val row = CsvCatalogParser.parse(csv).rows.first()
        assertEquals("Milo, 1kg big pack", row.name)
        assertEquals("Rak \"A\", belakang", row.location)
    }

    @Test
    fun `crlf and empty rows and bom are tolerated`() {
        val text = "﻿name,price\r\n\r\nA,1.00\r\n\r\nB,2.00\r\n"
        val result = CsvCatalogParser.parse(text)
        assertEquals(2, result.okCount)
        assertEquals(0, result.failedCount)
    }

    @Test
    fun `decodes utf8 bom utf16 and falls back to windows1252`() {
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "name,price\nCafé,1.00".toByteArray(StandardCharsets.UTF_8)
        assertEquals("Café", CsvCatalogParser.parse(CsvCatalogParser.decode(utf8Bom)).rows.first().name)

        val utf16Le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "name,price\nCafé,1.00".toByteArray(StandardCharsets.UTF_16LE)
        assertEquals("Café", CsvCatalogParser.parse(CsvCatalogParser.decode(utf16Le)).rows.first().name)

        // 0xE9 is é in Windows-1252 but invalid UTF-8 → parser must fall back.
        val cp1252 = "name,price\nCaf".toByteArray(StandardCharsets.UTF_8) +
            byteArrayOf(0xE9.toByte()) + ",1.00".toByteArray(StandardCharsets.UTF_8)
        assertEquals("Café", CsvCatalogParser.parse(CsvCatalogParser.decode(cp1252)).rows.first().name)
    }
}
