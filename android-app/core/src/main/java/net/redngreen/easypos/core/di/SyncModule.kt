package net.redngreen.easypos.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.redngreen.easypos.core.data.LocalIdentity
import net.redngreen.easypos.core.data.dao.JournalDao
import net.redngreen.easypos.core.data.sync.AccountSyncRepository
import net.redngreen.easypos.core.data.sync.AuthRemoteDataSource
import net.redngreen.easypos.core.data.sync.HttpBackendApi
import net.redngreen.easypos.core.data.sync.HttpTransport
import net.redngreen.easypos.core.data.sync.LocalSyncStore
import net.redngreen.easypos.core.data.sync.PrefsTokenStore
import net.redngreen.easypos.core.data.sync.RemoteApplier
import net.redngreen.easypos.core.data.sync.RoomLocalSyncStore
import net.redngreen.easypos.core.data.sync.SyncEngine
import net.redngreen.easypos.core.data.sync.SyncRemoteDataSource
import net.redngreen.easypos.core.data.sync.SyncStatusRepository
import net.redngreen.easypos.core.data.sync.TokenStore
import net.redngreen.easypos.core.data.sync.UrlConnectionTransport

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideHttpTransport(): HttpTransport = UrlConnectionTransport()

    @Provides
    @Singleton
    fun provideSyncRemote(api: HttpBackendApi): SyncRemoteDataSource = api

    @Provides
    @Singleton
    fun provideAuthRemote(api: HttpBackendApi): AuthRemoteDataSource = api

    @Provides
    @Singleton
    fun provideTokenStore(store: PrefsTokenStore): TokenStore = store

    @Provides
    @Singleton
    fun provideLocalSyncStore(store: RoomLocalSyncStore): LocalSyncStore = store

    @Provides
    @Singleton
    fun provideSyncEngine(
        journalDao: JournalDao,
        store: LocalSyncStore,
        remote: SyncRemoteDataSource,
        auth: AuthRemoteDataSource,
        tokens: TokenStore,
        identity: LocalIdentity,
        applier: RemoteApplier,
        status: SyncStatusRepository,
    ): SyncEngine = SyncEngine(
        journalDao = journalDao,
        store = store,
        remote = remote,
        auth = auth,
        tokens = tokens,
        identity = identity,
        applier = applier,
        status = status,
    )

    @Provides
    @Singleton
    fun provideAccountSyncRepository(
        auth: AuthRemoteDataSource,
        tokens: TokenStore,
        identity: LocalIdentity,
        engine: SyncEngine,
        status: SyncStatusRepository,
    ): AccountSyncRepository = AccountSyncRepository(
        auth = auth,
        tokens = tokens,
        identity = identity,
        engine = engine,
        status = status,
    )
}
