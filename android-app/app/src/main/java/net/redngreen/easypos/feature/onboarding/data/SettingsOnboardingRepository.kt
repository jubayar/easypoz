package net.redngreen.easypos.feature.onboarding.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.redngreen.easypos.core.database.SettingsKeys
import net.redngreen.easypos.core.data.repo.CatalogRepository
import net.redngreen.easypos.core.data.repo.SettingsRepository
import net.redngreen.easypos.core.data.repo.TenantConfigRepository
import net.redngreen.easypos.core.model.Permission
import net.redngreen.easypos.core.print.BluetoothPrinter
import net.redngreen.easypos.core.print.PrintController
import net.redngreen.easypos.core.print.PrintState
import net.redngreen.easypos.core.session.SessionManager
import net.redngreen.easypos.feature.onboarding.domain.OnboardingRepository
import net.redngreen.easypos.feature.onboarding.domain.OnboardingStep
import net.redngreen.easypos.feature.onboarding.domain.PrinterOption
import net.redngreen.easypos.feature.onboarding.domain.PrinterSetupSnapshot
import net.redngreen.easypos.feature.onboarding.domain.ShopProfile

/**
 * First-run wizard over the core repositories. Every step persists the resume
 * point, so closing the app mid-wizard resumes where it left off; printer
 * selection goes through the same permissioned settings path as the
 * settings screen.
 */
class SettingsOnboardingRepository(
    private val settings: SettingsRepository,
    private val tenantConfig: TenantConfigRepository,
    private val catalog: CatalogRepository,
    private val session: SessionManager,
    private val printer: BluetoothPrinter,
    private val printController: PrintController,
    private val appScope: CoroutineScope,
) : OnboardingRepository {

    private val devices = MutableStateFlow<List<PrinterOption>>(emptyList())

    override fun observeDone(): Flow<Boolean?> =
        settings.observe(SettingsKeys.ONBOARDING_STEP).map { raw ->
            raw == null || raw == SettingsKeys.ONBOARDING_DONE
        }

    override suspend fun load(): Pair<OnboardingStep, ShopProfile?> {
        val step = when (settings.get(SettingsKeys.ONBOARDING_STEP)) {
            SettingsKeys.ONBOARDING_PRINTER -> OnboardingStep.PRINTER
            SettingsKeys.ONBOARDING_PRODUCTS -> OnboardingStep.PRODUCTS
            else -> OnboardingStep.PROFILE
        }
        val config = tenantConfig.observe().first()
        val profile = config?.let {
            ShopProfile(
                name = it.name.orEmpty(),
                address = it.address.orEmpty(),
                phone = it.phone.orEmpty(),
            )
        }
        return step to profile
    }

    override fun observePrinterState(): Flow<PrinterSetupSnapshot> = combine(
        devices,
        settings.observe(SettingsKeys.PRINTER_MAC),
        settings.observe(SettingsKeys.PRINTER_WIDTH_MM),
        printController.state,
    ) { deviceList, mac, width, printState ->
        PrinterSetupSnapshot(
            bluetoothAvailable = printer.isAvailable,
            devices = deviceList,
            mac = mac,
            widthMm = width?.toIntOrNull() ?: SettingsKeys.PRINTER_WIDTH_DEFAULT_MM,
            printState = printState,
        )
    }

    override fun refreshDevices() {
        devices.value = printer.bondedDevices().map { PrinterOption(it.name ?: it.address, it.address) }
    }

    override suspend fun saveProfile(profile: ShopProfile) {
        tenantConfig.saveProfile(profile.name, profile.address, profile.phone, null, session.activeUserId.value)
    }

    override suspend fun addStarterProduct(name: String, priceCents: Long) {
        catalog.createProduct(
            fields = CatalogRepository.ProductFields(
                name = name.trim(),
                categoryId = null,
                barcode = null,
                sku = null,
                costCents = 0L,
                priceCents = priceCents,
            ),
            initialStock = null,
            actorUserId = session.activeUserId.value,
        )
    }

    override suspend fun advanceTo(step: OnboardingStep) {
        settings.putLocal(SettingsKeys.ONBOARDING_STEP, step.settingsKey)
    }

    override suspend fun finish() {
        settings.putLocal(SettingsKeys.ONBOARDING_STEP, SettingsKeys.ONBOARDING_DONE)
    }

    override fun selectPrinter(address: String) {
        appScope.launch {
            if (!session.requirePermission(Permission.MANAGE_SETTINGS, ACTION_SETUP)) return@launch
            settings.put(SettingsKeys.PRINTER_MAC, address, session.activeUserId.value)
        }
    }

    override fun setPrinterWidth(widthMm: Int) {
        appScope.launch {
            if (!session.requirePermission(Permission.MANAGE_SETTINGS, ACTION_SETUP)) return@launch
            settings.put(SettingsKeys.PRINTER_WIDTH_MM, widthMm.toString(), session.activeUserId.value)
        }
    }

    override fun testPrint() {
        appScope.launch {
            if (!session.requirePermission(Permission.MANAGE_SETTINGS, ACTION_TEST)) return@launch
            printController.testPrint()
        }
    }

    companion object {
        const val ACTION_SETUP = "printer.setup"
        const val ACTION_TEST = "printer.test"
    }
}

private val OnboardingStep.settingsKey: String
    get() = when (this) {
        OnboardingStep.PROFILE -> SettingsKeys.ONBOARDING_PROFILE
        OnboardingStep.PRINTER -> SettingsKeys.ONBOARDING_PRINTER
        OnboardingStep.PRODUCTS -> SettingsKeys.ONBOARDING_PRODUCTS
    }
