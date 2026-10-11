package net.redngreen.easypos.feature.login.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.redngreen.easypos.feature.login.domain.LoginRepository
import net.redngreen.easypos.feature.login.ui.model.LoginAction
import net.redngreen.easypos.feature.login.ui.model.LoginEvent
import net.redngreen.easypos.feature.login.ui.model.LoginState

/**
 * Drives the lock overlay: who is active, who can unlock, and the idle-lock
 * timeout. Login is uniform — initial unlock, re-unlock, and quick-switch
 * all present a user + that user's PIN (user-access-control spec).
 */
class LoginViewModel(
    private val loginRepo: LoginRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            loginRepo.observeActiveUser().collect { user ->
                _state.update { it.copy(activeUser = user) }
            }
        }
        viewModelScope.launch {
            loginRepo.observeUnlockableUsers().collect { users ->
                _state.update { it.copy(unlockableUsers = users) }
            }
        }
        viewModelScope.launch {
            loginRepo.observeHasAnyUser().collect { hasAny ->
                _state.update { it.copy(hasAnyUser = hasAny) }
            }
        }
        viewModelScope.launch {
            loginRepo.observeHasUnlockableUser().collect { hasUnlockable ->
                _state.update { it.copy(hasUnlockableUser = hasUnlockable) }
            }
        }
        viewModelScope.launch {
            loginRepo.observeIdleLockMinutes().collect { minutes ->
                _state.update { it.copy(idleLockMinutes = minutes) }
            }
        }
    }

    fun onAction(action: LoginAction) {
        when (action) {
            is LoginAction.Unlock -> viewModelScope.launch {
                if (!loginRepo.login(action.userId, action.pin)) {
                    _events.send(LoginEvent.UnlockFailed)
                }
            }

            is LoginAction.CreateFirstAdmin -> viewModelScope.launch {
                if (!loginRepo.createFirstAdmin(action.name, action.pin)) {
                    _events.send(LoginEvent.AdminCreateFailed)
                }
            }

            is LoginAction.LockNow -> loginRepo.lockNow(action.detail)
        }
    }
}
