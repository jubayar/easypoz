package net.redngreen.easypos.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.redngreen.easypos.feature.catalog.data.CsvCatalogParser
import net.redngreen.easypos.feature.catalog.domain.CatalogCsvRow
import net.redngreen.easypos.feature.catalog.domain.CatalogRepository
import net.redngreen.easypos.feature.catalog.domain.ImportStatus
import net.redngreen.easypos.feature.catalog.domain.ImportSummary
import net.redngreen.easypos.core.model.Permission
import net.redngreen.easypos.core.session.SessionManager

/**
 * CSV import (task 6.4): pick a file → tolerant parse → per-row apply with a
 * created/updated/failed report; failures can be retried alone. Bad rows
 * never abort the import (product-catalog spec).
 */
@HiltViewModel
class CsvImportViewModel @Inject constructor(
    private val session: SessionManager,
    private val catalog: CatalogRepository,
) : ViewModel() {

    /** Parse-level failures shown before import; DB failures land in summary. */
    data class Parsed(
        val rows: List<CatalogCsvRow>,
        val failures: List<CsvCatalogParser.RowFailure>,
    )

    sealed interface UiState {
        data object Idle : UiState
        data object Parsing : UiState
        data class Ready(val parsed: Parsed) : UiState
        data object Importing : UiState
        data class Done(val summary: ImportSummary) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** The last successfully parsed rows, kept for failed-row retry. */
    private var lastParsedRows: List<CatalogCsvRow> = emptyList()

    private var gated = false
    private var allowed = false

    fun onFilePicked(bytes: ByteArray) {
        _state.value = UiState.Parsing
        viewModelScope.launch {
            if (!gated) {
                gated = true
                allowed = session.requirePermission(Permission.EDIT_CATALOG, ACTION_OPEN)
            }
            if (!allowed) {
                _state.value = UiState.Error(DENIED)
                return@launch
            }
            try {
                val text = CsvCatalogParser.decode(bytes)
                val result = CsvCatalogParser.parse(text)
                lastParsedRows = result.rows
                _state.value = UiState.Ready(Parsed(result.rows, result.failures))
            } catch (e: CsvCatalogParser.FormatException) {
                _state.value = UiState.Error(e.message ?: "unreadable file")
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "unreadable file")
            }
        }
    }

    fun import() {
        val ready = _state.value as? UiState.Ready ?: return
        applyRows(ready.parsed.rows)
    }

    /** Retry only the rows that failed at apply time (row numbers → rows). */
    fun retryFailed() {
        val done = _state.value as? UiState.Done ?: return
        val failedNumbers = done.summary.outcomes
            .filter { it.status == ImportStatus.FAILED }
            .map { it.rowNumber }
            .toSet()
        val retryRows = lastParsedRows.filter { it.rowNumber in failedNumbers }
        if (retryRows.isEmpty()) return
        applyRows(retryRows)
    }

    private fun applyRows(rows: List<CatalogCsvRow>) {
        _state.value = UiState.Importing
        viewModelScope.launch {
            val summary = catalog.importRows(rows, session.activeUserId.value)
            session.logAction("catalog.csv_import", "applied=${rows.size} failed=${summary.failed}")
            _state.value = UiState.Done(summary)
        }
    }

    fun reset() {
        lastParsedRows = emptyList()
        _state.value = UiState.Idle
    }

    companion object {
        const val ACTION_OPEN = "catalog.csv_import.open"
        const val DENIED = "access denied"
    }
}
