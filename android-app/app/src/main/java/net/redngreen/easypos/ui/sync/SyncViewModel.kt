package net.redngreen.easypos.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.redngreen.easypos.core.database.dao.ConflictLogDao
import net.redngreen.easypos.core.database.entity.ConflictLog
import net.redngreen.easypos.core.sync.AccountSyncRepository
import net.redngreen.easypos.core.sync.SignInOutcome
import net.redngreen.easypos.core.sync.SyncEngine
import net.redngreen.easypos.core.sync.SyncOutcome
import net.redngreen.easypos.core.sync.SyncPhase
import net.redngreen.easypos.core.sync.SyncStatus
import net.redngreen.easypos.core.sync.SyncStatusRepository
import net.redngreen.easypos.core.network.TokenStore

/** Localizable sync feedback; resolved to a string in the composable. */
sealed interface SyncMessage {
    data class Pushed(val pushed: Int, val pulled: Int) : SyncMessage

    data class Restored(val count: Int) : SyncMessage

    data object SendFailed : SyncMessage

    /** Raw network/backend error text (already human-readable from the server). */
    data class Error(val text: String) : SyncMessage
}

data class SyncUiState(
    val signedIn: Boolean = false,
    val shopId: String? = null,
    val status: SyncStatus = SyncStatus(SyncPhase.SYNCED, 0, false),
    val identifier: String = "",
    val code: String = "",
    val awaitingCode: Boolean = false,
    val devCode: String? = null,
    val busy: Boolean = false,
    val message: SyncMessage? = null,
)

/**
 * Drives the sync screen (tasks 12.4/12.5): manual push, honest status,
 * conflict log, and the account sign-in that registers this tablet as the
 * shop's active device and restores the dataset from the cloud.
 */
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val account: AccountSyncRepository,
    private val engine: SyncEngine,
    private val statusRepository: SyncStatusRepository,
    private val tokens: TokenStore,
    conflictLogDao: ConflictLogDao,
) : ViewModel() {

    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    val conflicts: StateFlow<List<ConflictLog>> =
        conflictLogDao.observeRecent()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val stored = tokens.load()
            _state.update { it.copy(signedIn = stored != null, shopId = stored?.shopId) }
        }
        viewModelScope.launch {
            statusRepository.status.collect { status ->
                _state.update { it.copy(status = status, signedIn = status.signedIn) }
            }
        }
    }

    fun setIdentifier(value: String) = _state.update { it.copy(identifier = value, message = null) }

    fun setCode(value: String) = _state.update { it.copy(code = value, message = null) }

    fun requestCode() {
        val identifier = _state.value.identifier.trim()
        if (identifier.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            try {
                val response = account.requestCode(identifier)
                _state.update { it.copy(busy = false, awaitingCode = true, devCode = response.devCode) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, message = e.message?.let(SyncMessage::Error) ?: SyncMessage.SendFailed)
                }
            }
        }
    }

    fun signIn() {
        val current = _state.value
        if (current.identifier.isBlank() || current.code.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            when (val outcome = account.signIn(current.identifier.trim(), current.code.trim())) {
                is SignInOutcome.Success -> _state.update {
                    it.copy(
                        busy = false,
                        signedIn = true,
                        awaitingCode = false,
                        devCode = null,
                        code = "",
                        shopId = tokens.load()?.shopId,
                        message = SyncMessage.Restored(outcome.restored),
                    )
                }
                is SignInOutcome.Failure -> _state.update {
                    it.copy(busy = false, message = SyncMessage.Error(outcome.message))
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            account.signOut()
            _state.update { it.copy(signedIn = false, shopId = null, awaitingCode = false) }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val message = when (val outcome = engine.syncOnce()) {
                is SyncOutcome.Success -> SyncMessage.Pushed(outcome.pushed, outcome.pulled)
                SyncOutcome.NotSignedIn -> null
                is SyncOutcome.Failure -> SyncMessage.Error(outcome.message)
            }
            _state.update { it.copy(busy = false, message = message) }
        }
    }
}
