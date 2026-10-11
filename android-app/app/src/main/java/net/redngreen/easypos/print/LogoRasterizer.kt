package net.redngreen.easypos.print

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import net.redngreen.easypos.core.data.dao.TenantConfigDao
import net.redngreen.easypos.core.print.MonoBitmap

/**
 * Rasterizes the shop logo (base64 PNG stored on the synced tenant config)
 * into the 1-bit form the ESC/POS `GS v 0` command needs (task 10.1).
 * Scaling/thresholding is a framework concern, so it lives here.
 */
@Singleton
class LogoRasterizer @Inject constructor(
    private val tenantConfigDao: TenantConfigDao,
) {
    suspend fun loadShopLogo(maxWidth: Int = 384): MonoBitmap? {
        val encoded = tenantConfigDao.get()?.logo?.takeIf { it.isNotBlank() } ?: return null
        val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull() ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return toMono(bitmap, maxWidth)
    }

    /** Scales to [maxWidth] (preserving aspect), then thresholds to 1bpp, MSB-first. */
    fun toMono(source: Bitmap, maxWidth: Int): MonoBitmap {
        val scale = if (source.width > maxWidth) maxWidth.toFloat() / source.width else 1f
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, width, height, true)

        val bytesPerRow = (width + 7) / 8
        val out = ByteArray(bytesPerRow * height)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val a = (pixel ushr 24) and 0xFF
                // Treat transparent pixels as white (paper), else darken below threshold.
                val luminance = (r * 299 + g * 587 + b * 114) / 1000
                val black = a > 32 && luminance < 128
                if (black) {
                    val index = y * bytesPerRow + (x / 8)
                    out[index] = (out[index].toInt() or (0x80 shr (x % 8))).toByte()
                }
            }
        }
        return MonoBitmap(width, height, out)
    }
}
