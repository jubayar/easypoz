package net.redngreen.easypos.feature.catalog.ui.model

sealed interface CsvImportAction {
    data class FilePicked(val bytes: ByteArray) : CsvImportAction

    data object Import : CsvImportAction

    data object RetryFailed : CsvImportAction

    data object Reset : CsvImportAction
}
