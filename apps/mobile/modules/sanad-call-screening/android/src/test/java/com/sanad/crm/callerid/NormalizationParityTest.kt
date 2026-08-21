package com.sanad.crm.callerid

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * NORMALIZATION_PARITY gate (G8 EXECUTION 05 §32): the Kotlin native
 * normalizer must produce the same result as the backend Java authority and
 * the mobile TypeScript normalizer for EVERY entry of the SHARED vectors file
 * docs/crm/g8/caller-phone-normalization-vectors.json (byte-identical copy in
 * test resources — the vectors are never re-authored independently).
 */
class NormalizationParityTest {

    private fun vectors(): JSONObject =
        JSONObject(
            checkNotNull(javaClass.classLoader.getResource("caller-phone-normalization-vectors.json"))
                .readText()
        )

    @Test
    fun nativeNormalizerMatchesEverySharedVector() {
        val normalization = vectors().getJSONArray("normalization")
        for (i in 0 until normalization.length()) {
            val v = normalization.getJSONObject(i)
            val input = if (v.isNull("input")) null else v.getString("input")
            val hint = if (v.isNull("countryHint")) null else v.getString("countryHint")
            val expected = if (v.isNull("expected")) null else v.getString("expected")

            val actual = KotlinPhoneNormalizer.normalizePhone(input, hint)
            if (expected == null) {
                assertNull("vector[$i] $input must NOT normalize", actual)
            } else {
                assertEquals("vector[$i] $input", expected, actual)
            }
        }
    }

    @Test
    fun saFormsAndE164PassThroughAreExact() {
        // Extra direct assertions for the §32 listed shapes (same rules, no
        // new vectors — these are the same shapes covered by the shared file).
        assertEquals("+966541234567", KotlinPhoneNormalizer.normalizePhone("0541234567", "SA"))
        assertEquals("+966541234567", KotlinPhoneNormalizer.normalizePhone("541234567", "SA"))
        assertEquals("+966541234567", KotlinPhoneNormalizer.normalizePhone("966541234567", "SA"))
        assertEquals("+966541234567", KotlinPhoneNormalizer.normalizePhone("+966541234567", "SA"))
        assertEquals("+966541234567", KotlinPhoneNormalizer.normalizePhone("00966541234567", "SA"))
        assertEquals("+966541234567", KotlinPhoneNormalizer.normalizePhone(" 054 123 4567 ", "SA"))
        assertEquals("+966541234567", KotlinPhoneNormalizer.normalizePhone("05-4123-4567", "SA"))
        assertEquals("+971501234567", KotlinPhoneNormalizer.normalizePhone("+971501234567", null))
        assertNull(KotlinPhoneNormalizer.normalizePhone("0541234567", null))
        assertNull(KotlinPhoneNormalizer.normalizePhone("not-a-number", "SA"))
        assertNull(KotlinPhoneNormalizer.normalizePhone("12345", "SA"))
        assertNull(KotlinPhoneNormalizer.normalizePhone("", "SA"))
        assertNull(KotlinPhoneNormalizer.normalizePhone(null, "SA"))
    }
}
