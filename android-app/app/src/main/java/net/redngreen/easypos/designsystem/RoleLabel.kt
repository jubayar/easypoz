package net.redngreen.easypos.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.redngreen.easypos.R

@Composable
fun roleLabel(role: String): String = when (role) {
    "ADMIN" -> stringResource(R.string.role_admin)
    "MANAGER" -> stringResource(R.string.role_manager)
    else -> stringResource(R.string.role_cashier)
}
