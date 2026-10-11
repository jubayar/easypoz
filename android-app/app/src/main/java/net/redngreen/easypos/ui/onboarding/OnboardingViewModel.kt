package net.redngreen.easypos.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.redngreen.easypos.core.database.SettingsKeys
import net.redngreen.easypos.core.data.repo.CatalogRepository
import net.redngreen.easypos.core.data.repo.SettingsRepository
import net.redngreen.easypos.core.data.repo.TenantConfigRepository
import net.redngreen.easypos.core.money.MoneyFormatter
import net.redngreen.easypos.core.session.SessionManager

enum class OnboardingStep { PROFILE, PRINTER, PRODUCTS }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.PROFILE,
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val productName: String = "",
    val productPrice: String = "",
    val addedProducts: Int = 0,
    val saving: Boolean = false,
    val message: String? = null,
)

/**
 * First-run wizard (task 11.2): shop profile → printer (optional) → products
 * (optional) → sell screen. The Admin account is created by the lock screen's
 * first-run form, which seeds the resume point; every step persists it, so
 * closing the app mid-wizard resumes where it left off (no data loss).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val tenantConfig: TenantConfigRepository,
    private val catalog: CatalogRepository,
    private val session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** null while loading; true = wizard not applicable or already finished. */
    val done: StateFlow<Boolean?> = settings.observe(SettingsKeys.ONBOARDING_STEP)
        .map { raw -> raw == null || raw == SettingsKeys.ONBOARDING_DONE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val raw = settings.get(SettingsKeys.ONBOARDING_STEP)
            val step = when (raw) {
                SettingsKeys.ONBOARDING_PRINTER -> OnboardingStep.PRINTER
                SettingsKeys.ONBOARDING_PRODUCTS -> OnboardingStep.PRODUCTS
                else -> OnboardingStep.PROFILE
            }
            val config = tenantConfig.observe().first()
            _state.update {
                it.copy(
                    step = step,
                    name = config?.name.orEmpty(),
                    address = config?.address.orEmpty(),
                    phone = config?.phone.orEmpty(),
                )
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v, message = null) }
    fun setAddress(v: String) = _state.update { it.copy(address = v, message = null) }
    fun setPhone(v: String) = _state.update { it.copy(phone = v, message = null) }
    fun setProductName(v: String) = _state.update { it.copy(productName = v, message = null) }
    fun setProductPrice(v: String) = _state.update { it.copy(productPrice = v, message = null) }

    fun saveProfileNext() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(message = MESSAGE_NAME) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            tenantConfig.saveProfile(s.name, s.address, s.phone, null, session.activeUserId.value)
            advance(OnboardingStep.PRINTER, SettingsKeys.ONBOARDING_PRINTER)
        }
    }

    fun toProducts() = advance(OnboardingStep.PRODUCTS, SettingsKeys.ONBOARDING_PRODUCTS)

    fun addProduct() {
        val s = _state.value
        val cents = runCatching { MoneyFormatter.parseCents(s.productPrice) }.getOrNull()
        if (s.productName.isBlank() || cents == null || cents < 0) {
            _state.update { it.copy(message = MESSAGE_PRODUCT) }
            return
        }
        viewModelScope.launch {
            catalog.createProduct(
                fields = CatalogRepository.ProductFields(
                    name = s.productName.trim(),
                    categoryId = null,
                    barcode = null,
                    sku = null,
                    costCents = 0L,
                    priceCents = cents,
                ),
                initialStock = null,
                actorUserId = session.activeUserId.value,
            )
            _state.update {
                it.copy(productName = "", productPrice = "", addedProducts = it.addedProducts + 1, message = null)
            }
        }
    }

    /** Ends the wizard — skippable at any point once the Admin account exists. */
    fun finish() {
        viewModelScope.launch {
            settings.putLocal(SettingsKeys.ONBOARDING_STEP, SettingsKeys.ONBOARDING_DONE)
        }
    }

    private fun advance(step: OnboardingStep, key: String) {
        viewModelScope.launch {
            settings.putLocal(SettingsKeys.ONBOARDING_STEP, key)
            _state.update { it.copy(step = step, saving = false, message = null) }
        }
    }

    companion object {
        const val MESSAGE_NAME = "__name__"
        const val MESSAGE_PRODUCT = "__product__"
    }
}
