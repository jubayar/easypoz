package net.redngreen.easypos.core.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Grabs [SyncEngine] inside the worker without pulling in the Hilt-Work
 * integration (one fewer dependency). The app already installs a Hilt
 * component; this entry point just reads from it.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerEntryPoint {
    fun syncEngine(): SyncEngine
}

/**
 * WorkManager entry point for background sync (task 12.3). Runs a single
 * [SyncEngine.syncOnce] pass; WorkManager applies the retry backoff
 * (exponential, seeded by the scheduler) on failure. Never invoked from the
 * sell path.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkerEntryPoint::class.java,
        )
        return when (val outcome = entry.syncEngine().syncOnce()) {
            is SyncOutcome.Success -> Result.success()
            // Nothing linked yet is not a failure — the app is offline-only.
            SyncOutcome.NotSignedIn -> Result.success()
            is SyncOutcome.Failure ->
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 5
    }
}
