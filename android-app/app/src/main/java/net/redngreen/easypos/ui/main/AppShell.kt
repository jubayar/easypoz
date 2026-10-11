package net.redngreen.easypos.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import net.redngreen.easypos.R
import net.redngreen.easypos.core.data.sync.SyncPhase
import net.redngreen.easypos.core.data.sync.SyncStatus
import net.redngreen.easypos.core.model.Permission
import net.redngreen.easypos.core.model.Role
import net.redngreen.easypos.theme.ExtendedTheme
import net.redngreen.easypos.ui.lock.roleLabel
import net.redngreen.easypos.ui.session.SessionViewModel

/** Sidebar + content slot — the persistent shell every screen renders in. */
@Composable
fun AppShell(
    current: NavKey?,
    onOpen: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    sessionViewModel: SessionViewModel = hiltViewModel(),
    shellViewModel: ShellViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val activeUser by sessionViewModel.activeUser.collectAsStateWithLifecycle()
    val shopName by shellViewModel.shopName.collectAsStateWithLifecycle()
    val syncStatus by shellViewModel.syncStatus.collectAsStateWithLifecycle()

    val role = activeUser?.role?.let { Role.fromName(it) }
    val canViewStock = role?.can(Permission.VIEW_STOCK) == true
    val canViewCash =
        role?.can(Permission.VIEW_ALL_CASH) == true || role?.can(Permission.VIEW_OWN_CASH) == true
    val canEditCatalog = role?.can(Permission.EDIT_CATALOG) == true
    val canManageUsers = role?.can(Permission.MANAGE_USERS) == true
    val canManageSettings = role?.can(Permission.MANAGE_SETTINGS) == true

    val primaryItems =
        buildList {
            add(SidebarItem.Main)
            if (canViewCash) add(SidebarItem.Cash)
            if (canViewStock) add(SidebarItem.Stock)
            if (canViewCash) add(SidebarItem.DaySummary)
            add(SidebarItem.History)
            if (canEditCatalog) add(SidebarItem.Products)
        }
    val adminItems =
        buildList {
            if (canManageUsers) add(SidebarItem.Users)
            if (canManageUsers) add(SidebarItem.ActivityLog)
            if (canManageSettings) add(SidebarItem.Settings)
        }

    Row(modifier) {
        Sidebar(
            shopName = shopName,
            userName = activeUser?.name,
            userRole = activeUser?.role,
            syncStatus = syncStatus,
            primaryItems = primaryItems,
            adminItems = adminItems,
            current = current,
            onOpen = onOpen,
            onLock = { sessionViewModel.lockNow("manual") },
            modifier = Modifier.width(224.dp),
        )
        HorizontalDivider(Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun Sidebar(
    shopName: String,
    userName: String?,
    userRole: String?,
    syncStatus: SyncStatus,
    primaryItems: List<SidebarItem>,
    adminItems: List<SidebarItem>,
    current: NavKey?,
    onOpen: (NavKey) -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
            // Brand + shop identity
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (shopName.isNotBlank()) {
                Text(
                    text = shopName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(16.dp))

            primaryItems.forEach { item -> SidebarRow(item, current == item.key, onOpen) }

            if (adminItems.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                adminItems.forEach { item -> SidebarRow(item, current == item.key, onOpen) }
            }

            Spacer(Modifier.weight(1f))

            SyncChip(status = syncStatus, onClick = { onOpen(net.redngreen.easypos.Sync) })
            Spacer(Modifier.height(8.dp))
            UserCard(userName = userName, userRole = userRole, onLock = onLock)
        }
    }
}

@Composable
private fun SidebarRow(item: SidebarItem, selected: Boolean, onOpen: (NavKey) -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable { onOpen(item.key) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = stringResource(item.labelRes),
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Honest, always-visible sync state (data-sync spec, task 12.4): synced /
 * pending / syncing / offline-with-count / error, plus a "set up" nudge when
 * no account is linked. Tap opens the sync screen.
 */
@Composable
private fun SyncChip(status: SyncStatus, onClick: () -> Unit) {
    val container: Color
    val content: Color
    val icon: ImageVector
    val text: String
    when {
        !status.signedIn -> {
            container = MaterialTheme.colorScheme.surfaceVariant
            content = MaterialTheme.colorScheme.onSurfaceVariant
            icon = Icons.Filled.CloudOff
            text = stringResource(R.string.sync_setup)
        }
        status.phase == SyncPhase.SYNCING -> {
            container = MaterialTheme.colorScheme.primaryContainer
            content = MaterialTheme.colorScheme.onPrimaryContainer
            icon = Icons.Filled.CloudSync
            text = stringResource(R.string.sync_syncing)
        }
        status.phase == SyncPhase.ERROR -> {
            container = MaterialTheme.colorScheme.errorContainer
            content = MaterialTheme.colorScheme.onErrorContainer
            icon = Icons.Filled.CloudOff
            text = stringResource(R.string.sync_error)
        }
        status.phase == SyncPhase.OFFLINE -> {
            container = ExtendedTheme.warningContainer
            content = ExtendedTheme.onWarningContainer
            icon = Icons.Filled.CloudOff
            text = stringResource(R.string.sync_offline_pending, status.pending)
        }
        status.phase == SyncPhase.PENDING -> {
            container = ExtendedTheme.warningContainer
            content = ExtendedTheme.onWarningContainer
            icon = Icons.Filled.CloudUpload
            text = stringResource(R.string.sync_pending, status.pending)
        }
        else -> {
            container = ExtendedTheme.successContainer
            content = ExtendedTheme.onSuccessContainer
            icon = Icons.Filled.CloudDone
            text = stringResource(R.string.sync_synced)
        }
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = container,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UserCard(userName: String?, userRole: String?, onLock: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                Text(
                    text = userName?.take(1)?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = userName ?: stringResource(R.string.lock_title),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            userRole?.let {
                Text(
                    text = roleLabel(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onLock) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = stringResource(R.string.action_lock),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val SidebarItem.key: NavKey
    get() = when (this) {
        SidebarItem.Main -> net.redngreen.easypos.Main
        SidebarItem.Cash -> net.redngreen.easypos.Cash
        SidebarItem.Stock -> net.redngreen.easypos.Stock
        SidebarItem.DaySummary -> net.redngreen.easypos.DaySummary
        SidebarItem.History -> net.redngreen.easypos.SalesHistory
        SidebarItem.Products -> net.redngreen.easypos.Products
        SidebarItem.Users -> net.redngreen.easypos.Users
        SidebarItem.ActivityLog -> net.redngreen.easypos.ActivityLog
        SidebarItem.Settings -> net.redngreen.easypos.Settings
    }

private val SidebarItem.labelRes: Int
    get() = when (this) {
        SidebarItem.Main -> R.string.nav_sell
        SidebarItem.Cash -> R.string.nav_cash
        SidebarItem.Stock -> R.string.nav_stock
        SidebarItem.DaySummary -> R.string.nav_day_summary
        SidebarItem.History -> R.string.nav_history
        SidebarItem.Products -> R.string.nav_products
        SidebarItem.Users -> R.string.users_title
        SidebarItem.ActivityLog -> R.string.activity_log_title
        SidebarItem.Settings -> R.string.settings_title
    }

private val SidebarItem.icon: ImageVector
    get() = when (this) {
        SidebarItem.Main -> Icons.Filled.PointOfSale
        SidebarItem.Cash -> Icons.Filled.AccountBalanceWallet
        SidebarItem.Stock -> Icons.Filled.Inventory2
        SidebarItem.DaySummary -> Icons.Filled.CalendarToday
        SidebarItem.History -> Icons.Filled.History
        SidebarItem.Products -> Icons.Filled.Storefront
        SidebarItem.Users -> Icons.Filled.ManageAccounts
        SidebarItem.ActivityLog -> Icons.Filled.ReceiptLong
        SidebarItem.Settings -> Icons.Filled.Settings
    }

private enum class SidebarItem {
    Main,
    Cash,
    Stock,
    DaySummary,
    History,
    Products,
    Users,
    ActivityLog,
    Settings,
}
