package net.redngreen.easypos.core.data.journal

/** Entity/opType pairs the journal can carry — mirrors backend expectations. */
object JournalEntities {
    const val PRODUCT = "product"
    const val CATEGORY = "category"
    const val USER = "user"
    const val SALE = "sale"
    const val STOCK_DELTA = "stock_delta"
    const val CASH_ENTRY = "cash_entry"
    const val DRAWER_COUNT = "drawer_count"
    const val PAYMENT_TYPE = "payment_type"
    const val TENANT_CONFIG = "tenant_config"
    const val SETTING = "setting"

    const val UPSERT = "UPSERT"
    const val INSERT = "INSERT"
    const val VOID = "VOID"
}
