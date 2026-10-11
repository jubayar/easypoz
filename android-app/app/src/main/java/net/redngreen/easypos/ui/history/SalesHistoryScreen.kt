package net.redngreen.easypos.ui.history

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import net.redngreen.easypos.R
import net.redngreen.easypos.core.money.MoneyFormatter

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy")

/** Sales history (task 7.8): day picker, newest-first list, detail + reprint entry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SalesHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shareFile by viewModel.shareFile.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()

    LaunchedEffect(shareFile) {
        val file = shareFile ?: return@LaunchedEffect
        context.startActivity(Intent.createChooser(viewModel.buildShareIntent(file), null))
        viewModel.clearShare()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            val today = LocalDate.now(zone).toEpochDay()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.selectedEpochDay == today,
                    onClick = { viewModel.selectDay(today) },
                    label = { Text(stringResource(R.string.history_today)) },
                )
                FilterChip(
                    selected = state.selectedEpochDay == today - 1,
                    onClick = { viewModel.selectDay(today - 1) },
                    label = { Text(stringResource(R.string.history_yesterday)) },
                )
                FilterChip(
                    selected = state.selectedEpochDay != today && state.selectedEpochDay != today - 1,
                    onClick = { showDatePicker = true },
                    label = {
                        Text(
                            if (state.selectedEpochDay != today && state.selectedEpochDay != today - 1) {
                                DATE_FORMAT.format(LocalDate.ofEpochDay(state.selectedEpochDay))
                            } else {
                                stringResource(R.string.history_pick_date)
                            }
                        )
                    },
                )
            }
            Spacer(Modifier.padding(4.dp))

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (state.rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.history_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(state.rows.size) { index ->
                        val row = state.rows[index]
                        Card(onClick = { viewModel.openDetail(row.sale.id) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "#${row.sale.localNo} · " + TIME_FORMAT.format(
                                            Instant.ofEpochMilli(row.sale.at).atZone(zone)
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.history_row_summary,
                                            row.itemCount,
                                            row.paymentTypeName ?: "—",
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    MoneyFormatter.format(row.sale.totalCents),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.selectedEpochDay * 86_400_000L,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.selectDay(millis / 86_400_000L)
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.user_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.lock_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    state.detail?.let { detail ->
        AlertDialog(
            onDismissRequest = viewModel::closeDetail,
            title = {
                Text(
                    stringResource(R.string.history_detail_title, detail.sale.localNo) + " · " +
                        TIME_FORMAT.format(Instant.ofEpochMilli(detail.sale.at).atZone(zone))
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    detail.lines.forEach { line ->
                        Row {
                            Text(
                                text = line.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "${line.qty} × ${MoneyFormatter.formatPlain(line.unitCents)} = " +
                                    MoneyFormatter.formatPlain(line.lineCents),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row {
                        Text(stringResource(R.string.cart_total), modifier = Modifier.weight(1f))
                        Text(MoneyFormatter.format(detail.sale.totalCents))
                    }
                    detail.sale.tenderedCents?.let {
                        Row {
                            Text(stringResource(R.string.payment_cash_tendered), modifier = Modifier.weight(1f))
                            Text(MoneyFormatter.format(it))
                        }
                    }
                    detail.sale.changeCents?.let {
                        Row {
                            Text(stringResource(R.string.payment_change), modifier = Modifier.weight(1f))
                            Text(MoneyFormatter.format(it))
                        }
                    }
                    Row {
                        Text(stringResource(R.string.history_payment_type), modifier = Modifier.weight(1f))
                        Text(detail.paymentTypeName ?: "—")
                    }
                    Row {
                        Text(stringResource(R.string.history_cashier), modifier = Modifier.weight(1f))
                        Text(detail.cashierName ?: "—")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::reprint) { Text(stringResource(R.string.history_reprint)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::shareReceipt) { Text(stringResource(R.string.history_share)) }
            },
        )
    }
}
