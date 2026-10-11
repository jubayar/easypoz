package net.redngreen.easypos.core.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import net.redngreen.easypos.core.data.dao.ActivityLogDao
import net.redngreen.easypos.core.data.dao.UserDao
import net.redngreen.easypos.core.data.entity.ActivityLog
import net.redngreen.easypos.core.data.entity.User
import net.redngreen.easypos.core.model.Permission
import net.redngreen.easypos.core.security.PinHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {

    private val pinHasher = PinHasher()

    private class FakeUserDao(initial: List<User>) : UserDao {
        private val users = MutableStateFlow(initial.toList())

        private fun edit(id: String, block: (User) -> User) {
            users.value = users.value.map { if (it.id == id) block(it) else it }
        }

        override suspend fun upsert(user: User) {
            edit(user.id) { user }
            if (users.value.none { it.id == user.id }) users.value = users.value + user
        }

        override suspend fun upsertAll(users: List<User>) = users.forEach { upsert(it) }

        override fun observeActive(): Flow<List<User>> =
            users.map { list -> list.filter { it.active }.sortedBy { it.name } }

        override fun observeAll(): Flow<List<User>> =
            users.map { list -> list.sortedBy { it.name } }

        override suspend fun getById(id: String): User? = users.value.firstOrNull { it.id == id }

        override fun observeById(id: String): Flow<User?> = users.map { it.firstOrNull { u -> u.id == id } }

        override suspend fun count(): Int = users.value.size

        override suspend fun countActiveAdmins(): Int =
            users.value.count { it.active && it.role == "ADMIN" }
    }

    private class FakeActivityLogDao : ActivityLogDao {
        val entries = MutableStateFlow<List<ActivityLog>>(emptyList())

        override suspend fun insert(entry: ActivityLog) {
            entries.value = entries.value + entry
        }

        override fun observeRecent(limit: Int): Flow<List<ActivityLog>> =
            entries.map { list -> list.sortedByDescending { it.at }.take(limit) }

        override suspend fun recent(limit: Int): List<ActivityLog> =
            entries.value.sortedByDescending { it.at }.take(limit)
    }

    private fun manager(
        users: List<User>,
    ): Triple<SessionManager, FakeUserDao, FakeActivityLogDao> {
        val userDao = FakeUserDao(users)
        val logDao = FakeActivityLogDao()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        return Triple(SessionManager(userDao, pinHasher, logDao, scope), userDao, logDao)
    }

    private fun user(id: String, name: String, role: String, pin: String, active: Boolean = true): User {
        val salt = pinHasher.generateSalt()
        return User(
            id = id,
            shopId = "shop-1",
            name = name,
            role = role,
            pinSalt = salt,
            pinHash = pinHasher.hash(pin, salt),
            active = active,
        )
    }

    @Test
    fun `login with correct pin unlocks the session and logs it`() = runTest {
        val saliman = user("u1", "SALIMAN", "CASHIER", "4321")
        val (session, _, log) = manager(listOf(saliman))

        assertNull(session.activeUserId.value)
        assertEquals(SessionManager.LoginResult.SUCCESS, session.login("u1", "4321"))
        assertEquals("u1", session.activeUserId.value)

        val entry = log.entries.value.single()
        assertEquals(ActivityLog.TYPE_LOGIN, entry.type)
        assertEquals("u1", entry.userId)
        assertEquals("SALIMAN", entry.userName)
    }

    @Test
    fun `wrong pin is generically rejected and the attempt is logged`() = runTest {
        val saliman = user("u1", "SALIMAN", "CASHIER", "4321")
        val (session, _, log) = manager(listOf(saliman))

        assertEquals(SessionManager.LoginResult.REJECTED, session.login("u1", "0000"))
        assertNull(session.activeUserId.value)

        val entry = log.entries.value.single()
        assertEquals(ActivityLog.TYPE_LOGIN_FAILED, entry.type)
        assertEquals("u1", entry.userId)
    }

    @Test
    fun `unknown user and inactive user are rejected like a wrong pin`() = runTest {
        val inactive = user("u2", "GONE", "CASHIER", "1111", active = false)
        val (session, _, _) = manager(listOf(inactive))

        assertEquals(SessionManager.LoginResult.REJECTED, session.login("no-such-user", "1111"))
        assertEquals(SessionManager.LoginResult.REJECTED, session.login("u2", "1111"))
        assertNull(session.activeUserId.value)
    }

    @Test
    fun `lock clears the active user and logs the event`() = runTest {
        val admin = user("a1", "BOSS", "ADMIN", "9999")
        val (session, _, log) = manager(listOf(admin))
        session.login("a1", "9999")

        session.lock(detail = "manual")

        assertNull(session.activeUserId.value)
        val lockEntry = log.entries.value.last()
        assertEquals(ActivityLog.TYPE_LOCK, lockEntry.type)
        assertEquals("a1", lockEntry.userId)
    }

    @Test
    fun `lock without an active user is a no-op`() = runTest {
        val (session, _, log) = manager(emptyList())
        session.lock()
        assertTrue(log.entries.value.isEmpty())
    }

    @Test
    fun `requirePermission allows entitled roles and denies the rest with a log entry`() = runTest {
        val cashier = user("c1", "ALI", "CASHIER", "1111")
        val admin = user("a1", "BOSS", "ADMIN", "9999")
        val (session, _, log) = manager(listOf(cashier, admin))

        // No session → denied + logged (user is null).
        assertFalse(session.requirePermission(Permission.SELL, "sell.complete"))
        assertEquals(ActivityLog.TYPE_DENIAL, log.entries.value.single().type)

        // Cashier: selling is fine, catalog editing is denied and logged.
        session.login("c1", "1111")
        assertTrue(session.requirePermission(Permission.SELL, "sell.complete"))
        val denialsBefore = log.entries.value.count { it.type == ActivityLog.TYPE_DENIAL }
        assertFalse(session.requirePermission(Permission.EDIT_CATALOG, "product.edit"))
        val denialsAfter = log.entries.value.count { it.type == ActivityLog.TYPE_DENIAL }
        assertEquals(denialsBefore + 1, denialsAfter)
        val denial = log.entries.value.last { it.type == ActivityLog.TYPE_DENIAL }
        assertEquals("c1", denial.userId)
        assertEquals("product.edit", denial.action)

        // Admin: user management allowed, and it stamps an ACTION entry.
        session.login("a1", "9999")
        assertTrue(session.requirePermission(Permission.MANAGE_USERS, "user.create"))
        session.logAction("user.create", "name=NEW")
        val action = log.entries.value.last()
        assertEquals(ActivityLog.TYPE_ACTION, action.type)
        assertEquals("a1", action.userId)
        assertEquals("BOSS", action.userName)
    }
}
