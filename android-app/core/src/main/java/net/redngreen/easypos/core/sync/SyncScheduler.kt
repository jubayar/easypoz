package net.redngreen.easypos.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager

/**
 * The three sync triggers from design D5 / task 12.3 — periodic WorkManager,
 * connectivity callbacks, and app foreground — none of which is ever on the
 * sell path. All requests carry the "connected" constraint, so a queued sync
 * simply waits out an outage.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val status: SyncStatusRepository,
) {
    private val workManager get() = WorkManager.getInstance(context)

    private val connectivity get() =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Enqueues the 15-minute periodic sync (WorkManager's floor). Idempotent. */
    fun ensurePeriodic() {
        val request =
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(connected())
                .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Requests an immediate one-shot sync; keeps an already-queued one. */
    fun requestNow() {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(connected())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        workManager.enqueueUniqueWork(UNIQUE_NOW, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Mirrors connectivity into honest sync status and kicks a sync the moment
     * the network returns. Call once from Application start.
     */
    fun startConnectivityObserver() {
        status.setOnline(isOnline())
        connectivity.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    status.setOnline(true)
                    requestNow()
                }

                override fun onLost(network: Network) {
                    status.setOnline(isOnline())
                }
            }
        )
    }

    private fun isOnline(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun connected() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    companion object {
        const val UNIQUE_PERIODIC = "easypos-sync-periodic"
        const val UNIQUE_NOW = "easypos-sync-now"
    }
}
