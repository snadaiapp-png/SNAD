package com.sanad.crm.callerid

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 lookup-token derivation (G8-ADR-004) — native parity
 * implementation of the backend `javax.crypto.HmacSHA256` and the mobile
 * `hmacSha256Hex` (Track D). Parity is proven by the shared token vector in
 * docs/crm/g8/caller-phone-normalization-vectors.json (G8 EXECUTION 05 §33).
 */
object KotlinHmac {

    fun hmacSha256Hex(key: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
