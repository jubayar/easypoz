package net.redngreen.easypos.core.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import net.redngreen.easypos.core.database.LocalIdentity
import net.redngreen.easypos.core.database.dao.TenantConfigDao
import net.redngreen.easypos.core.database.entity.TenantConfig
import net.redngreen.easypos.core.database.journal.JournalEntities
import net.redngreen.easypos.core.database.journal.JournalizedWriter
import net.redngreen.easypos.core.database.journal.tenantConfigPayload

/**
 * Shop profile + language (shop-settings spec, task 11.1). Profile changes are
 * Admin-gated by the caller (MANAGE_SETTINGS) and journaled (TENANT_CONFIG).
 */
@Singleton
class TenantConfigRepository @Inject constructor(
    private val tenantConfigDao: TenantConfigDao,
    private val identity: LocalIdentity,
    private val writer: JournalizedWriter,
) {
    fun observe(): Flow<TenantConfig?> = tenantConfigDao.observe()

    suspend fun saveProfile(
        name: String,
        address: String,
        phone: String,
        logo: String?,
        actorUserId: String?,
    ) {
        val existing = tenantConfigDao.get()
        val config = TenantConfig(
            shopId = identity.shopId(),
            name = name.trim(),
            address = address.trim(),
            phone = phone.trim(),
            logo = logo,
            currency = existing?.currency ?: "MYR",
            language = existing?.language ?: "en",
            updatedAt = System.currentTimeMillis(),
        )
        write(config, actorUserId)
    }

    suspend fun setLanguage(language: String, actorUserId: String?) {
        val config = (tenantConfigDao.get() ?: TenantConfig(shopId = identity.shopId())).copy(
            language = language,
            updatedAt = System.currentTimeMillis(),
        )
        write(config, actorUserId)
    }

    private suspend fun write(config: TenantConfig, actorUserId: String?) {
        writer.journalized(
            shopId = identity.shopId(),
            deviceId = identity.deviceId(),
            entity = JournalEntities.TENANT_CONFIG,
            opType = JournalEntities.UPSERT,
            actorUserId = actorUserId,
            payloadJson = writer.encode(tenantConfigPayload(config)),
        ) {
            tenantConfigDao.upsert(config)
        }
    }
}
