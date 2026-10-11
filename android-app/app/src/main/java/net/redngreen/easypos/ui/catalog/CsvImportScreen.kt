package net.redngreen.easypos.ui.catalog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.redngreen.easypos.R
import net.redngreen.easypos.core.data.repo.CatalogRepository
import net.redngreen.easypos.ui.catalog.CsvImportViewModel.UiState

/**
 * CSV import flow (task 6.4): choose file → parse preview → apply →
 * per-row report with reasons and retry of just the failed rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CsvImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) viewModel.onFilePicked(bytes)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.csv_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            when (val s = state) {
                is UiState.Idle -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        text = stringResource(R.string.csv_import_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth(0.7f),
                    )
                    Button(onClick = {
                        pickFile.launch(
                            arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain")
                        )
                    }) {
                        Text(stringResource(R.string.csv_import_pick))
                    }
                }

                is UiState.Parsing, is UiState.Importing -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                is UiState.Error -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        text = if (s.message == CsvImportViewModel.DENIED) {
                            stringResource(R.string.access_denied)
                        } else {
                            stringResource(R.string.csv_import_format_error, s.message)
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(0.7f),
                    )
                    OutlinedButton(onClick = viewModel::reset) {
                        Text(stringResource(R.string.action_back))
                    }
                }

                is UiState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(
                            R.string.csv_import_preview,
                            s.parsed.rows.size,
                            s.parsed.failures.size,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (s.parsed.failures.isNotEmpty()) {
                        LazyColumn(Modifier.weight(0.4f, fill = false)) {
                            items(s.parsed.failures.size) { index ->
                                val failure = s.parsed.failures[index]
                                Text(
                                    text = stringResource(
                                        R.string.csv_import_row_failure,
                                        failure.rowNumber,
                                        failure.reason,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = viewModel::import, enabled = s.parsed.rows.isNotEmpty()) {
                            Text(
                                stringResource(
                                    R.string.csv_import_apply,
                                    s.parsed.rows.size,
                                )
                            )
                        }
                        OutlinedButton(onClick = viewModel::reset) {
                            Text(stringResource(R.string.csv_import_choose_other))
                        }
                    }
                }

                is UiState.Done -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val summary = s.summary
                    Text(
                        text = stringResource(
                            R.string.csv_import_summary,
                            summary.created,
                            summary.updated,
                            summary.failed,
                        ),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (summary.failed > 0) {
                        Text(
                            text = stringResource(R.string.csv_import_failed_rows),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        LazyColumn(
                            Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(summary.outcomes.size) { index ->
                                val outcome = summary.outcomes[index]
                                if (outcome.status == CatalogRepository.ImportStatus.FAILED) {
                                    Card(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(8.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                AssistChip(onClick = {}, label = { Text("#${outcome.rowNumber}") })
                                                Text(
                                                    text = outcome.productName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                                                )
                                            }
                                            Text(
                                                text = outcome.reason ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Button(onClick = viewModel::retryFailed) {
                            Text(stringResource(R.string.csv_import_retry_failed))
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.csv_import_all_done),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    OutlinedButton(onClick = viewModel::reset) {
                        Text(stringResource(R.string.csv_import_another))
                    }
                }
            }
        }
    }
}
