package net.redngreen.easypos.feature.sell.domain

import kotlinx.coroutines.flow.Flow

/**
 * Sale completion + hold/resume (tasks 7.5–7.9). The commit is ONE Room
 * transaction inside the implementation: sale + lines + stock deltas + every
 * journal op — fully offline, never blocked by network or license state
 * (the sell path is sacred, design D5 + invariants).
 */
interface SaleRepository {
    /**
     * Commits a sale atomically: sequential local number, lines, SALE stock
     * deltas with cache bumps, and the journal ops — or nothing at all.
     * Resumed carts pass [resumedHeldId] so the parked row dies here too.
     */
    suspend fun completeSale(
        lines: List<CheckoutLine>,
        paymentTypeId: String?,
        tenderedCents: Long?,
        actorUserId: String?,
        resumedHeldId: String? = null,
    ): CompletedSale

    /** Parks the cart — no sale, no stock movement, no number consumed. */
    suspend fun holdCart(
        lines: List<CheckoutLine>,
        label: String?,
        actorUserId: String?,
    ): HeldCart

    fun resumeLines(held: HeldCart): List<CheckoutLine>

    fun observeHeld(): Flow<List<HeldCart>>

    suspend fun discardHeld(id: String)

    fun observePaymentTypes(): Flow<List<PaymentOption>>

    /** Shop-configurable list; seeded with the spec defaults on first run. */
    suspend fun ensureDefaultPaymentTypes(actorUserId: String?)
}
