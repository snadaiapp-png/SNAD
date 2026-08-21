package com.sanad.crm.callerid

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * HMAC_PARITY gate (G8 EXECUTION 05 §33): the shared token vector must yield
 * the exact same hex on Android Kotlin as on the backend (javax.crypto) and
 * mobile TypeScript — byte-for-byte.
 */
class HmacParityTest {

    private fun tokenVector(): JSONObject =
        JSONObject(
            checkNotNull(javaClass.classLoader.getResource("caller-phone-normalization-vectors.json"))
                .readText()
        ).getJSONObject("tokenVector")

    @Test
    fun tenantDatasetKeyDerivationMatchesSharedVector() {
        val v = tokenVector()
        val actual = KotlinHmac.hmacSha256Hex(v.getString("masterKey"), v.getString("tenantId"))
        assertEquals(v.getString("tenantDatasetKey"), actual)
    }

    @Test
    fun lookupTokenDerivationMatchesSharedVector() {
        val v = tokenVector()
        val tenantKey = KotlinHmac.hmacSha256Hex(v.getString("masterKey"), v.getString("tenantId"))
        val lookup = KotlinHmac.hmacSha256Hex(tenantKey, v.getString("message"))
        assertEquals(v.getString("lookupToken"), lookup)
    }

    @Test
    fun hmacIsDeterministicAndHexEncoded() {
        val a = KotlinHmac.hmacSha256Hex("key", "message")
        val b = KotlinHmac.hmacSha256Hex("key", "message")
        assertEquals(a, b)
        assertEquals(64, a.length)
    }
}
