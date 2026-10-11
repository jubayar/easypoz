package net.redngreen.easypos.ui.reports

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import net.redngreen.easypos.R
import net.redngreen.easypos.core.money.MoneyFormatter
import net.redngreen.easypos.print.PrintState
import net.redngreen.easypos.designsystem.ExtendedTheme
import net.redngreen.easypos.feature.login.ui.LoginViewModel
import net.redngreen.easypos.feature.login.ui.model.LoginAction
import org.koin.androidx.compose.koinViewModel

private val DAY_LABEL = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

/**
 * Day summary / Z-report (tasks 9.3 + 9.5, Stitch "EasyPOS Day Summary"):
 * date-navigable KPI cards, cash drawer movement with physical-count
 * reconciliation, payment tenders, top sellers, print + PDF share, and
 * close-register (print Z + lock).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySummaryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DaySummaryViewModel = hiltViewModel(),
    loginViewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shopName by viewModel.shopName.collectAsStateWithLifecycle()
    val printState by viewModel.printState.collectAsStateWithLifecycle()
    val shareFile by viewModel.shareFile.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    var countedText by remember(state.epochDay) { mutableStateOf("") }

    // PDF ready → hand it to the system share sheet.
    LaunchedEffect(shareFile) {
        shareFile?.let { file ->
            context.startActivity(
                Intent.createChooser(viewModel.shareIntentFor(file), null)
            )
            viewModel.consumeShareFile()
        }
    }

    // "vs yesterday" caption — only meaningful with a non-zero baseline.
    val yesterday = state.yesterdayTotalCents
    val trendCaption =
        if (state.summary != null && yesterday > 0) {
            val pct = java.lang.Math.abs(
                (state.summary!!.salesTotalCents - yesterday) * 100.0 / yesterday
            )
            stringResource(
                if (state.summary!!.salesTotalCents >= yesterday) R.string.day_vs_yesterday_up
                else R.string.day_vs_yesterday_down,
                "%.1f".format(java.util.Locale.ROOT, pct),
            )
        } else {
            null
        }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        // ── Header + date nav + actions ───────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.day_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.day_subtitle, shopName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = viewModel::shareReport) {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.day_share_pdf))
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = viewModel::printReport) {
                Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.day_print))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = viewModel::prevDay) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.day_prev))
            }
            OutlinedButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = (if (state.isToday) stringResource(R.string.history_today) + " · " else "") +
                        DAY_LABEL.format(LocalDate.ofEpochDay(state.epochDay)),
                )
            }
            IconButton(onClick = viewModel::nextDay, enabled = !state.isToday) {
                Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.day_next))
            }
        }

        if (state.message != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = state.message ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::consumeMessage) { Text(stringResource(R.string.action_close)) }
            }
        }

        if (state.loading) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.summary != null) {
            val summary = state.summary!!

            // ── KPI cards ────────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                KpiCard(
                    title = stringResource(R.string.day_gross_sales),
                    value = MoneyFormatter.format(summary.salesTotalCents),
                    caption = stringResource(R.string.day_transactions, summary.salesCount),
                    trendCaption = trendCaption,
                    modifier = Modifier.weight(1f),
                )
                KpiCard(
                    title = stringResource(R.string.day_avg_basket),
                    value = MoneyFormatter.format(summary.averageBasketCents),
                    caption = stringResource(R.string.day_avg_basket_caption),
                    modifier = Modifier.weight(1f),
                )
                KpiCard(
                    title = stringResource(R.string.day_expected_drawer),
                    value = MoneyFormatter.format(summary.expectedDrawerCents),
                    caption = null,
                    reconciled = state.latestDrawerCount?.varianceCents,
                    modifier = Modifier.weight(1f),
                )
            }

            // ── Cash drawer movement + reconciliation (task 9.5) ─────────────
            SectionCard(title = stringResource(R.string.day_cash_movement), subtitle = stringResource(R.string.day_cash_movement_hint)) {
                MovementRow(stringResource(R.string.day_cash_sales), MoneyFormatter.format(summary.cashSalesCents), positive = true)
                MovementRow(stringResource(R.string.day_cash_in), MoneyFormatter.format(summary.cashInCents), positive = true)
                MovementRow(stringResource(R.string.day_cash_out), MoneyFormatter.format(summary.cashOutCents), positive = false)
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row {
                    Text(
                        text = stringResource(R.string.day_expected_drawer),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = MoneyFormatter.format(summary.expectedDrawerCents),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = countedText,
                        onValueChange = { countedText = it },
                        label = { Text(stringResource(R.string.day_physical_count)) },
                        prefix = { Text("RM ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { viewModel.saveDrawerCount(countedText) },
                        enabled = !state.saving && countedText.isNotBlank(),
                    ) { Text(stringResource(R.string.day_save_count)) }
                }
                state.latestDrawerCount?.let { count ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            if (count.varianceCents == 0L) R.string.day_count_match else R.string.day_count_variance,
                            MoneyFormatter.format(count.varianceCents),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (count.varianceCents == 0L) ExtendedTheme.onSuccessContainer else MaterialTheme.colorScheme.error,
                    )
                }
            }

            // ── Payment tenders ──────────────────────────────────────────────
            SectionCard(title = stringResource(R.string.day_tenders), subtitle = stringResource(R.string.day_tenders_caption, summary.salesCount)) {
                if (summary.byPaymentType.isEmpty()) {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    summary.byPaymentType.forEach { tender ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(tender.paymentTypeName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = stringResource(R.string.day_tender_orders, tender.saleCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val pct =
                                if (summary.salesTotalCents > 0) "%.1f%%".format(java.util.Locale.ROOT, tender.totalCents * 100.0 / summary.salesTotalCents) else "0%"
                            Text(
                                text = "${MoneyFormatter.format(tender.totalCents)}  ($pct)",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }

            // ── Top sellers ──────────────────────────────────────────────────
            SectionCard(title = stringResource(R.string.day_top_sellers), subtitle = stringResource(R.string.day_top_sellers_caption)) {
                if (summary.topSellers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    summary.topSellers.take(5).forEachIndexed { index, seller ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(20.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(seller.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = stringResource(R.string.day_top_seller_units, seller.qty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = MoneyFormatter.format(seller.totalCents),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }

            // ── Close register (print Z + lock) ──────────────────────────────
            if (state.canCloseRegister) {
                Spacer(Modifier.height(8.dp))
                PrintStatusLine(printState, onRetry = viewModel::retryPrint)
                Button(
                    onClick = {
                        viewModel.printReport()
                        loginViewModel.onAction(LoginAction.LockNow("register closed"))
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.day_close_register), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showDatePicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = state.epochDay * 86_400_000L,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.setEpochDay(millis / 86_400_000L)
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
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    caption: String?,
    modifier: Modifier = Modifier,
    trendCaption: String? = null,
    reconciled: Long? = null,
) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            caption?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trendCaption?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            reconciled?.let { variance ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (variance == 0L) ExtendedTheme.successContainer else ExtendedTheme.warningContainer,
                ) {
                    Text(
                        text = stringResource(if (variance == 0L) R.string.day_reconciled else R.string.day_variance, MoneyFormatter.format(variance)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (variance == 0L) ExtendedTheme.onSuccessContainer else ExtendedTheme.onWarningContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun MovementRow(label: String, value: String, positive: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = (if (positive) "+" else "−") + value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (positive) ExtendedTheme.success else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun PrintStatusLine(printState: PrintState, onRetry: () -> Unit) {
    when (printState) {
        is PrintState.Printing -> Text(
            text = stringResource(R.string.printer_printing),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        is PrintState.Error -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = stringResource(R.string.print_failed_retry),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            if (printState.canRetry) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.printer_retry)) }
            }
        }
        PrintState.Idle -> Unit
    }
}
