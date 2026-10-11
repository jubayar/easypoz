package net.redngreen.easypos.core.data.backup

import kotlinx.serialization.Serializable
import net.redngreen.easypos.core.data.journal.CashEntryPayload
import net.redngreen.easypos.core.data.journal.CategoryPayload
import net.redngreen.easypos.core.data.journal.DrawerCountPayload
import net.redngreen.easypos.core.data.journal.PaymentTypePayload
import net.redngreen.easypos.core.data.journal.ProductPayload
import net.redngreen.easypos.core.data.journal.SalePayload
import net.redngreen.easypos.core.data.journal.SettingPayload
import net.redngreen.easypos.core.data.journal.StockDeltaPayload
import net.redngreen.easypos.core.data.journal.TenantConfigPayload

/**
 * A user as stored in a manual backup (task 11.3): identity, role and state
 * but **never** the PIN salt/hash — the backup file is safe to copy off the
 * device (shop-settings spec).
 */
@Serializable
data class BackupUser(
    val id: String,
    val name: String,
    val role: String,
    val active: Boolean = true,
    val createdAt: String,
    val updatedAt: String,
)

/** Single-file manual backup of the whole local dataset (task 11.3). */
@Serializable
data class BackupDocument(
    val version: Int = 1,
    val exportedAt: String,
    val tenantConfig: TenantConfigPayload? = null,
    val users: List<BackupUser> = emptyList(),
    val categories: List<CategoryPayload> = emptyList(),
    val products: List<ProductPayload> = emptyList(),
    val sales: List<SalePayload> = emptyList(),
    val stockDeltas: List<StockDeltaPayload> = emptyList(),
    val cashEntries: List<CashEntryPayload> = emptyList(),
    val drawerCounts: List<DrawerCountPayload> = emptyList(),
    val paymentTypes: List<PaymentTypePayload> = emptyList(),
    val settings: List<SettingPayload> = emptyList(),
)
