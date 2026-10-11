package net.redngreen.easypos.core.data.sync

import net.redngreen.easypos.core.data.LocalIdentity

/** Result of linking the tablet to a cloud shop account. */
sealed interface SignInOutcome {
    /** Linked + registered; [restored] ops were pulled to rebuild the dataset. */
    data class Success(val restored: Int) : SignInOutcome

    data class Failure(val message: String) : SignInOutcome
}

/**
 * Account + device lifecycle (tasks 12.5, and the sync half of 13.1/13.3):
 * request an OTP, verify it, register this tablet as the shop's active device
 * (demoting any other — one-active-tablet rule, design D5), adopt the
 * device-scoped token, then restore the full dataset from the cloud.
 *
 * Nothing here touches the sell path; the app remains fully usable offline
 * before an account is ever created.
 */
class AccountSyncRepository(
    private val auth: AuthRemoteDataSource,
    private val tokens: TokenStore,
    private val identity: LocalIdentity,
    private val engine: SyncEngine,
    private val status: SyncStatusRepository,
    private val deviceName: String = "EasyPOS tablet",
) {
    /** Step 1 of sign-in: send a one-time code to phone/email. */
    suspend fun requestCode(identifier: String, shopName: String? = null): RequestCodeResponse =
        auth.requestCode(identifier.trim(), shopName)

    /**
     * Step 2: verify the code, bind this device, and restore. On success the
     * tablet is the shop's active device and its dataset is rebuilt locally.
     */
    suspend fun signIn(identifier: String, code: String, shopName: String? = null): SignInOutcome {
        val normalized = identifier.trim()
        return try {
            val verified = auth.verify(normalized, code.trim(), shopName)
            tokens.save(
                AuthTokens(
                    shopId = verified.shopId,
                    accessToken = verified.accessToken,
                    refreshToken = verified.refreshToken,
                    accessExpiresAt = isoMillis(verified.accessExpiresAt ?: ""),
                    refreshExpiresAt = isoMillis(verified.refreshExpiresAt ?: ""),
                ),
            )
            status.setSignedIn(true)

            val device = auth.registerDevice(verified.accessToken, deviceName)
            identity.bindDevice(device.deviceId)
            tokens.save(
                AuthTokens(
                    shopId = verified.shopId,
                    accessToken = device.accessToken,
                    refreshToken = device.refreshToken,
                    accessExpiresAt = isoMillis(device.accessExpiresAt ?: ""),
                    refreshExpiresAt = isoMillis(device.refreshExpiresAt ?: ""),
                ),
            )

            when (val outcome = engine.restoreFromCloud()) {
                is SyncOutcome.Success -> SignInOutcome.Success(outcome.pulled)
                SyncOutcome.NotSignedIn -> SignInOutcome.Failure("sign-in did not persist")
                is SyncOutcome.Failure -> SignInOutcome.Failure(outcome.message)
            }
        } catch (e: Exception) {
            status.setSignedIn(false)
            SignInOutcome.Failure(
                if (e is ApiException) e.apiMessage else e.message ?: "sign-in failed",
            )
        }
    }

    /** Unlink this tablet: drops tokens (never the shop's data). */
    suspend fun signOut() {
        tokens.clear()
        status.setSignedIn(false)
    }
}
