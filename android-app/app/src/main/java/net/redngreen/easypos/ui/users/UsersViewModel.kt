package net.redngreen.easypos.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.redngreen.easypos.core.database.entity.User
import net.redngreen.easypos.core.data.repo.UserRepository
import net.redngreen.easypos.core.model.Permission
import net.redngreen.easypos.core.session.SessionManager

data class UsersUiState(
    val loading: Boolean = true,
    /** MANAGE_USERS denied — screen shows the denial instead of the list. */
    val denied: Boolean = false,
    val users: List<User> = emptyList(),
    /** The acting user's own id — protects self-deactivation in the UI. */
    val actorId: String? = null,
)

@HiltViewModel
class UsersViewModel @Inject constructor(
    private val session: SessionManager,
    private val userRepo: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UsersUiState())
    val state: StateFlow<UsersUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Role gate at the ViewModel level (task 5.3) — denial is logged
            // by SessionManager with user, action, and timestamp.
            if (!session.requirePermission(Permission.MANAGE_USERS, ACTION_OPEN_USERS)) {
                _state.update { it.copy(loading = false, denied = true) }
                return@launch
            }
            _state.update { it.copy(actorId = session.activeUserId.value) }
            userRepo.observeUsers().collect { users ->
                _state.update { it.copy(loading = false, users = users) }
            }
        }
    }

    companion object {
        const val ACTION_OPEN_USERS = "users.open"
    }
}
