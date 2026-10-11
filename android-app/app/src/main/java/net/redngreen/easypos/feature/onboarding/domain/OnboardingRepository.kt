package net.redngreen.easypos.feature.onboarding.domain

import kotlinx.coroutines.flow.Flow

/** First-run wizard steps (task 11.2); persisted so a killed app resumes. */
enum class OnboardingStep { PROFILE, PRINTER, PRODUCTS }

data class ShopProfile(
    val name: String,
    val address: String,
    val phone: String,
)

data class PrinterOption(
    val name: String,
    val address: String,
)

/** Bonded-printer snapshot for the wizard's optional printer step. */
data class PrinterDevicesSnapshot(
    val bluetoothAvailable: Boolean = true,
    val devices: List<PrinterOption> = emptyList(),
    val mac: String? = null,
    val widthMm: Int = 80,
)

interface OnboardingRepository {
    /** null while loading; true = wizard not applicable or already finished. */
    fun observeDone(): Flow<Boolean?>

    suspend fun load(): Pair<OnboardingStep, ShopProfile?>

    fun observePrinterDevices(): Flow<PrinterDevicesSnapshot>

    fun refreshDevices()

    suspend fun saveProfile(profile: ShopProfile)

    suspend fun addStarterProduct(name: String, priceCents: Long)

    suspend fun advanceTo(step: OnboardingStep)

    /** Ends the wizard — skippable at any point once the Admin account exists. */
    suspend fun finish()

    fun selectPrinter(address: String)

    fun setPrinterWidth(widthMm: Int)

    fun testPrint()
}
