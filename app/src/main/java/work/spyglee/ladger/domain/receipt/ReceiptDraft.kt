package work.spyglee.ladger.domain.receipt

import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryKind
import work.spyglee.ladger.data.MerchantCategory
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.TransactionDraft

/**
 * Fills the form from a scanned receipt, as iOS does: the transaction becomes an expense; the
 * category and source learned for this merchant win over the model's category guess; the total
 * replaces the amount; and the merchant becomes the note unless one was typed. As on iOS, the
 * receipt's date is not used: new transactions are dated now.
 */
fun TransactionDraft.withReceipt(receipt: ParsedReceipt, learned: MerchantCategory?, categories: List<Category>): TransactionDraft {
    var draft = withKind(TransactionKind.EXPENSE, categories)
    learned?.sourceId?.let { draft = draft.copy(sourceId = it) }

    val expenseCategories = categories.filter { it.kind == CategoryKind.EXPENSE }
    val category = expenseCategories.firstOrNull { it.id == learned?.categoryId }
        ?: receipt.categoryName?.let { name -> expenseCategories.firstOrNull { it.name == name } }
    category?.let { draft = draft.copy(categoryId = it.id) }

    receipt.total?.let { draft = draft.copy(amount = it) }
    if (receipt.merchant != null && draft.note.isEmpty()) draft = draft.copy(note = receipt.merchant)
    return draft
}
