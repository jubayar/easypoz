package net.redngreen.easypos.feature.login.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.redngreen.easypos.core.database.SettingsKeys
import net.redngreen.easypos.core.database.dao.SettingDao
import net.redngreen.easypos.core.database.entity.User
import net.redngreen.easypos.core.data.repo.SettingsRepository
import net.redngreen.easypos.core.data.repo.UserRepository
import net.redngreen.easypos.core.model.Role
import net.redngreen.easypos.core.session.SessionManager
import net.redngreen.easypos.feature.login.domain.LoginRepository
import net.redngreen.easypos.feature.login.domain.UserAccount

/**
 * Lock-screen session over the core session manager. Failure details stay
 * generic by design: a wrong PIN never reveals whether the user exists
 * (SessionManager owns denial logging).
 */
class SessionLoginRepository(
    private val session: SessionManager,
    private val userRepo: UserRepository,
    private val settings: SettingsRepository,
    private val settingDao: SettingDao,
) : LoginRepository {

    override fun observeActiveUser(): Flow<UserAccount?> =
        session.activeUser.map { it?.toAccount() }

    override fun observeUnlockableUsers(): Flow<List<UserAccount>> =
        userRepo.observeUsers()
            .map { users -> users.filter { it.active && it.pinHash.isNotBlank() }.map { it.toAccount() } }

    override fun observeHasAnyUser(): Flow<Boolean?> =
        userRepo.observeUsers().map<List<User>, Boolean?> { users -> users.isNotEmpty() }

    override fun observeHasUnlockableUser(): Flow<Boolean?> =
        userRepo.observeUsers().map<List<User>, Boolean?> { users ->
            users.any { it.active && it.pinHash.isNotBlank() }
        }

    override fun observeIdleLockMinutes(): Flow<Int> =
        settingDao.observe(SettingsKeys.IDLE_LOCK_MINUTES)
            .map { raw -> raw?.trim()?.toIntOrNull() ?: SettingsKeys.DEFAULT_IDLE_LOCK_MINUTES }

    override suspend fun login(userId: String, pin: String): Boolean =
        session.login(userId, pin) == SessionManager.LoginResult.SUCCESS

    override suspend fun createFirstAdmin(name: String, pin: String): Boolean = try {
        val admin = userRepo.createUser(name.trim(), Role.ADMIN, pin, actorUserId = null)
        session.login(admin.id, pin)
        settings.putLocal(SettingsKeys.ONBOARDING_STEP, SettingsKeys.ONBOARDING_PROFILE)
        true
    } catch (e: Exception) {
        false
    }

    override fun lockNow(detail: String) = session.lock(detail)

    private fun User.toAccount() = UserAccount(id = id, name = name, role = role)
}
