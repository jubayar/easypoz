package net.redngreen.easypos.ui.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.redngreen.easypos.core.database.dao.CategoryDao
import net.redngreen.easypos.core.database.dao.UserDao
import net.redngreen.easypos.feature.catalog.domain.Product
import net.redngreen.easypos.feature.catalog.domain.CatalogRepository
import net.redngreen.easypos.feature.catalog.domain.ProductFields
import net.redngreen.easypos.core.model.Permission
import net.redngreen.easypos.core.model.Role
import net.redngreen.easypos.core.security.PinHasher
import net.redngreen.easypos.core.session.SessionManager

data class QuickEditUiState(
    val loading: Boolean = true,
    val product: Product? = null,
    val categoryName: String? = null,
    /** True while the active cashier still needs a Manager/Admin PIN (spec 6.7). */
    val needsAuth: Boolean = false,
    val authorizedId: String? = null,
    val authorizedName: String? = null,
    val authorizedRole: String? = null,
    val priceCents: Long = 0L,
    val costCents: Long = 0L,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val message: String? = null,
)

/**
 * Quick price edit (task 6.7): long-press a sell tile or cart line → inline
 * sale/cost edit applied immediately, journaled and attributed. Cashier
 * triggers require a Manager/Admin PIN (product-catalog spec). Everything is
 * local — the sell path is never blocked.
 */
@HiltViewModel
class QuickEditViewModel @Inject constructor(
    private val session: SessionManager,
    private val userDao: UserDao,
    private val categoryDao: CategoryDao,
    private val catalog: CatalogRepository,
    private val pinHasher: PinHasher,
) : ViewModel() {

    private val _state = MutableStateFlow(QuickEditUiState())
    val state: StateFlow<QuickEditUiState> = _state.asStateFlow()

    fun load(productId: String) {
        _state.value = QuickEditUiState(loading = true)
        viewModelScope.launch {
            val product = catalog.getProduct(productId)
            if (product == null) {
                _state.update { it.copy(loading = false, saved = true) }
                return@launch
            }
            val activeUser = session.activeUserId.value?.let { userDao.getById(it) }
            val isPrivileged = activeUser?.role?.let { role ->
                Role.fromName(role)?.can(Permission.EDIT_CATALOG) == true
            } == true
            val category = product.categoryId?.let { categoryDao.getById(it) }
            _state.update {
                it.copy(
                    loading = false,
                    product = product,
                    categoryName = category?.name,
                    needsAuth = !isPrivileged,
                    authorizedId = if (isPrivileged) activeUser?.id else null,
                    authorizedName = if (isPrivileged) activeUser?.name else null,
                    authorizedRole = if (isPrivileged) activeUser?.role else null,
                    priceCents = product.priceCents,
                    costCents = product.costCents,
                )
            }
        }
    }

    /** Verifies the PIN against any active Manager/Admin and records who authorized. */
    fun verifyPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val match = userDao.observeActive().first()
                .filter { it.role == "ADMIN" || it.role == "MANAGER" }
                .firstOrNull { pinHasher.verify(pin, it.pinSalt, it.pinHash) }
            if (match != null) {
                session.logAction(
                    "product.quick_edit.auth",
                    "authorized=${match.name} role=${match.role}",
                )
                _state.update {
                    it.copy(
                        needsAuth = false,
                        authorizedId = match.id,
                        authorizedName = match.name,
                        authorizedRole = match.role,
                    )
                }
            }
            onResult(match != null)
        }
    }

    fun setPrice(cents: Long) = _state.update { it.copy(priceCents = cents.coerceIn(0L, 99_999_999L)) }

    fun setCost(cents: Long) = _state.update { it.copy(costCents = cents.coerceIn(0L, 99_999_999L)) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun save() {
        val current = _state.value
        val product = current.product
        if (current.saving || product == null) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                // Attribution: the authorizing Manager/Admin when PIN-gated,
                // otherwise the acting privileged user (design: "Rekod diaudit
                // atas nama …"). The operating cashier rides the action log.
                val actor = current.authorizedId ?: session.activeUserId.value
                catalog.updateProduct(
                    id = product.id,
                    fields = ProductFields(
                        name = product.name,
                        categoryId = product.categoryId,
                        barcode = product.barcode,
                        sku = product.sku,
                        costCents = current.costCents,
                        priceCents = current.priceCents,
                        taxRateBps = product.taxRateBps,
                        active = product.active,
                        lowStockThreshold = product.lowStockThreshold,
                        emoji = product.emoji,
                        color = product.color,
                        photo = product.photo,
                        location = product.location,
                    ),
                    actorUserId = actor,
                )
                session.logAction(
                    "product.quick_edit",
                    "product=${product.name} price=${current.priceCents} cost=${current.costCents}",
                )
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, message = e.message ?: "save failed") }
            }
        }
    }
}
