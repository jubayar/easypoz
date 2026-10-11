package net.redngreen.easypos.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.redngreen.easypos.R

/**
 * Cash-register numeric keypad shared by the payment sheet, cash entry, and
 * quick price edit (Stitch designs all show the same 3×4 grid: 1–9, 00, 0,
 * backspace). Pure input — callers own the amount state.
 */
@Composable
fun PosKeypad(
    onDigit: (Int) -> Unit,
    onDoubleZero: () -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 56.dp,
    horizontalGap: Dp = 8.dp,
    verticalGap: Dp = 8.dp,
) {
    val rows = listOf(
        listOf(1, 2, 3),
        listOf(4, 5, 6),
        listOf(7, 8, 9),
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(verticalGap)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(horizontalGap)) {
                row.forEach { digit ->
                    OutlinedButton(
                        onClick = { onDigit(digit) },
                        modifier = Modifier.weight(1f).height(keyHeight),
                    ) {
                        Text(text = digit.toString(), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(horizontalGap)) {
            OutlinedButton(
                onClick = onDoubleZero,
                modifier = Modifier.weight(1f).height(keyHeight),
            ) {
                Text(text = "00", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedButton(
                onClick = { onDigit(0) },
                modifier = Modifier.weight(1f).height(keyHeight),
            ) {
                Text(text = "0", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedButton(
                onClick = onBackspace,
                modifier = Modifier.weight(1f).height(keyHeight),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = stringResource(R.string.keypad_backspace),
                )
            }
        }
    }
}

/** Digits typed append as sen (2050 → RM 20.50); backspace drops the last digit. */
fun appendDigit(cents: Long, digit: Int): Long = cents * 10 + digit
