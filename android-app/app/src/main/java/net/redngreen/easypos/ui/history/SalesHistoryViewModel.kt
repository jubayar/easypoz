package net.redngreen.easypos.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.redngreen.easypos.core.database.LocalIdentity
import net.redngreen.easypos.core.database.dao.PaymentTypeDao
import net.redngreen.easypos.core.database.dao.ProductDao
import net.redngreen.easypos.core.database.dao.SaleDao
import net.redngreen.easypos.core.database.dao.UserDao
import net.redngreen.easypos.core.database.entity.Sale
import net.redngreen.easypos.feature.sell.domain.ReceiptRepository
import net.redngreen.easypos.core.print.PdfReceiptWriter
import net.redngreen.easypos.print.PrintController

data class HistoryRow(
    val sale: Sale,
    val itemCount: Int,
    val paymentTypeName: String?,
)

data class DetailLine(
    val name: String,
    val qty: Int,
    val unitCents: Long,
    val lineCents: Long,
)

data class SaleDetail(
    val sale: Sale,
    val lines: List<DetailLine>,
    val cashierName: String?,
    val paymentTypeName: String?,
)

data class SalesHistoryUiState(
    val loading: Boolean = true,
    /** Epoch-day of the shown list (java.time.LocalDate.toEpochDay). */
    val selectedEpochDay: Long = LocalDate.now(ZoneId.systemDefault()).toEpochDay(),
    val rows: List<HistoryRow> = emptyList(),
    val detail: SaleDetail? = null,
)

/** Sales history (task 7.8): per-day list, detail view, reprint entry point. */
@HiltViewModel
class SalesHistoryViewModel @Inject constructor(
    private val saleDao: SaleDao,
    private val userDao: UserDao,
    private val paymentTypeDao: PaymentTypeDao,
    private val productDao: ProductDao,
    private val identity: LocalIdentity,
    private val printController: PrintController,
    private val receipts: ReceiptRepository,
    private val pdfReceipts: PdfReceiptWriter,
) : ViewModel() {

    private val selectedEpochDay = MutableStateFlow(LocalDate.now(ZoneId.systemDefault()).toEpochDay())
    private val detail = MutableStateFlow<SaleDetail?>(null)
    private val salesOfDay = MutableStateFlow<List<Sale>>(emptyList())
    private val counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val paymentNames = MutableStateFlow<Map<String, String>>(emptyMap())

    private val _shareFile = MutableStateFlow<File?>(null)

    /** A PDF ready to hand to the system share sheet (task 10.4); consumed by the screen. */
    val shareFile: StateFlow<File?> = _shareFile.asStateFlow()

    val state: StateFlow<SalesHistoryUiState> = combine(
        selectedEpochDay, salesOfDay, counts, paymentNames, detail,
    ) { day, sales, lineCounts, payNames, detailValue ->
        SalesHistoryUiState(
            loading = false,
            selectedEpochDay = day,
            rows = sales.map { sale ->
                HistoryRow(
                    sale = sale,
                    itemCount = lineCounts[sale.id] ?: 0,
                    paymentTypeName = payNames[sale.paymentTypeId],
                )
            },
            detail = detailValue,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SalesHistoryUiState())

    init {
        viewModelScope.launch { refresh() }
    }

    fun selectDay(epochDay: Long) {
        selectedEpochDay.value = epochDay
        detail.value = null
        viewModelScope.launch { refresh() }
    }

    fun openDetail(saleId: String) {
        viewModelScope.launch {
            val sale = saleDao.getById(saleId) ?: return@launch
            val lines = saleDao.linesFor(saleId)
            val productNames = productDao
                .getByIds(lines.mapNotNull { it.productId })
                .associate { it.id to it.name }
            val displayLines = lines.map { line ->
                DetailLine(
                    name = line.customName ?: productNames[line.productId] ?: "",
                    qty = line.qty,
                    unitCents = line.unitCents,
                    lineCents = line.lineCents,
                )
            }
            val cashier = sale.userId?.let { userDao.getById(it)?.name }
            val paymentName = sale.paymentTypeId?.let { paymentTypeDao.getById(it)?.name }
            detail.value = SaleDetail(sale, displayLines, cashier, paymentName)
        }
    }

    fun closeDetail() {
        detail.value = null
    }

    /** Reprint (task 10.1/10.2): marks the receipt as a reprint; async off the UI. */
    fun reprint() {
        val saleId = detail.value?.sale?.id ?: return
        printController.printSale(saleId, isReprint = true)
    }

    /** Share receipt as PDF (task 10.4): builds the file, then the screen fires the chooser. */
    fun shareReceipt() {
        val saleId = detail.value?.sale?.id ?: return
        viewModelScope.launch {
            val receipt = receipts.buildReceipt(saleId, isReprint = false, logo = null) ?: return@launch
            val file = runCatching { pdfReceipts.write(receipt) }.getOrNull() ?: return@launch
            _shareFile.value = file
        }
    }

    fun clearShare() {
        _shareFile.value = null
    }

    /** Chooser intent for a prepared PDF receipt (content URI via FileProvider). */
    fun buildShareIntent(file: File): Intent = pdfReceipts.shareIntent(file)

    private suspend fun refresh() {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.ofEpochDay(selectedEpochDay.value)
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val list = saleDao.observeBetween(identity.shopId(), from, to).first()
        salesOfDay.value = list
        counts.value = if (list.isEmpty()) {
            emptyMap()
        } else {
            saleDao.lineCounts(list.map { it.id }).associate { it.saleId to it.lineCount }
        }
        val typeIds = list.mapNotNull { it.paymentTypeId }.distinct()
        paymentNames.value = typeIds.mapNotNull { id ->
            paymentTypeDao.getById(id)?.let { id to it.name }
        }.toMap()
    }
}
