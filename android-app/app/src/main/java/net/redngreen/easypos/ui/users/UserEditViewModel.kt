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
import net.redngreen.easypos.core.data.repo.UserRepository
import net.redngreen.easypos.core.model.Permission
import net.redngreen.easypos.core.model.Role
import net.redngreen.easypos.core.session.SessionManager

enum class UserEditError {
    INVALID_NAME,
    INVALID_PIN,
    LAST_ADMIN,
    UNKNOWN,
}

data class UserEditUiState(
    val loading: Boolean = true,
    /** denied = MANAGE_USERS refused (denial already logged). */
    val denied: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val role: Role = Role.CASHIER,
    val active: Boolean = true,
    val saving: Boolean = false,
    val error: UserEditError? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class UserEditViewModel @Inject constructor(
    private val session: SessionManager,
    private val userRepo: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UserEditUiState())
    val state: StateFlow<UserEditUiState> = _state.asStateFlow()

    private var started = false
    private var loadedUserId: String? = null

    /** Loads an existing user by id, or primes a blank form for create. */
    fun load(userId: String?) {
        if (started) return
        started = true
        viewModelScope.launch {
            if (!session.requirePermission(Permission.MANAGE_USERS, UsersViewModel.ACTION_OPEN_USERS)) {
                _state.update { it.copy(loading = false, denied = true) }
                return@launch
            }
            if (userId == null) {
                loadedUserId = null
                _state.update { it.copy(loading = false, isNew = true) }
                return@launch
            }
            userRepo.observeUser(userId).collect { user ->
                if (user == null) {
                    _state.update { it.copy(loading = false, isNew = true) }
                } else {
                    loadedUserId = user.id
                    _state.update {
                        it.copy(
                            loading = false,
                            isNew = false,
                            name = user.name,
                            role = Role.fromName(user.role) ?: Role.CASHIER,
                            active = user.active,
                        )
                    }
                }
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value, error = null) }

    fun setRole(value: Role) = _state.update { it.copy(role = value, error = null) }

    fun setActive(value: Boolean) = _state.update { it.copy(active = value, error = null) }

    fun clearError() = _state.update { it.copy(error = null) }

    /** newPin: blank = keep current (edit only); required for create. */
    fun save(newPin: String) {
        val current = _state.value
        if (current.saving || current.denied) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val actorId = session.activeUserId.value
            try {
                if (current.isNew) {
                    userRepo.createUser(current.name, current.role, newPin, actorId)
                } else {
                    userRepo.updateUser(
                        id = loadedUserId ?: "",
                        name = current.name,
                        role = current.role,
                        active = current.active,
                        newPin = newPin.ifBlank { null },
                        actorUserId = actorId,
                    )
                }
                session.logAction(
                    action = if (current.isNew) "user.create" else "user.update",
                    detail = "name=${current.name.trim()} role=${current.role.name}",
                )
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: UserRepository.UserError.InvalidPin) {
                _state.update { it.copy(saving = false, error = UserEditError.INVALID_PIN) }
            } catch (e: UserRepository.UserError.InvalidName) {
                _state.update { it.copy(saving = false, error = UserEditError.INVALID_NAME) }
            } catch (e: UserRepository.UserError.LastActiveAdmin) {
                _state.update { it.copy(saving = false, error = UserEditError.LAST_ADMIN) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = UserEditError.UNKNOWN) }
            }
        }
    }
}
