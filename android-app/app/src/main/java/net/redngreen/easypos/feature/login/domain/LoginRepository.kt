package net.redngreen.easypos.feature.login.domain

import kotlinx.coroutines.flow.Flow

/** The signed-in user as the lock screen sees it — no Room entity leakage. */
data class UserAccount(
    val id: String,
    val name: String,
    val role: String,
)

/**
 * Uniform lock-screen session: initial unlock, re-unlock after idle, and
 * quick-switch are the same gesture (user-access-control spec).
 */
interface LoginRepository {
    fun observeActiveUser(): Flow<UserAccount?>

    fun observeUnlockableUsers(): Flow<List<UserAccount>>

    /** null while loading — guards the first-run form against a false flash. */
    fun observeHasAnyUser(): Flow<Boolean?>

    /**
     * Whether anyone can actually unlock. A restored backup carries users with
     * no PIN hash (excluded by design), so a shop with only PIN-less users is
     * treated as first run again — this prevents a lockout after restore.
     */
    fun observeHasUnlockableUser(): Flow<Boolean?>

    /** Idle auto-lock (task 5.4) — configured via settings, default 5 min. */
    fun observeIdleLockMinutes(): Flow<Int>

    suspend fun login(userId: String, pin: String): Boolean

    /** First-run bootstrap: create the Admin, then unlock straight away. */
    suspend fun createFirstAdmin(name: String, pin: String): Boolean

    fun lockNow(detail: String)
}
