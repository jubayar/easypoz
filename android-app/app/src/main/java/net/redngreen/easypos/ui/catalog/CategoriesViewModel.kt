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
import net.redngreen.easypos.core.data.repo.CatalogRepository
import net.redngreen.easypos.core.model.Permission
import net.redngreen.easypos.core.session.SessionManager

data class CategoriesUiState(
    val loading: Boolean = true,
    val denied: Boolean = false,
    val categories: List<Category> = emptyList(),
    /** Set when a rename/create hits a data error (shown as a snackbar). */
    val message: String? = null,
)

/** Category management (task 6.1): create, rename, reorder — the sell tabs. */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val session: SessionManager,
    private val catalog: CatalogRepository,
) : ViewModel() {

    private val denied = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val orderDirty = MutableStateFlow<List<Category>?>(null)

    val state: StateFlow<CategoriesUiState> =
        combine(catalog.observeCategories(), denied, message, orderDirty) { cats, isDenied, msg, pending ->
            CategoriesUiState(
                loading = false,
                denied = isDenied,
                categories = pending ?: cats,
                message = msg,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    init {
        viewModelScope.launch {
            if (!session.requirePermission(Permission.EDIT_CATALOG, ACTION_OPEN)) {
                denied.value = true
            }
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            runCatching { catalog.ensureCategory(name, session.activeUserId.value) }
                .onFailure { message.value = it.message }
        }
    }

    fun renameCategory(id: String, newName: String) {
        viewModelScope.launch {
            runCatching { catalog.renameCategory(id, newName, session.activeUserId.value) }
                .onFailure { message.value = it.message }
        }
    }

    /** Local move for instant feedback; persisted on drop (apply order). */
    fun move(id: String, up: Boolean) {
        val list = state.value.categories.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in list.indices) return
        val item = list.removeAt(index)
        list.add(target, item)
        orderDirty.value = list.mapIndexed { i, c -> c.copy(sortOrder = i) }
    }

    fun applyOrder() {
        val ordered = orderDirty.value ?: return
        orderDirty.value = null
        viewModelScope.launch {
            catalog.reorderCategories(ordered.map { it.id }, session.activeUserId.value)
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    companion object {
        const val ACTION_OPEN = "categories.open"
    }
}
