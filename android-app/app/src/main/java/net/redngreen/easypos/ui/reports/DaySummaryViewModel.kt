package net.redngreen.easypos.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.redngreen.easypos.core.data.repo.ReportsRepository
import net.redngreen.easypos.core.data.repo.TenantConfigRepository
import net.redngreen.easypos.core.model.Permission
import net.redngreen.easypos.core.model.Role
import net.redngreen.easypos.core.money.MoneyFormatter
import net.redngreen.easypos.core.session.SessionManager
import net.redngreen.easypos.print.DaySummaryShare
import net.redngreen.easypos.print.PrintController

data class DaySummaryUiState(
    val loading: Boolean = true,
    val epochDay: Long = LocalDate.now().toEpochDay(),
    val summary: ReportsRepository.DaySummary? = null,
    /** Yesterday's gross total — the "vs yesterday" comparison. */
    val yesterdayTotalCents: Long = 0L,
    val shopName: String = "",
    val saving: Boolean = false,
    val message: String? = null,
    val canCloseRegister: Boolean = false,
) {
    val isToday: Boolean get() = epochDay == LocalDate.now().toEpochDay()
    val latestDrawerCount: net.redngreen.easypos.core.data.entity.DrawerCount?
        get() = summary?.drawerCounts?.firstOrNull()
}

/**
 * Day summary / Z-report (tasks 9.3 + 9.5): all figures are computed queries
 * over local data via [ReportsRepository]; drawer reconciliation persists a
 * journaled DrawerCount with attribution. Print/share run off-screen.
 */
@HiltViewModel
class DaySummaryViewModel @Inject constructor(
    private val reports: ReportsRepository,
    private val session: SessionManager,
    private val printer: PrintController,
    private val share: DaySummaryShare,
    tenantRepo: TenantConfigRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DaySummaryUiState())
    val state: StateFlow<DaySummaryUiState> = _state.asStateFlow()

    /** Non-null once a PDF is ready — the screen launches the share sheet. */
    val shareFile = MutableStateFlow<File?>(null)

    val printState: StateFlow<net.redngreen.easypos.print.PrintState> = printer.state

    val shopName: StateFlow<String> =
        tenantRepo.observe()
            .map { it?.name?.takeIf { name -> name.isNotBlank() } ?: "EasyPOS" }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "EasyPOS")

    init {
        viewModelScope.launch {
            session.activeUser.collect { user ->
                val role = user?.role?.let(Role::fromName)
                _state.update { it.copy(canCloseRegister = role?.can(Permission.VIEW_ALL_CASH) == true) }
            }
        }
        reload()
    }

    fun setEpochDay(epochDay: Long) {
        val clamped = epochDay.coerceAtMost(LocalDate.now().toEpochDay())
        _state.update { it.copy(epochDay = clamped) }
        reload()
    }

    fun prevDay() = setEpochDay(_state.value.epochDay - 1)

    fun nextDay() = setEpochDay(_state.value.epochDay + 1)

    fun today() = setEpochDay(LocalDate.now().toEpochDay())

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Drawer reconciliation (task 9.5): expected vs counted, variance journaled. */
    fun saveDrawerCount(countedText: String) {
        val summary = _state.value.summary ?: return
        val counted = runCatching { MoneyFormatter.parseCents(countedText) }.getOrNull()
        if (counted == null || counted < 0) {
            _state.update { it.copy(message = MESSAGE_INVALID_COUNT) }
            return
        }
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                if (!session.requirePermission(Permission.VIEW_ALL_CASH, ACTION_DRAWER_COUNT)) {
                    _state.update { it.copy(saving = false, message = MESSAGE_DENIED) }
                    return@launch
                }
                reports.recordDrawerCount(summary.expectedDrawerCents, counted, session.activeUserId.value)
                session.logAction(ACTION_DRAWER_COUNT, "expected=${summary.expectedDrawerCents} counted=$counted")
                _state.update { it.copy(saving = false) }
                reload()
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, message = e.message ?: "count failed") }
            }
        }
    }

    fun printReport() = printer.printDaySummary(_state.value.epochDay)

    fun retryPrint() = printer.retry()

    /** Renders the PDF off the UI thread; the screen picks it up from [shareFile]. */
    fun shareReport() {
        val current = _state.value
        val summary = current.summary ?: return
        viewModelScope.launch {
            try {
                shareFile.value = share.write(summary, shopName.value, current.epochDay)
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "share failed") }
            }
        }
    }

    fun consumeShareFile() {
        shareFile.value = null
    }

    fun shareIntentFor(file: File): Intent = share.shareIntent(file)

    private fun reload() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val day = _state.value.epochDay
                val summary = reports.daySummary(day)
                val yesterday = reports.daySummary(day - 1).salesTotalCents
                _state.update {
                    it.copy(loading = false, summary = summary, yesterdayTotalCents = yesterday)
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, message = e.message ?: "load failed") }
            }
        }
    }

    companion object {
        const val ACTION_DRAWER_COUNT = "day.drawer_count"

        const val MESSAGE_DENIED = "__denied__"
        const val MESSAGE_INVALID_COUNT = "__invalid_count__"
    }
}
