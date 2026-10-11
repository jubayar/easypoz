package net.redngreen.easypos.core.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import net.redngreen.easypos.core.database.SettingsKeys
import net.redngreen.easypos.core.database.dao.PaymentTypeDao
import net.redngreen.easypos.core.database.dao.ProductDao
import net.redngreen.easypos.core.database.dao.SaleDao
import net.redngreen.easypos.core.database.dao.SettingDao
import net.redngreen.easypos.core.database.dao.TenantConfigDao
import net.redngreen.easypos.core.database.dao.UserDao
import net.redngreen.easypos.core.print.MonoBitmap
import net.redngreen.easypos.core.print.Receipt
import net.redngreen.easypos.core.print.ReceiptLine

/**
 * Assembles a printable [Receipt] from local data (task 10.1) and reads the
 * printer configuration (task 10.5). All local — the receipt is built and
 * printed with no network (receipts spec).
 */
@Singleton
class ReceiptRepository @Inject constructor(
    private val saleDao: SaleDao,
    private val productDao: ProductDao,
    private val userDao: UserDao,
    private val tenantConfigDao: TenantConfigDao,
    private val paymentTypeDao: PaymentTypeDao,
    private val settingDao: SettingDao,
) {
    /** Printer settings the app needs at print time. */
    data class PrintConfig(
        val macAddress: String?,
        val widthMm: Int,
        val columns: Int,
        val drawerKick: Boolean,
    ) {
        val isConfigured: Boolean get() = !macAddress.isNullOrBlank()
    }

    suspend fun printConfig(): PrintConfig {
        val mac = settingDao.get(SettingsKeys.PRINTER_MAC)?.takeIf { it.isNotBlank() }
        val widthMm = settingDao.get(SettingsKeys.PRINTER_WIDTH_MM)?.toIntOrNull()
            ?: SettingsKeys.PRINTER_WIDTH_DEFAULT_MM
        val kick = settingDao.get(SettingsKeys.DRAWER_KICK)?.toBooleanStrictOrNull() ?: true
        return PrintConfig(
            macAddress = mac,
            widthMm = widthMm,
            columns = SettingsKeys.columnsForWidthMm(widthMm),
            drawerKick = kick,
        )
    }

    /** Builds the receipt for a sale; [logo] is rasterized by the caller (framework concern). */
    suspend fun buildReceipt(saleId: String, isReprint: Boolean, logo: MonoBitmap?): Receipt? {
        val sale = saleDao.getById(saleId) ?: return null
        val lines = saleDao.linesFor(saleId)
        val config = tenantConfigDao.get()
        val payment = sale.paymentTypeId?.let { paymentTypeDao.getById(it) }
        val cashier = sale.userId?.let { userDao.getById(it)?.name }

        val receiptLines = lines.map { line ->
            val name = line.customName
                ?: line.productId?.let { productDao.getById(it)?.name }
                ?: "Item"
            ReceiptLine(
                name = name,
                qty = line.qty,
                unitCents = line.unitCents,
                lineCents = line.lineCents,
            )
        }
        val subtotal = receiptLines.sumOf { it.lineCents }

        return Receipt(
            shopName = config?.name?.takeIf { it.isNotBlank() } ?: "EasyPOS",
            shopAddress = config?.address.orEmpty(),
            shopPhone = config?.phone.orEmpty(),
            headerLine = settingDao.get(SettingsKeys.RECEIPT_HEADER),
            footer = settingDao.get(SettingsKeys.RECEIPT_FOOTER),
            saleNo = sale.localNo,
            timestamp = sale.at,
            cashierName = cashier,
            lines = receiptLines,
            subtotalCents = subtotal,
            taxCents = (sale.totalCents - subtotal).coerceAtLeast(0L),
            totalCents = sale.totalCents,
            paymentTypeName = payment?.name,
            isCash = payment?.kind == "CASH",
            tenderedCents = sale.tenderedCents,
            changeCents = sale.changeCents,
            isReprint = isReprint,
            logo = logo,
        )
    }
}
