package net.redngreen.easypos.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.redngreen.easypos.core.data.SettingsKeys
import net.redngreen.easypos.core.data.dao.SettingDao
import net.redngreen.easypos.core.data.entity.User
import net.redngreen.easypos.core.data.repo.SettingsRepository
import net.redngreen.easypos.core.data.repo.UserRepository
import net.redngreen.easypos.core.model.Role
import net.redngreen.easypos.core.session.SessionManager

/**
 * Drives the lock overlay: who is active, who can unlock, and the idle-lock
 * timeout. Login is uniform — initial unlock, re-unlock, and quick-switch
 * all present a user + that user's PIN (user-access-control spec).
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val session: SessionManager,
    private val userRepo: UserRepository,
    private val settings: SettingsRepository,
    settingDao: SettingDao,
) : ViewModel() {

    val activeUser: StateFlow<User?> = session.activeUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Users offered on the lock screen (active with a PIN, name order). */
    val unlockableUsers: StateFlow<List<User>> = userRepo.observeUsers()
        .map { users -> users.filter { it.active && it.pinHash.isNotBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** null while loading — guards the first-run form against a false flash. */
    val hasAnyUser: StateFlow<Boolean?> = userRepo.observeUsers()
        .map<List<User>, Boolean?> { users -> users.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Whether anyone can actually unlock. A restored backup carries users with
     * no PIN hash (excluded by design), so a shop with only PIN-less users is
     * treated as first run again — this prevents a lockout after restore.
     */
    val hasUnlockableUser: StateFlow<Boolean?> = userRepo.observeUsers()
        .map<List<User>, Boolean?> { users -> users.any { it.active && it.pinHash.isNotBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Idle auto-lock (task 5.4) — configured via settings, default 5 min. */
    val idleLockMinutes: StateFlow<Int> = settingDao.observe(SettingsKeys.IDLE_LOCK_MINUTES)
        .map { raw -> raw?.trim()?.toIntOrNull() ?: SettingsKeys.DEFAULT_IDLE_LOCK_MINUTES }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsKeys.DEFAULT_IDLE_LOCK_MINUTES)

    fun unlock(userId: String, pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(session.login(userId, pin) == SessionManager.LoginResult.SUCCESS)
        }
    }

    /** First-run bootstrap: create the Admin, then unlock straight away and start the wizard. */
    fun createFirstAdmin(name: String, pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val admin = userRepo.createUser(name.trim(), Role.ADMIN, pin, actorUserId = null)
                session.login(admin.id, pin)
                settings.putLocal(SettingsKeys.ONBOARDING_STEP, SettingsKeys.ONBOARDING_PROFILE)
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun lockNow(detail: String) = session.lock(detail)
}
