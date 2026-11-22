package work.spyglee.ladger.billing

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Checks that a purchase was signed by Google Play, the Android counterpart of iOS's verified
 * transactions. The key is the app's public licence key from Play Console (Monetize with Play →
 * Monetization setup), set as the Gradle property `ledger.playLicenseKey` (see docs/RELEASE.md).
 * There's no server, so this check on the phone is the one we have, as on iOS.
 */
object PurchaseSignature {
    /** Without a key (before the app exists in Play Console), purchases are taken as Play reports them. */
    fun isValid(base64PublicKey: String, signedData: String, signature: String): Boolean {
        if (base64PublicKey.isBlank()) return true
        if (signature.isBlank()) return false
        return try {
            val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(base64PublicKey.trim())))
            Signature.getInstance("SHA1withRSA").run {
                initVerify(key)
                update(signedData.toByteArray())
                verify(Base64.getDecoder().decode(signature))
            }
        } catch (e: GeneralSecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
