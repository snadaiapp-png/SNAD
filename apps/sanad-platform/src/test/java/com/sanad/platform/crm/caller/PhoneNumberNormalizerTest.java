package com.sanad.platform.crm.caller;

import com.sanad.platform.crm.party.domain.PhoneNumberNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Single normalization authority matrix (G8-02 §38).
 *
 * <p>These are the exact forms the callers may arrive in. The same authority
 * continues to serve the CRM-007 write path (regression covered by the
 * existing {@code AddressCommunicationUseCasesTest}).
 */
class PhoneNumberNormalizerTest {

    @Test
    void normalizesAllSaudiE164Forms() {
        assertThat(PhoneNumberNormalizer.normalizePhone("0541234567", "SA")).isEqualTo("+966541234567");
        assertThat(PhoneNumberNormalizer.normalizePhone("541234567", "SA")).isEqualTo("+966541234567");
        assertThat(PhoneNumberNormalizer.normalizePhone("966541234567", "SA")).isEqualTo("+966541234567");
        assertThat(PhoneNumberNormalizer.normalizePhone("+966541234567", "SA")).isEqualTo("+966541234567");
        assertThat(PhoneNumberNormalizer.normalizePhone("00966541234567", "SA")).isEqualTo("+966541234567");
        assertThat(PhoneNumberNormalizer.normalizePhone("966541234567", "SA")).isEqualTo("+966541234567");
    }

    @Test
    void stripsSpacesDashesAndParentheses() {
        assertThat(PhoneNumberNormalizer.normalizePhone(" 054 123 4567 ", "SA")).isEqualTo("+966541234567");
        assertThat(PhoneNumberNormalizer.normalizePhone("05-4123-4567", "SA")).isEqualTo("+966541234567");
        assertThat(PhoneNumberNormalizer.normalizePhone("(054) 123-4567", "SA")).isEqualTo("+966541234567");
    }

    @Test
    void passesThroughCanonicalE164ForAnyCountry() {
        assertThat(PhoneNumberNormalizer.normalizePhone("+971501234567", null)).isEqualTo("+971501234567");
        assertThat(PhoneNumberNormalizer.normalizePhone("+442071234567", "GB")).isEqualTo("+442071234567");
    }

    @Test
    void refusesInvalidAndEmptyValues() {
        assertThat(PhoneNumberNormalizer.normalizePhone("invalid text", "SA")).isNull();
        assertThat(PhoneNumberNormalizer.normalizePhone("123", "SA")).isNull();
        assertThat(PhoneNumberNormalizer.normalizePhone("+9665412345678910111213", "SA")).isNull();
        assertThat(PhoneNumberNormalizer.normalizePhone("", "SA")).isNull();
        assertThat(PhoneNumberNormalizer.normalizePhone(null, "SA")).isNull();
        assertThat(PhoneNumberNormalizer.normalizePhone("     ", null)).isNull();
    }

    @Test
    void countryHintIsCaseInsensitiveAndOthersAreNotGuessed() {
        assertThat(PhoneNumberNormalizer.normalizePhone("0541234567", "sa")).isEqualTo("+966541234567");
        // Without the SA hint a purely local number has no country to attach to.
        assertThat(PhoneNumberNormalizer.normalizePhone("0541234567", null)).isNull();
        assertThat(PhoneNumberNormalizer.normalizePhone("0541234567", "GB")).isNull();
    }

    @Test
    void isE164MatchesCanonicalFormOnly() {
        assertThat(PhoneNumberNormalizer.isE164("+966541234567")).isTrue();
        assertThat(PhoneNumberNormalizer.isE164("541234567")).isFalse();
        assertThat(PhoneNumberNormalizer.isE164(null)).isFalse();
    }
}
