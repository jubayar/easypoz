package net.redngreen.easypos.ui.settings

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.redngreen.easypos.R
import net.redngreen.easypos.core.database.entity.PaymentType

/** Settings hub (tasks 11.1 + 11.5): profile, branding + preview, toggles, payment types, QR, language. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPrinter: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSync: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load() }

    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.setLogo(readBase64(context, it)) }
    }
    val pickQr = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.setQr(readBase64(context, it)) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.denied) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.access_denied), color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ─── Shop profile ───────────────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_shop_profile))
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.settings_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.address,
                onValueChange = viewModel::setAddress,
                label = { Text(stringResource(R.string.settings_address)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.phone,
                onValueChange = viewModel::setPhone,
                label = { Text(stringResource(R.string.settings_phone)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ImageRow(
                label = stringResource(R.string.settings_logo),
                base64 = state.logoBase64,
                onPick = { pickLogo.launch("image/*") },
                onRemove = { viewModel.setLogo(null) },
            )
            SaveButton(onClick = viewModel::saveProfile, enabled = !state.saving)

            HorizontalDivider()

            // ─── Receipt branding ───────────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_receipt_branding))
            OutlinedTextField(
                value = state.header,
                onValueChange = viewModel::setHeader,
                label = { Text(stringResource(R.string.settings_receipt_header)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.footer,
                onValueChange = viewModel::setFooter,
                label = { Text(stringResource(R.string.settings_receipt_footer)) },
                modifier = Modifier.fillMaxWidth(),
            )
            ReceiptPreview(shopName = state.name, header = state.header, footer = state.footer)
            SaveButton(onClick = viewModel::saveBranding, enabled = !state.saving)

            HorizontalDivider()

            // ─── Idle lock + drawer kick ────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_security))
            OutlinedTextField(
                value = state.idleMinutes,
                onValueChange = viewModel::setIdleMinutes,
                label = { Text(stringResource(R.string.settings_idle_lock)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(220.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.printer_drawer_kick),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.drawerKick, onCheckedChange = viewModel::setDrawerKick)
            }
            SaveButton(onClick = viewModel::saveToggles, enabled = !state.saving)

            HorizontalDivider()

            // ─── Language ───────────────────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_language))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.language == "en",
                    onClick = { viewModel.setLanguage("en") },
                    label = { Text(stringResource(R.string.settings_language_english)) },
                )
                FilterChip(
                    selected = state.language == "ms",
                    onClick = { viewModel.setLanguage("ms") },
                    label = { Text(stringResource(R.string.settings_language_bahasa)) },
                )
            }

            HorizontalDivider()

            // ─── Payment types (inline manager) ─────────────────────────────
            PaymentTypesSection(state, viewModel)

            HorizontalDivider()

            // ─── Merchant QR ────────────────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_merchant_qr))
            ImageRow(
                label = stringResource(R.string.payment_merchant_qr),
                base64 = state.qrBase64,
                onPick = { pickQr.launch("image/*") },
                onRemove = { viewModel.setQr(null) },
            )
            SaveButton(onClick = viewModel::saveQr, enabled = !state.saving)

            HorizontalDivider()

            OutlinedButton(onClick = onOpenPrinter, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.printer_title))
            }
            OutlinedButton(onClick = onOpenSync, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sync_title))
            }
            OutlinedButton(onClick = onOpenBackup, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_backup))
            }

            state.message?.let { msg ->
                Text(
                    text = when (msg) {
                        SettingsViewModel.MESSAGE_DENIED -> stringResource(R.string.access_denied)
                        SettingsViewModel.MESSAGE_NAME_REQUIRED -> stringResource(R.string.settings_error_name)
                        else -> msg
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.saved) {
                Text(stringResource(R.string.settings_saved), color = MaterialTheme.colorScheme.primary)
            }
            if (state.saving) CircularProgressIndicator(Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SaveButton(onClick: () -> Unit, enabled: Boolean) {
    Button(onClick = onClick, enabled = enabled) { Text(stringResource(R.string.settings_save)) }
}

@Composable
private fun ImageRow(
    label: String,
    base64: String?,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ImagePreview(base64 = base64)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onPick) { Text(stringResource(R.string.settings_choose_image)) }
        if (base64 != null) {
            TextButton(onClick = onRemove) { Text(stringResource(R.string.settings_remove_image)) }
        }
    }
}

@Composable
private fun ImagePreview(base64: String?, size: Dp = 64.dp) {
    if (base64 == null) {
        Box(Modifier.size(size)) {}
        return
    }
    val bitmap = remember(base64) {
        runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    Box(
        Modifier.size(size).border(1.dp, MaterialTheme.colorScheme.outline),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(size),
            )
        }
    }
}

@Composable
private fun ReceiptPreview(shopName: String, header: String, footer: String) {
    Text(stringResource(R.string.settings_preview), style = MaterialTheme.typography.labelLarge)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            MonoText(shopName.ifBlank { "EasyPOS" }, align = TextAlign.Center, bold = true)
            if (header.isNotBlank()) MonoText(header, align = TextAlign.Center)
            MonoText("-".repeat(28))
            MonoText("Milo 1kg")
            MonoRow("  2 x 12.00", "24.00")
            MonoText("-".repeat(28))
            MonoRow("TOTAL", "24.00", bold = true)
            if (footer.isNotBlank()) MonoText(footer, align = TextAlign.Center)
        }
    }
}

@Composable
private fun MonoText(text: String, align: TextAlign = TextAlign.Start, bold: Boolean = false) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        textAlign = align,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MonoRow(left: String, right: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        MonoText(left, bold = bold)
        Text(
            text = right,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun PaymentTypesSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PaymentType?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(stringResource(R.string.payment_types_title))
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { showAdd = true }) { Text(stringResource(R.string.payment_add)) }
    }
    state.paymentTypes.forEachIndexed { index, type ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                TextButton(onClick = { renameTarget = type }) {
                    Text(
                        text = type.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Text(
                    text = stringResource(
                        if (type.kind == "CASH") R.string.payment_kind_cash else R.string.payment_kind_other,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            if (index > 0) {
                TextButton(onClick = { viewModel.movePaymentType(type.id, up = true) }) { Text("↑") }
            }
            if (index < state.paymentTypes.size - 1) {
                TextButton(onClick = { viewModel.movePaymentType(type.id, up = false) }) { Text("↓") }
            }
            Switch(checked = type.active, onCheckedChange = { viewModel.setPaymentTypeActive(type.id, it) })
        }
    }

    if (showAdd) {
        PaymentTypeDialog(
            title = stringResource(R.string.payment_add),
            onConfirm = { name, kind ->
                viewModel.addPaymentType(name, kind)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
    renameTarget?.let { target ->
        PaymentTypeDialog(
            title = stringResource(R.string.action_rename),
            initialName = target.name,
            initialKind = target.kind,
            onConfirm = { name, _ ->
                viewModel.renamePaymentType(target.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
}

@Composable
private fun PaymentTypeDialog(
    title: String,
    initialName: String = "",
    initialKind: String = "OTHER",
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var kind by remember { mutableStateOf(initialKind) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.payment_name)) },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == "CASH",
                        onClick = { kind = "CASH" },
                        label = { Text(stringResource(R.string.payment_kind_cash)) },
                    )
                    FilterChip(
                        selected = kind == "OTHER",
                        onClick = { kind = "OTHER" },
                        label = { Text(stringResource(R.string.payment_kind_other)) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name, kind) }) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) } },
    )
}

/** Reads a picked image and returns it base64 (NO_WRAP) for local storage. */
private fun readBase64(context: Context, uri: Uri): String? {
    val bytes = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: return null
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}
