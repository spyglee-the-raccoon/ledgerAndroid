package work.spyglee.ladger.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import work.spyglee.ladger.R
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.DaySection
import work.spyglee.ladger.ui.dayTitle
import work.spyglee.ladger.ui.formatMoney
import work.spyglee.ladger.ui.formatTime
import work.spyglee.ladger.ui.theme.ExpenseColor
import work.spyglee.ladger.ui.theme.IncomeColor
import work.spyglee.ladger.ui.theme.iconFor
import work.spyglee.ladger.ui.theme.toColor

/** Icon, title and color that represent a transaction: its category, "Transfer" or "Uncategorized". */
@Composable
fun TransactionBadge(transaction: Transaction, category: Category?) {
    when {
        transaction.kind == TransactionKind.TRANSFER ->
            CategoryIcon(Icons.Outlined.SyncAlt, CategoryColor.GRAY.toColor())
        category != null -> CategoryIcon(iconFor(category.systemImage), category.color.toColor())
        else -> CategoryIcon(Icons.Outlined.QuestionMark, CategoryColor.GRAY.toColor())
    }
}

@Composable
fun transactionTitle(transaction: Transaction, category: Category?): String = when {
    transaction.kind == TransactionKind.TRANSFER -> stringResource(R.string.transfer)
    category != null -> category.name
    else -> stringResource(R.string.uncategorized)
}

/**
 * One transaction: category badge, title and note, amount colored by kind, and the time.
 * Tapping opens it; a long press offers "Add to collection" when [onAddToCollection] is set.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    transaction: Transaction,
    category: Category?,
    onClick: () -> Unit,
    onAddToCollection: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onAddToCollection?.let { { menuOpen = true } })
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()
            TransactionBadge(transaction, category)
            Column(Modifier.weight(1f)) {
                Text(transactionTitle(transaction, category), style = MaterialTheme.typography.bodyLarge)
                if (transaction.note.isNotEmpty()) {
                    Text(
                        transaction.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                formatMoney(transaction.amount),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = when (transaction.kind) {
                    TransactionKind.EXPENSE -> ExpenseColor
                    TransactionKind.INCOME -> IncomeColor
                    TransactionKind.TRANSFER -> MaterialTheme.colorScheme.onSurface
                },
            )
            Text(formatTime(transaction.date), style = MaterialTheme.typography.bodyLarge)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_to_collection)) },
                leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onAddToCollection?.invoke()
                },
            )
        }
    }
}

/**
 * Day-grouped transaction list items: a "Today"/"Yesterday"/date header per day, then its rows.
 * Rows can be swiped away when [onSwipe] is set.
 */
fun LazyListScope.transactionSections(
    sections: List<DaySection>,
    categories: Map<Long, Category>,
    onClick: (Transaction) -> Unit,
    onSwipe: ((Transaction) -> Unit)? = null,
    onAddToCollection: ((Transaction) -> Unit)? = null,
) {
    sections.forEach { section ->
        item(key = "day-${section.day}") {
            SectionHeader(dayTitle(section.day), Modifier.padding(top = 8.dp))
        }
        items(section.items, key = { "transaction-${it.id}" }) { transaction ->
            val row = @Composable {
                TransactionRow(
                    transaction = transaction,
                    category = transaction.categoryId?.let(categories::get),
                    onClick = { onClick(transaction) },
                    onAddToCollection = onAddToCollection?.let { add -> { add(transaction) } },
                )
            }
            if (onSwipe != null) SwipeToDelete(onDelete = { onSwipe(transaction) }) { row() } else row()
        }
    }
}
