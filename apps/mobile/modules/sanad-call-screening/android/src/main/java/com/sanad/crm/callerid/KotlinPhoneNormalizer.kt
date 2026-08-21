package com.sanad.crm.callerid

/**
 * Phone normalizer for the native ring-time runtime ONLY (G8 EXECUTION 05
 * §31). SEMANTIC PARITY with the backend authority
 * `com.sanad.platform.crm.party.domain.PhoneNumberNormalizer` and the mobile
 * `normalizePhone` (Track D) — same rules, same shared test vectors
 * (NORMALIZATION_PARITY, G8-05 §32):
 *   - strip `[\s().-]`
 *   - `00…` -> `+…`
 *   - E.164 pass-through `^\+[1-9][0-9]{7,14}$`
 *   - Saudi forms `05xxxxxxxx` / `5xxxxxxxx` / `966xxxxxxxxx` only with an
 *     explicit `countryHint = SA`
 */
object KotlinPhoneNormalizer {

    private val E164 = Regex("^\\+[1-9][0-9]{7,14}$")

    fun normalizePhone(raw: String?, countryHint: String? = null): String? {
        if (raw == null) return null
        var compact = raw.replace(Regex("[\\s().-]"), "")
        if (compact.isEmpty()) return null
        if (compact.startsWith("00")) compact = "+" + compact.substring(2)
        if (E164.matches(compact)) return compact

        val hint = countryHint?.trim()?.uppercase()
        if (hint == "SA") {
            when {
                Regex("^05[0-9]{8}$").matches(compact) -> compact = "+966" + compact.substring(1)
                Regex("^5[0-9]{8}$").matches(compact) -> compact = "+966" + compact
                Regex("^966[0-9]{9}$").matches(compact) -> compact = "+" + compact
            }
            if (E164.matches(compact)) return compact
        }
        return null
    }

    fun digits(normalizedE164: String): String =
        if (normalizedE164.startsWith("+")) normalizedE164.substring(1) else normalizedE164
}
