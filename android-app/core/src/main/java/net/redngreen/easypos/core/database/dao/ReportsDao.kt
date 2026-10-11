package net.redngreen.easypos.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import net.redngreen.easypos.core.database.entity.DrawerCount

/** One payment type's day total. */
data class PaymentTypeTotal(
    val paymentTypeName: String,
    val saleCount: Int,
    val totalCents: Long,
)

/** A day's top-selling product by units. */
data class TopSeller(
    val name: String,
    val qty: Long,
    val totalCents: Long,
)

/**
 * Day-summary + reconciliation queries (tasks 9.3/9.5), all computed locally
 * from the sales/cash tables (design D4: analytics are computed queries).
 */
@Dao
interface ReportsDao {
    @Query("SELECT COUNT(*) FROM sales WHERE shopId = :shopId AND voided = 0 AND at BETWEEN :from AND :to")
    suspend fun salesCount(shopId: String, from: Long, to: Long): Int

    @Query("SELECT COALESCE(SUM(totalCents), 0) FROM sales WHERE shopId = :shopId AND voided = 0 AND at BETWEEN :from AND :to")
    suspend fun salesTotal(shopId: String, from: Long, to: Long): Long

    @Query(
        """
        SELECT COALESCE(p.name, '—') AS paymentTypeName, COUNT(s.id) AS saleCount,
               COALESCE(SUM(s.totalCents), 0) AS totalCents
        FROM sales s LEFT JOIN payment_types p ON s.paymentTypeId = p.id
        WHERE s.shopId = :shopId AND s.voided = 0 AND s.at BETWEEN :from AND :to
        GROUP BY s.paymentTypeId
        ORDER BY totalCents DESC
        """
    )
    suspend fun totalsByPaymentType(shopId: String, from: Long, to: Long): List<PaymentTypeTotal>

    @Query(
        """
        SELECT COALESCE(pr.name, sl.customName, '—') AS name, SUM(sl.qty) AS qty,
               SUM(sl.lineCents) AS totalCents
        FROM sale_lines sl JOIN sales s ON sl.saleId = s.id
        LEFT JOIN products pr ON sl.productId = pr.id
        WHERE s.shopId = :shopId AND s.voided = 0 AND s.at BETWEEN :from AND :to
        GROUP BY sl.productId, pr.name, sl.customName
        ORDER BY qty DESC
        LIMIT :limit
        """
    )
    suspend fun topSellers(shopId: String, from: Long, to: Long, limit: Int): List<TopSeller>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM cash_entries WHERE direction = 'IN' AND at BETWEEN :from AND :to")
    suspend fun cashIn(from: Long, to: Long): Long

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM cash_entries WHERE direction = 'OUT' AND at BETWEEN :from AND :to")
    suspend fun cashOut(from: Long, to: Long): Long

    /** Cash sales to date — the sales side of the expected drawer (with opening context). */
    @Query(
        """
        SELECT COALESCE(SUM(s.totalCents), 0) FROM sales s
        JOIN payment_types p ON s.paymentTypeId = p.id
        WHERE s.shopId = :shopId AND s.voided = 0 AND p.kind = 'CASH' AND s.at <= :to
        """
    )
    suspend fun cashSalesUpTo(shopId: String, to: Long): Long

    /** Cash sales within the day window — the Z-report movement row. */
    @Query(
        """
        SELECT COALESCE(SUM(s.totalCents), 0) FROM sales s
        JOIN payment_types p ON s.paymentTypeId = p.id
        WHERE s.shopId = :shopId AND s.voided = 0 AND p.kind = 'CASH' AND s.at >= :from AND s.at < :to
        """
    )
    suspend fun cashSalesBetween(shopId: String, from: Long, to: Long): Long

    @Query("SELECT COALESCE(SUM(CASE WHEN direction = 'IN' THEN amountCents ELSE -amountCents END), 0) FROM cash_entries WHERE at <= :to")
    suspend fun netCashUpTo(to: Long): Long

    @Query("SELECT * FROM drawer_counts WHERE shopId = :shopId AND at BETWEEN :from AND :to ORDER BY at DESC")
    suspend fun drawerCounts(shopId: String, from: Long, to: Long): List<DrawerCount>
}
