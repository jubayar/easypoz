package net.redngreen.easypos.core.data.repo

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import net.redngreen.easypos.core.data.LocalIdentity
import net.redngreen.easypos.core.data.dao.DrawerCountDao
import net.redngreen.easypos.core.data.dao.PaymentTypeTotal
import net.redngreen.easypos.core.data.dao.ReportsDao
import net.redngreen.easypos.core.data.dao.TopSeller
import net.redngreen.easypos.core.data.entity.DrawerCount
import net.redngreen.easypos.core.data.journal.JournalEntities
import net.redngreen.easypos.core.data.journal.JournalizedWriter
import net.redngreen.easypos.core.data.journal.drawerCountPayload

/**
 * Day summary + drawer reconciliation (tasks 9.3/9.5). Everything is a computed
 * query over local sales/cash (design D4) — no network. Recorded drawer counts
 * are journaled (DRAWER_COUNT INSERT) with attribution.
 */
@Singleton
class ReportsRepository @Inject constructor(
    private val reportsDao: ReportsDao,
    private val drawerCountDao: DrawerCountDao,
    private val identity: LocalIdentity,
    private val writer: JournalizedWriter,
) {
    data class DaySummary(
        val salesCount: Int,
        val salesTotalCents: Long,
        val byPaymentType: List<PaymentTypeTotal>,
        val cashSalesCents: Long,
        val cashInCents: Long,
        val cashOutCents: Long,
        val netCashCents: Long,
        val averageBasketCents: Long,
        val topSellers: List<TopSeller>,
        val expectedDrawerCents: Long,
        val drawerCounts: List<DrawerCount>,
    )

    suspend fun daySummary(epochDay: Long): DaySummary {
        val shopId = identity.shopId()
        val zone = ZoneId.systemDefault()
        val date = LocalDate.ofEpochDay(epochDay)
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val count = reportsDao.salesCount(shopId, from, to)
        val total = reportsDao.salesTotal(shopId, from, to)
        val cashIn = reportsDao.cashIn(from, to)
        val cashOut = reportsDao.cashOut(from, to)

        return DaySummary(
            salesCount = count,
            salesTotalCents = total,
            byPaymentType = reportsDao.totalsByPaymentType(shopId, from, to),
            cashSalesCents = reportsDao.cashSalesBetween(shopId, from, to),
            cashInCents = cashIn,
            cashOutCents = cashOut,
            netCashCents = cashIn - cashOut,
            averageBasketCents = if (count > 0) total / count else 0L,
            topSellers = reportsDao.topSellers(shopId, from, to, TOP_SELLER_LIMIT),
            // Expected drawer includes opening context: all cash to date up to end of day.
            expectedDrawerCents = reportsDao.cashSalesUpTo(shopId, to) + reportsDao.netCashUpTo(to),
            drawerCounts = reportsDao.drawerCounts(shopId, from, to),
        )
    }

    /** Persists a drawer reconciliation with variance + attribution (task 9.5). */
    suspend fun recordDrawerCount(
        expectedCents: Long,
        countedCents: Long,
        actorUserId: String?,
    ): DrawerCount {
        val now = System.currentTimeMillis()
        val count = DrawerCount(
            id = UUID.randomUUID().toString(),
            shopId = identity.shopId(),
            at = now,
            expectedCents = expectedCents,
            countedCents = countedCents,
            varianceCents = countedCents - expectedCents,
            userId = actorUserId,
            createdAt = now,
            updatedAt = now,
        )
        writer.journalized(
            shopId = identity.shopId(),
            deviceId = identity.deviceId(),
            entity = JournalEntities.DRAWER_COUNT,
            opType = JournalEntities.INSERT,
            actorUserId = actorUserId,
            payloadJson = writer.encode(drawerCountPayload(count)),
        ) {
            drawerCountDao.insert(count)
        }
        return count
    }

    companion object {
        const val TOP_SELLER_LIMIT = 10
    }
}
