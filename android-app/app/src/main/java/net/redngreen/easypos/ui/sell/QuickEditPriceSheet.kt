package net.redngreen.easypos.ui.sell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.redngreen.easypos.R
import net.redngreen.easypos.core.money.MoneyFormatter
import net.redngreen.easypos.theme.ExtendedTheme
import net.redngreen.easypos.ui.common.PosKeypad
import net.redngreen.easypos.ui.common.appendDigit
import net.redngreen.easypos.ui.lock.roleLabel

/**
 * Quick price edit (task 6.7, Stitch "EasyPOS Quick Edit Price Sheet"):
 * manager-gated inline sale/cost edit — keypad editors, quick increments,
 * live margin. The save is journaled + attributed; the sell grid refreshes
 * from the product flow.
 */
@Composable
fun QuickEditPriceSheet(
    productId: String,
    onDismiss: () -> Unit,
    viewModel: QuickEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(productId) { viewModel.load(productId) }
    LaunchedEffect(state.saved) { if (state.saved) onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
        ) {
            if (state.loading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.needsAuth) {
                ManagerPinGate(
                    product = state.product,
                    onVerify = viewModel::verifyPin,
                    onDismiss = onDismiss,
                )
            } else {
                EditorContent(
                    state = state,
                    onSetPrice = viewModel::setPrice,
                    onSetCost = viewModel::setCost,
                    onSave = viewModel::save,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

/** PIN gate shown when the acting cashier lacks catalog-edit permission. */
@Composable
private fun ManagerPinGate(
    product: net.redngreen.easypos.core.data.entity.Product?,
    onVerify: (String, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quickedit_auth_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.quickedit_auth_hint, product?.name ?: ""))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(6); error = false },
                    label = { Text(stringResource(R.string.quickedit_auth_pin)) },
                    singleLine = true,
                    isError = error,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (error) {
                    Text(
                        text = stringResource(R.string.lock_wrong_pin),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onVerify(pin) { ok -> error = !ok } },
                enabled = pin.length >= 4,
            ) { Text(stringResource(R.string.quickedit_auth_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) }
        },
    )
}

private enum class EditTarget { PRICE, COST }

@Composable
private fun EditorContent(
    state: QuickEditUiState,
    onSetPrice: (Long) -> Unit,
    onSetCost: (Long) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val product = state.product ?: return
    var target by remember { mutableStateOf(EditTarget.PRICE) }

    Column(
        Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Product header ───────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(product.emoji ?: product.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = listOfNotNull(state.categoryName, product.sku?.let { "SKU: $it" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
            }
        }

        // Authorization badge (design: green, "Kebenaran Pengurus Disahkan").
        Row(
            modifier =
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(ExtendedTheme.successContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = ExtendedTheme.onSuccessContainer)
            Text(
                text = stringResource(
                    R.string.quickedit_authorized_badge,
                    state.authorizedName ?: "",
                    roleLabel(state.authorizedRole ?: "MANAGER"),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = ExtendedTheme.onSuccessContainer,
            )
        }

        // ── Price / cost editors ─────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EditorCard(
                title = stringResource(R.string.quickedit_sale_price),
                oldCents = product.priceCents,
                cents = state.priceCents,
                selected = target == EditTarget.PRICE,
                onClick = { target = EditTarget.PRICE },
                modifier = Modifier.weight(1f),
            )
            EditorCard(
                title = stringResource(R.string.quickedit_cost),
                oldCents = null,
                cents = state.costCents,
                selected = target == EditTarget.COST,
                onClick = { target = EditTarget.COST },
                modifier = Modifier.weight(1f),
            )
        }

        // Quick increments target whichever editor is selected.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10L, 50L, 100L).forEach { inc ->
                OutlinedButton(onClick = {
                    if (target == EditTarget.PRICE) onSetPrice(state.priceCents + inc) else onSetCost(state.costCents + inc)
                }) {
                    Text(stringResource(R.string.payment_add_rm, (inc / 100).toInt()))
                }
            }
            OutlinedButton(onClick = {
                if (target == EditTarget.PRICE) onSetPrice(product.priceCents) else onSetCost(product.costCents)
            }) {
                Text(stringResource(R.string.quickedit_reset, MoneyFormatter.formatPlain(if (target == EditTarget.PRICE) product.priceCents else product.costCents)))
            }
        }

        PosKeypad(
            onDigit = { digit ->
                if (target == EditTarget.PRICE) onSetPrice(appendDigit(state.priceCents, digit))
                else onSetCost(appendDigit(state.costCents, digit))
            },
            onDoubleZero = {
                if (target == EditTarget.PRICE) onSetPrice(state.priceCents * 100) else onSetCost(state.costCents * 100)
            },
            onBackspace = {
                if (target == EditTarget.PRICE) onSetPrice(state.priceCents / 10) else onSetCost(state.costCents / 10)
            },
            modifier = Modifier.fillMaxWidth(),
            keyHeight = 48.dp,
        )

        // Live margin (design: green banner + health chip).
        val marginBps = MoneyFormatter.marginBps(state.costCents, state.priceCents)
        if (marginBps != null) {
            Row(
                modifier =
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.quickedit_margin,
                        MoneyFormatter.format((state.priceCents - state.costCents).coerceAtLeast(0)),
                        "%.1f".format(java.util.Locale.ROOT, marginBps / 100.0),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                val healthy = marginBps >= 1500
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (healthy) ExtendedTheme.successContainer else ExtendedTheme.warningContainer,
                ) {
                    Text(
                        text = stringResource(if (healthy) R.string.quickedit_margin_healthy else R.string.quickedit_margin_thin),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (healthy) ExtendedTheme.onSuccessContainer else ExtendedTheme.onWarningContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }

        if (state.message != null) {
            Text(
                text = state.message ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.height(52.dp)) {
                Text(stringResource(R.string.lock_cancel))
            }
            Button(
                onClick = onSave,
                enabled = !state.saving && state.priceCents > 0,
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.quickedit_save), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Text(
            text = stringResource(R.string.quickedit_footnote, state.authorizedName ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EditorCard(
    title: String,
    oldCents: Long?,
    cents: Long,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable { onClick() }
                .padding(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (oldCents != null && oldCents != cents) {
            val delta = cents - oldCents
            Text(
                text = stringResource(R.string.quickedit_old_price, MoneyFormatter.format(oldCents)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (delta > 0) {
                    stringResource(R.string.quickedit_delta_up, MoneyFormatter.format(delta))
                } else {
                    stringResource(R.string.quickedit_delta_down, MoneyFormatter.format(-delta))
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (delta >= 0) ExtendedTheme.success else MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = MoneyFormatter.format(cents),
            style = MaterialTheme.typography.headlineMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
