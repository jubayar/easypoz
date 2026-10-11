package net.redngreen.easypos.ui.cash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import net.redngreen.easypos.R
import net.redngreen.easypos.core.money.MoneyFormatter
import net.redngreen.easypos.designsystem.ExtendedTheme
import net.redngreen.easypos.designsystem.PosKeypad
import net.redngreen.easypos.designsystem.appendDigit

private val LOG_TIME = DateTimeFormatter.ofPattern("d MMM · HH:mm")

/**
 * Cash in/out + log (tasks 9.1/9.2/9.4, Stitch "EasyPOS Cash In / Out"):
 * entry card on the left (direction cards, keypad amount, reason presets,
 * cashier-in-charge footer) and the live drawer ledger on the right.
 */
@Composable
fun CashScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CashViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.denied) {
        Column(modifier.fillMaxSize().padding(16.dp)) {
            HeaderBand(onBack = onBack)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.access_denied), color = MaterialTheme.colorScheme.error)
            }
        }
        return
    }

    Row(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            HeaderBand(onBack = onBack)
            if (state.canRecord) {
                CashEntryCard(
                    state = state,
                    onRecord = viewModel::record,
                    onConsumeMessage = viewModel::consumeMessage,
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        // ── Right: drawer balance + ledger ────────────────────────────────────
        Column(Modifier.weight(1.4f).fillMaxHeight()) {
            Spacer(Modifier.height(52.dp))
            DrawerBalanceCard(
                expectedCents = state.expectedDrawerCents,
                todayInCents = state.todayInCents,
                todayOutCents = state.todayOutCents,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.cash_log_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.cash_log_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.loading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (state.entries.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.cash_log_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val zone = ZoneId.systemDefault()
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.entries.size, key = { state.entries[it].entry.id }) { index ->
                        val row = state.entries[index]
                        CashLogRow(row = row, zone = zone)
                    }
                    item {
                        Text(
                            text = stringResource(R.string.cash_log_showing, state.entries.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderBand(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
        }
        Column {
            Text(text = stringResource(R.string.cash_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.cash_title_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DrawerBalanceCard(expectedCents: Long, todayInCents: Long, todayOutCents: Long) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.cash_expected_drawer),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = MoneyFormatter.format(expectedCents),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.cash_expected_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.cash_total_in_today, MoneyFormatter.format(todayInCents)),
                    style = MaterialTheme.typography.labelMedium,
                    color = ExtendedTheme.success,
                )
                Text(
                    text = stringResource(R.string.cash_total_out_today, MoneyFormatter.format(todayOutCents)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CashEntryCard(
    state: CashUiState,
    onRecord: (CashDirection, Long, String) -> Unit,
    onConsumeMessage: () -> Unit,
) {
    var direction by rememberSaveable { mutableStateOf(CashDirection.IN) }
    var amountCents by rememberSaveable { mutableLongStateOf(0L) }
    var reason by rememberSaveable { mutableStateOf("") }

    val messageText = when (state.message) {
        null -> null
        CashViewModel.MESSAGE_DENIED -> stringResource(R.string.access_denied)
        CashViewModel.MESSAGE_REASON_REQUIRED -> stringResource(R.string.cash_error_reason)
        CashViewModel.MESSAGE_INVALID_AMOUNT -> stringResource(R.string.cash_error_amount)
        else -> state.message
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.cash_new_entry_title),
                style = MaterialTheme.typography.titleMedium,
            )

            // Direction cards (design: ＋ Add Cash (In) / − Remove Cash (Out)).
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DirectionCard(
                    selected = direction == CashDirection.IN,
                    positive = true,
                    title = stringResource(R.string.cash_in),
                    icon = { Icon(Icons.Filled.AddCircle, contentDescription = null) },
                    onClick = { direction = CashDirection.IN },
                    modifier = Modifier.weight(1f),
                )
                DirectionCard(
                    selected = direction == CashDirection.OUT,
                    positive = false,
                    title = stringResource(R.string.cash_out),
                    icon = { Icon(Icons.Filled.DoNotDisturbOn, contentDescription = null) },
                    onClick = { direction = CashDirection.OUT },
                    modifier = Modifier.weight(1f),
                )
            }

            // Cash-register amount display.
            Text(
                text = MoneyFormatter.format(amountCents),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(10, 50, 100).forEach { ringgit ->
                    OutlinedButton(onClick = { amountCents += ringgit * 100 }) {
                        Text(stringResource(R.string.payment_add_rm, ringgit))
                    }
                }
            }

            PosKeypad(
                onDigit = { amountCents = appendDigit(amountCents, it).coerceAtMost(99_999_999L) },
                onDoubleZero = { amountCents = (amountCents * 100).coerceAtMost(99_999_999L) },
                onBackspace = { amountCents /= 10 },
                modifier = Modifier.fillMaxWidth(),
                keyHeight = 48.dp,
            )

            Text(
                text = stringResource(R.string.cash_reason),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val presets = listOf(
                    R.string.cash_reason_float,
                    R.string.cash_reason_restock,
                    R.string.cash_reason_ewallet,
                    R.string.cash_reason_petty,
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
                label = { Text(stringResource(R.string.cash_reason_custom_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (messageText != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = messageText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onConsumeMessage) { Text(stringResource(R.string.action_close)) }
                }
            }

            Button(
                onClick = {
                    onRecord(direction, amountCents, reason)
                    amountCents = 0L
                    reason = ""
                },
                enabled = !state.saving && amountCents > 0 && reason.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    text = stringResource(
                        if (direction == CashDirection.IN) R.string.cash_record_in else R.string.cash_record_out,
                        MoneyFormatter.format(amountCents),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            // Cashier-in-charge footer (design attribution line).
            HorizontalDivider()
            Text(
                text = stringResource(
                    R.string.cash_cashier_in_charge,
                    state.activeUserName ?: "—",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DirectionCard(
    selected: Boolean,
    positive: Boolean,
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container =
        when {
            selected && positive -> ExtendedTheme.successContainer
            selected -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        }
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(container)
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon()
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = when {
                selected && positive -> ExtendedTheme.onSuccessContainer
                selected -> MaterialTheme.colorScheme.onErrorContainer
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CashLogRow(row: CashEntryRow, zone: ZoneId) {
    val isIn = row.entry.direction == "IN"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (isIn) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
            contentDescription = null,
            tint = if (isIn) ExtendedTheme.success else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = row.entry.reason,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    row.userName,
                    LOG_TIME.format(Instant.ofEpochMilli(row.entry.at).atZone(zone)),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = (if (isIn) "+" else "−") + MoneyFormatter.format(row.entry.amountCents),
            style = MaterialTheme.typography.titleMedium,
            color = if (isIn) ExtendedTheme.success else MaterialTheme.colorScheme.error,
        )
    }
}
