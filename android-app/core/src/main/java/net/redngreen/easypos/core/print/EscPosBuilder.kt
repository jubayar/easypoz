package net.redngreen.easypos.core.print

import java.io.ByteArrayOutputStream

/**
 * A 1-bit-per-pixel, row-major bitmap (MSB first, stride = ceil(width/8)).
 * Rasterizing an Android Bitmap is a framework concern; the core only needs
 * the raw mono pixels to emit `GS v 0`.
 */
class MonoBitmap(
    val width: Int,
    val height: Int,
    val bytes: ByteArray,
)

/**
 * Minimal ESC/POS byte DSL (design D7). Deliberately tiny: init, alignment,
 * emphasis, text, logo raster, drawer kick, cut — enough for receipts and day
 * summaries on the cheap 58/80mm printers Malaysian shops actually buy.
 */
class EscPosBuilder {
    enum class Align(val code: Int) { LEFT(0), CENTER(1), RIGHT(2) }

    private val out = ByteArrayOutputStream()
    private val charset = Charsets.ISO_8859_1

    fun raw(bytes: ByteArray): EscPosBuilder = apply { out.write(bytes) }

    /** ESC @ — reset printer to defaults. */
    fun init(): EscPosBuilder = apply { raw(byteArrayOf(0x1B, 0x40)) }

    /** ESC a n — alignment. */
    fun align(align: Align): EscPosBuilder = apply { raw(byteArrayOf(0x1B, 0x61, align.code.toByte())) }

    /** ESC E n — emphasis on/off. */
    fun bold(on: Boolean): EscPosBuilder = apply { raw(byteArrayOf(0x1B, 0x45, if (on) 1 else 0)) }

    fun text(value: String): EscPosBuilder = apply { raw(value.toByteArray(charset)) }

    fun line(value: String): EscPosBuilder = apply { text(value); newline() }

    fun newline(count: Int = 1): EscPosBuilder = apply { repeat(count) { raw(byteArrayOf(0x0A)) } }

    fun divider(widthCols: Int, ch: Char = '-'): EscPosBuilder = line(ch.toString().repeat(widthCols))

    /** Left/right justified pair padded to the paper width. */
    fun twoColumn(left: String, right: String, widthCols: Int): EscPosBuilder {
        val gap = (widthCols - left.length - right.length).coerceAtLeast(1)
        return line(left + " ".repeat(gap) + right)
    }

    /** GS v 0 — raster bit image (logo). */
    fun logo(bitmap: MonoBitmap): EscPosBuilder {
        val bytesPerRow = (bitmap.width + 7) / 8
        raw(
            byteArrayOf(
                0x1D, 0x76, 0x30, 0x00,
                (bytesPerRow and 0xFF).toByte(),
                ((bytesPerRow shr 8) and 0xFF).toByte(),
                (bitmap.height and 0xFF).toByte(),
                ((bitmap.height shr 8) and 0xFF).toByte(),
            )
        )
        raw(bitmap.bytes)
        return this
    }

    /** ESC p m t1 t2 — pulse the cash drawer kick pin. */
    fun kick(pin: Int = 0): EscPosBuilder = apply {
        raw(byteArrayOf(0x1B, 0x70, pin.toByte(), 25, 250.toByte()))
    }

    /** GS V B n — partial cut. */
    fun cut(): EscPosBuilder = apply { newline(3); raw(byteArrayOf(0x1D, 0x56, 0x42, 0x00)) }

    fun build(): ByteArray = out.toByteArray()
}
