package net.redngreen.easypos.ui.lock

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.redngreen.easypos.R
import net.redngreen.easypos.core.security.PinHasher
import net.redngreen.easypos.ui.session.SessionViewModel

/**
 * Full-screen lock overlay (user-access-control spec). Unlocking, re-unlock
 * after idle, and quick user switching are the same gesture: tap your name,
 * enter your PIN. App state underneath (open carts, screens) is never
 * destroyed — this composable merely covers it.
 *
 * First run (no users yet): shows the bootstrap Admin form instead.
 */
@Composable
fun LockScreen(
    modifier: Modifier = Modifier,
    viewModel: SessionViewModel = hiltViewModel(),
    shellViewModel: net.redngreen.easypos.ui.main.ShellViewModel = hiltViewModel(),
) {
    val hasUnlockableUser by viewModel.hasUnlockableUser.collectAsStateWithLifecycle()

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (hasUnlockableUser) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            true -> UnlockContent(viewModel, shellViewModel)
            false -> FirstRunAdminContent(viewModel)
        }
    }
}

@Composable
private fun UnlockContent(
    viewModel: SessionViewModel,
    shellViewModel: net.redngreen.easypos.ui.main.ShellViewModel,
) {
    val users by viewModel.unlockableUsers.collectAsStateWithLifecycle()
    val shopName by shellViewModel.shopName.collectAsStateWithLifecycle()
    var selectedUserId by rememberSaveable { mutableStateOf<String?>(null) }
    var pin by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }

    // Drop the selection if the user disappeared (deactivated while locked).
    val selected = users.firstOrNull { it.id == selectedUserId }
    LaunchedEffect(users) {
        if (selectedUserId != null && selected == null) {
            selectedUserId = null
            pin = ""
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        // Who is unlocking — big tiles for counter use.
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.app_name).take(1),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Column {
                    Text(
                        text = stringResource(R.string.app_name) + (shopName.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.lock_pick_profile),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(users.size) { index ->
                    val user = users[index]
                    UserTile(
                        name = user.name,
                        role = user.role,
                        selected = user.id == selectedUserId,
                        onClick = {
                            selectedUserId = user.id
                            pin = ""
                            error = false
                        },
                    )
                }
            }
        }

        // The PIN pad — only once a user is picked.
        if (selected != null) {
            PinPad(
                userName = selected.name,
                pin = pin,
                error = error,
                onDigit = { digit ->
                    if (pin.length < PinHasher.PIN_MAX_LENGTH) {
                        pin += digit
                        error = false
                    }
                },
                onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1); error = false },
                onSubmit = {
                    if (pin.length >= PinHasher.PIN_MIN_LENGTH) {
                        val attemptUserId = selected.id
                        val attemptPin = pin
                        viewModel.unlock(attemptUserId, attemptPin) { ok ->
                            if (!ok) {
                                pin = ""
                                error = true
                            }
                        }
                    } else {
                        error = true
                    }
                },
                onCancel = {
                    selectedUserId = null
                    pin = ""
                    error = false
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserTile(
    name: String,
    role: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val roleLabel = roleLabel(role)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                border =
                    if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    else null,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = roleLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = roleIcon(role),
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Design: shield for privileged roles, person for cashiers. */
private fun roleIcon(role: String) = when (role) {
    "ADMIN" -> Icons.Filled.AdminPanelSettings
    "MANAGER" -> Icons.Filled.Security
    else -> Icons.Filled.Person
}

@Composable
private fun PinPad(
    userName: String,
    pin: String,
    error: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.lock_enter_pin, userName),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        PinDots(count = pin.length, error = error)
        Text(
            text = if (error) stringResource(R.string.lock_wrong_pin) else "",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫"),
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { key ->
                    when {
                        key.isEmpty() -> Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.size(88.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        key == "⌫" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = onBackspace, modifier = Modifier.size(88.dp)) {
                                Text(text = key, style = MaterialTheme.typography.headlineSmall)
                            }
                            Text(
                                text = stringResource(R.string.lock_backspace),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> OutlinedButton(
                            onClick = { onDigit(key) },
                            modifier = Modifier.size(88.dp),
                        ) {
                            Text(text = key, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.lock_cancel)) }
            Button(onClick = onSubmit, enabled = pin.length >= PinHasher.PIN_MIN_LENGTH) {
                Text(stringResource(R.string.lock_unlock))
            }
        }
        Spacer(Modifier.height(16.dp))
        // Design footer: the shop-floor PIN recovery line.
        Text(
            text = stringResource(R.string.lock_forgot_pin),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PinDots(count: Int, error: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(PinHasher.PIN_MAX_LENGTH) { index ->
            val filled = index < count
            Surface(
                shape = CircleShape,
                color = when {
                    error -> MaterialTheme.colorScheme.error
                    filled -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                modifier = Modifier.size(16.dp),
            ) {}
        }
    }
}

/** Bootstrap: no users exist yet — create the first Admin (feeds 11.2 wizard later). */
@Composable
private fun FirstRunAdminContent(viewModel: SessionViewModel) {
    val pinHasher = remember { PinHasher() }
    var name by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = false },
            label = { Text(stringResource(R.string.setup_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.6f),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(PinHasher.PIN_MAX_LENGTH); error = false },
            label = { Text(stringResource(R.string.setup_pin_hint)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = error,
            supportingText = if (error) {
                { Text(stringResource(R.string.error_pin_invalid)) }
            } else null,
            modifier = Modifier.fillMaxWidth(0.6f),
        )
        Spacer(Modifier.height(24.dp))
        if (saving) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    if (name.isBlank() || !pinHasher.isValidPinFormat(pin)) {
                        error = true
                    } else {
                        saving = true
                        viewModel.createFirstAdmin(name, pin) { ok ->
                            saving = false
                            if (!ok) error = true
                        }
                    }
                },
                enabled = name.isNotBlank() && pin.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(0.6f),
            ) {
                Text(stringResource(R.string.setup_create))
            }
        }
    }
}

@Composable
fun roleLabel(role: String): String = when (role) {
    "ADMIN" -> stringResource(R.string.role_admin)
    "MANAGER" -> stringResource(R.string.role_manager)
    else -> stringResource(R.string.role_cashier)
}
