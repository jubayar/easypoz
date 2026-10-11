package net.redngreen.easypos.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import net.redngreen.easypos.core.data.entity.Category
import net.redngreen.easypos.core.data.entity.Product
import net.redngreen.easypos.core.data.repo.CatalogRepository
import net.redngreen.easypos.core.model.Permission
import net.redngreen.easypos.core.session.SessionManager

data class ProductRow(
    val product: Product,
    val categoryName: String,
)

data class ProductsUiState(
    val loading: Boolean = true,
    val denied: Boolean = false,
    val query: String = "",
    val rows: List<ProductRow> = emptyList(),
    val totalCount: Int = 0,
)

/**
 * Catalog management list (tasks 6.2/6.6): search-as-you-type over partial
 * name / barcode prefix / SKU prefix, barcode-exact hits ranked first —
 * the same ranking the sell screen's capture field uses.
 */
@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val session: SessionManager,
    private val catalog: CatalogRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val denied = MutableStateFlow(false)

    val state: StateFlow<ProductsUiState> =
        combine(catalog.observeProducts(), catalog.observeCategories(), query, denied) {
                products, categories, q, isDenied ->
            val byId = categories.associateBy(Category::id)
            ProductsUiState(
                loading = false,
                denied = isDenied,
                query = q,
                totalCount = products.size,
                rows = filterProducts(products, q).map { product ->
                    ProductRow(
                        product = product,
                        categoryName = byId[product.categoryId]?.name ?: "",
                    )
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductsUiState())

    init {
        viewModelScope.launch {
            if (!session.requirePermission(Permission.EDIT_CATALOG, ACTION_OPEN)) {
                denied.value = true
            }
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    suspend fun exportCsv(): String = catalog.exportCsv()

    private fun filterProducts(products: List<Product>, raw: String): List<Product> {
        val q = raw.trim()
        if (q.isEmpty()) return products
        return products
            .map { product ->
                val nameHit = product.name.contains(q, ignoreCase = true)
                val barcodeHit = product.barcode?.startsWith(q) == true
                val skuHit = product.sku?.startsWith(q, ignoreCase = true) == true
                // Barcode-exact wins outright (spec: instant resolution).
                val rank = when {
                    product.barcode == q -> 0
                    barcodeHit -> 1
                    nameHit -> 2
                    skuHit -> 3
                    else -> -1
                }
                product to rank
            }
            .filter { (_, rank) -> rank >= 0 }
            .sortedWith(compareBy({ (_, rank) -> rank }, { (product, _) -> product.name }))
            .map { (product, _) -> product }
    }

    companion object {
        const val ACTION_OPEN = "products.open"
    }
}
