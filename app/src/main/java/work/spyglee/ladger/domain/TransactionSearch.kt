package work.spyglee.ladger.domain

import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.ReceiptItemName
import work.spyglee.ladger.data.Transaction

/**
 * Note, category name or the name of an item bought on the scanned receipt containing [query],
 * ignoring case and diacritics, as iOS `Transaction.matches` does. [itemNames] is by transaction id.
 */
fun Transaction.matches(query: String, categories: Map<Long, Category>, itemNames: Map<Long, List<String>>): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    return note.containsFolded(trimmed) ||
        categoryId?.let(categories::get)?.name?.containsFolded(trimmed) == true ||
        itemNames[id].orEmpty().any { it.containsFolded(trimmed) }
}

fun List<ReceiptItemName>.byTransaction(): Map<Long, List<String>> = groupBy({ it.transactionId }, { it.name })
