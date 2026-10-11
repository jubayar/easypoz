package net.redngreen.easypos.core.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** The signed-in account's token pair + cloud shop id. */
data class AuthTokens(
    val shopId: String,
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Long,
    val refreshExpiresAt: Long,
)

/**
 * Persists the shop's JWT pair. Kept in DataStore — never in the Room dataset,
 * so PIN hashes, tokens, and secrets are excluded from the manual backup file
 * by construction (design D9, shop-settings spec).
 */
interface TokenStore {
    suspend fun load(): AuthTokens?

    suspend fun save(tokens: AuthTokens)

    suspend fun clear()
}

private val Context.tokenDataStore by preferencesDataStore(name = "easypos_auth")

@Singleton
class DataStoreTokenStore @Inject constructor(
    private val context: Context,
) : TokenStore {

    override suspend fun load(): AuthTokens? {
        val prefs = context.tokenDataStore.data.first()
        val shopId = prefs[KEY_SHOP_ID] ?: return null
        val access = prefs[KEY_ACCESS] ?: return null
        val refresh = prefs[KEY_REFRESH] ?: return null
        return AuthTokens(
            shopId = shopId,
            accessToken = access,
            refreshToken = refresh,
            accessExpiresAt = prefs[KEY_ACCESS_EXP] ?: 0L,
            refreshExpiresAt = prefs[KEY_REFRESH_EXP] ?: 0L,
        )
    }

    override suspend fun save(tokens: AuthTokens) {
        context.tokenDataStore.edit { prefs ->
            prefs[KEY_SHOP_ID] = tokens.shopId
            prefs[KEY_ACCESS] = tokens.accessToken
            prefs[KEY_REFRESH] = tokens.refreshToken
            prefs[KEY_ACCESS_EXP] = tokens.accessExpiresAt
            prefs[KEY_REFRESH_EXP] = tokens.refreshExpiresAt
        }
    }

    override suspend fun clear() {
        context.tokenDataStore.edit { it.clear() }
    }

    companion object {
        private val KEY_SHOP_ID = stringPreferencesKey("shop_id")
        private val KEY_ACCESS = stringPreferencesKey("access_token")
        private val KEY_REFRESH = stringPreferencesKey("refresh_token")
        private val KEY_ACCESS_EXP = longPreferencesKey("access_expires_at")
        private val KEY_REFRESH_EXP = longPreferencesKey("refresh_expires_at")
    }
}
