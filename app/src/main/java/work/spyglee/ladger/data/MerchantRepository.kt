package work.spyglee.ladger.data

import java.time.Instant
import work.spyglee.ladger.domain.MerchantLearning

/** Remembers which category and source the user chose for a scanned merchant. */
class MerchantRepository(private val dao: MerchantCategoryDao) {
    suspend fun learned(merchant: String): MerchantCategory? {
        val key = MerchantLearning.normalize(merchant)
        if (key.isEmpty()) return null
        return dao.findByKey(key) ?: MerchantLearning.prefixMatch(key, dao.getAllRecentFirst())
    }

    suspend fun learn(merchant: String, categoryId: Long, sourceId: Long?) {
        val key = MerchantLearning.normalize(merchant)
        if (key.isEmpty()) return
        val existing = dao.findByKey(key)
        if (existing != null) {
            dao.update(existing.copy(categoryId = categoryId, sourceId = sourceId, updatedAt = Instant.now()))
        } else {
            dao.insert(MerchantCategory(merchantKey = key, categoryId = categoryId, sourceId = sourceId))
        }
    }
}
