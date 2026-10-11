package net.redngreen.easypos.ui.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.redngreen.easypos.core.data.dao.UserDao
import net.redngreen.easypos.core.data.entity.Category
import net.redngreen.easypos.core.data.entity.Product
import net.redngreen.easypos.core.data.entity.StockDelta
import net.redngreen.easypos.core.data.entity.User
import net.redngreen.easypos.core.data.repo.CatalogRepository
import net.redngreen.easypos.core.data.repo.StockRepository
import net.redngreen.easypos.core.model.Permission
import net.redngreen.easypos.core.model.Role
import net.redngreen.easypos.core.money.MoneyFormatter
import net.redngreen.easypos.core.session.SessionManager

/** Stock list quantity filters (stock-management spec). */
enum class StockFilter { ALL, LOW, ZERO, NEGATIVE }

/** How a manual adjustment is expressed (task 8.4). */
enum class StockAdjustMode { SET, RECEIVE, REMOVE }

data class StockRow(
    val product: Product,
    val categoryName: String,
    val valueCostCents: Long,
    val valueSaleCents: Long,
) {
    /** Low = positive but at/below the product's threshold (zero/negative are their own filters). */
    val low: Boolean get() = product.stockOnHand > 0 && product.stockOnHand <= product.lowStockThreshold
    val negative: Boolean get() = product.stockOnHand < 0
}

data class StockHistoryRow(
    val id: String,
    val at: Long,
    val delta: Long,
    val before: Long,
    val after: Long,
    val reason: String, // SALE | ADJUST | IMPORT
    val note: String?,
    val userName: String?,
)

data class StockDetail(
    val product: Product,
    val history: List<StockHistoryRow>,
)

data class StockUiState(
    val loading: Boolean = true,
    val denied: Boolean = false,
    val filter: StockFilter = StockFilter.ALL,
    val selectedCategoryId: String? = null, // null = all categories
    val counts: Map<StockFilter, Int> = emptyMap(),
    /** Categories for the segment cards. */
    val categories: List<Category> = emptyList(),
    /** Per-category product counts for the segment cards (null key = uncategorized). */
    val categoryCounts: Map<String?, Int> = emptyMap(),
    val rows: List<StockRow> = emptyList(),
    val totalCount: Int = 0,
    /** Today's gross units moved in / out (design: Pergerakan Hari Ini). */
    val movementIn: Long = 0L,
    val movementOut: Long = 0L,
    val totalCostCents: Long = 0L,
    val totalSaleCents: Long = 0L,
    val marginAmountCents: Long = 0L,
    val marginBps: Int? = null,
    val canAdjust: Boolean = false,
    val canEdit: Boolean = false,
    val detail: StockDetail? = null,
    val saving: Boolean = false,
    val message: String? = null,
) {
    companion object {
        /** Sentinel category id meaning "the uncategorized bucket" (null = all). */
        const val UNSET_CATEGORY = "__uncategorized__"
    }
}

private data class BaseStock(
    val counts: Map<StockFilter, Int>,
    val categories: List<Category>,
    val categoryCounts: Map<String?, Int>,
    val rows: List<StockRow>,
    val totalCount: Int,
    val totalCostCents: Long,
    val totalSaleCents: Long,
)

private data class Flags(
    val canAdjust: Boolean,
    val canEdit: Boolean,
    val saving: Boolean,
    val message: String?,
    val denied: Boolean,
)

private data class StockExtras(
    val detail: StockDetail?,
    val flags: Flags,
)

/**
 * Stock management (tasks 8.1–8.4): filtered overview with per-category
 * grouping, live valuation, scrollable product history, and manual
 * adjustments. Reads are local; adjustments go through StockRepository,
 * permission-checked at action level with denials logged (task 5.3).
 */
@HiltViewModel
class StockViewModel @Inject constructor(
    private val session: SessionManager,
    private val stock: StockRepository,
    private val catalog: CatalogRepository,
    userDao: UserDao,
) : ViewModel() {

    private val filter = MutableStateFlow(StockFilter.ALL)
    private val selectedCategory = MutableStateFlow<String?>(null)
    private val denied = MutableStateFlow(false)
    private val selectedProductId = MutableStateFlow<String?>(null)
    private val saving = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val products = stock.observeProducts()
    private val categories = catalog.observeCategories()
    private val users = userDao.observeAll()

    private val permissions = session.activeUser.map { user ->
        val role = user?.role?.let(Role::fromName)
        (role?.can(Permission.ADJUST_STOCK) == true) to (role?.can(Permission.EDIT_CATALOG) == true)
    }

    private val movementToday = stock.observeMovementToday()

    private val movement = combine(movementToday.first, movementToday.second) { i, o -> i to o }

    private val base = combine(
        products,
        categories,
        combine(filter, selectedCategory) { f, c -> f to c },
    ) { productList, categoryList, filterAndCategory ->
        val (activeFilter, categoryId) = filterAndCategory
        buildBase(productList, categoryList, activeFilter, categoryId)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val detailFlow: Flow<StockDetail?> = selectedProductId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            combine(stock.observeDeltas(id), products, users) { deltas, productList, userList ->
                val product = productList.firstOrNull { it.id == id } ?: return@combine null
                StockDetail(product, buildHistory(product, deltas, userList))
            }
        }
    }

    private val flagsFlow = combine(permissions, saving, message, denied) { perms, isSaving, msg, isDenied ->
        Flags(
            canAdjust = perms.first,
            canEdit = perms.second,
            saving = isSaving,
            message = msg,
            denied = isDenied,
        )
    }

    private val extras = combine(detailFlow, flagsFlow) { detail, flags -> StockExtras(detail, flags) }

    val state: StateFlow<StockUiState> = combine(base, movement, extras) { b, m, e ->
        StockUiState(
            loading = false,
            denied = e.flags.denied,
            filter = filter.value,
            selectedCategoryId = selectedCategory.value,
            counts = b.counts,
            categories = b.categories,
            categoryCounts = b.categoryCounts,
            rows = b.rows,
            totalCount = b.totalCount,
            movementIn = m.first,
            movementOut = m.second,
            totalCostCents = b.totalCostCents,
            totalSaleCents = b.totalSaleCents,
            marginAmountCents = b.totalSaleCents - b.totalCostCents,
            marginBps = MoneyFormatter.marginBps(b.totalCostCents, b.totalSaleCents),
            canAdjust = e.flags.canAdjust,
            canEdit = e.flags.canEdit,
            detail = e.detail,
            saving = e.flags.saving,
            message = e.flags.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StockUiState())

    init {
        viewModelScope.launch {
            if (!session.requirePermission(Permission.VIEW_STOCK, ACTION_OPEN)) {
                denied.value = true
            }
        }
    }

    // ─── Overview (tasks 8.1 / 8.2) ─────────────────────────────────────────

    fun setFilter(value: StockFilter) {
        filter.value = value
    }

    /** Segment-card category filter (null = all products). */
    fun setCategory(categoryId: String?) {
        selectedCategory.value = categoryId
    }

    suspend fun exportCsv(): String = catalog.exportCsv()

    fun selectProduct(productId: String?) {
        selectedProductId.value = productId
        message.value = null
    }

    fun consumeMessage() {
        message.value = null
    }

    // ─── Adjust (tasks 8.3 / 8.4) ───────────────────────────────────────────

    fun applyAdjustment(product: Product, mode: StockAdjustMode, amount: Long, reason: String) {
        if (saving.value) return
        if (amount < 0) {
            message.value = MESSAGE_INVALID_AMOUNT
            return
        }
        if (mode != StockAdjustMode.SET && amount == 0L) {
            message.value = MESSAGE_INVALID_AMOUNT
            return
        }
        if (reason.isBlank()) {
            message.value = MESSAGE_REASON_REQUIRED
            return
        }
        saving.value = true
        message.value = null
        viewModelScope.launch {
            try {
                if (!session.requirePermission(Permission.ADJUST_STOCK, ACTION_ADJUST, "product=${product.name}")) {
                    message.value = MESSAGE_DENIED
                    return@launch
                }
                val actor = session.activeUserId.value
                when (mode) {
                    StockAdjustMode.SET -> stock.setStockCount(product, amount, reason, actor)
                    StockAdjustMode.RECEIVE -> stock.adjustStock(product.id, amount, reason, actor)
                    StockAdjustMode.REMOVE -> stock.adjustStock(product.id, -amount, reason, actor)
                }
                session.logAction(ACTION_ADJUST, "product=${product.name} mode=$mode amount=$amount reason=${reason.trim()}")
            } catch (e: Exception) {
                message.value = e.message ?: "adjust failed"
            } finally {
                saving.value = false
            }
        }
    }

    fun saveThreshold(product: Product, threshold: Int) {
        if (threshold < 0) {
            message.value = MESSAGE_INVALID_AMOUNT
            return
        }
        viewModelScope.launch {
            if (!session.requirePermission(Permission.EDIT_CATALOG, ACTION_THRESHOLD, "product=${product.name}")) {
                message.value = MESSAGE_DENIED
                return@launch
            }
            stock.updateThreshold(product, threshold, session.activeUserId.value)
            session.logAction(ACTION_THRESHOLD, "product=${product.name} threshold=$threshold")
        }
    }

    // ─── Derivations ────────────────────────────────────────────────────────

    private fun buildBase(
        products: List<Product>,
        categories: List<Category>,
        activeFilter: StockFilter,
        categoryId: String?,
    ): BaseStock {
        val byId = categories.associateBy { it.id }
        val counts = mapOf(
            StockFilter.ALL to products.size,
            StockFilter.LOW to products.count { it.stockOnHand > 0 && it.stockOnHand <= it.lowStockThreshold },
            StockFilter.ZERO to products.count { it.stockOnHand == 0L },
            StockFilter.NEGATIVE to products.count { it.stockOnHand < 0L },
        )
        val rows = products
            .filter { product ->
                when (categoryId) {
                    null -> true
                    StockUiState.UNSET_CATEGORY -> product.categoryId == null
                    else -> product.categoryId == categoryId
                }
            }
            .filter { matches(it, activeFilter) }
            .sortedBy { it.name.lowercase() }
            .map { product ->
                StockRow(
                    product = product,
                    categoryName = byId[product.categoryId]?.name ?: "",
                    valueCostCents = product.stockOnHand * product.costCents,
                    valueSaleCents = product.stockOnHand * product.priceCents,
                )
            }
        return BaseStock(
            counts = counts,
            categories = categories.sortedBy { it.sortOrder },
            categoryCounts = products.groupingBy { it.categoryId }.eachCount(),
            rows = rows,
            totalCount = products.size,
            totalCostCents = rows.sumOf { it.valueCostCents },
            totalSaleCents = rows.sumOf { it.valueSaleCents },
        )
    }

    private fun matches(product: Product, filter: StockFilter): Boolean = when (filter) {
        StockFilter.ALL -> true
        StockFilter.LOW -> product.stockOnHand > 0 && product.stockOnHand <= product.lowStockThreshold
        StockFilter.ZERO -> product.stockOnHand == 0L
        StockFilter.NEGATIVE -> product.stockOnHand < 0L
    }

    /** Old/new per delta derived from the ledger: on-hand is SUM(deltas), so walk back from current. */
    private fun buildHistory(
        product: Product,
        deltasNewestFirst: List<StockDelta>,
        users: List<User>,
    ): List<StockHistoryRow> {
        val names = users.associate { it.id to it.name }
        val total = deltasNewestFirst.sumOf { it.delta }
        var running = product.stockOnHand - total // balance before the oldest delta
        val rows = deltasNewestFirst.sortedBy { it.at }.map { delta ->
            val before = running
            running += delta.delta
            StockHistoryRow(
                id = delta.id,
                at = delta.at,
                delta = delta.delta,
                before = before,
                after = running,
                reason = delta.reason,
                note = delta.note,
                userName = delta.userId?.let { names[it] },
            )
        }
        return rows.asReversed()
    }

    companion object {
        const val ACTION_OPEN = "stock.open"
        const val ACTION_ADJUST = "stock.adjust"
        const val ACTION_THRESHOLD = "stock.threshold"

        const val MESSAGE_DENIED = "__denied__"
        const val MESSAGE_REASON_REQUIRED = "__reason_required__"
        const val MESSAGE_INVALID_AMOUNT = "__invalid_amount__"
    }
}
