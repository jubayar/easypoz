package net.redngreen.easypos.feature.onboarding.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.redngreen.easypos.R
import net.redngreen.easypos.core.money.MoneyFormatter
import net.redngreen.easypos.feature.onboarding.domain.OnboardingRepository
import net.redngreen.easypos.feature.onboarding.domain.OnboardingStep
import net.redngreen.easypos.feature.onboarding.domain.ShopProfile
import net.redngreen.easypos.feature.onboarding.ui.model.OnboardingAction
import net.redngreen.easypos.feature.onboarding.ui.model.OnboardingState
import net.redngreen.easypos.print.PrintController
import net.redngreen.easypos.shared.UiText

/**
 * First-run wizard (task 11.2): shop profile → printer (optional) → products
 * (optional) → sell screen. Form fields survive process death through
 * [SavedStateHandle]; the step itself persists via settings, so a killed app
 * resumes where it left off (no data loss). All feedback is state-driven —
 * the wizard has no one-time side effects.
 */
class OnboardingViewModel(
    private val onboardingRepo: OnboardingRepository,
    private val printController: PrintController,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        OnboardingState(
            name = savedStateHandle[KEY_NAME] ?: "",
            address = savedStateHandle[KEY_ADDRESS] ?: "",
            phone = savedStateHandle[KEY_PHONE] ?: "",
            productName = savedStateHandle[KEY_PRODUCT_NAME] ?: "",
            productPrice = savedStateHandle[KEY_PRODUCT_PRICE] ?: "",
        ),
    )
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            onboardingRepo.observeDone().collect { done ->
                _state.update { it.copy(done = done) }
            }
        }
        viewModelScope.launch {
            onboardingRepo.observePrinterDevices().collect { printer ->
                _state.update {
                    it.copy(
                        bluetoothAvailable = printer.bluetoothAvailable,
                        printers = printer.devices,
                        printerMac = printer.mac,
                        printerWidthMm = printer.widthMm,
                    )
                }
            }
        }
        viewModelScope.launch {
            printController.state.collect { printState ->
                _state.update { it.copy(printState = printState) }
            }
        }
        // Persisted step/profile fill anything the process-death restore left blank.
        viewModelScope.launch {
            val (step, profile) = onboardingRepo.load()
            _state.update { current ->
                current.copy(
                    step = step,
                    name = current.name.ifEmpty { profile?.name.orEmpty() },
                    address = current.address.ifEmpty { profile?.address.orEmpty() },
                    phone = current.phone.ifEmpty { profile?.phone.orEmpty() },
                )
            }
            onboardingRepo.refreshDevices()
        }
    }

    fun onAction(action: OnboardingAction) {
        when (action) {
            is OnboardingAction.SetName -> {
                savedStateHandle[KEY_NAME] = action.value
                _state.update { it.copy(name = action.value, message = null) }
            }

            is OnboardingAction.SetAddress -> {
                savedStateHandle[KEY_ADDRESS] = action.value
                _state.update { it.copy(address = action.value, message = null) }
            }

            is OnboardingAction.SetPhone -> {
                savedStateHandle[KEY_PHONE] = action.value
                _state.update { it.copy(phone = action.value, message = null) }
            }

            is OnboardingAction.SetProductName -> {
                savedStateHandle[KEY_PRODUCT_NAME] = action.value
                _state.update { it.copy(productName = action.value, message = null) }
            }

            is OnboardingAction.SetProductPrice -> {
                savedStateHandle[KEY_PRODUCT_PRICE] = action.value
                _state.update { it.copy(productPrice = action.value, message = null) }
            }

            OnboardingAction.SaveProfileNext -> saveProfileNext()

            OnboardingAction.ToProducts -> advance(OnboardingStep.PRODUCTS)

            is OnboardingAction.SelectPrinter -> onboardingRepo.selectPrinter(action.address)

            is OnboardingAction.SetPrinterWidth -> onboardingRepo.setPrinterWidth(action.widthMm)

            OnboardingAction.TestPrint -> onboardingRepo.testPrint()

            OnboardingAction.AddProduct -> addProduct()

            OnboardingAction.Finish -> viewModelScope.launch { onboardingRepo.finish() }
        }
    }

    private fun saveProfileNext() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(message = UiText.StringResource(R.string.onboarding_error_name)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            onboardingRepo.saveProfile(ShopProfile(s.name, s.address, s.phone))
            advance(OnboardingStep.PRINTER)
        }
    }

    private fun addProduct() {
        val s = _state.value
        val cents = runCatching { MoneyFormatter.parseCents(s.productPrice) }.getOrNull()
        if (s.productName.isBlank() || cents == null || cents < 0) {
            _state.update { it.copy(message = UiText.StringResource(R.string.onboarding_error_product)) }
            return
        }
        viewModelScope.launch {
            onboardingRepo.addStarterProduct(s.productName, cents)
            _state.update {
                it.copy(productName = "", productPrice = "", addedProducts = it.addedProducts + 1, message = null)
            }
        }
    }

    private fun advance(step: OnboardingStep) {
        viewModelScope.launch {
            onboardingRepo.advanceTo(step)
            _state.update { it.copy(step = step, saving = false, message = null) }
        }
    }

    companion object {
        private const val KEY_NAME = "onboarding_name"
        private const val KEY_ADDRESS = "onboarding_address"
        private const val KEY_PHONE = "onboarding_phone"
        private const val KEY_PRODUCT_NAME = "onboarding_product_name"
        private const val KEY_PRODUCT_PRICE = "onboarding_product_price"
    }
}
