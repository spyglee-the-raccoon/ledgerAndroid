package work.spyglee.ladger.data

import androidx.annotation.StringRes
import androidx.room.withTransaction
import work.spyglee.ladger.R

/**
 * Seeds a starter set of categories and sources on first launch so new users don't open the app
 * to empty lists. The flag keeps deleted defaults from coming back, and the emptiness checks avoid
 * duplicating data the user already created.
 */
class DefaultDataSeeder(
    private val database: LadgerDatabase,
    private val settings: SettingsRepository,
    private val getString: (Int) -> String,
) {
    suspend fun seedIfNeeded() {
        if (settings.didSeedDefaultData()) return
        database.withTransaction {
            if (database.categoryDao().count() == 0) {
                database.categoryDao().insertAll(categories.map { it.toCategory() })
            }
            if (database.sourceDao().count() == 0) {
                database.sourceDao().insertAll(sources.map { it.toSource() })
            }
        }
        settings.markDefaultDataSeeded()
    }

    private class Seed(
        @param:StringRes val name: Int,
        val systemImage: String,
        val color: CategoryColor,
        val kind: CategoryKind = CategoryKind.EXPENSE,
    )

    private fun Seed.toCategory() =
        Category(name = getString(name), systemImage = systemImage, color = color, kind = kind)

    private fun Seed.toSource() = Source(name = getString(name), systemImage = systemImage, color = color)

    private val categories = listOf(
        Seed(R.string.seed_groceries, "cart", CategoryColor.GREEN),
        Seed(R.string.seed_restaurants, "fork.knife", CategoryColor.ORANGE),
        Seed(R.string.seed_transport, "car", CategoryColor.BLUE),
        Seed(R.string.seed_housing, "house", CategoryColor.BROWN),
        Seed(R.string.seed_utilities, "lightbulb", CategoryColor.YELLOW),
        Seed(R.string.seed_health, "cross.case", CategoryColor.RED),
        Seed(R.string.seed_entertainment, "film", CategoryColor.PURPLE),
        Seed(R.string.seed_shopping, "bag", CategoryColor.PINK),
        Seed(R.string.seed_subscriptions, "tv", CategoryColor.INDIGO),
        Seed(R.string.seed_other, "questionmark.circle", CategoryColor.GRAY),
        Seed(R.string.seed_salary, "briefcase", CategoryColor.GREEN, CategoryKind.INCOME),
        Seed(R.string.seed_freelance, "laptopcomputer", CategoryColor.TEAL, CategoryKind.INCOME),
        Seed(R.string.seed_gifts, "gift", CategoryColor.PINK, CategoryKind.INCOME),
        Seed(R.string.seed_investments, "chart.line.uptrend.xyaxis", CategoryColor.MINT, CategoryKind.INCOME),
    )

    private val sources = listOf(
        Seed(R.string.seed_cash, "banknote", CategoryColor.GREEN),
        Seed(R.string.seed_bank_card, "creditcard", CategoryColor.BLUE),
        Seed(R.string.seed_savings, "building.columns", CategoryColor.INDIGO),
    )
}
