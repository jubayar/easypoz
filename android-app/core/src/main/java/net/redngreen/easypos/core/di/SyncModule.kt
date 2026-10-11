package net.redngreen.easypos.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton
import net.redngreen.easypos.core.database.LocalIdentity
import net.redngreen.easypos.core.database.dao.JournalDao
import net.redngreen.easypos.core.network.DataStoreTokenStore
import net.redngreen.easypos.core.network.HttpClientFactory
import net.redngreen.easypos.core.network.TokenStore
import net.redngreen.easypos.core.sync.AccountSyncRepository
import net.redngreen.easypos.core.sync.AuthRemoteDataSource
import net.redngreen.easypos.core.sync.HttpBackendApi
import net.redngreen.easypos.core.sync.LocalSyncStore
import net.redngreen.easypos.core.sync.RemoteApplier
import net.redngreen.easypos.core.sync.RoomLocalSyncStore
import net.redngreen.easypos.core.sync.SyncEngine
import net.redngreen.easypos.core.sync.SyncRemoteDataSource
import net.redngreen.easypos.core.sync.SyncStatusRepository

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClientFactory.create(OkHttp.create())

    @Provides
    @Singleton
    fun provideSyncRemote(api: HttpBackendApi): SyncRemoteDataSource = api

    @Provides
    @Singleton
    fun provideAuthRemote(api: HttpBackendApi): AuthRemoteDataSource = api

    @Provides
    @Singleton
    fun provideTokenStore(store: DataStoreTokenStore): TokenStore = store

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
