package net.redngreen.easypos.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.redngreen.easypos.core.database.SettingsKeys
import net.redngreen.easypos.core.data.repo.SettingsRepository
import net.redngreen.easypos.core.model.Permission
import net.redngreen.easypos.core.session.SessionManager
import net.redngreen.easypos.core.print.BluetoothPrinter
import net.redngreen.easypos.core.print.PrintController
import net.redngreen.easypos.core.print.PrintState

/** Printer setup (task 10.5): pick a paired printer, width, drawer kick, test print. */
@HiltViewModel
class PrinterSetupViewModel @Inject constructor(
    private val session: SessionManager,
    private val settings: SettingsRepository,
    private val printer: BluetoothPrinter,
    private val printController: PrintController,
) : ViewModel() {

    data class Device(val name: String, val address: String)

    data class PrinterSetupUiState(
        val bluetoothAvailable: Boolean = true,
        val devices: List<Device> = emptyList(),
        val mac: String? = null,
        val widthMm: Int = SettingsKeys.PRINTER_WIDTH_DEFAULT_MM,
        val drawerKick: Boolean = true,
        val printState: PrintState = PrintState.Idle,
        val message: String? = null,
    )

    private val bluetoothAvailable = printer.isAvailable
    private val devices = MutableStateFlow<List<Device>>(emptyList())
    private val mac = MutableStateFlow<String?>(null)
    private val widthMm = MutableStateFlow(SettingsKeys.PRINTER_WIDTH_DEFAULT_MM)
    private val drawerKick = MutableStateFlow(true)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<PrinterSetupUiState> = combine(
        devices,
        mac,
        widthMm,
        drawerKick,
        combine(printController.state, message) { ps, msg -> ps to msg },
    ) { deviceList, macAddr, width, kick, flags ->
        PrinterSetupUiState(
            bluetoothAvailable = bluetoothAvailable,
            devices = deviceList,
            mac = macAddr,
            widthMm = width,
            drawerKick = kick,
            printState = flags.first,
            message = flags.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrinterSetupUiState())

    init {
        viewModelScope.launch {
            mac.value = settings.get(SettingsKeys.PRINTER_MAC)
            widthMm.value = settings.get(SettingsKeys.PRINTER_WIDTH_MM)?.toIntOrNull()
                ?: SettingsKeys.PRINTER_WIDTH_DEFAULT_MM
            drawerKick.value = settings.get(SettingsKeys.DRAWER_KICK)?.toBooleanStrictOrNull() ?: true
            refreshDevices()
        }
    }

    fun refreshDevices() {
        devices.value = printer.bondedDevices().map { Device(it.name ?: it.address, it.address) }
    }

    fun select(address: String) {
        mac.value = address
        persist(SettingsKeys.PRINTER_MAC, address, ACTION_SETUP)
    }

    fun setWidth(widthMmValue: Int) {
        widthMm.value = widthMmValue
        persist(SettingsKeys.PRINTER_WIDTH_MM, widthMmValue.toString(), ACTION_SETUP)
    }

    fun setDrawerKick(enabled: Boolean) {
        drawerKick.value = enabled
        persist(SettingsKeys.DRAWER_KICK, enabled.toString(), ACTION_SETUP)
    }

    fun testPrint() {
        viewModelScope.launch {
            if (!session.requirePermission(Permission.MANAGE_SETTINGS, ACTION_TEST)) {
                message.value = MESSAGE_DENIED
                return@launch
            }
            printController.testPrint()
        }
    }

    fun dismissPrintError() = printController.dismiss()

    fun consumeMessage() {
        message.value = null
    }

    private fun persist(key: String, value: String, action: String) {
        viewModelScope.launch {
            if (!session.requirePermission(Permission.MANAGE_SETTINGS, action)) {
                message.value = MESSAGE_DENIED
                return@launch
            }
            settings.put(key, value, session.activeUserId.value)
        }
    }

    companion object {
        const val ACTION_SETUP = "printer.setup"
        const val ACTION_TEST = "printer.test"
        const val MESSAGE_DENIED = "__denied__"
    }
}
