package net.redngreen.easypos.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.redngreen.easypos.core.database.entity.Category
import net.redngreen.easypos.core.database.entity.Product
import net.redngreen.easypos.core.data.repo.CatalogRepository
import net.redngreen.easypos.core.model.Permission
import net.redngreen.easypos.core.money.MoneyFormatter
import net.redngreen.easypos.core.session.SessionManager

enum class ProductEditError {
    INVALID_NAME,
    INVALID_PRICE,
    INVALID_COST,
    INVALID_STOCK,
    BARCODE_TAKEN,
    UNKNOWN,
}

/** Live gross-margin preview (product-catalog spec): RM value + bps. */
data class MarginPreview(
    val marginCents: Long,
    val marginBps: Int,
)

data class ProductEditUiState(
    val loading: Boolean = true,
    val denied: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val categoryId: String? = null,
    val categories: List<Category> = emptyList(),
    val barcode: String = "",
    val sku: String = "",
    val costText: String = "",
    val priceText: String = "",
    val emoji: String = "",
    val location: String = "",
    val active: Boolean = true,
    /** Create-only: initial stock journaled as ADJUST (task 6.2). */
    val initialStockText: String = "",
    /** Low-stock alert threshold (design: amber badge trigger). */
    val thresholdText: String = "5",
    /** Edit-only: cached on-hand for display. */
    val stockOnHand: Long? = null,
    val margin: MarginPreview? = null,
    val saving: Boolean = false,
    val error: ProductEditError? = null,
    /** Raw repository detail (e.g. which product owns the barcode). */
    val errorDetail: String? = null,
    val saved: Boolean = false,
)

/**
 * Create/edit a product (task 6.2). The margin preview recomputes as
 * cost/price are typed; save enforces barcode uniqueness and journals
 * everything (plus the initial stock delta on create).
 */
@HiltViewModel
class ProductEditViewModel @Inject constructor(
    private val session: SessionManager,
    private val catalog: CatalogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProductEditUiState())
    val state: StateFlow<ProductEditUiState> = _state.asStateFlow()

    private var started = false
    private var productId: String? = null

    fun load(id: String?, prefillBarcode: String? = null) {
        if (started) return
        started = true
        productId = id
        if (id == null && prefillBarcode != null) {
            // Unknown-scan affordance: prime the create form with the barcode.
            _state.update { it.copy(barcode = prefillBarcode) }
        }
        viewModelScope.launch {
            if (!session.requirePermission(Permission.EDIT_CATALOG, ACTION_OPEN)) {
                _state.update { it.copy(loading = false, denied = true) }
                return@launch
            }

            combine(
                catalog.observeProducts(),
                catalog.observeCategories(),
            ) { products, categories ->
                val existing = productId?.let { id -> products.firstOrNull { it.id == id } }
                Triple(existing, categories, products.any())
            }.collect { (existing, categories, productsLoaded) ->
                _state.update { current ->
                    if (existing == null) {
                        // Create form: defaults settle once the catalog has loaded.
                        current.copy(
                            loading = !productsLoaded && current.loading,
                            isNew = true,
                            categories = categories,
                            categoryId = current.categoryId ?: categories.firstOrNull()?.id,
                        )
                    } else {
                        // First emission that carries the product primes the form;
                        // later ones only refresh the category list.
                        if (current.isNew) {
                            current.copy(
                                loading = false,
                                isNew = false,
                                categories = categories,
                                name = existing.name,
                                categoryId = existing.categoryId,
                                barcode = existing.barcode ?: "",
                                sku = existing.sku ?: "",
                                costText = MoneyFormatter.formatPlain(existing.costCents),
                                priceText = MoneyFormatter.formatPlain(existing.priceCents),
                                emoji = existing.emoji ?: "",
                                location = existing.location ?: "",
                                active = existing.active,
                                thresholdText = existing.lowStockThreshold.toString(),
                                stockOnHand = existing.stockOnHand,
                                margin = margin(existing.costCents, existing.priceCents),
                            )
                        } else {
                            current.copy(categories = categories)
                        }
                    }
                }
            }
        }
    }

    // ─── Field setters (cost/price recompute the margin preview) ─────────────

    fun setName(v: String) = edit { it.copy(name = v, error = null, errorDetail = null) }
    fun setCategoryId(v: String?) = edit { it.copy(categoryId = v) }
    fun setBarcode(v: String) = edit { it.copy(barcode = v, error = null, errorDetail = null) }
    fun setSku(v: String) = edit { it.copy(sku = v) }
    fun setEmoji(v: String) = edit { it.copy(emoji = v.take(4)) }
    fun setLocation(v: String) = edit { it.copy(location = v) }
    fun setActive(v: Boolean) = edit { it.copy(active = v) }

    fun setCost(v: String) = edit {
        it.copy(costText = v, margin = marginOfSafe(v, it.priceText))
    }

    fun setPrice(v: String) = edit {
        it.copy(priceText = v, margin = marginOfSafe(it.costText, v))
    }

    fun setInitialStock(v: String) = edit {
        it.copy(initialStockText = v.filter(Char::isDigit))
    }

    fun setThreshold(v: String) = edit {
        it.copy(thresholdText = v.filter(Char::isDigit).take(4))
    }

    private fun edit(block: (ProductEditUiState) -> ProductEditUiState) = _state.update(block)

    private fun marginOfSafe(costText: String, priceText: String): MarginPreview? = try {
        margin(
            MoneyFormatter.parseCents(costText.ifBlank { "0" }),
            MoneyFormatter.parseCents(priceText.ifBlank { "0" }),
        )
    } catch (e: NumberFormatException) {
        null // typing in progress; preview returns once both parse
    }

    private fun margin(costCents: Long, priceCents: Long): MarginPreview? {
        val bps = MoneyFormatter.marginBps(costCents, priceCents) ?: return null
        return MarginPreview(marginCents = priceCents - costCents, marginBps = bps)
    }

    // ─── Save ────────────────────────────────────────────────────────────────

    fun save() {
        val current = _state.value
        if (current.saving || current.denied) return
        if (current.name.isBlank()) {
            _state.update { it.copy(error = ProductEditError.INVALID_NAME) }
            return
        }
        _state.update { it.copy(saving = true, error = null, errorDetail = null) }
        viewModelScope.launch {
            val actorId = session.activeUserId.value

            val costCents = try {
                MoneyFormatter.parseCents(current.costText.ifBlank { "0" })
            } catch (e: NumberFormatException) {
                fail(ProductEditError.INVALID_COST); return@launch
            }
            val priceCents = try {
                MoneyFormatter.parseCents(current.priceText)
            } catch (e: NumberFormatException) {
                fail(ProductEditError.INVALID_PRICE); return@launch
            }
            val initialStock = if (current.isNew && current.initialStockText.isNotBlank()) {
                val stock = current.initialStockText.toLongOrNull()
                if (stock == null) {
                    fail(ProductEditError.INVALID_STOCK); return@launch
                }
                stock
            } else {
                null
            }

            try {
                val fields = CatalogRepository.ProductFields(
                    name = current.name,
                    categoryId = current.categoryId,
                    barcode = current.barcode.trim().ifEmpty { null },
                    sku = current.sku.trim().ifEmpty { null },
                    costCents = costCents,
                    priceCents = priceCents,
                    active = current.active,
                    lowStockThreshold = current.thresholdText.toIntOrNull() ?: 5,
                    emoji = current.emoji.trim().ifEmpty { null },
                    location = current.location.trim().ifEmpty { null },
                )
                if (current.isNew) {
                    catalog.createProduct(fields, initialStock, actorId)
                } else {
                    catalog.updateProduct(productId ?: "", fields, actorId)
                }
                session.logAction(
                    action = if (current.isNew) "product.create" else "product.update",
                    detail = "name=${current.name.trim()}",
                )
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: CatalogRepository.CatalogError.BarcodeTaken) {
                fail(ProductEditError.BARCODE_TAKEN, e.message)
            } catch (e: Exception) {
                fail(ProductEditError.UNKNOWN, e.message)
            }
        }
    }

    private fun fail(error: ProductEditError, detail: String? = null) {
        _state.update { it.copy(saving = false, error = error, errorDetail = detail) }
    }

    companion object {
        const val ACTION_OPEN = "product.edit.open"
    }
}
