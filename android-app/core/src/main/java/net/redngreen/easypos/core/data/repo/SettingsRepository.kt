package net.redngreen.easypos.core.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import net.redngreen.easypos.core.data.LocalIdentity
import net.redngreen.easypos.core.data.dao.SettingDao
import net.redngreen.easypos.core.data.entity.Setting
import net.redngreen.easypos.core.data.journal.JournalEntities
import net.redngreen.easypos.core.data.journal.JournalizedWriter
import net.redngreen.easypos.core.data.journal.settingPayload

/**
 * Shop-level scalar settings (printer MAC/width, drawer kick, receipt
 * header/footer, idle lock, …). Writes are journaled so they sync and restore
 * with the rest of the shop (design D4 settings entity). Image settings
 * (logo, merchant QR) are handled separately — too large for the scalar row.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val settingDao: SettingDao,
    private val identity: LocalIdentity,
    private val writer: JournalizedWriter,
) {
    fun observe(key: String): Flow<String?> = settingDao.observe(key)

    suspend fun get(key: String): String? = settingDao.get(key)

    suspend fun put(key: String, value: String, actorUserId: String?) {
        val now = System.currentTimeMillis()
        val setting = Setting(key = key, value = value, updatedAt = now)
        writer.journalized(
            shopId = identity.shopId(),
            deviceId = identity.deviceId(),
            entity = JournalEntities.SETTING,
            opType = JournalEntities.UPSERT,
            actorUserId = actorUserId,
            payloadJson = writer.encode(settingPayload(setting)),
        ) {
            settingDao.upsert(setting)
        }
    }

    /**
     * Local-only write for values too large to sync as a scalar setting row
     * (base64 logos / merchant QR). In v1 these travel in the manual backup
     * file, not the op journal (design D4 settings entity is scalar).
     */
    suspend fun putLocal(key: String, value: String?) {
        if (value.isNullOrEmpty()) {
            settingDao.delete(key)
        } else {
            settingDao.upsert(Setting(key = key, value = value, updatedAt = System.currentTimeMillis()))
        }
    }
}
