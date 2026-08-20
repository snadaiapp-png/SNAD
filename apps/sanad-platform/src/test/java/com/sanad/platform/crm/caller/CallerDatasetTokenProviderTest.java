package com.sanad.platform.crm.caller;

import com.sanad.platform.crm.caller.application.CallerDatasetTokenProvider;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Secure lookup token tests (G8-03 §34–§35): deterministic, tenant-bound,
 * fails closed without a configured master key.
 */
class CallerDatasetTokenProviderTest {

    private static final UUID TENANT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void derivesTheSharedTokenVector() {
        CallerDatasetTokenProvider provider = new CallerDatasetTokenProvider("g8-test-master-key");

        assertThat(provider.tenantDatasetKey(TENANT))
                .isEqualTo("b84db436b474829bc8eea528f5938a522969ee85fea4858141390be76f514b3b");
        assertThat(provider.lookupToken(TENANT, "+966541234567"))
                .isEqualTo("939e9069d4b3fc201a2746551de30f2e4dfd68616c6c2535913d42aa285aecf8");
    }

    @Test
    void tokenIsTenantBound() {
        CallerDatasetTokenProvider provider = new CallerDatasetTokenProvider("master-key");
        assertThat(provider.lookupToken(TENANT, "+966541234567"))
                .isNotEqualTo(provider.lookupToken(UUID.randomUUID(), "+966541234567"));
    }

    @Test
    void tokenIsDeterministicAndNumberSensitive() {
        CallerDatasetTokenProvider provider = new CallerDatasetTokenProvider("master-key");
        assertThat(provider.lookupToken(TENANT, "+966541234567"))
                .isEqualTo(provider.lookupToken(TENANT, "+966541234567"));
        assertThat(provider.lookupToken(TENANT, "+966541234567"))
                .isNotEqualTo(provider.lookupToken(TENANT, "+966541234568"));
    }

    @Test
    void failsClosedWithoutMasterKey() {
        CallerDatasetTokenProvider provider = new CallerDatasetTokenProvider(null);

        assertThat(provider.isConfigured()).isFalse();
        assertThatThrownBy(() -> provider.lookupToken(TENANT, "+966541234567"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("master key");
    }
}
