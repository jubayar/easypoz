package net.redngreen.easypos.core.data.repo

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import net.redngreen.easypos.core.data.LocalIdentity
import net.redngreen.easypos.core.data.dao.CashEntryDao
import net.redngreen.easypos.core.data.dao.SaleDao
import net.redngreen.easypos.core.data.entity.CashEntry
import net.redngreen.easypos.core.data.journal.JournalEntities
import net.redngreen.easypos.core.data.journal.JournalizedWriter
import net.redngreen.easypos.core.data.journal.cashEntryPayload

/**
 * Cash in/out (tasks 9.1–9.4). Entries are immutable and journaled; the live
 * expected drawer balance is cash sales + cash in − cash out to date, computed
 * live and always labeled as expected (not counted) per the cash-management
 * spec. Corrections are compensating entries, never edits.
 */
@Singleton
class CashRepository @Inject constructor(
    private val cashEntryDao: CashEntryDao,
    private val saleDao: SaleDao,
    private val identity: LocalIdentity,
    private val writer: JournalizedWriter,
) {
    /** Full cash log, newest-first (visibility filtering is applied by callers). */
    fun observeEntries(): Flow<List<CashEntry>> = cashEntryDao.observeAll()

    /** Expected drawer cash = cash sales + cash in − cash out, live. */
    fun observeExpectedDrawerCents(): Flow<Long> =
        combine(saleDao.observeCashSalesCents(), cashEntryDao.observeNetCashCents()) { cashSales, netEntries ->
            cashSales + netEntries
        }

    /** Records an immutable cash entry, journaled for sync (reason is required). */
    suspend fun recordEntry(
        direction: String,
        amountCents: Long,
        reason: String,
        actorUserId: String?,
    ) {
        require(amountCents > 0) { "amount must be positive" }
        val trimmed = reason.trim()
        require(trimmed.isNotEmpty()) { "reason is required" }

        val now = System.currentTimeMillis()
        val entry = CashEntry(
            id = UUID.randomUUID().toString(),
            shopId = identity.shopId(),
            direction = direction,
            amountCents = amountCents,
            reason = trimmed,
            userId = actorUserId,
            at = now,
            createdAt = now,
            updatedAt = now,
        )
        writer.journalized(
            shopId = identity.shopId(),
            deviceId = identity.deviceId(),
            entity = JournalEntities.CASH_ENTRY,
            opType = JournalEntities.INSERT,
            actorUserId = actorUserId,
            payloadJson = writer.encode(cashEntryPayload(entry)),
        ) {
            cashEntryDao.insert(entry)
        }
    }

    companion object {
        const val DIRECTION_IN = "IN"
        const val DIRECTION_OUT = "OUT"
    }
}
