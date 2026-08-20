package com.sanad.platform.crm.party.domain;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Single normalization authority for CRM phone numbers (G8 EXECUTION 02,
 * G8-ADR-001 phone policy).
 *
 * <p>Extracted from {@code AddressCommunicationUseCases} without behavior
 * change (G8-02 §4–§5): supports E.164 pass-through, {@code 00 -> +}, and the
 * Saudi forms {@code 05xxxxxxxx}/{@code 5xxxxxxxx}/{@code 966xxxxxxxxx} with
 * an explicit {@code countryHint=SA}. Both the CRM-007 write path and the G8
 * caller-identification lookup MUST use this one authority — a second,
 * different normalizer is forbidden.
 */
public final class PhoneNumberNormalizer {

    /** Phone-ish communication method types that are normalized as phone numbers. */
    public static final Set<String> PHONE_TYPES = Set.of("PHONE", "MOBILE", "FAX", "WHATSAPP", "SMS");

    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    private PhoneNumberNormalizer() {
    }

    /** Returns true when the value is a canonical E.164 phone number. */
    public static boolean isE164(String value) {
        return value != null && E164.matcher(value).matches();
    }

    /**
     * Normalizes a raw phone number to E.164.
     *
     * @return the normalized number, or {@code null} when the value cannot be
     *         normalized with the given hint — the caller decides the error policy
     */
    public static String normalizePhone(String raw, String countryHint) {
        if (raw == null) return null;
        String compact = raw.replaceAll("[\\s().-]", "");
        if (compact.isEmpty()) return null;
        if (compact.startsWith("00")) compact = "+" + compact.substring(2);
        if (E164.matcher(compact).matches()) return compact;
        String hint = countryHint == null ? null : countryHint.trim().toUpperCase(Locale.ROOT);
        if ("SA".equals(hint)) {
            if (compact.matches("05[0-9]{8}")) compact = "+966" + compact.substring(1);
            else if (compact.matches("5[0-9]{8}")) compact = "+966" + compact;
            else if (compact.matches("966[0-9]{9}")) compact = "+" + compact;
            if (E164.matcher(compact).matches()) return compact;
        }
        return null;
    }

    /** Digits-only representation of a normalized E.164 number (country code included). */
    public static String digits(String normalizedE164) {
        if (normalizedE164 == null || !normalizedE164.startsWith("+")) return normalizedE164;
        return normalizedE164.substring(1);
    }
}
