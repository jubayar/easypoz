package net.redngreen.easypos.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.redngreen.easypos.core.database.SettingsKeys
import net.redngreen.easypos.core.database.entity.PaymentType
import net.redngreen.easypos.core.data.repo.PaymentTypeRepository
import net.redngreen.easypos.core.data.repo.SettingsRepository
import net.redngreen.easypos.core.data.repo.TenantConfigRepository
import net.redngreen.easypos.core.model.Permission
import net.redngreen.easypos.core.session.SessionManager
import net.redngreen.easypos.i18n.LanguageController

data class SettingsUiState(
    val loading: Boolean = true,
    val denied: Boolean = false,
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val logoBase64: String? = null,
    val header: String = "",
    val footer: String = "",
    val idleMinutes: String = SettingsKeys.DEFAULT_IDLE_LOCK_MINUTES.toString(),
    val drawerKick: Boolean = true,
    val qrBase64: String? = null,
    val language: String = "en",
    val paymentTypes: List<PaymentType> = emptyList(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val message: String? = null,
)

/**
 * Shop settings hub (task 11.1 + 11.5): profile, receipt branding, idle-lock
 * and drawer-kick toggles, language, and the merchant QR image. Admin-gated
 * (MANAGE_SETTINGS); profile/branding/toggles are journaled, images are
 * local-only (too large for the synced scalar settings row).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val session: SessionManager,
    private val settings: SettingsRepository,
    private val tenantConfig: TenantConfigRepository,
    private val paymentTypes: PaymentTypeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private var started = false

    init {
        viewModelScope.launch {
            paymentTypes.observeAll().collect { list ->
                _state.update { it.copy(paymentTypes = list) }
            }
        }
    }

    fun load() {
        if (started) return
        started = true
        viewModelScope.launch {
            if (!session.requirePermission(Permission.MANAGE_SETTINGS, ACTION_OPEN)) {
                _state.update { it.copy(loading = false, denied = true) }
                return@launch
            }
            val config = tenantConfig.observe().first()
            _state.update {
                it.copy(
                    loading = false,
                    name = config?.name.orEmpty(),
                    address = config?.address.orEmpty(),
                    phone = config?.phone.orEmpty(),
                    logoBase64 = config?.logo,
                    language = config?.language ?: "en",
                    header = settings.get(SettingsKeys.RECEIPT_HEADER).orEmpty(),
                    footer = settings.get(SettingsKeys.RECEIPT_FOOTER).orEmpty(),
                    idleMinutes = (settings.get(SettingsKeys.IDLE_LOCK_MINUTES)
                        ?: SettingsKeys.DEFAULT_IDLE_LOCK_MINUTES.toString()),
                    drawerKick = settings.get(SettingsKeys.DRAWER_KICK)?.toBooleanStrictOrNull() ?: true,
                    qrBase64 = settings.get(SettingsKeys.DUITNOW_QR_IMAGE),
                )
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v, saved = false) }
    fun setAddress(v: String) = _state.update { it.copy(address = v, saved = false) }
    fun setPhone(v: String) = _state.update { it.copy(phone = v, saved = false) }
    fun setHeader(v: String) = _state.update { it.copy(header = v, saved = false) }
    fun setFooter(v: String) = _state.update { it.copy(footer = v, saved = false) }
    fun setIdleMinutes(v: String) = _state.update { it.copy(idleMinutes = v.filter(Char::isDigit), saved = false) }
    fun setDrawerKick(v: Boolean) = _state.update { it.copy(drawerKick = v, saved = false) }

    fun setLogo(base64: String?) = _state.update { it.copy(logoBase64 = base64, saved = false) }
    fun setQr(base64: String?) = _state.update { it.copy(qrBase64 = base64, saved = false) }

    fun consumeMessage() = _state.update { it.copy(message = null, saved = false) }

    fun saveProfile() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(message = MESSAGE_NAME_REQUIRED) }
            return
        }
        runGated(ACTION_PROFILE) {
            tenantConfig.saveProfile(s.name, s.address, s.phone, s.logoBase64, session.activeUserId.value)
            session.logAction(ACTION_PROFILE, "name=${s.name.trim()}")
        }
    }

    fun saveBranding() {
        val s = _state.value
        runGated(ACTION_BRANDING) {
            settings.put(SettingsKeys.RECEIPT_HEADER, s.header.trim(), session.activeUserId.value)
            settings.put(SettingsKeys.RECEIPT_FOOTER, s.footer.trim(), session.activeUserId.value)
        }
    }

    fun saveToggles() {
        val s = _state.value
        runGated(ACTION_TOGGLES) {
            val minutes = s.idleMinutes.toIntOrNull()?.coerceAtLeast(0) ?: SettingsKeys.DEFAULT_IDLE_LOCK_MINUTES
            settings.put(SettingsKeys.IDLE_LOCK_MINUTES, minutes.toString(), session.activeUserId.value)
            settings.put(SettingsKeys.DRAWER_KICK, s.drawerKick.toString(), session.activeUserId.value)
        }
    }

    fun saveQr() {
        val s = _state.value
        runGated(ACTION_QR) {
            settings.putLocal(SettingsKeys.DUITNOW_QR_IMAGE, s.qrBase64)
        }
    }

    fun setLanguage(tag: String) {
        runGated(ACTION_LANGUAGE) {
            tenantConfig.setLanguage(tag, session.activeUserId.value)
            _state.update { it.copy(language = tag) }
            LanguageController.apply(tag)
        }
    }

    // ─── Payment-type manager (task 11.1) ────────────────────────────────────

    fun addPaymentType(name: String, kind: String) {
        runGated(ACTION_PAYMENT_TYPE) {
            paymentTypes.add(name, kind, session.activeUserId.value)
        }
    }

    fun renamePaymentType(id: String, name: String) {
        runGated(ACTION_PAYMENT_TYPE) {
            paymentTypes.rename(id, name, session.activeUserId.value)
        }
    }

    fun setPaymentTypeActive(id: String, active: Boolean) {
        runGated(ACTION_PAYMENT_TYPE) {
            paymentTypes.setActive(id, active, session.activeUserId.value)
        }
    }

    fun movePaymentType(id: String, up: Boolean) {
        val list = _state.value.paymentTypes
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = if (up) index - 1 else index + 1
        if (target < 0 || target >= list.size) return
        val reordered = list.toMutableList().apply { add(target, removeAt(index)) }
        runGated(ACTION_PAYMENT_TYPE) {
            paymentTypes.reorder(reordered.map { it.id }, session.activeUserId.value)
        }
    }

    private fun runGated(action: String, block: suspend () -> Unit) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            try {
                if (!session.requirePermission(Permission.MANAGE_SETTINGS, action)) {
                    _state.update { it.copy(saving = false, message = MESSAGE_DENIED) }
                    return@launch
                }
                block()
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, message = e.message ?: "save failed") }
            }
        }
    }

    companion object {
        const val ACTION_OPEN = "settings.open"
        const val ACTION_PROFILE = "settings.profile"
        const val ACTION_BRANDING = "settings.branding"
        const val ACTION_TOGGLES = "settings.toggles"
        const val ACTION_QR = "settings.merchant_qr"
        const val ACTION_LANGUAGE = "settings.language"
        const val ACTION_PAYMENT_TYPE = "settings.payment_type"

        const val MESSAGE_DENIED = "__denied__"
        const val MESSAGE_NAME_REQUIRED = "__name_required__"
    }
}
