package net.redngreen.easypos

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import net.redngreen.easypos.ui.activity.ActivityLogScreen
import net.redngreen.easypos.ui.backup.BackupScreen
import net.redngreen.easypos.ui.cash.CashScreen
import net.redngreen.easypos.feature.catalog.ui.CategoriesRoot
import net.redngreen.easypos.feature.catalog.ui.CsvImportRoot
import net.redngreen.easypos.feature.catalog.ui.ProductEditRoot
import net.redngreen.easypos.feature.catalog.ui.ProductsRoot
import net.redngreen.easypos.ui.history.SalesHistoryScreen
import net.redngreen.easypos.ui.main.AppShell
import net.redngreen.easypos.ui.main.MainScreen
import net.redngreen.easypos.ui.settings.PrinterSetupScreen
import net.redngreen.easypos.ui.settings.SettingsScreen
import net.redngreen.easypos.ui.stock.StockScreen
import net.redngreen.easypos.ui.users.UserEditScreen
import net.redngreen.easypos.ui.users.UsersScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  // Top-level switch: clear + add in one lambda = one snapshot commit, so the
  // stack is never observed empty (NavDisplay requires a non-empty stack).
  fun openTopLevel(key: NavKey) {
    backStack.clear()
    backStack.add(key)
  }

  // The shell owns system-bar insets; screens inside the slot stay clear of them.
  Row(Modifier.safeDrawingPadding()) {
    AppShell(
      current = backStack.lastOrNull(),
      onOpen = ::openTopLevel,
    ) {
      NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider =
          entryProvider {
            entry<Main> {
              MainScreen(
                onOpenUsers = { backStack.add(Users) },
                onOpenActivityLog = { backStack.add(ActivityLog) },
                onOpenProducts = { backStack.add(Products) },
                onOpenStock = { backStack.add(Stock) },
                onOpenCash = { backStack.add(Cash) },
                onOpenPrinter = { backStack.add(Printer) },
                onOpenSettings = { backStack.add(Settings) },
                onOpenHistory = { backStack.add(SalesHistory) },
                onCreateProductForBarcode = { barcode ->
                  backStack.add(ProductEdit(productId = null, prefillBarcode = barcode))
                },
                modifier = Modifier.padding(16.dp),
              )
            }
            entry<DaySummary> {
              net.redngreen.easypos.ui.reports.DaySummaryScreen(
                onBack = { openTopLevel(Main) },
              )
            }
        entry<Users> {
          UsersScreen(
            onBack = { backStack.removeLastOrNull() },
            onEditUser = { userId -> backStack.add(UserEdit(userId)) },
          )
        }
        entry<UserEdit> { key ->
          UserEditScreen(
            userId = key.userId,
            onDone = { backStack.removeLastOrNull() },
          )
        }
        entry<ActivityLog> {
          ActivityLogScreen(onBack = { backStack.removeLastOrNull() })
        }
        entry<Products> {
          ProductsRoot(
            onBack = { backStack.removeLastOrNull() },
            onEditProduct = { productId -> backStack.add(ProductEdit(productId)) },
            onOpenCategories = { backStack.add(Categories) },
            onOpenImport = { backStack.add(CsvImport) },
          )
        }
        entry<ProductEdit> { key ->
          ProductEditRoot(
            productId = key.productId,
            prefillBarcode = key.prefillBarcode,
            onDone = { backStack.removeLastOrNull() },
          )
        }
        entry<Categories> {
          CategoriesRoot(onBack = { backStack.removeLastOrNull() })
        }
        entry<CsvImport> {
          CsvImportRoot(onBack = { backStack.removeLastOrNull() })
        }
        entry<SalesHistory> {
          SalesHistoryScreen(onBack = { backStack.removeLastOrNull() })
        }
        entry<Stock> {
          StockScreen(
            onBack = { backStack.removeLastOrNull() },
            onEditProduct = { productId -> backStack.add(ProductEdit(productId)) },
          )
        }
        entry<Cash> {
          CashScreen(onBack = { backStack.removeLastOrNull() })
        }
        entry<Printer> {
          PrinterSetupScreen(onBack = { backStack.removeLastOrNull() })
        }
        entry<Settings> {
          SettingsScreen(
            onBack = { backStack.removeLastOrNull() },
            onOpenPrinter = { backStack.add(Printer) },
            onOpenBackup = { backStack.add(Backup) },
            onOpenSync = { backStack.add(Sync) },
          )
        }
        entry<Backup> {
          BackupScreen(onBack = { backStack.removeLastOrNull() })
        }
        entry<Sync> {
          net.redngreen.easypos.ui.sync.SyncScreen(onBack = { backStack.removeLastOrNull() })
        }
          },
      )
    }
  }
}
