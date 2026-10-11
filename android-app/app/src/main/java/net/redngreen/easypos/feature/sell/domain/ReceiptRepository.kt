package net.redngreen.easypos.feature.sell.domain

import net.redngreen.easypos.core.print.MonoBitmap
import net.redngreen.easypos.core.print.Receipt

/**
 * Assembles a printable [Receipt] from local data (task 10.1) and reads the
 * printer configuration (task 10.5). All local — the receipt is built and
 * printed with no network (receipts spec).
 */
interface ReceiptRepository {

    /** Printer settings the app needs at print time. */
    data class ReceiptConfig(
        val macAddress: String?,
        val widthMm: Int,
        val columns: Int,
        val drawerKick: Boolean,
    ) {
        val isConfigured: Boolean get() = !macAddress.isNullOrBlank()
    }

    suspend fun printConfig(): ReceiptConfig

    /** Builds the receipt for a sale; [logo] is rasterized by the caller (framework concern). */
    suspend fun buildReceipt(saleId: String, isReprint: Boolean, logo: MonoBitmap?): Receipt?
}
