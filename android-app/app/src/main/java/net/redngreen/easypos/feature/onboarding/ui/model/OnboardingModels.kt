package net.redngreen.easypos.feature.onboarding.ui.model

import net.redngreen.easypos.print.PrintState
import net.redngreen.easypos.core.database.SettingsKeys
import net.redngreen.easypos.feature.onboarding.domain.OnboardingStep
import net.redngreen.easypos.feature.onboarding.domain.PrinterOption
import net.redngreen.easypos.shared.UiText

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.PROFILE,
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val productName: String = "",
    val productPrice: String = "",
    val addedProducts: Int = 0,
    val saving: Boolean = false,
    val message: UiText? = null,
    /** null while loading; true = wizard not applicable or already finished. */
    val done: Boolean? = null,
    val bluetoothAvailable: Boolean = true,
    val printers: List<PrinterOption> = emptyList(),
    val printerMac: String? = null,
    val printerWidthMm: Int = SettingsKeys.PRINTER_WIDTH_DEFAULT_MM,
    val printState: PrintState = PrintState.Idle,
)

sealed interface OnboardingAction {
    data class SetName(val value: String) : OnboardingAction

    data class SetAddress(val value: String) : OnboardingAction

    data class SetPhone(val value: String) : OnboardingAction

    data class SetProductName(val value: String) : OnboardingAction

    data class SetProductPrice(val value: String) : OnboardingAction

    data object SaveProfileNext : OnboardingAction

    data object ToProducts : OnboardingAction

    data class SelectPrinter(val address: String) : OnboardingAction

    data class SetPrinterWidth(val widthMm: Int) : OnboardingAction

    data object TestPrint : OnboardingAction

    data object AddProduct : OnboardingAction

    data object Finish : OnboardingAction
}
