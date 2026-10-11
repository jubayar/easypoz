package net.redngreen.easypos.ui.sell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import net.redngreen.easypos.R
import net.redngreen.easypos.core.data.entity.PaymentType
import net.redngreen.easypos.core.money.MoneyFormatter
import net.redngreen.easypos.theme.ExtendedTheme
import net.redngreen.easypos.ui.common.PosKeypad
import net.redngreen.easypos.ui.common.appendDigit

private val PAYMENT_TIME = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")

/**
 * Payment capture (tasks 7.5 + 7.11, Stitch "EasyPOS Payment Sheet"): full-
 * screen dialog — tender method cards on the left, cash-register keypad +
 * quick tenders + change on the right, and a success panel once the sale
 * commits. Keypad math is pure local cents; nothing here touches the network.
 */
@Composable
fun PaymentSheet(
    totalCents: Long,
    itemCount: Int,
    paymentTypes: List<PaymentType>,
    merchantQrBase64: String?,
    drawerKick: Boolean,
    completing: Boolean,
    success: PaymentSuccess?,
    initialTypeId: String?,
    initialTenderCents: Long?,
    onConfirm: (paymentTypeId: String?, tenderedCents: Long?) -> Unit,
    onDismissSuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember {
        mutableStateOf(paymentTypes.firstOrNull { it.id == initialTypeId } ?: paymentTypes.firstOrNull())
    }
    var tenderCents by remember(initialTenderCents) { mutableLongStateOf(initialTenderCents ?: 0L) }

    val isCash = selected?.kind == "CASH"
    val tendered = if (isCash && tenderCents > 0) tenderCents else null
    val change = tendered?.let { MoneyFormatter.change(it, totalCents) }
    val under = change != null && change < 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
        ) {
            if (success != null) {
                SuccessPanel(success = success, onNextCustomer = onDismissSuccess)
            } else {
                Column(Modifier.padding(24.dp)) {
                    HeaderBlock(
                        totalCents = totalCents,
                        itemCount = itemCount,
                        drawerKick = drawerKick,
                    )
                    Spacer(Modifier.height(16.dp))

                    Row(Modifier.weight(1f)) {
                        // ── Left: tender methods ────────────────────────────────
                        Column(
                            Modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            paymentTypes.forEach { type ->
                                MethodCard(
                                    type = type,
                                    selected = selected?.id == type.id,
                                    onClick = { selected = type },
                                )
                            }
                        }

                        Box(Modifier.padding(horizontal = 20.dp)) {
                            HorizontalDivider(
                                Modifier.fillMaxHeight().width(1.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }

                        // ── Right: tender capture ───────────────────────────────
                        if (isCash) {
                            CashTenderPane(
                                totalCents = totalCents,
                                tenderCents = tenderCents,
                                under = under,
                                change = change,
                                onTenderChange = { tenderCents = it },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            QrTenderPane(
                                totalCents = totalCents,
                                merchantQrBase64 = merchantQrBase64,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    FooterBlock(
                        completing = completing,
                        isCash = isCash,
                        change = change,
                        under = under,
                        selectedId = selected?.id,
                        tendered = tendered,
                        onConfirm = { onConfirm(selected?.id, if (isCash) tendered else null) },
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderBlock(totalCents: Long, itemCount: Int, drawerKick: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.payment_checkout_total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = MoneyFormatter.format(totalCents),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.payment_items_line, itemCount, PAYMENT_TIME.format(ZonedDateTime.now())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (drawerKick) {
                Text(
                    text = stringResource(R.string.payment_drawer_ready),
                    style = MaterialTheme.typography.labelMedium,
                    color = ExtendedTheme.success,
                )
            }
        }
    }
    Text(
        text = stringResource(R.string.payment_sst_included),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MethodCard(type: PaymentType, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow
                )
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = methodIcon(type),
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(text = type.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(
                    if (type.kind == "CASH") R.string.payment_method_instant_drawer
                    else R.string.payment_method_instant_pay
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun methodIcon(type: PaymentType): ImageVector = when {
    type.kind == "CASH" -> Icons.Filled.Payments
    type.name.contains("duit", ignoreCase = true) ||
        type.name.contains("qr", ignoreCase = true) -> Icons.Filled.QrCode2
    type.name.contains("tng", ignoreCase = true) ||
        type.name.contains("wallet", ignoreCase = true) ||
        type.name.contains("e-wallet", ignoreCase = true) -> Icons.Filled.AccountBalanceWallet
    else -> Icons.Filled.Contactless
}

@Composable
private fun CashTenderPane(
    totalCents: Long,
    tenderCents: Long,
    under: Boolean,
    change: Long?,
    onTenderChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Cash-register display
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.payment_tendered_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { onTenderChange(0L) }) {
                Icon(
                    imageVector = Icons.Filled.RestartAlt,
                    contentDescription = stringResource(R.string.payment_reset),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = MoneyFormatter.format(tenderCents),
            style = MaterialTheme.typography.displaySmall,
            color = if (under) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (under && change != null) {
            Text(
                text = stringResource(R.string.payment_under_tender, MoneyFormatter.format(-change)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (change != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.payment_change_due),
                    style = MaterialTheme.typography.titleMedium,
                    color = ExtendedTheme.onSuccessContainer,
                )
                Text(
                    text = MoneyFormatter.format(change),
                    style = MaterialTheme.typography.headlineMedium,
                    color = ExtendedTheme.success,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 5, 10).forEach { ringgit ->
                OutlinedButton(onClick = { onTenderChange(tenderCents + ringgit * 100) }) {
                    Text(stringResource(R.string.payment_add_rm, ringgit))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        // Quick tenders: exact + round-ups, each previewing its change.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoneyFormatter.quickTenders(totalCents).forEach { chip ->
                FilterChip(
                    selected = tenderCents == chip && chip > 0,
                    onClick = { onTenderChange(chip) },
                    label = {
                        Text(
                            if (chip == totalCents) stringResource(R.string.payment_tender_exact, MoneyFormatter.format(chip))
                            else stringResource(
                                R.string.payment_tender_change_hint,
                                MoneyFormatter.formatPlain(chip),
                                MoneyFormatter.format(chip - totalCents),
                            )
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        PosKeypad(
            onDigit = { onTenderChange(appendDigit(tenderCents, it).coerceAtMost(99_999_999L)) },
            onDoubleZero = { onTenderChange((tenderCents * 100).coerceAtMost(99_999_999L)) },
            onBackspace = { onTenderChange(tenderCents / 10) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Non-cash tender: the shop's static merchant QR (offline-safe, task 7.11). */
@Composable
private fun QrTenderPane(totalCents: Long, merchantQrBase64: String?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val qrBitmap =
            remember(merchantQrBase64) {
                merchantQrBase64?.let {
                    runCatching {
                        val bytes = android.util.Base64.decode(it, android.util.Base64.DEFAULT)
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }.getOrNull()
                }
            }
        if (qrBitmap != null) {
            Text(
                text = stringResource(R.string.payment_merchant_qr),
                style = MaterialTheme.typography.titleMedium,
            )
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.payment_merchant_qr),
                modifier = Modifier.size(260.dp).padding(vertical = 12.dp),
            )
        }
        Text(
            text = stringResource(R.string.payment_merchant_qr_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = MoneyFormatter.format(totalCents),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun FooterBlock(
    completing: Boolean,
    isCash: Boolean,
    change: Long?,
    under: Boolean,
    selectedId: String?,
    tendered: Long?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val confirmLabel =
        when {
            !isCash -> stringResource(R.string.payment_confirm_paid)
            change != null && !under -> stringResource(R.string.payment_complete_change, MoneyFormatter.format(change))
            else -> stringResource(R.string.payment_confirm)
        }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onDismiss, modifier = Modifier.height(56.dp)) {
            Text(stringResource(R.string.payment_return))
        }
        Button(
            onClick = onConfirm,
            enabled =
                !completing && selectedId != null &&
                    (!isCash || (tendered != null && !under)),
            modifier = Modifier.weight(1f).height(56.dp),
        ) {
            if (completing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(confirmLabel, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Post-commit panel (Stitch: Bayaran Berjaya + Next Customer). */
@Composable
private fun SuccessPanel(success: PaymentSuccess, onNextCustomer: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = ExtendedTheme.success,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.payment_success_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.payment_receipt_no, success.saleNo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SuccessRow(stringResource(R.string.payment_success_total_paid), MoneyFormatter.format(success.tenderedCents ?: success.totalCents))
                if (success.changeCents != null && success.changeCents > 0) {
                    SuccessRow(stringResource(R.string.payment_success_change_dispensed), MoneyFormatter.format(success.changeCents))
                }
                SuccessRow(
                    stringResource(R.string.payment_success_method),
                    success.paymentTypeName ?: "—",
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onNextCustomer, modifier = Modifier.fillMaxWidth(0.6f).height(56.dp)) {
            Text(stringResource(R.string.payment_next_customer), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SuccessRow(label: String, value: String) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}
