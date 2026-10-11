package net.redngreen.easypos.ui.catalog

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.redngreen.easypos.R
import net.redngreen.easypos.feature.catalog.domain.Product
import net.redngreen.easypos.core.money.MoneyFormatter
import net.redngreen.easypos.designsystem.ExtendedTheme

private val QUICK_EMOJIS = listOf("🥛", "🍞", "🥤", "🍜", "🧴", "🧹", "🍫", "☕")

/**
 * Product form (task 6.2, Stitch "EasyPOS Product Entry"): sectioned cards —
 * Identity & Classification, Pricing & Margin (live banner + health chip),
 * Stock & Terminal Visibility (threshold stepper, on-hand, sellable toggle),
 * and a live sell-tile preview. Emoji quick-picker over the free-text field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditScreen(
    productId: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    prefillBarcode: String? = null,
    viewModel: ProductEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(productId, prefillBarcode) { viewModel.load(productId, prefillBarcode) }
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.product_create_title else R.string.product_edit_title
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.denied -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.access_denied),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            else -> Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // ── Left: the form sections ───────────────────────────────────
                Column(
                    Modifier.weight(1.5f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionCard(title = stringResource(R.string.product_section_identity)) {
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = viewModel::setName,
                            label = { Text(stringResource(R.string.user_name_label)) },
                            singleLine = true,
                            isError = state.error == ProductEditError.INVALID_NAME,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.barcode,
                            onValueChange = viewModel::setBarcode,
                            label = { Text(stringResource(R.string.product_barcode_label)) },
                            singleLine = true,
                            isError = state.error == ProductEditError.BARCODE_TAKEN,
                            supportingText = {
                                when (state.error) {
                                    ProductEditError.BARCODE_TAKEN -> Text(
                                        stringResource(R.string.error_barcode_taken),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    else -> Text(stringResource(R.string.product_barcode_hint))
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CategoryDropdown(
                                categories = state.categories,
                                selectedId = state.categoryId,
                                onSelect = viewModel::setCategoryId,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = state.sku,
                                onValueChange = viewModel::setSku,
                                label = { Text(stringResource(R.string.product_sku_label)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OutlinedTextField(
                            value = state.location,
                            onValueChange = viewModel::setLocation,
                            label = { Text(stringResource(R.string.product_location_label)) },
                            singleLine = true,
                            supportingText = { Text(stringResource(R.string.product_location_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    SectionCard(title = stringResource(R.string.product_section_pricing)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = state.costText,
                                onValueChange = viewModel::setCost,
                                label = { Text(stringResource(R.string.product_cost_label)) },
                                prefix = { Text("RM ") },
                                singleLine = true,
                                isError = state.error == ProductEditError.INVALID_COST,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = state.priceText,
                                onValueChange = viewModel::setPrice,
                                label = { Text(stringResource(R.string.product_price_label)) },
                                prefix = { Text("RM ") },
                                singleLine = true,
                                isError = state.error == ProductEditError.INVALID_PRICE,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Live margin banner + health chip (design: Untung Kasar).
                        state.margin?.let { margin ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.product_margin_preview,
                                            MoneyFormatter.format(margin.marginCents),
                                            formatBps(margin.marginBps),
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    val healthy = margin.marginBps >= 1500
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (healthy) ExtendedTheme.successContainer else ExtendedTheme.warningContainer,
                                    ) {
                                        Text(
                                            text = stringResource(
                                                if (healthy) R.string.product_margin_healthy else R.string.product_margin_thin
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (healthy) ExtendedTheme.onSuccessContainer else ExtendedTheme.onWarningContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SectionCard(title = stringResource(R.string.product_section_stock)) {
                        if (state.isNew) {
                            OutlinedTextField(
                                value = state.initialStockText,
                                onValueChange = viewModel::setInitialStock,
                                label = { Text(stringResource(R.string.product_initial_stock_label)) },
                                singleLine = true,
                                isError = state.error == ProductEditError.INVALID_STOCK,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(0.6f),
                            )
                        } else {
                            state.stockOnHand?.let { onHand ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Inventory2,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.product_current_on_hand, onHand),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        // Threshold stepper (design: − 5 units + with amber-badge hint).
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalIconButton(
                                onClick = { viewModel.setThreshold(((state.thresholdText.toIntOrNull() ?: 0) - 1).coerceAtLeast(0).toString()) },
                                modifier = Modifier.size(40.dp),
                            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                            Text(
                                text = stringResource(
                                    R.string.product_threshold_units,
                                    state.thresholdText.toIntOrNull() ?: 0,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            FilledTonalIconButton(
                                onClick = { viewModel.setThreshold(((state.thresholdText.toIntOrNull() ?: 0) + 1).toString()) },
                                modifier = Modifier.size(40.dp),
                            ) { Icon(Icons.Filled.AddCircle, contentDescription = null) }
                        }
                        Text(
                            text = stringResource(R.string.product_threshold_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!state.isNew) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = state.active, onCheckedChange = viewModel::setActive)
                                Text(
                                    stringResource(
                                        if (state.active) R.string.product_active_sellable
                                        else R.string.product_inactive_hidden
                                    ),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }

                    val errorText = when (state.error) {
                        ProductEditError.INVALID_NAME -> stringResource(R.string.error_name_required)
                        ProductEditError.INVALID_PRICE -> stringResource(R.string.error_price_invalid)
                        ProductEditError.INVALID_COST -> stringResource(R.string.error_cost_invalid)
                        ProductEditError.INVALID_STOCK -> stringResource(R.string.error_stock_invalid)
                        ProductEditError.BARCODE_TAKEN -> stringResource(R.string.error_barcode_taken)
                        ProductEditError.UNKNOWN -> stringResource(R.string.error_unknown)
                        null -> null
                    }
                    if (errorText != null) {
                        Text(
                            text = errorText + (state.errorDetail?.let { " ($it)" } ?: ""),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onDone, modifier = Modifier.height(52.dp)) {
                            Text(stringResource(R.string.lock_cancel))
                        }
                        Button(
                            onClick = viewModel::save,
                            enabled = !state.saving && state.name.isNotBlank() && state.priceText.isNotBlank(),
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            if (state.saving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text(stringResource(R.string.user_save), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }

                // ── Right: emoji picker + live sell-tile preview ──────────────
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionCard(title = stringResource(R.string.product_emoji_label)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            QUICK_EMOJIS.forEach { emoji ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (state.emoji == emoji) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                    onClick = { viewModel.setEmoji(emoji) },
                                ) {
                                    Text(
                                        text = emoji,
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(8.dp),
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = state.emoji,
                            onValueChange = viewModel::setEmoji,
                            label = { Text(stringResource(R.string.product_emoji_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(0.5f),
                        )
                    }

                    SectionCard(title = stringResource(R.string.product_tile_preview)) {
                        Text(
                            text = stringResource(R.string.product_tile_preview_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        val previewPrice = runCatching { MoneyFormatter.parseCents(state.priceText.ifBlank { "0" }) }.getOrNull() ?: 0L
                        ProductTile(
                            product = Product(
                                id = "preview",
                                shopId = "preview",
                                categoryId = state.categoryId,
                                name = state.name.ifBlank { stringResource(R.string.product_preview_name) },
                                barcode = null,
                                sku = null,
                                costCents = 0L,
                                priceCents = previewPrice,
                                emoji = state.emoji.takeIf { it.isNotBlank() },
                                lowStockThreshold = state.thresholdText.toIntOrNull() ?: 5,
                            ),
                            onClick = {},
                            showStock = true,
                            modifier = Modifier.size(140.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    categories: List<net.redngreen.easypos.feature.catalog.domain.Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == selectedId }
    val fallback = stringResource(R.string.csv_category_default)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.name ?: fallback,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.product_category_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(fallback) },
                onClick = { onSelect(null); expanded = false },
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = { onSelect(category.id); expanded = false },
                )
            }
        }
    }
}

/** 2083 bps → "20.8%". */
private fun formatBps(bps: Int): String = "%.1f%%".format(java.util.Locale.ROOT, bps / 100.0)
