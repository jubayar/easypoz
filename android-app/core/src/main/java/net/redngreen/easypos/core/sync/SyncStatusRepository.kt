package net.redngreen.easypos.core.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import net.redngreen.easypos.core.database.dao.JournalDao

/** The four states the data-sync spec requires the app to surface, plus setup. */
enum class SyncPhase { SYNCED, PENDING, SYNCING, OFFLINE, ERROR }

data class SyncStatus(
    val phase: SyncPhase,
    val pending: Int,
    val signedIn: Boolean,
    val lastError: String? = null,
)

/**
 * Honest sync state (data-sync spec): never reports "synced" while ops remain
 * unconfirmed, and always carries the real pending count. Phase is derived
 * from live inputs (pending ops, connectivity, an in-flight run, last error)
 * rather than being set directly, so it can't drift from reality.
 */
@Singleton
class SyncStatusRepository @Inject constructor(
    journalDao: JournalDao,
    scope: CoroutineScope,
) {
    private val online = MutableStateFlow(true)
    private val running = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val signedIn = MutableStateFlow(false)

    private val _lastSyncedAt = MutableStateFlow<Long?>(null)
    val lastSyncedAt: StateFlow<Long?> = _lastSyncedAt.asStateFlow()

    val status: StateFlow<SyncStatus> =
        combine(
            journalDao.observePendingCount(),
            online,
            running,
            error,
            signedIn,
        ) { pending, isOnline, isRunning, err, isSignedIn ->
            SyncStatus(
                phase = when {
                    isRunning -> SyncPhase.SYNCING
                    !isOnline -> SyncPhase.OFFLINE
                    err != null -> SyncPhase.ERROR
                    pending == 0 -> SyncPhase.SYNCED
                    else -> SyncPhase.PENDING
                },
                pending = pending,
                signedIn = isSignedIn,
                lastError = err,
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), SyncStatus(SyncPhase.SYNCED, 0, false))

    fun setOnline(value: Boolean) {
        online.value = value
    }

    fun setSignedIn(value: Boolean) {
        signedIn.value = value
    }

    fun markSyncing() {
        error.value = null
        running.value = true
    }

    fun markDone() {
        running.value = false
        error.value = null
        _lastSyncedAt.value = System.currentTimeMillis()
    }

    fun markError(message: String) {
        running.value = false
        error.value = message
    }
}
