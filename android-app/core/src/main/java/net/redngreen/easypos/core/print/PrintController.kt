package net.redngreen.easypos.core.print

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.redngreen.easypos.core.data.repo.ReceiptRepository
import net.redngreen.easypos.core.data.repo.ReportsRepository
import net.redngreen.easypos.core.data.repo.TenantConfigRepository
import net.redngreen.easypos.core.print.DaySummaryEscPos
import net.redngreen.easypos.core.print.DaySummaryPrint
import net.redngreen.easypos.core.print.Receipt
import net.redngreen.easypos.core.print.ReceiptEscPos
import net.redngreen.easypos.core.print.ReceiptLine

/** Print lifecycle for the retry chip (receipts spec: failure never blocks the sale). */
sealed interface PrintState {
    data object Idle : PrintState
    data object Printing : PrintState
    data class Error(val message: String, val canRetry: Boolean = true) : PrintState
}

/**
 * Dispatches receipt printing off the sale path (tasks 10.2/10.3): the sale is
 * already committed when this runs, so any failure here only surfaces a
 * retry affordance. Drawer kick travels inside the receipt bytes for cash
 * sales, honoring the configurable toggle. Also owns the Z-report print
 * (task 9.3) using the same retry lifecycle.
 */
@Singleton
class PrintController @Inject constructor(
    private val printer: BluetoothPrinter,
    private val receipts: ReceiptRepository,
    private val logoRasterizer: LogoRasterizer,
    private val reports: ReportsRepository,
    private val tenant: TenantConfigRepository,
    private val appScope: CoroutineScope,
) {
    private val _state = MutableStateFlow<PrintState>(PrintState.Idle)
    val state: StateFlow<PrintState> = _state.asStateFlow()

    private sealed interface PrintJob {
        data class Sale(val saleId: String, val isReprint: Boolean) : PrintJob
        data class DaySummary(val epochDay: Long) : PrintJob
    }

    private var lastJob: PrintJob? = null

    fun printSale(saleId: String, isReprint: Boolean = false) {
        lastJob = PrintJob.Sale(saleId, isReprint)
        appScope.launch {
            val config = receipts.printConfig()
            val mac = config.macAddress
            if (mac.isNullOrBlank()) {
                // Paperless shop: no printer configured, nothing to do (spec).
                _state.value = PrintState.Idle
                return@launch
            }
            _state.value = PrintState.Printing
            try {
                val logo = logoRasterizer.loadShopLogo()
                val receipt = receipts.buildReceipt(saleId, isReprint, logo)
                if (receipt == null) {
                    _state.value = PrintState.Idle
                    return@launch
                }
                val bytes = ReceiptEscPos.render(receipt, config.columns, config.drawerKick)
                printer.print(mac, bytes)
                _state.value = PrintState.Idle
            } catch (e: Exception) {
                _state.value = PrintState.Error(e.message ?: "Print failed")
            }
        }
    }

    /** Z-report thermal print (task 9.3) — no drawer kick on a report. */
    fun printDaySummary(epochDay: Long) {
        lastJob = PrintJob.DaySummary(epochDay)
        appScope.launch {
            val config = receipts.printConfig()
            val mac = config.macAddress
            if (mac.isNullOrBlank()) {
                _state.value = PrintState.Error("No printer configured", canRetry = false)
                return@launch
            }
            _state.value = PrintState.Printing
            try {
                val summary = reports.daySummary(epochDay)
                val printable = summary.toPrintable(shopName(), epochDay)
                printer.print(mac, DaySummaryEscPos.render(printable, config.columns))
                _state.value = PrintState.Idle
            } catch (e: Exception) {
                _state.value = PrintState.Error(e.message ?: "Print failed")
            }
        }
    }

    fun retry() {
        when (val job = lastJob) {
            is PrintJob.Sale -> printSale(job.saleId, job.isReprint)
            is PrintJob.DaySummary -> printDaySummary(job.epochDay)
            null -> Unit
        }
    }

    private suspend fun shopName(): String =
        tenant.observe().first()?.name?.takeIf { it.isNotBlank() } ?: "EasyPOS"

    private fun ReportsRepository.DaySummary.toPrintable(shopName: String, epochDay: Long) =
        DaySummaryPrint(
            shopName = shopName,
            dateLabel = DATE_LABEL.format(LocalDate.ofEpochDay(epochDay)),
            salesCount = salesCount,
            salesTotalCents = salesTotalCents,
            byPaymentType = byPaymentType.map { it.paymentTypeName to it.totalCents },
            cashInCents = cashInCents,
            cashOutCents = cashOutCents,
            netCashCents = netCashCents,
            averageBasketCents = averageBasketCents,
            topSellers = topSellers.map { it.name to it.qty },
            expectedDrawerCents = expectedDrawerCents,
        )

    private companion object {
        val DATE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
    }

    fun dismiss() {
        _state.value = PrintState.Idle
    }

    fun testPrint() {
        appScope.launch {
            val config = receipts.printConfig()
            val mac = config.macAddress
            if (mac.isNullOrBlank()) {
                _state.value = PrintState.Error("No printer configured", canRetry = false)
                return@launch
            }
            _state.value = PrintState.Printing
            try {
                printer.print(mac, ReceiptEscPos.render(testReceipt(config.columns), config.columns, kickDrawer = false))
                _state.value = PrintState.Idle
            } catch (e: Exception) {
                _state.value = PrintState.Error(e.message ?: "Test print failed")
            }
        }
    }

    private fun testReceipt(columns: Int) = Receipt(
        shopName = "EasyPOS",
        saleNo = 0L,
        timestamp = System.currentTimeMillis(),
        lines = listOf(ReceiptLine("Test print", 1, 0L, 0L)),
        subtotalCents = 0L,
        totalCents = 0L,
        footer = "Printer OK · $columns cols",
    )
}
