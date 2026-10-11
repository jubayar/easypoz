package net.redngreen.easypos.ui.sell

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import net.redngreen.easypos.R
import net.redngreen.easypos.core.money.MoneyFormatter
import net.redngreen.easypos.core.print.PrintState
import net.redngreen.easypos.designsystem.ExtendedTheme
import net.redngreen.easypos.designsystem.ProductTile

private val HELD_TIME = DateTimeFormatter.ofPattern("HH:mm")

/**
 * The sell screen (task 7.1, Stitch "EasyPOS Sell / Cashier"): landscape
 * split — scanner bar + category chips + product grid on the left, persistent
 * cart on the right. Scanner-wedge capture field on top (task 7.2): always
 * focused, Enter-terminated.
 */
@Composable
fun SellScreen(
    onCreateProductForBarcode: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SellViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val printState by viewModel.printState.collectAsStateWithLifecycle()
    var showQuickSell by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showPayment by remember { mutableStateOf(false) }
    var showHoldDialog by remember { mutableStateOf(false) }
    var voidTarget by remember { mutableStateOf<String?>(null) }
    // Payment entry hints: preselected method/tender from the DuitNow and
    // quick-cash shortcuts (null = sheet defaults).
    var pendingTypeId by remember { mutableStateOf<String?>(null) }
    var pendingTenderCents by remember { mutableStateOf<Long?>(null) }
    // Quick price edit target (task 6.7): long-press a tile or cart line.
    var quickEditProductId by remember { mutableStateOf<String?>(null) }

    Row(modifier = modifier.fillMaxSize()) {
        // ─── Left: capture + tabs + grid ─────────────────────────────────────
        Column(Modifier.weight(1.6f).fillMaxHeight().padding(end = 12.dp)) {
            ScannerBar(
                value = state.query,
                onValueChange = viewModel::setQuery,
                onSubmit = viewModel::submitCapture,
                onCustom = { showQuickSell = true },
            )
            PrintStatusBanner(
                printState = printState,
                onRetry = viewModel::retryPrint,
                onDismiss = viewModel::dismissPrintError,
            )
            Spacer(Modifier.height(6.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = state.selectedCategoryId == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = {
                            Text(
                                stringResource(
                                    R.string.category_chip_count,
                                    stringResource(R.string.category_all),
                                    state.categoryCounts.values.sum(),
                                )
                            )
                        },
                    )
                }
                items(state.categories.size) { index ->
                    val category = state.categories[index]
                    FilterChip(
                        selected = state.selectedCategoryId == category.id,
                        onClick = { viewModel.selectCategory(category.id) },
                        label = {
                            Text(
                                stringResource(
                                    R.string.category_chip_count,
                                    category.name,
                                    state.categoryCounts[category.id] ?: 0,
                                )
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            val gridProducts = state.searchResults ?: state.products
            if (gridProducts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.searchResults != null) {
                            stringResource(R.string.search_no_results)
                        } else {
                            stringResource(R.string.grid_empty_hint)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 128.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(gridProducts, key = { it.id }) { product ->
                        ProductTile(
                            product = product,
                            onClick = { viewModel.addToCart(product) },
                            showStock = true,
                            onLongClick = { quickEditProductId = product.id },
                        )
                    }
                }
            }
        }

        // ─── Right: held strip + cart panel ──────────────────────────────────
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (state.heldSales.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.heldSales.size) { index ->
                        val held = state.heldSales[index]
                        AssistChip(
                            onClick = { viewModel.resumeHeld(held) },
                            label = {
                                Text(
                                    text = (held.label ?: stringResource(R.string.held_default_label)) +
                                        " · " + HELD_TIME.format(
                                        Instant.ofEpochMilli(held.heldAt).atZone(ZoneId.systemDefault())
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            CartPanel(
                state = state,
                onIncrement = viewModel::increment,
                onDecrement = viewModel::decrement,
                onVoid = { voidTarget = it },
                onClear = { showClearConfirm = true },
                onHold = { showHoldDialog = true },
                onQuickEdit = { quickEditProductId = it },
                onPay = { typeId, tender ->
                    pendingTypeId = typeId
                    pendingTenderCents = tender
                    showPayment = true
                },
            )
        }
    }

    // ─── Dialogs & sheets ────────────────────────────────────────────────────

    state.unknownBarcode?.let { barcode ->
        UnknownBarcodeDialog(
            barcode = barcode,
            onCreateProduct = {
                viewModel.dismissUnknownBarcode()
                onCreateProductForBarcode(barcode)
            },
            onQuickSell = { viewModel.dismissUnknownBarcode(); showQuickSell = true },
            onDismiss = viewModel::dismissUnknownBarcode,
        )
    }

    if (showQuickSell) {
        QuickSellDialog(
            onConfirm = { name, priceCents ->
                viewModel.quickSell(name, priceCents)
                showQuickSell = false
            },
            onDismiss = { showQuickSell = false },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.action_clear)) },
            text = { Text(stringResource(R.string.clear_cart_confirm)) },
            confirmButton = {
                TextButton(onClick = { showClearConfirm = false; viewModel.clearCart() }) {
                    Text(stringResource(R.string.clear_cart_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.lock_cancel))
                }
            },
        )
    }

    if (showHoldDialog) {
        var label by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showHoldDialog = false },
            title = { Text(stringResource(R.string.action_hold)) },
            text = {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.held_label_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.holdCart(label); showHoldDialog = false },
                ) { Text(stringResource(R.string.action_hold)) }
            },
            dismissButton = {
                TextButton(onClick = { showHoldDialog = false }) {
                    Text(stringResource(R.string.lock_cancel))
                }
            },
        )
    }

    voidTarget?.let { key ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { voidTarget = null },
            title = { Text(stringResource(R.string.void_line_title)) },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(stringResource(R.string.void_reason_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.voidLine(key, reason); voidTarget = null },
                    enabled = reason.isNotBlank(),
                ) { Text(stringResource(R.string.void_line_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { voidTarget = null }) {
                    Text(stringResource(R.string.lock_cancel))
                }
            },
        )
    }

    if (showPayment) {
        PaymentSheet(
            totalCents = state.totalCents,
            itemCount = state.cartCount,
            paymentTypes = state.paymentTypes,
            merchantQrBase64 = state.merchantQrBase64,
            drawerKick = state.drawerKick,
            completing = state.completing,
            success = state.lastPayment,
            initialTypeId = pendingTypeId,
            initialTenderCents = pendingTenderCents,
            onConfirm = { paymentTypeId, tendered ->
                viewModel.completeSale(paymentTypeId, tendered)
            },
            onDismissSuccess = {
                viewModel.dismissPaymentSuccess()
                showPayment = false
            },
            onDismiss = {
                // Backing out of the success panel counts as acknowledging it —
                // otherwise a stale success resurfaces on the next open.
                viewModel.dismissPaymentSuccess()
                showPayment = false
            },
        )
    }

    quickEditProductId?.let { productId ->
        QuickEditPriceSheet(
            productId = productId,
            onDismiss = { quickEditProductId = null },
        )
    }
}

/** Scanner wedge + quick-sell shortcut + honest "ready" status. */
@Composable
private fun ScannerBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCustom: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CaptureField(
            value = value,
            onValueChange = onValueChange,
            onSubmit = onSubmit,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = onCustom, modifier = Modifier.height(56.dp)) {
            Icon(Icons.Filled.Dialpad, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.action_custom_item))
        }
    }
}

/** Non-blocking receipt print status (task 10.2): progress + retry; never gates selling. */
@Composable
private fun PrintStatusBanner(
    printState: PrintState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (printState) {
        is PrintState.Printing -> Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.printer_printing), style = MaterialTheme.typography.bodySmall)
        }

        is PrintState.Error -> Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.print_failed_retry),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (printState.canRetry) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.printer_retry)) }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }

        PrintState.Idle -> Unit
    }
}

/** Scanner-wedge capture field (design D8): always focused, Enter-terminated. */
@Composable
private fun CaptureField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        keyboard?.hide()
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.focusRequester(focusRequester),
        singleLine = true,
        label = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
        trailingIcon = { ScannerReadyBadge() },
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Go,
            keyboardType = KeyboardType.Ascii,
        ),
        keyboardActions = KeyboardActions(
            onGo = {
                onSubmit()
                runCatching { focusRequester.requestFocus() } // cleared + refocused for next scan
                keyboard?.hide()
            },
        ),
    )
}

@Composable
private fun ScannerReadyBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(8.dp).background(ExtendedTheme.success, CircleShape)
        )
        Text(
            text = stringResource(R.string.sell_scanner_ready),
            style = MaterialTheme.typography.labelSmall,
            color = ExtendedTheme.success,
        )
    }
}

@Composable
private fun CartPanel(
    state: SellUiState,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onVoid: (String) -> Unit,
    onClear: () -> Unit,
    onHold: () -> Unit,
    onQuickEdit: (productId: String) -> Unit,
    onPay: (preselectTypeId: String?, preselectTenderCents: Long?) -> Unit,
) {
    Card(Modifier.fillMaxSize()) {
        Column(Modifier.padding(12.dp)) {
            // Header: title + hold + clear (design: pause_circle / delete_sweep)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.cart_title, state.cartCount),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onHold, enabled = state.cart.isNotEmpty()) {
                    Icon(
                        Icons.Filled.PauseCircle,
                        contentDescription = stringResource(R.string.action_hold),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.cart.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            Icons.Filled.DeleteSweep,
                            contentDescription = stringResource(R.string.action_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))

            // Customer row (v1: walk-in only — membership is a deferred non-goal).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Filled.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.cart_walk_in),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))

            if (state.cart.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.cart_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.cart.forEach { line ->
                        CartLineRow(
                            line = line,
                            onIncrement = { onIncrement(line.key) },
                            onDecrement = { onDecrement(line.key) },
                            onVoid = { onVoid(line.key) },
                            onQuickEdit = line.productId?.let { id -> { onQuickEdit(id) } },
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 6.dp))

            // Quick-cash shortcuts: exact last (design: RM 25 / 30 / 50 / Exact).
            val tenders = MoneyFormatter.quickTenders(state.totalCents)
            val roundUps = tenders.filter { it != state.totalCents }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                roundUps.forEach { chip ->
                    OutlinedButton(
                        onClick = { onPay(null, chip) },
                        enabled = state.cart.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = MoneyFormatter.formatPlain(chip),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
                OutlinedButton(
                    onClick = { onPay(null, state.totalCents) },
                    enabled = state.cart.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.payment_tender_exact_short),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }

            // Totals block (SST is display-only 0% — computation is a non-goal).
            Row {
                Text(
                    text = stringResource(R.string.cart_subtotal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = MoneyFormatter.format(state.totalCents),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row {
                Text(
                    text = stringResource(R.string.sell_sst_zero),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = MoneyFormatter.format(0L),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.cart_total_due),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.cart_items_count, state.cartCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = MoneyFormatter.format(state.totalCents),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onPay(duitNowTypeId(state), null) },
                    enabled = state.cart.isNotEmpty(),
                    modifier = Modifier.height(56.dp),
                ) {
                    Icon(Icons.Filled.QrCode2, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pay_duitnow))
                }
                Button(
                    onClick = { onPay(null, null) },
                    enabled = state.cart.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text(
                        text = stringResource(R.string.action_pay_amount, MoneyFormatter.format(state.totalCents)),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** First non-cash configured type — the DuitNow shortcut target. */
private fun duitNowTypeId(state: SellUiState): String? =
    state.paymentTypes.firstOrNull { it.kind != "CASH" }?.id

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CartLineRow(
    line: CartLine,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onVoid: () -> Unit,
    onQuickEdit: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.combinedClickable(
                onClick = {},
                onLongClick = onQuickEdit,
            ),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = line.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.cart_unit_price, MoneyFormatter.format(line.unitCents)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconButton(onClick = onDecrement, modifier = Modifier.size(36.dp)) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = line.qty.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 6.dp).width(28.dp),
            textAlign = TextAlign.Center,
        )
        FilledTonalIconButton(onClick = onIncrement, modifier = Modifier.size(36.dp)) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = MoneyFormatter.format(line.lineCents),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 8.dp).width(88.dp),
            textAlign = TextAlign.End,
        )
        IconButton(onClick = onVoid, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.void_line_short),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Unknown scan: offer create-product or quick-sell; cart untouched (spec). */
@Composable
private fun UnknownBarcodeDialog(
    barcode: String,
    onCreateProduct: () -> Unit,
    onQuickSell: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.barcode_not_found)) },
        text = { Text(stringResource(R.string.barcode_not_found_detail, barcode)) },
        confirmButton = {
            TextButton(onClick = onCreateProduct) {
                Text(stringResource(R.string.barcode_not_found_create))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onQuickSell) {
                    Text(stringResource(R.string.barcode_not_found_quick_sell))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) }
            }
        },
    )
}

@Composable
private fun QuickSellDialog(
    onConfirm: (String, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_quick_sell)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = false },
                    label = { Text(stringResource(R.string.quick_sell_name_hint)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it; error = false },
                    label = { Text(stringResource(R.string.quick_sell_price_hint)) },
                    singleLine = true,
                    isError = error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        val cents = MoneyFormatter.parseCents(priceText)
                        if (name.isBlank() || cents < 0) error = true
                        else onConfirm(name, cents)
                    } catch (e: NumberFormatException) {
                        error = true
                    }
                },
                enabled = name.isNotBlank() && priceText.isNotBlank(),
            ) { Text(stringResource(R.string.quick_sell_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) }
        },
    )
}
