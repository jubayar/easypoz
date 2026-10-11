package net.redngreen.easypos.core.database

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import net.redngreen.easypos.core.database.dao.SyncStateDao
import net.redngreen.easypos.core.database.entity.SyncState

/**
 * The device's local identity: shopId (until a cloud account binds a real
 * tenant) and deviceId for journal rows. Both are generated on first use and
 * persisted in sync_state — the offline world must never wait on a server
 * round-trip to know who it is.
 */
@Singleton
class LocalIdentity @Inject constructor(
    private val syncStateDao: SyncStateDao,
) {
    suspend fun shopId(): String = ensure(KEY_SHOP_ID)

    suspend fun deviceId(): String = ensure(KEY_DEVICE_ID)

    /**
     * Adopts the backend-issued device id after `POST /device/register`. The
     * server's `canSync` gate keys on this id, so push batches must carry it;
     * journal rows created from here on are stamped with it too.
     */
    suspend fun bindDevice(deviceId: String) {
        syncStateDao.put(SyncState(key = KEY_DEVICE_ID, value = deviceId))
    }

    private suspend fun ensure(key: String): String {
        syncStateDao.get(key)?.let { return it }
        val id = UUID.randomUUID().toString()
        syncStateDao.put(SyncState(key = key, value = id))
        return id
    }

    companion object {
        const val KEY_SHOP_ID = "shop_id"
        const val KEY_DEVICE_ID = "device_id"
    }
}
