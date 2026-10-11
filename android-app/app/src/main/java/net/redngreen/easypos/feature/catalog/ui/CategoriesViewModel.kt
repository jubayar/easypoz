package net.redngreen.easypos.feature.catalog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.redngreen.easypos.core.model.Permission
import net.redngreen.easypos.core.session.SessionManager
import net.redngreen.easypos.feature.catalog.domain.CatalogRepository
import net.redngreen.easypos.feature.catalog.domain.Category

data class CategoriesUiState(
    val loading: Boolean = true,
    val denied: Boolean = false,
    val categories: List<Category> = emptyList(),
    /** Set when a rename/create hits a data error (shown as a snackbar). */
    val message: String? = null,
)

sealed interface CategoriesAction {
    data class Add(val name: String) : CategoriesAction

    data class Rename(val id: String, val newName: String) : CategoriesAction

    data class Move(val id: String, val up: Boolean) : CategoriesAction

    data object ApplyOrder : CategoriesAction

    data object ConsumeMessage : CategoriesAction
}

/** Category management (task 6.1): create, rename, reorder — the sell tabs. */
class CategoriesViewModel(
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

    fun onAction(action: CategoriesAction) {
        when (action) {
            is CategoriesAction.Add -> viewModelScope.launch {
                runCatching { catalog.ensureCategory(action.name, session.activeUserId.value) }
                    .onFailure { message.value = it.message }
            }

            is CategoriesAction.Rename -> viewModelScope.launch {
                runCatching { catalog.renameCategory(action.id, action.newName, session.activeUserId.value) }
                    .onFailure { message.value = it.message }
            }

            // Local move for instant feedback; persisted on drop (apply order).
            is CategoriesAction.Move -> {
                val list = state.value.categories.toMutableList()
                val index = list.indexOfFirst { it.id == action.id }
                val target = if (action.up) index - 1 else index + 1
                if (index < 0 || target !in list.indices) return
                val item = list.removeAt(index)
                list.add(target, item)
                orderDirty.value = list.mapIndexed { i, c -> c.copy(sortOrder = i) }
            }

            CategoriesAction.ApplyOrder -> {
                val ordered = orderDirty.value ?: return
                orderDirty.value = null
                viewModelScope.launch {
                    catalog.reorderCategories(ordered.map { it.id }, session.activeUserId.value)
                }
            }

            CategoriesAction.ConsumeMessage -> message.value = null
        }
    }

    companion object {
        const val ACTION_OPEN = "categories.open"
    }
}
