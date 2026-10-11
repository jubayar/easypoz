package net.redngreen.easypos.core.data.sync

import javax.inject.Inject
import javax.inject.Singleton
import net.redngreen.easypos.core.data.dao.SyncStateDao
import net.redngreen.easypos.core.data.entity.SyncState

/**
 * Device-local backend location. Lives in `sync_state` (never synced, never
 * in a backup). Defaults to the emulator loopback host so a dev backend on
 * the same machine is reachable out of the box; production overrides it.
 */
@Singleton
class SyncConfig @Inject constructor(
    private val syncStateDao: SyncStateDao,
) {
    suspend fun baseUrl(): String =
        syncStateDao.get(KEY_BASE_URL)?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_BASE_URL

    suspend fun setBaseUrl(url: String) {
        syncStateDao.put(SyncState(KEY_BASE_URL, url.trim().trimEnd('/')))
    }

    companion object {
        const val KEY_BASE_URL = "sync_base_url"

        /** Android emulator alias for the host machine's localhost. */
        const val DEFAULT_BASE_URL = "http://10.0.2.2:3000"
    }
}
