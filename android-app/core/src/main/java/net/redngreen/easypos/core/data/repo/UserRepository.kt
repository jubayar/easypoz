package net.redngreen.easypos.core.data.repo

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import net.redngreen.easypos.core.data.LocalIdentity
import net.redngreen.easypos.core.data.dao.UserDao
import net.redngreen.easypos.core.data.entity.User
import net.redngreen.easypos.core.data.journal.JournalEntities
import net.redngreen.easypos.core.data.journal.JournalizedWriter
import net.redngreen.easypos.core.data.journal.userPayload
import net.redngreen.easypos.core.model.Role
import net.redngreen.easypos.core.security.PinHasher

/**
 * User CRUD for the Admin screens (task 5.1). Every mutation is journaled
 * (USER UPSERT) with acting-user attribution in the same transaction —
 * design D5's "journal never lags state" applies to users too.
 *
 * Role enforcement lives in the ViewModels via SessionManager.requirePermission;
 * this repo assumes the caller already checked MANAGE_USERS. The guards kept
 * here are data-integrity ones (PIN format, last-active-admin lockout).
 */
@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao,
    private val identity: LocalIdentity,
    private val writer: JournalizedWriter,
    private val pinHasher: PinHasher,
) {
    fun observeUsers(): Flow<List<User>> = userDao.observeAll()

    fun observeUser(id: String): Flow<User?> = userDao.observeById(id)

    sealed class UserError(message: String) : Exception(message) {
        class InvalidName : UserError("name must not be blank")
        class InvalidPin : UserError("PIN must be 4–6 digits")
        class InvalidRole : UserError("unknown role")
        class NoSuchUser : UserError("user not found")
        class LastActiveAdmin : UserError("cannot deactivate or demote the last active admin")
    }

    /** Creates a user (Admin CRUD, or the first-run bootstrap Admin). */
    suspend fun createUser(
        name: String,
        role: Role,
        pin: String,
        actorUserId: String?,
    ): User {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw UserError.InvalidName()
        if (!pinHasher.isValidPinFormat(pin)) throw UserError.InvalidPin()

        val now = System.currentTimeMillis()
        val salt = pinHasher.generateSalt()
        val user = User(
            id = UUID.randomUUID().toString(),
            shopId = identity.shopId(),
            name = trimmed,
            role = role.name,
            pinSalt = salt,
            pinHash = pinHasher.hash(pin, salt),
            active = true,
            createdAt = now,
            updatedAt = now,
        )
        writeJournalized(user, actorUserId)
        return user
    }

    /**
     * Edits name/role/active, and/or resets the PIN. Pass null to leave the
     * current PIN untouched; pass a string to set a new one.
     */
    suspend fun updateUser(
        id: String,
        name: String,
        role: Role,
        active: Boolean,
        newPin: String?,
        actorUserId: String?,
    ) {
        val existing = userDao.getById(id) ?: throw UserError.NoSuchUser()
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw UserError.InvalidName()

        if (!active) {
            if (existing.active && existing.role == Role.ADMIN.name && userDao.countActiveAdmins() <= 1) {
                throw UserError.LastActiveAdmin()
            }
        }

        val demotingLastAdmin = active &&
            existing.role == Role.ADMIN.name &&
            role != Role.ADMIN &&
            userDao.countActiveAdmins() <= 1
        if (demotingLastAdmin) throw UserError.LastActiveAdmin()

        val now = System.currentTimeMillis()
        val (salt, hash) = if (newPin != null) {
            if (!pinHasher.isValidPinFormat(newPin)) throw UserError.InvalidPin()
            val s = pinHasher.generateSalt()
            s to pinHasher.hash(newPin, s)
        } else {
            existing.pinSalt to existing.pinHash
        }
        val updated = existing.copy(
            name = trimmed,
            role = role.name,
            active = active,
            pinSalt = salt,
            pinHash = hash,
            updatedAt = now,
        )
        writeJournalized(updated, actorUserId)
    }

    private suspend fun writeJournalized(user: User, actorUserId: String?) {
        val payload = userPayload(user)
        writer.journalized(
            shopId = identity.shopId(),
            deviceId = identity.deviceId(),
            entity = JournalEntities.USER,
            opType = JournalEntities.UPSERT,
            actorUserId = actorUserId,
            payloadJson = writer.encode(payload),
        ) {
            userDao.upsert(user)
        }
    }
}
