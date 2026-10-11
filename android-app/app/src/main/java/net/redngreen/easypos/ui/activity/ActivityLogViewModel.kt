package net.redngreen.easypos.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.redngreen.easypos.core.data.dao.ActivityLogDao
import net.redngreen.easypos.core.data.entity.ActivityLog
import net.redngreen.easypos.core.model.Permission
import net.redngreen.easypos.core.session.SessionManager

data class ActivityLogUiState(
    val loading: Boolean = true,
    val denied: Boolean = false,
    val entries: List<ActivityLog> = emptyList(),
)

/** Admin viewer over denials, logins, locks, and attributed actions (task 5.5). */
@HiltViewModel
class ActivityLogViewModel @Inject constructor(
    private val session: SessionManager,
    private val activityLogDao: ActivityLogDao,
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityLogUiState())
    val state: StateFlow<ActivityLogUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (!session.requirePermission(Permission.VIEW_ACTIVITY_LOG, ACTION_OPEN)) {
                _state.update { it.copy(loading = false, denied = true) }
                return@launch
            }
            activityLogDao.observeRecent().collect { entries ->
                _state.update { it.copy(loading = false, entries = entries) }
            }
        }
    }

    companion object {
        const val ACTION_OPEN = "activity_log.open"
    }
}
