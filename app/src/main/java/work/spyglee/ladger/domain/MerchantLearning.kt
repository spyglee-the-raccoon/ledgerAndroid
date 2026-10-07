package work.spyglee.ladger.domain

import work.spyglee.ladger.data.MerchantCategory

object MerchantLearning {
    private val legalForms = setOf("d", "o", "dd", "doo", "jdoo", "obrt", "vl", "ltd", "llc", "inc", "gmbh")

    /**
     * Stable key for a merchant name: case and diacritics folded, punctuation removed and legal
     * forms dropped, so "KONZUM plus d.o.o." and "Konzum Plus" share the key "konzum plus".
     */
    fun normalize(merchant: String): String =
        merchant.folded()
            .split(Regex("[^\\p{L}\\p{N}.]+"))
            .map { it.replace(".", "") }
            .filter { it.isNotEmpty() && it !in legalForms }
            .joinToString(" ")

    /**
     * Mapping whose key extends [key] or is extended by it on a word boundary,
     * e.g. "lidl" matches "lidl hrvatska". [recentFirst] must be sorted by most recently updated.
     */
    fun prefixMatch(key: String, recentFirst: List<MerchantCategory>): MerchantCategory? =
        recentFirst.firstOrNull { mapping ->
            key.startsWith(mapping.merchantKey + " ") || mapping.merchantKey.startsWith("$key ")
        }
}
