package net.redngreen.easypos.ui.stock

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import net.redngreen.easypos.R
import net.redngreen.easypos.core.money.MoneyFormatter
import net.redngreen.easypos.theme.ExtendedTheme

private val HISTORY_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm")

/**
 * Stock management (tasks 8.1–8.4, Stitch "EasyPOS Stock Inventory"): movement
 * header + category segment cards + status filters over a flat table with
 * cost/price/value columns, valuation footer with CSV export, and the
 * per-product detail sheet (adjust / threshold / history).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    onBack: () -> Unit,
    onEditProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Export (task 6.5 mechanism): CreateDocument gives a fresh URI.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val csv = viewModel.exportCsv()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        HeaderBand(
            movementIn = state.movementIn,
            movementOut = state.movementOut,
            onBack = onBack,
        )

        if (state.denied) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.access_denied), color = MaterialTheme.colorScheme.error)
            }
            return@Column
        }

        CategorySegments(state = state, onSelect = viewModel::setCategory)
        FilterRow(state = state, onSelect = viewModel::setFilter)

        Text(
            text = stringResource(R.string.stock_showing_of, state.rows.size, state.totalCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )

        Box(Modifier.weight(1f)) {
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (state.rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.stock_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.rows.size, key = { state.rows[it].product.id }) { index ->
                        StockTableRow(
                            row = state.rows[index],
                            canEdit = state.canEdit,
                            onClick = { viewModel.selectProduct(state.rows[index].product.id) },
                            onEdit = { onEditProduct(state.rows[index].product.id) },
                        )
                    }
                }
            }
        }

        ValuationFooter(state = state, onExport = { exportLauncher.launch("easypos-stock.csv") })
    }

    state.detail?.let { detail ->
        StockDetailSheet(
            detail = detail,
            saving = state.saving,
            canAdjust = state.canAdjust,
            canEdit = state.canEdit,
            message = state.message,
            onAdjust = viewModel::applyAdjustment,
            onThreshold = viewModel::saveThreshold,
            onConsumeMessage = viewModel::consumeMessage,
            onDismiss = { viewModel.selectProduct(null) },
        )
    }
}

@Composable
private fun HeaderBand(movementIn: Long, movementOut: Long, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
        }
        Column(Modifier.weight(1f)) {
            Text(text = stringResource(R.string.stock_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.stock_title_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Today's gross movement (design: Pergerakan Hari Ini).
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.SwapVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Column {
                    Text(
                        text = stringResource(R.string.stock_movement_today),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.stock_movement_value,
                            movementIn,
                            -movementOut,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

/** Horizontal category cards with counts (design: Kategori Produk segments). */
@Composable
private fun CategorySegments(state: StockUiState, onSelect: (String?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SegmentCard(
                name = stringResource(R.string.stock_segment_all),
                count = state.totalCount,
                selected = state.selectedCategoryId == null,
                onClick = { onSelect(null) },
            )
        }
        items(state.categories.size) { index ->
            val category = state.categories[index]
            SegmentCard(
                name = category.name,
                count = state.categoryCounts[category.id] ?: 0,
                selected = state.selectedCategoryId == category.id,
                onClick = { onSelect(category.id) },
            )
        }
        if (state.categoryCounts.containsKey(null)) {
            item {
                SegmentCard(
                    name = stringResource(R.string.csv_category_default),
                    count = state.categoryCounts[null] ?: 0,
                    selected = state.selectedCategoryId == StockUiState.UNSET_CATEGORY,
                    onClick = { onSelect(StockUiState.UNSET_CATEGORY) },
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SegmentCard(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.clickable { onClick() },
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilterRow(state: StockUiState, onSelect: (StockFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val filters = listOf(
            StockFilter.ALL to R.string.stock_filter_all,
            StockFilter.LOW to R.string.stock_filter_low,
            StockFilter.ZERO to R.string.stock_filter_zero,
            StockFilter.NEGATIVE to R.string.stock_filter_negative,
        )
        items(filters.size) { index ->
            val (filter, labelRes) = filters[index]
            FilterChip(
                selected = state.filter == filter,
                onClick = { onSelect(filter) },
                label = {
                    Text(
                        stringResource(
                            R.string.stock_filter_count,
                            stringResource(labelRes),
                            state.counts[filter] ?: 0,
                        )
                    )
                },
            )
        }
    }
}

/** Flat table row (design: Produk & Butiran) — emoji, SKU·location, category, on-hand, cost, price, value, actions. */
@Composable
private fun StockTableRow(
    row: StockRow,
    canEdit: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    val product = row.product
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = product.emoji ?: product.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    product.sku?.let { stringResource(R.string.stock_row_sku, it) },
                    product.location?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = row.categoryName.ifEmpty { stringResource(R.string.csv_category_default) },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(120.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column(Modifier.width(120.dp)) {
                val (qtyColor, statusRes) = when {
                    row.negative -> MaterialTheme.colorScheme.error to R.string.stock_status_negative
                    product.stockOnHand == 0L -> MaterialTheme.colorScheme.error to R.string.stock_status_out
                    row.low -> ExtendedTheme.onWarningContainer to R.string.stock_status_low
                    else -> ExtendedTheme.success to R.string.stock_status_ok
                }
                Text(
                    text = stringResource(R.string.stock_on_hand_units, product.stockOnHand),
                    style = MaterialTheme.typography.titleSmall,
                    color = qtyColor,
                )
                Text(
                    text = stringResource(statusRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = qtyColor,
                )
            }
            Text(
                text = MoneyFormatter.formatPlain(product.costCents),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(72.dp),
                maxLines = 1,
            )
            Text(
                text = MoneyFormatter.formatPlain(product.priceCents),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(72.dp),
                maxLines = 1,
            )
            Text(
                text = MoneyFormatter.format(row.valueCostCents),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(96.dp),
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
            if (canEdit) {
                TextButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.stock_row_edit))
                }
            }
        }
    }
}

@Composable
private fun ValuationFooter(state: StockUiState, onExport: () -> Unit) {
    val marginPct = state.marginBps?.let { "%.1f%%".format(java.util.Locale.ROOT, it / 100.0) } ?: "—"
    Card(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.stock_valuation_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onExport) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.stock_export_csv))
                }
            }
            ValuationRow(stringResource(R.string.stock_valuation_cost), MoneyFormatter.format(state.totalCostCents))
            ValuationRow(stringResource(R.string.stock_valuation_sale), MoneyFormatter.format(state.totalSaleCents))
            ValuationRow(
                label = stringResource(R.string.stock_valuation_margin),
                value = stringResource(
                    R.string.stock_valuation_margin_value,
                    MoneyFormatter.format(state.marginAmountCents),
                    marginPct,
                ),
            )
        }
    }
}

@Composable
private fun ValuationRow(label: String, value: String) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.titleSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockDetailSheet(
    detail: StockDetail,
    saving: Boolean,
    canAdjust: Boolean,
    canEdit: Boolean,
    message: String?,
    onAdjust: (net.redngreen.easypos.core.database.entity.Product, StockAdjustMode, Long, String) -> Unit,
    onThreshold: (net.redngreen.easypos.core.database.entity.Product, Int) -> Unit,
    onConsumeMessage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val product = detail.product
    var mode by remember(product.id) { mutableStateOf(StockAdjustMode.SET) }
    var amountText by remember(product.id) { mutableStateOf("") }
    var reason by remember(product.id) { mutableStateOf("") }
    var thresholdText by remember(product.id) { mutableStateOf(product.lowStockThreshold.toString()) }
    val zone = ZoneId.systemDefault()

    val messageText = when (message) {
        null -> null
        StockViewModel.MESSAGE_DENIED -> stringResource(R.string.access_denied)
        StockViewModel.MESSAGE_REASON_REQUIRED -> stringResource(R.string.stock_error_reason)
        StockViewModel.MESSAGE_INVALID_AMOUNT -> stringResource(R.string.stock_error_amount)
        else -> message
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(product.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.stock_current_on_hand, product.stockOnHand),
                style = MaterialTheme.typography.titleMedium,
            )
            product.location?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (messageText != null) {
                Text(
                    text = messageText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onConsumeMessage) { Text(stringResource(R.string.action_close)) }
            }

            if (canEdit) {
                HorizontalDivider()
                Text(stringResource(R.string.stock_threshold_label), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it.filter(Char::isDigit) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(140.dp),
                    )
                    OutlinedButton(
                        onClick = { thresholdText.toIntOrNull()?.let { onThreshold(product, it) } },
                    ) { Text(stringResource(R.string.stock_threshold_save)) }
                }
            }

            if (canAdjust) {
                HorizontalDivider()
                Text(stringResource(R.string.stock_adjust_title), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val modes = listOf(
                        StockAdjustMode.SET to R.string.stock_adjust_mode_set,
                        StockAdjustMode.RECEIVE to R.string.stock_adjust_mode_receive,
                        StockAdjustMode.REMOVE to R.string.stock_adjust_mode_remove,
                    )
                    items(modes.size) { index ->
                        val (m, labelRes) = modes[index]
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m },
                            label = { Text(stringResource(labelRes)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.stock_adjust_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val presets = listOf(
                        R.string.stock_reason_count,
                        R.string.stock_reason_damaged,
                        R.string.stock_reason_new_stock,
                    )
                    items(presets.size) { index ->
                        val presetText = stringResource(presets[index])
                        FilterChip(
                            selected = reason == presetText,
                            onClick = { reason = presetText },
                            label = { Text(presetText) },
                        )
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(stringResource(R.string.stock_adjust_reason)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        val amount = amountText.toLongOrNull()
                        if (amount != null && reason.isNotBlank()) onAdjust(product, mode, amount, reason)
                    },
                    enabled = !saving && amountText.toLongOrNull() != null && reason.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.stock_adjust_apply)) }
            }

            HorizontalDivider()
            Text(stringResource(R.string.stock_history_title), style = MaterialTheme.typography.titleSmall)

            if (detail.history.isEmpty()) {
                Text(
                    text = stringResource(R.string.stock_history_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                detail.history.forEach { row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.stock_history_change, row.before, row.after),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = reasonLabel(row) + (row.userName?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = HISTORY_TIME.format(Instant.ofEpochMilli(row.at).atZone(zone)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun reasonLabel(row: StockHistoryRow): String = when (row.reason) {
    "ADJUST" -> row.note?.takeIf { it.isNotBlank() } ?: stringResource(R.string.stock_reason_adjust)
    "SALE" -> stringResource(R.string.stock_reason_sale)
    "IMPORT" -> stringResource(R.string.stock_reason_import)
    else -> row.note ?: row.reason
}
