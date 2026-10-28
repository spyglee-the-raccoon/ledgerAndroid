package work.spyglee.ladger.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.ui.collections.CollectionDetailScreen
import work.spyglee.ladger.ui.collections.CollectionEditorScreen
import work.spyglee.ladger.ui.collections.CollectionsScreen
import work.spyglee.ladger.ui.components.ReadableWidth
import work.spyglee.ladger.ui.main.MainScreen
import work.spyglee.ladger.ui.budgets.BudgetEditorScreen
import work.spyglee.ladger.ui.budgets.BudgetsScreen
import work.spyglee.ladger.ui.planned.PlannedTransactionEditorScreen
import work.spyglee.ladger.ui.planned.PlannedTransactionsScreen
import work.spyglee.ladger.ui.settings.CategoriesScreen
import work.spyglee.ladger.ui.settings.CategoryEditorScreen
import work.spyglee.ladger.ui.settings.SettingsScreen
import work.spyglee.ladger.ui.settings.SourceEditorScreen
import work.spyglee.ladger.ui.settings.SourcesScreen
import work.spyglee.ladger.ui.sources.SourcesOverviewScreen
import work.spyglee.ladger.ui.stats.IncomeExpenseScreen
import work.spyglee.ladger.ui.stats.PriceComparisonScreen
import work.spyglee.ladger.ui.stats.PriceHistoryScreen
import work.spyglee.ladger.ui.stats.SavingsScreen
import work.spyglee.ladger.ui.stats.TopCategoriesScreen
import work.spyglee.ladger.ui.transactions.TransactionEditorScreen
import work.spyglee.ladger.ui.transactions.TransactionFilter
import work.spyglee.ladger.ui.transactions.TransactionListScreen

private object Routes {
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val CATEGORIES = "categories"
    const val SOURCES = "sources"
    const val SOURCES_OVERVIEW = "sources_overview"
    const val COLLECTIONS = "collections"
    const val PLANNED_TRANSACTIONS = "planned_transactions"
    const val BUDGETS = "budgets"
    const val INCOME_EXPENSE = "stats/income_expense"
    const val SAVINGS = "stats/savings"
    const val TOP_CATEGORIES = "stats/top_categories"
    const val PRICE_COMPARISON = "stats/price_comparison"
    const val PRICE_HISTORY = "stats/price_history/{key}"
    const val TRANSACTION_EDITOR = "transaction?id={id}&kind={kind}&receipt={receipt}"
    const val TRANSACTION_LIST = "transactions/{filter}?sourceId={sourceId}"
    const val CATEGORY_EDITOR = "category?id={id}"
    const val SOURCE_EDITOR = "source?id={id}"
    const val COLLECTION = "collection/{id}"
    const val COLLECTION_EDITOR = "collection_editor?id={id}"
    const val PLANNED_TRANSACTION_EDITOR = "planned_transaction?id={id}"
    const val BUDGET_EDITOR = "budget?id={id}"

    fun transactionEditor(id: Long? = null, kind: TransactionKind = TransactionKind.EXPENSE, receipt: Uri? = null) =
        "transaction?id=${id ?: NO_ID}&kind=${kind.raw}" + (receipt?.let { "&receipt=${Uri.encode(it.toString())}" } ?: "")

    fun transactionList(filter: TransactionFilter) = when (filter) {
        TransactionFilter.Expenses -> "transactions/expenses"
        TransactionFilter.Incomes -> "transactions/incomes"
        TransactionFilter.Unassigned -> "transactions/unassigned"
        is TransactionFilter.Source -> "transactions/source?sourceId=${filter.sourceId}"
    }

    fun categoryEditor(id: Long? = null) = "category?id=${id ?: NO_ID}"
    fun sourceEditor(id: Long? = null) = "source?id=${id ?: NO_ID}"
    fun collection(id: Long) = "collection/$id"
    fun collectionEditor(id: Long? = null) = "collection_editor?id=${id ?: NO_ID}"
    fun plannedTransactionEditor(id: Long? = null) = "planned_transaction?id=${id ?: NO_ID}"
    fun budgetEditor(id: Long? = null) = "budget?id=${id ?: NO_ID}"

    /** [key] is a product's normalized name, which may contain any character. */
    fun priceHistory(key: String) = "stats/price_history/${Uri.encode(key)}"
}

private const val NO_ID = -1L

/**
 * A destination whose content stays a readable width on tablets and in landscape. The main screen
 * uses the full width for its side-by-side layout, and the transaction editor keeps the camera full-screen.
 */
private fun NavGraphBuilder.readableComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry -> ReadableWidth { content(entry) } }

private val optionalId = navArgument("id") {
    type = NavType.LongType
    defaultValue = NO_ID
}

private fun NavBackStackEntry.optionalId(): Long? = arguments?.getLong("id")?.takeIf { it != NO_ID }

/** [sharedReceipt] is a receipt shared from another app; the add transaction screen opens with it. */
@Composable
fun LadgerNavHost(navController: NavHostController = rememberNavController(), sharedReceipt: Uri? = null) {
    val back: () -> Unit = { navController.popBackStack() }
    val openTransaction: (Long) -> Unit = { navController.navigate(Routes.transactionEditor(it)) }
    val openCollection: (Long) -> Unit = { navController.navigate(Routes.collection(it)) }

    LaunchedEffect(sharedReceipt) {
        if (sharedReceipt != null) navController.navigate(Routes.transactionEditor(receipt = sharedReceipt))
    }

    // The screens cross-fade; without a background the window would show through halfway and flash.
    NavHost(navController, startDestination = Routes.MAIN, modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        composable(Routes.MAIN) {
            MainScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onAddTransaction = { navController.navigate(Routes.transactionEditor()) },
                onOpenSourcesOverview = { navController.navigate(Routes.SOURCES_OVERVIEW) },
                onOpenExpenses = { navController.navigate(Routes.transactionList(TransactionFilter.Expenses)) },
                onOpenIncomes = { navController.navigate(Routes.transactionList(TransactionFilter.Incomes)) },
            )
        }
        composable(
            Routes.TRANSACTION_EDITOR,
            arguments = listOf(
                optionalId,
                navArgument("kind") { type = NavType.StringType; defaultValue = TransactionKind.EXPENSE.raw },
                navArgument("receipt") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            TransactionEditorScreen(
                transactionId = entry.optionalId(),
                initialKind = TransactionKind.fromRaw(entry.arguments?.getString("kind") ?: TransactionKind.EXPENSE.raw),
                onDone = back,
                sharedReceipt = entry.arguments?.getString("receipt")?.let(Uri::parse),
            )
        }
        readableComposable(
            Routes.TRANSACTION_LIST,
            arguments = listOf(
                navArgument("filter") { type = NavType.StringType },
                navArgument("sourceId") { type = NavType.LongType; defaultValue = NO_ID },
            ),
        ) { entry ->
            val filter = when (entry.arguments?.getString("filter")) {
                "incomes" -> TransactionFilter.Incomes
                "unassigned" -> TransactionFilter.Unassigned
                "source" -> TransactionFilter.Source(entry.arguments?.getLong("sourceId") ?: NO_ID)
                else -> TransactionFilter.Expenses
            }
            TransactionListScreen(
                filter = filter,
                onBack = back,
                onOpenTransaction = openTransaction,
                onAddTransaction = { kind -> navController.navigate(Routes.transactionEditor(kind = kind)) },
                onOpenCollection = openCollection,
            )
        }
        readableComposable(Routes.SOURCES_OVERVIEW) {
            SourcesOverviewScreen(
                onBack = back,
                onOpenSource = { navController.navigate(Routes.transactionList(TransactionFilter.Source(it))) },
                onOpenUnassigned = { navController.navigate(Routes.transactionList(TransactionFilter.Unassigned)) },
                onOpenTransaction = openTransaction,
                onOpenCollection = openCollection,
            )
        }
        readableComposable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = back,
                onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                onOpenSources = { navController.navigate(Routes.SOURCES) },
                onOpenCollections = { navController.navigate(Routes.COLLECTIONS) },
                onOpenPlannedTransactions = { navController.navigate(Routes.PLANNED_TRANSACTIONS) },
                onOpenBudgets = { navController.navigate(Routes.BUDGETS) },
                onOpenIncomeExpense = { navController.navigate(Routes.INCOME_EXPENSE) },
                onOpenSavings = { navController.navigate(Routes.SAVINGS) },
                onOpenTopCategories = { navController.navigate(Routes.TOP_CATEGORIES) },
                onOpenPriceComparison = { navController.navigate(Routes.PRICE_COMPARISON) },
            )
        }
        readableComposable(Routes.INCOME_EXPENSE) {
            IncomeExpenseScreen(onBack = back)
        }
        readableComposable(Routes.SAVINGS) {
            SavingsScreen(onBack = back, onOpenTransaction = openTransaction, onOpenCollection = openCollection)
        }
        readableComposable(Routes.TOP_CATEGORIES) {
            TopCategoriesScreen(onBack = back)
        }
        readableComposable(Routes.CATEGORIES) {
            CategoriesScreen(
                onBack = back,
                onAdd = { navController.navigate(Routes.categoryEditor()) },
                onEdit = { navController.navigate(Routes.categoryEditor(it)) },
            )
        }
        readableComposable(Routes.CATEGORY_EDITOR, arguments = listOf(optionalId)) { entry ->
            CategoryEditorScreen(categoryId = entry.optionalId(), onDone = back)
        }
        readableComposable(Routes.SOURCES) {
            SourcesScreen(
                onBack = back,
                onAdd = { navController.navigate(Routes.sourceEditor()) },
                onEdit = { navController.navigate(Routes.sourceEditor(it)) },
            )
        }
        readableComposable(Routes.SOURCE_EDITOR, arguments = listOf(optionalId)) { entry ->
            SourceEditorScreen(sourceId = entry.optionalId(), onDone = back)
        }
        readableComposable(Routes.COLLECTIONS) {
            CollectionsScreen(
                onBack = back,
                onAdd = { navController.navigate(Routes.collectionEditor()) },
                onOpen = openCollection,
            )
        }
        readableComposable(Routes.COLLECTION, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: NO_ID
            CollectionDetailScreen(
                collectionId = id,
                onBack = back,
                onEdit = { navController.navigate(Routes.collectionEditor(id)) },
                onOpenTransaction = openTransaction,
                onOpenCollection = openCollection,
            )
        }
        readableComposable(Routes.COLLECTION_EDITOR, arguments = listOf(optionalId)) { entry ->
            CollectionEditorScreen(collectionId = entry.optionalId(), onDone = back)
        }
        readableComposable(Routes.PLANNED_TRANSACTIONS) {
            PlannedTransactionsScreen(
                onBack = back,
                onAdd = { navController.navigate(Routes.plannedTransactionEditor()) },
                onEdit = { navController.navigate(Routes.plannedTransactionEditor(it)) },
            )
        }
        readableComposable(Routes.PLANNED_TRANSACTION_EDITOR, arguments = listOf(optionalId)) { entry ->
            PlannedTransactionEditorScreen(plannedId = entry.optionalId(), onDone = back)
        }
        readableComposable(Routes.BUDGETS) {
            BudgetsScreen(
                onBack = back,
                onAdd = { navController.navigate(Routes.budgetEditor()) },
                onEdit = { navController.navigate(Routes.budgetEditor(it)) },
            )
        }
        readableComposable(Routes.BUDGET_EDITOR, arguments = listOf(optionalId)) { entry ->
            BudgetEditorScreen(budgetId = entry.optionalId(), onDone = back)
        }
        readableComposable(Routes.PRICE_COMPARISON) {
            PriceComparisonScreen(onBack = back, onOpenHistory = { navController.navigate(Routes.priceHistory(it)) })
        }
        readableComposable(Routes.PRICE_HISTORY, arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
            PriceHistoryScreen(key = entry.arguments?.getString("key").orEmpty(), onBack = back)
        }
    }
}
