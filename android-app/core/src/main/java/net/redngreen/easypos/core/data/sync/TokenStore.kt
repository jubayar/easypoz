package net.redngreen.easypos.core.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The signed-in account's token pair + cloud shop id. */
data class AuthTokens(
    val shopId: String,
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Long,
    val refreshExpiresAt: Long,
)

/**
 * Persists the shop's JWT pair. Kept in app-private SharedPreferences — never
 * in the Room dataset, so PIN hashes, tokens, and secrets are excluded from
 * the manual backup file by construction (design D9, shop-settings spec).
 */
interface TokenStore {
    suspend fun load(): AuthTokens?

    suspend fun save(tokens: AuthTokens)

    suspend fun clear()
}

@Singleton
class PrefsTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : TokenStore {

    override suspend fun load(): AuthTokens? {
        val prefs = prefs()
        val shopId = prefs.getString(KEY_SHOP_ID, null) ?: return null
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        return AuthTokens(
            shopId = shopId,
            accessToken = access,
            refreshToken = refresh,
            accessExpiresAt = prefs.getLong(KEY_ACCESS_EXP, 0L),
            refreshExpiresAt = prefs.getLong(KEY_REFRESH_EXP, 0L),
        )
    }

    override suspend fun save(tokens: AuthTokens) {
        prefs().edit()
            .putString(KEY_SHOP_ID, tokens.shopId)
            .putString(KEY_ACCESS, tokens.accessToken)
            .putString(KEY_REFRESH, tokens.refreshToken)
            .putLong(KEY_ACCESS_EXP, tokens.accessExpiresAt)
            .putLong(KEY_REFRESH_EXP, tokens.refreshExpiresAt)
            .apply()
    }

    override suspend fun clear() {
        prefs().edit().clear().apply()
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "easypos_auth"
        private const val KEY_SHOP_ID = "shop_id"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_ACCESS_EXP = "access_expires_at"
        private const val KEY_REFRESH_EXP = "refresh_expires_at"
    }
}
