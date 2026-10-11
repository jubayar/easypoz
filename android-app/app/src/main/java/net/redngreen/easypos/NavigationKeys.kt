package net.redngreen.easypos

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data object Users : NavKey

@Serializable data class UserEdit(val userId: String?) : NavKey

@Serializable data object ActivityLog : NavKey

@Serializable data object Products : NavKey

@Serializable data class ProductEdit(
    val productId: String?,
    val prefillBarcode: String? = null,
) : NavKey

@Serializable data object SalesHistory : NavKey

@Serializable data object DaySummary : NavKey

@Serializable data object Stock : NavKey

@Serializable data object Cash : NavKey

@Serializable data object Printer : NavKey

@Serializable data object Settings : NavKey

@Serializable data object Backup : NavKey

@Serializable data object Sync : NavKey

@Serializable data object Categories : NavKey

@Serializable data object CsvImport : NavKey
