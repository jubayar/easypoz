package net.redngreen.easypos.feature.sell.domain

import net.redngreen.easypos.core.money.MoneyFormatter

/** A cart line at payment time — product or quick-sell custom item. */
data class CheckoutLine(
    val productId: String?,
    val name: String,
    val qty: Int,
    val unitCents: Long,
) {
    val lineCents: Long get() = MoneyFormatter.multiply(qty, unitCents)
}

/** A committed sale as the app sees it — no Room entity leakage (design D5). */
data class CompletedSale(
    val id: String,
    val shopId: String,
    val localNo: Long,
    val userId: String?,
    val at: Long,
    val totalCents: Long,
    val paymentTypeId: String?,
    val tenderedCents: Long?,
    val changeCents: Long?,
    val voided: Boolean,
)

/** A parked cart (task 7.9) — local-only, never journaled. */
data class HeldCart(
    val id: String,
    val userId: String?,
    val label: String?,
    val lines: List<CheckoutLine>,
    val heldAt: Long,
)

/** A shop-configurable tender option on the payment sheet (task 7.5). */
data class PaymentOption(
    val id: String,
    val name: String,
    val kind: String,
)
