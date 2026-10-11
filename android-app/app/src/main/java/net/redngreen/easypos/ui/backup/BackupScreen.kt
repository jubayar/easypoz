package net.redngreen.easypos.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.redngreen.easypos.R

/** Manual backup export + restore-from-file (task 11.3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val json = state.exportJson
        if (uri != null && json != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }
        }
        viewModel.consumeExport()
    }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            }.getOrNull()
            if (text != null) viewModel.restore(text)
        }
    }

    LaunchedEffect(state.exportJson) {
        if (state.exportJson != null) saveFile.launch("easypos-backup-${System.currentTimeMillis()}.json")
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.backup_export_hint),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = viewModel::export, enabled = !state.busy) {
                Text(stringResource(R.string.backup_export))
            }
            OutlinedButton(
                onClick = { pickFile.launch(arrayOf("application/json", "text/plain", "*/*")) },
                enabled = !state.busy,
            ) {
                Text(stringResource(R.string.backup_restore))
            }

            if (state.busy) CircularProgressIndicator()

            state.summary?.let {
                Text(
                    text = stringResource(R.string.backup_restored, it.products, it.sales, it.users),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            state.error?.let {
                Text(stringResource(R.string.backup_error, it), color = MaterialTheme.colorScheme.error)
            }
            if (state.denied) {
                Text(stringResource(R.string.access_denied), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
