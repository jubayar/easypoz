package net.redngreen.easypos

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.redngreen.easypos.core.data.sync.SyncScheduler
import net.redngreen.easypos.core.data.sync.SyncStatusRepository
import net.redngreen.easypos.core.data.sync.TokenStore

@HiltAndroidApp
class EasyPosApp : Application() {

    @Inject lateinit var syncScheduler: SyncScheduler

    @Inject lateinit var syncStatus: SyncStatusRepository

    @Inject lateinit var tokenStore: TokenStore

    @Inject lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Background sync triggers (task 12.3): never on the sell path.
        syncScheduler.ensurePeriodic()
        syncScheduler.startConnectivityObserver()
        appScope.launch {
            val signedIn = tokenStore.load() != null
            syncStatus.setSignedIn(signedIn)
            if (signedIn) syncScheduler.requestNow()
        }
    }
}
