package net.redngreen.easypos.ui.cash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.redngreen.easypos.core.database.dao.UserDao
import net.redngreen.easypos.core.database.entity.CashEntry
import net.redngreen.easypos.core.data.repo.CashRepository
import net.redngreen.easypos.core.model.Permission
import net.redngreen.easypos.core.model.Role
import net.redngreen.easypos.core.money.MoneyFormatter
import net.redngreen.easypos.core.session.SessionManager

enum class CashDirection { IN, OUT }

data class CashEntryRow(
    val entry: CashEntry,
    val userName: String?,
)

data class CashUiState(
    val loading: Boolean = true,
    val denied: Boolean = false,
    val canRecord: Boolean = false,
    val canViewAll: Boolean = false,
    val expectedDrawerCents: Long = 0L,
    val entries: List<CashEntryRow> = emptyList(),
    /** Today's visible in/out sums (design: Total In Today / Total Out Today). */
    val todayInCents: Long = 0L,
    val todayOutCents: Long = 0L,
    /** Who is on the terminal — the entry card footer (design: Cashier in Charge). */
    val activeUserName: String? = null,
    val saving: Boolean = false,
    val message: String? = null,
)

private data class CashPerms(
    val canRecord: Boolean,
    val canViewAll: Boolean,
    val canViewOwn: Boolean,
    val userId: String?,
)

/**
 * Cash in/out screen (tasks 9.1/9.2/9.4). The entry form is gated by
 * CASH_IN_OUT (a Cashier's attempt is denied and logged); the log shows all
 * entries to Admin/Manager and only the viewer's own to a Cashier
 * (cash-management spec). The drawer figure is live and labeled expected.
 */
@HiltViewModel
class CashViewModel @Inject constructor(
    private val session: SessionManager,
    private val cash: CashRepository,
    userDao: UserDao,
) : ViewModel() {

    private val saving = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val entries = cash.observeEntries()
    private val users = userDao.observeAll()
    private val drawer = cash.observeExpectedDrawerCents()

    private val perms = session.activeUser.map { user ->
        val role = user?.role?.let(Role::fromName)
        CashPerms(
            canRecord = role?.can(Permission.CASH_IN_OUT) == true,
            canViewAll = role?.can(Permission.VIEW_ALL_CASH) == true,
            canViewOwn = role?.can(Permission.VIEW_OWN_CASH) == true,
            userId = user?.id,
        )
    }

    val state: StateFlow<CashUiState> = combine(
        entries,
        users,
        drawer,
        perms,
        combine(saving, message) { s, m -> s to m },
    ) { entryList, userList, drawerCents, p, flags ->
        val (isSaving, msg) = flags
        val names = userList.associate { it.id to it.name }
        val visible = if (p.canViewAll) entryList
        else entryList.filter { it.userId != null && it.userId == p.userId }
        val startOfDay = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val todays = visible.filter { it.at >= startOfDay }
        CashUiState(
            loading = false,
            denied = !p.canViewAll && !p.canViewOwn,
            canRecord = p.canRecord,
            canViewAll = p.canViewAll,
            expectedDrawerCents = drawerCents,
            entries = visible.map { CashEntryRow(it, it.userId?.let { id -> names[id] }) },
            todayInCents = todays.filter { it.direction == CashRepository.DIRECTION_IN }.sumOf { it.amountCents },
            todayOutCents = todays.filter { it.direction == CashRepository.DIRECTION_OUT }.sumOf { it.amountCents },
            activeUserName = p.userId?.let { names[it] },
            saving = isSaving,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CashUiState())

    /** Records a keypad-built entry (cents — no float parsing on this path). */
    fun record(direction: CashDirection, amountCents: Long, reason: String) {
        if (saving.value) return
        if (amountCents <= 0) {
            message.value = MESSAGE_INVALID_AMOUNT
            return
        }
        if (reason.isBlank()) {
            message.value = MESSAGE_REASON_REQUIRED
            return
        }
        val amount = amountCents
        saving.value = true
        message.value = null
        viewModelScope.launch {
            try {
                val dir = if (direction == CashDirection.IN) {
                    CashRepository.DIRECTION_IN
                } else {
                    CashRepository.DIRECTION_OUT
                }
                if (!session.requirePermission(Permission.CASH_IN_OUT, ACTION_RECORD, "direction=$dir amount=$amount")) {
                    message.value = MESSAGE_DENIED
                    return@launch
                }
                cash.recordEntry(dir, amount, reason, session.activeUserId.value)
                session.logAction(ACTION_RECORD, "direction=$dir amount=$amount reason=${reason.trim()}")
            } catch (e: Exception) {
                message.value = e.message ?: "cash entry failed"
            } finally {
                saving.value = false
            }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    companion object {
        const val ACTION_OPEN = "cash.open"
        const val ACTION_RECORD = "cash.entry"

        const val MESSAGE_DENIED = "__denied__"
        const val MESSAGE_REASON_REQUIRED = "__reason_required__"
        const val MESSAGE_INVALID_AMOUNT = "__invalid_amount__"
    }
}
