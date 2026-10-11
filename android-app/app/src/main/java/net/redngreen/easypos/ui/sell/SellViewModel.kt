package net.redngreen.easypos.ui.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.redngreen.easypos.core.database.SettingsKeys
import net.redngreen.easypos.core.database.dao.ProductDao
import net.redngreen.easypos.core.database.dao.SettingDao
import net.redngreen.easypos.core.database.entity.Category
import net.redngreen.easypos.core.database.entity.HeldSale
import net.redngreen.easypos.core.database.entity.PaymentType
import net.redngreen.easypos.core.database.entity.Product
import net.redngreen.easypos.core.data.repo.CatalogRepository
import net.redngreen.easypos.core.data.repo.SaleRepository
import net.redngreen.easypos.core.money.MoneyFormatter
import net.redngreen.easypos.core.session.SessionManager
import net.redngreen.easypos.core.print.PrintController
import net.redngreen.easypos.core.print.PrintState

/** One row of the live cart. Product lines increment by identity; quick-sell never matches. */
data class CartLine(
    val key: String, // productId, or a fresh UUID for custom items
    val productId: String?,
    val name: String,
    val unitCents: Long,
    val qty: Int,
    val emoji: String? = null,
    val color: String? = null,
) {
    val lineCents: Long get() = MoneyFormatter.multiply(qty, unitCents)
}

/** What the payment sheet shows after a successful sale (design: success panel). */
data class PaymentSuccess(
    val saleNo: Long,
    val totalCents: Long,
    val tenderedCents: Long?,
    val changeCents: Long?,
    val paymentTypeName: String?,
)

data class SellUiState(
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null, // null = All
    val products: List<Product> = emptyList(),
    /** Active-product count per category id (null key = uncategorized). */
    val categoryCounts: Map<String?, Int> = emptyMap(),
    val query: String = "",
    /** null = browsing (grid); non-null = typing (search results). */
    val searchResults: List<Product>? = null,
    val cart: List<CartLine> = emptyList(),
    val totalCents: Long = 0L,
    /** Set when a scan found nothing — drives the create/quick-sell affordance. */
    val unknownBarcode: String? = null,
    val heldSales: List<HeldSale> = emptyList(),
    val paymentTypes: List<PaymentType> = emptyList(),
    /** Base64 merchant QR (DuitNow) — shown at non-cash tender when configured. */
    val merchantQrBase64: String? = null,
    /** Drawer kick setting — drives the "drawer ready" hint on the payment sheet. */
    val drawerKick: Boolean = true,
    val lastCompletedSaleNo: Long? = null,
    /** Non-null while the payment sheet shows its success panel. */
    val lastPayment: PaymentSuccess? = null,
    val completing: Boolean = false,
    val message: String? = null,
) {
    val cartCount: Int get() = cart.sumOf { it.qty }
}

/**
 * The sell screen (tasks 7.1–7.4, 7.9–7.11): scanner-wedge capture, category
 * grid, persistent cart, quick-sell, hold/resume. Everything here is local —
 * no network, no sync, no license check on this path (invariant).
 */
@HiltViewModel
class SellViewModel @Inject constructor(
    private val session: SessionManager,
    private val productDao: ProductDao,
    private val catalog: CatalogRepository,
    private val sales: SaleRepository,
    settingDao: SettingDao,
    private val printer: PrintController,
) : ViewModel() {

    private val selectedCategoryId = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")
    private val cart = MutableStateFlow<List<CartLine>>(emptyList())
    private val unknownBarcode = MutableStateFlow<String?>(null)
    private val lastCompleted = MutableStateFlow<Long?>(null)
    private val lastPayment = MutableStateFlow<PaymentSuccess?>(null)
    private val completing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private var resumedHeldId: String? = null
    private var paymentTypesCache: List<PaymentType> = emptyList()

    /** Receipt print lifecycle (task 10.2) — surfaced as a retry affordance, never blocks selling. */
    val printState: StateFlow<PrintState> = printer.state

    private data class Flags(
        val unknownBarcode: String?,
        val lastCompleted: Long?,
        val completing: Boolean,
        val lastPayment: PaymentSuccess?,
    )

    private data class Ledger(
        val held: List<HeldSale>,
        val paymentTypes: List<PaymentType>,
        val message: String?,
        val drawerKick: Boolean,
    )

    val state: StateFlow<SellUiState> = combine(
        catalog.observeCategories(),
        combine(selectedCategoryId, catalog.observeProducts()) { selected, products ->
            val active = products.filter { it.active }
            val filtered = if (selected == null) active else active.filter { it.categoryId == selected }
            filtered to active.groupingBy { it.categoryId }.eachCount()
        },
        combine(query, catalog.observeProducts()) { q, products -> filterSearch(products, q) },
        cart,
        combine(
            combine(
                combine(unknownBarcode, lastCompleted, completing, lastPayment) { u, l, c, p ->
                    Flags(u, l, c, p)
                },
                combine(
                    sales.observeHeld(),
                    sales.observePaymentTypes(),
                    message,
                    settingDao.observe(SettingsKeys.DRAWER_KICK),
                ) { held, types, msg, kick ->
                    Ledger(held, types, msg, kick?.toBooleanStrictOrNull() ?: true)
                },
            ) { flags, ledger -> flags to ledger },
            settingDao.observe(SettingsKeys.DUITNOW_QR_IMAGE),
        ) { (flags, ledger), qr -> Triple(flags, ledger, qr) },
    ) { categories, browse, search, cartLines, (flags, ledger, qr) ->
        SellUiState(
            categories = categories,
            selectedCategoryId = selectedCategoryId.value,
            products = browse.first,
            categoryCounts = browse.second,
            query = query.value,
            searchResults = search,
            cart = cartLines,
            totalCents = cartLines.sumOf { it.lineCents },
            unknownBarcode = flags.unknownBarcode,
            heldSales = ledger.held,
            paymentTypes = ledger.paymentTypes,
            merchantQrBase64 = qr,
            drawerKick = ledger.drawerKick,
            lastCompletedSaleNo = flags.lastCompleted,
            lastPayment = flags.lastPayment,
            completing = flags.completing,
            message = ledger.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SellUiState())

    init {
        viewModelScope.launch {
            sales.ensureDefaultPaymentTypes(session.activeUserId.value)
        }
        viewModelScope.launch {
            sales.observePaymentTypes().collect { paymentTypesCache = it }
        }
    }

    // ─── Browsing & search ───────────────────────────────────────────────────

    fun selectCategory(categoryId: String?) {
        selectedCategoryId.value = categoryId
    }

    fun setQuery(value: String) {
        query.value = value
        if (value.isBlank()) unknownBarcode.value = null
    }

    /** Scanner Enter or search submit: exact barcode → cart; otherwise search/unknown. */
    fun submitCapture() {
        val code = query.value.trim()
        if (code.isEmpty()) return
        viewModelScope.launch {
            val product = productDao.getActiveByBarcode(code)
            if (product != null) {
                addToCart(product)
                query.value = ""
                unknownBarcode.value = null
            } else if (productDao.search(code, 1).isEmpty()) {
                // Neither exact nor partial — offer create/quick-sell, keep the code.
                unknownBarcode.value = code
            }
            // else: partial matches exist — leave them listed as search results.
        }
    }

    private fun filterSearch(products: List<Product>, raw: String): List<Product>? {
        val q = raw.trim()
        if (q.isEmpty()) return null
        return products
            .filter { it.active }
            .filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.barcode?.startsWith(q) == true ||
                    it.sku?.startsWith(q, ignoreCase = true) == true
            }
            .sortedWith(
                compareBy({ it.barcode != q }, { it.name })
            )
            .take(30)
    }

    // ─── Cart ────────────────────────────────────────────────────────────────

    fun addToCart(product: Product) {
        cart.update { lines ->
            val existing = lines.indexOfFirst { it.productId == product.id }
            if (existing >= 0) {
                lines.mapIndexed { index, line ->
                    if (index == existing) line.copy(qty = line.qty + 1) else line
                }
            } else {
                lines + CartLine(
                    key = product.id,
                    productId = product.id,
                    name = product.name,
                    unitCents = product.priceCents,
                    qty = 1,
                    emoji = product.emoji,
                    color = product.color,
                )
            }
        }
        lastCompleted.value = null
    }

    fun quickSell(name: String, priceCents: Long) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || priceCents < 0) return
        cart.update { lines ->
            lines + CartLine(
                key = UUID.randomUUID().toString(),
                productId = null,
                name = trimmed,
                unitCents = priceCents,
                qty = 1,
            )
        }
        unknownBarcode.value = null
        query.value = ""
    }

    fun increment(key: String) = changeQty(key, +1)

    fun decrement(key: String) = changeQty(key, -1)

    private fun changeQty(key: String, by: Int) {
        cart.update { lines ->
            lines.mapNotNull { line ->
                if (line.key != key) line
                else {
                    val newQty = line.qty + by
                    if (newQty <= 0) null else line.copy(qty = newQty)
                }
            }
        }
    }

    /** Pre-payment void: removes the line and audits reason + user (task 7.7). */
    fun voidLine(key: String, reason: String) {
        val line = cart.value.firstOrNull { it.key == key } ?: return
        cart.update { lines -> lines.filterNot { it.key == key } }
        viewModelScope.launch {
            session.logAction("cart.void_line", "name=${line.name} qty=${line.qty} reason=${reason.trim()}")
        }
    }

    fun removeLine(key: String) {
        cart.update { lines -> lines.filterNot { it.key == key } }
    }

    fun clearCart() {
        cart.value = emptyList()
        resumedHeldId = null
    }

    fun dismissUnknownBarcode() {
        unknownBarcode.value = null
        query.value = ""
    }

    fun consumeMessage() {
        message.value = null
    }

    fun retryPrint() = printer.retry()

    fun dismissPrintError() = printer.dismiss()

    // ─── Payment (task 7.5) ──────────────────────────────────────────────────

    fun completeSale(paymentTypeId: String?, tenderedCents: Long?) {
        if (completing.value) return
        completing.value = true
        viewModelScope.launch {
            try {
                val lines = cart.value.map { SaleRepository.CheckoutLine(it.productId, it.name, it.qty, it.unitCents) }
                val total = lines.sumOf { MoneyFormatter.multiply(it.qty, it.unitCents) }
                val sale = sales.completeSale(
                    lines = lines,
                    paymentTypeId = paymentTypeId,
                    tenderedCents = tenderedCents,
                    actorUserId = session.activeUserId.value,
                    resumedHeldId = resumedHeldId,
                )
                cart.value = emptyList()
                resumedHeldId = null
                query.value = ""
                lastCompleted.value = sale.localNo
                lastPayment.value = PaymentSuccess(
                    saleNo = sale.localNo,
                    totalCents = total,
                    tenderedCents = tenderedCents,
                    changeCents = tenderedCents?.let { MoneyFormatter.change(it, total) },
                    paymentTypeName = paymentTypesCache.firstOrNull { it.id == paymentTypeId }?.name,
                )
                // Printing runs off the sale path — the sale is already committed.
                printer.printSale(sale.id)
            } catch (e: Exception) {
                message.value = e.message ?: "sale failed"
            } finally {
                completing.value = false
            }
        }
    }

    /** "Next customer" on the payment success panel. */
    fun dismissPaymentSuccess() {
        lastPayment.value = null
    }

    // ─── Hold & resume (task 7.9) ────────────────────────────────────────────

    fun holdCart(label: String) {
        val lines = cart.value.map { SaleRepository.CheckoutLine(it.productId, it.name, it.qty, it.unitCents) }
        if (lines.isEmpty()) return
        viewModelScope.launch {
            sales.holdCart(lines, label, session.activeUserId.value)
            clearCart()
        }
    }

    /** Resumes a held cart; a non-empty cart is parked first so nothing is lost. */
    fun resumeHeld(held: HeldSale) {
        viewModelScope.launch {
            if (cart.value.isNotEmpty()) {
                val current = cart.value.map { SaleRepository.CheckoutLine(it.productId, it.name, it.qty, it.unitCents) }
                sales.holdCart(current, null, session.activeUserId.value)
            }
            cart.value = sales.resumeLines(held).map {
                CartLine(
                    key = it.productId ?: UUID.randomUUID().toString(),
                    productId = it.productId,
                    name = it.name,
                    unitCents = it.unitCents,
                    qty = it.qty,
                )
            }
            resumedHeldId = held.id
        }
    }

    companion object {
        const val ACTION_SELL = "sell.screen.open"
    }
}
