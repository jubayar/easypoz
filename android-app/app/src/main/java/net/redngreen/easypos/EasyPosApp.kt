package net.redngreen.easypos

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.redngreen.easypos.core.di.bridgeRepoModule
import net.redngreen.easypos.core.di.databaseModule
import net.redngreen.easypos.core.di.networkModule
import net.redngreen.easypos.core.di.printModule
import net.redngreen.easypos.core.di.sessionModule
import net.redngreen.easypos.core.di.syncModule
import net.redngreen.easypos.core.network.TokenStore
import net.redngreen.easypos.core.sync.SyncScheduler
import net.redngreen.easypos.core.sync.SyncStatusRepository
import net.redngreen.easypos.di.printControllerModule
import net.redngreen.easypos.feature.catalog.data.di.catalogDataModule
import net.redngreen.easypos.feature.catalog.ui.di.catalogUiModule
import net.redngreen.easypos.feature.login.data.di.loginDataModule
import net.redngreen.easypos.feature.login.ui.di.loginUiModule
import net.redngreen.easypos.feature.onboarding.data.di.onboardingDataModule
import net.redngreen.easypos.feature.onboarding.ui.di.onboardingUiModule
import net.redngreen.easypos.feature.sell.data.di.sellDataModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

@HiltAndroidApp
class EasyPosApp : Application() {

    @Inject lateinit var syncScheduler: SyncScheduler

    @Inject lateinit var syncStatus: SyncStatusRepository

    @Inject lateinit var tokenStore: TokenStore

    @Inject lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@EasyPosApp)
            modules(
                networkModule,
                databaseModule,
                syncModule,
                sessionModule,
                printModule,
                bridgeRepoModule,
                catalogDataModule,
                catalogUiModule,
                sellDataModule,
                printControllerModule,
                loginDataModule,
                loginUiModule,
                onboardingDataModule,
                onboardingUiModule,
            )
        }
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
