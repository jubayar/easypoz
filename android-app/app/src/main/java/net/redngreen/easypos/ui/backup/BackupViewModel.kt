package net.redngreen.easypos.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.redngreen.easypos.core.data.repo.BackupRepository
import net.redngreen.easypos.core.model.Permission
import net.redngreen.easypos.core.session.SessionManager

data class BackupUiState(
    val busy: Boolean = false,
    val denied: Boolean = false,
    /** Non-null when an export is ready to be written to a user-chosen file. */
    val exportJson: String? = null,
    val summary: BackupRepository.RestoreSummary? = null,
    val error: String? = null,
)

/** Manual backup + restore (task 11.3), Admin-gated (MANAGE_SETTINGS). */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val session: SessionManager,
    private val backup: BackupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun export() {
        run(ACTION_EXPORT) {
            val json = backup.exportJson()
            _state.update { it.copy(exportJson = json) }
        }
    }

    fun consumeExport() = _state.update { it.copy(exportJson = null) }

    fun restore(jsonText: String) {
        run(ACTION_RESTORE) {
            val summary = backup.restore(jsonText)
            _state.update { it.copy(summary = summary) }
        }
    }

    fun clearResult() = _state.update { it.copy(summary = null, error = null) }

    private fun run(action: String, block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, summary = null) }
        viewModelScope.launch {
            try {
                if (!session.requirePermission(Permission.MANAGE_SETTINGS, action)) {
                    _state.update { it.copy(busy = false, denied = true) }
                    return@launch
                }
                block()
                session.logAction(action)
                _state.update { it.copy(busy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "backup failed") }
            }
        }
    }

    companion object {
        const val ACTION_EXPORT = "backup.export"
        const val ACTION_RESTORE = "backup.restore"
    }
}
