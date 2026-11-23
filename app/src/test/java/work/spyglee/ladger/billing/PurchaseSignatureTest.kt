package work.spyglee.ladger.billing

import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseSignatureTest {
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded)
    private val json = """{"productId":"work.spyglee.ledger.pro.monthly","purchaseState":0}"""

    /** Signs like Google Play: SHA1withRSA over the purchase JSON, Base64. */
    private fun sign(data: String): String = Base64.getEncoder().encodeToString(
        Signature.getInstance("SHA1withRSA").run {
            initSign(keys.private)
            update(data.toByteArray())
            sign()
        }
    )

    @Test
    fun aPurchaseSignedWithTheAppsKeyIsValid() {
        assertTrue(PurchaseSignature.isValid(publicKey, json, sign(json)))
    }

    @Test
    fun aChangedPurchaseOrAnotherKeyIsNot() {
        val signature = sign(json)
        assertFalse(PurchaseSignature.isValid(publicKey, json.replace("monthly", "yearly"), signature))
        val otherKey = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public
        assertFalse(PurchaseSignature.isValid(Base64.getEncoder().encodeToString(otherKey.encoded), json, signature))
    }

    @Test
    fun aMissingOrBrokenSignatureOrKeyIsNot() {
        assertFalse(PurchaseSignature.isValid(publicKey, json, ""))
        assertFalse(PurchaseSignature.isValid(publicKey, json, "not base64!"))
        assertFalse(PurchaseSignature.isValid("not a key", json, sign(json)))
    }

    @Test
    fun withoutAKeyPurchasesAreRefusedExceptInDebugBuilds() {
        assertFalse(PurchaseSignature.isValid("", json, sign(json)))
        assertTrue(PurchaseSignature.isValid("", json, "", acceptWithoutKey = true))
        // A configured key is always checked.
        assertFalse(PurchaseSignature.isValid(publicKey, json, "", acceptWithoutKey = true))
    }
}
