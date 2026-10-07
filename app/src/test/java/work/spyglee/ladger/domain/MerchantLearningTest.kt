package work.spyglee.ladger.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import work.spyglee.ladger.data.MerchantCategory

class MerchantLearningTest {
    @Test
    fun `normalize drops case, diacritics, punctuation and legal forms`() {
        assertEquals("konzum plus", MerchantLearning.normalize("KONZUM plus d.o.o."))
        assertEquals("lidl hrvatska kd", MerchantLearning.normalize("Lidl Hrvatska d.o.o. k.d."))
        assertEquals("pekara dubravica", MerchantLearning.normalize("Pekara Dubravica, obrt"))
        assertEquals("duro dakovic", MerchantLearning.normalize("ĐURO ĐAKOVIĆ"))
        assertEquals("muller", MerchantLearning.normalize("Müller Ltd."))
        assertEquals("", MerchantLearning.normalize("d.o.o."))
    }

    private fun mapping(key: String, id: Long) =
        MerchantCategory(id = id, merchantKey = key, categoryId = id, sourceId = null, updatedAt = Instant.EPOCH)

    @Test
    fun `prefix match works on word boundaries in recency order`() {
        val recentFirst = listOf(mapping("konzum", 1), mapping("konzum plus", 2), mapping("lidl hrvatska", 3))
        assertEquals(1L, MerchantLearning.prefixMatch("konzum plus zagreb", recentFirst)?.id)
        assertEquals(3L, MerchantLearning.prefixMatch("lidl", recentFirst)?.id)
        assertNull(MerchantLearning.prefixMatch("konzumacija", recentFirst))
    }
}
