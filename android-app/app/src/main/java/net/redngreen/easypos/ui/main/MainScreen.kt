package net.redngreen.easypos.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.redngreen.easypos.R
import net.redngreen.easypos.core.model.Permission
import net.redngreen.easypos.core.model.Role
import net.redngreen.easypos.designsystem.roleLabel
import net.redngreen.easypos.feature.login.ui.LoginViewModel
import net.redngreen.easypos.feature.login.ui.model.LoginAction
import net.redngreen.easypos.ui.sell.SellScreen
import org.koin.androidx.compose.koinViewModel

/**
 * The main screen: identity strip on top (badge, switch/lock, entry points),
 * the sell terminal below (task 7.1). The sell screen IS the home screen —
 * everything else is a navigation away.
 */
@Composable
fun MainScreen(
  modifier: Modifier = Modifier,
  onOpenProducts: () -> Unit = {},
  onOpenStock: () -> Unit = {},
  onOpenCash: () -> Unit = {},
  onOpenPrinter: () -> Unit = {},
  onOpenSettings: () -> Unit = {},
  onOpenUsers: () -> Unit = {},
  onOpenActivityLog: () -> Unit = {},
  onOpenHistory: () -> Unit = {},
  onCreateProductForBarcode: (String) -> Unit = {},
  loginViewModel: LoginViewModel = koinViewModel(),
) {
  val loginState by loginViewModel.state.collectAsStateWithLifecycle()
  val activeUser = loginState.activeUser
  val role = activeUser?.role?.let { Role.fromName(it) }
  val canViewStock = role?.can(Permission.VIEW_STOCK) == true
  val canViewCash = role?.can(Permission.VIEW_ALL_CASH) == true || role?.can(Permission.VIEW_OWN_CASH) == true

  Column(modifier) {
    UserBadgeRow(
      userName = activeUser?.name,
      userRole = activeUser?.role,
      canViewStock = canViewStock,
      canViewCash = canViewCash,
      canEditCatalog = role?.can(Permission.EDIT_CATALOG) == true,
      canManageUsers = role?.can(Permission.MANAGE_USERS) == true,
      canManageSettings = role?.can(Permission.MANAGE_SETTINGS) == true,
      onSwitchUser = { loginViewModel.onAction(LoginAction.LockNow("switch user")) },
      onLock = { loginViewModel.onAction(LoginAction.LockNow("manual")) },
      onOpenProducts = onOpenProducts,
      onOpenStock = onOpenStock,
      onOpenCash = onOpenCash,
      onOpenPrinter = onOpenPrinter,
      onOpenSettings = onOpenSettings,
      onOpenUsers = onOpenUsers,
      onOpenActivityLog = onOpenActivityLog,
      onOpenHistory = onOpenHistory,
    )
    Spacer(Modifier.size(8.dp))
    SellScreen(
      onCreateProductForBarcode = onCreateProductForBarcode,
      modifier = Modifier.weight(1f),
    )
  }
}

/**
 * The always-visible identity strip: who is signed in, quick-switch, lock,
 * and role-gated entry points (affordance only — ViewModels re-check).
 */
@Composable
private fun UserBadgeRow(
  userName: String?,
  userRole: String?,
  canViewStock: Boolean,
  canViewCash: Boolean,
  canEditCatalog: Boolean,
  canManageUsers: Boolean,
  canManageSettings: Boolean,
  onSwitchUser: () -> Unit,
  onLock: () -> Unit,
  onOpenProducts: () -> Unit,
  onOpenStock: () -> Unit,
  onOpenCash: () -> Unit,
  onOpenPrinter: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenUsers: () -> Unit,
  onOpenActivityLog: () -> Unit,
  onOpenHistory: () -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }

  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(R.string.app_name),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.weight(1f),
    )
    if (canEditCatalog) {
      TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.history_title)) }
    }
    TextButton(onClick = { menuOpen = true }) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(32.dp),
      ) {}
      Spacer(Modifier.width(8.dp))
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = userName ?: stringResource(R.string.lock_title),
          style = MaterialTheme.typography.titleMedium,
        )
        userRole?.let {
          Text(
            text = roleLabel(it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
      DropdownMenuItem(
        text = { Text(stringResource(R.string.action_switch_user)) },
        onClick = { menuOpen = false; onSwitchUser() },
      )
      DropdownMenuItem(
        text = { Text(stringResource(R.string.action_lock)) },
        onClick = { menuOpen = false; onLock() },
      )
      if (canViewStock) {
        DropdownMenuItem(
          text = { Text(stringResource(R.string.nav_stock)) },
          onClick = { menuOpen = false; onOpenStock() },
        )
      }
      if (canViewCash) {
        DropdownMenuItem(
          text = { Text(stringResource(R.string.nav_cash)) },
          onClick = { menuOpen = false; onOpenCash() },
        )
      }
      if (canManageSettings) {
        DropdownMenuItem(
          text = { Text(stringResource(R.string.settings_title)) },
          onClick = { menuOpen = false; onOpenSettings() },
        )
      }
      if (canEditCatalog) {
        DropdownMenuItem(
          text = { Text(stringResource(R.string.products_title)) },
          onClick = { menuOpen = false; onOpenProducts() },
        )
      }
      if (canManageUsers) {
        DropdownMenuItem(
          text = { Text(stringResource(R.string.users_title)) },
          onClick = { menuOpen = false; onOpenUsers() },
        )
        DropdownMenuItem(
          text = { Text(stringResource(R.string.activity_log_title)) },
          onClick = { menuOpen = false; onOpenActivityLog() },
        )
      }
    }
  }
}
