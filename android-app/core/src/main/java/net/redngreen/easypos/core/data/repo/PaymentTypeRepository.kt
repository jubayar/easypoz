package net.redngreen.easypos.core.data.repo

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import net.redngreen.easypos.core.data.LocalIdentity
import net.redngreen.easypos.core.data.dao.PaymentTypeDao
import net.redngreen.easypos.core.data.entity.PaymentType
import net.redngreen.easypos.core.data.journal.JournalEntities
import net.redngreen.easypos.core.data.journal.JournalizedWriter
import net.redngreen.easypos.core.data.journal.paymentTypePayload

/**
 * Payment-type manager (task 11.1). Types are data, not code — the shop adds,
 * renames, activates/deactivates, and reorders them. Every change is journaled
 * (PAYMENT_TYPE UPSERT) so it syncs.
 */
@Singleton
class PaymentTypeRepository @Inject constructor(
    private val paymentTypeDao: PaymentTypeDao,
    private val identity: LocalIdentity,
    private val writer: JournalizedWriter,
) {
    fun observeAll(): Flow<List<PaymentType>> = paymentTypeDao.observeAll()

    suspend fun add(name: String, kind: String, actorUserId: String?) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "name is required" }
        val sort = (paymentTypeDao.getAll().maxOfOrNull { it.sort } ?: -1) + 1
        val type = PaymentType(
            id = UUID.randomUUID().toString(),
            shopId = identity.shopId(),
            name = trimmed,
            kind = if (kind == CASH) CASH else OTHER,
            active = true,
            sort = sort,
            updatedAt = System.currentTimeMillis(),
        )
        write(type, actorUserId)
    }

    suspend fun rename(id: String, name: String, actorUserId: String?) {
        val existing = paymentTypeDao.getById(id) ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        write(existing.copy(name = trimmed, updatedAt = System.currentTimeMillis()), actorUserId)
    }

    suspend fun setActive(id: String, active: Boolean, actorUserId: String?) {
        val existing = paymentTypeDao.getById(id) ?: return
        write(existing.copy(active = active, updatedAt = System.currentTimeMillis()), actorUserId)
    }

    suspend fun reorder(orderedIds: List<String>, actorUserId: String?) {
        orderedIds.forEachIndexed { index, id ->
            val existing = paymentTypeDao.getById(id) ?: return@forEachIndexed
            if (existing.sort != index) {
                write(existing.copy(sort = index, updatedAt = System.currentTimeMillis()), actorUserId)
            }
        }
    }

    private suspend fun write(type: PaymentType, actorUserId: String?) {
        writer.journalized(
            shopId = identity.shopId(),
            deviceId = identity.deviceId(),
            entity = JournalEntities.PAYMENT_TYPE,
            opType = JournalEntities.UPSERT,
            actorUserId = actorUserId,
            payloadJson = writer.encode(paymentTypePayload(type)),
        ) {
            paymentTypeDao.upsert(type)
        }
    }

    companion object {
        const val CASH = "CASH"
        const val OTHER = "OTHER"
    }
}
