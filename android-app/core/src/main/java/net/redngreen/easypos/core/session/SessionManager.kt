package net.redngreen.easypos.core.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import net.redngreen.easypos.core.data.dao.ActivityLogDao
import net.redngreen.easypos.core.data.dao.UserDao
import net.redngreen.easypos.core.data.entity.ActivityLog
import net.redngreen.easypos.core.data.entity.User
import net.redngreen.easypos.core.model.Permission
import net.redngreen.easypos.core.model.Role
import net.redngreen.easypos.core.security.PinHasher

/**
 * The active user session (user-access-control spec). Locked = no active
 * user; the app process (and with it open carts and screen state) stays
 * alive — locking never destroys ViewModels. Quick-switch is just another
 * login: whoever's PIN unlocks becomes the active user.
 *
 * All failures are generic by design: a wrong PIN never reveals whether
 * the user exists. Every attempt — success, failure, denial — lands in
 * activity_log with attribution.
 */
@Singleton
class SessionManager @Inject constructor(
    private val userDao: UserDao,
    private val pinHasher: PinHasher,
    private val activityLogDao: ActivityLogDao,
    private val scope: CoroutineScope,
) {
    private val _activeUserId = MutableStateFlow<String?>(null)

    /** null = locked (or first run before any user exists). */
    val activeUserId: StateFlow<String?> = _activeUserId.asStateFlow()

    /** Reactive view of the active user row (renames propagate to the badge). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeUser: Flow<User?> = _activeUserId.flatMapLatest { id ->
        if (id == null) flowOf(null) else userDao.observeById(id)
    }

    enum class LoginResult {
        /** PIN accepted — user is now active. */
        SUCCESS,

        /** Generic rejection: wrong PIN, unknown user, or deactivated user. */
        REJECTED,
    }

    /**
     * Unlock, quick-switch, and initial login are one operation: present a
     * user id + that user's PIN.
     */
    suspend fun login(userId: String, pin: String): LoginResult {
        val user = userDao.getById(userId)
        val ok = user != null &&
            user.active &&
            user.pinSalt.isNotEmpty() &&
            pinHasher.verify(pin, user.pinSalt, user.pinHash)
        if (!ok) {
            activityLogDao.insert(
                ActivityLog(
                    userId = userId,
                    userName = user?.name,
                    type = ActivityLog.TYPE_LOGIN_FAILED,
                    action = ACTION_LOGIN,
                    detail = "PIN rejected",
                    at = now(),
                )
            )
            return LoginResult.REJECTED
        }
        _activeUserId.value = userId
        activityLogDao.insert(
            ActivityLog(
                userId = user.id,
                userName = user.name,
                type = ActivityLog.TYPE_LOGIN,
                action = ACTION_LOGIN,
                at = now(),
            )
        )
        return LoginResult.SUCCESS
    }

    /** Lock (manual or idle) — session state elsewhere is untouched. */
    fun lock(detail: String? = null) {
        val userId = _activeUserId.value ?: return
        _activeUserId.value = null
        scope.launch {
            activityLogDao.insert(
                ActivityLog(
                    userId = userId,
                    type = ActivityLog.TYPE_LOCK,
                    action = ACTION_LOCK,
                    detail = detail,
                    at = now(),
                )
            )
        }
    }

    /**
     * ViewModel/action-level permission gate (task 5.3): true and no log entry
     * when allowed; a DENIAL row and false when not. Screens may hide
     * affordances, but every action re-checks here.
     */
    suspend fun requirePermission(permission: Permission, action: String, detail: String? = null): Boolean {
        val userId = _activeUserId.value
        val user = userId?.let { userDao.getById(it) }
        val role = user?.let { Role.fromName(it.role) }
        if (role != null && role.can(permission)) return true
        activityLogDao.insert(
            ActivityLog(
                userId = user?.id,
                userName = user?.name,
                type = ActivityLog.TYPE_DENIAL,
                action = action,
                detail = detail ?: "missing permission ${permission.name}",
                at = now(),
            )
        )
        return false
    }

    /** Attribution stamp (task 5.5) for mutating actions. */
    suspend fun logAction(action: String, detail: String? = null) {
        val userId = _activeUserId.value ?: return
        val user = userDao.getById(userId)
        activityLogDao.insert(
            ActivityLog(
                userId = userId,
                userName = user?.name,
                type = ActivityLog.TYPE_ACTION,
                action = action,
                detail = detail,
                at = now(),
            )
        )
    }

    suspend fun hasAnyUser(): Boolean = userDao.count() > 0

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        const val ACTION_LOGIN = "auth.login"
        const val ACTION_LOCK = "auth.lock"
    }
}
