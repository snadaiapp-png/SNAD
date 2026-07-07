package com.sanad.platform.crm.ai;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeterministicLeadScoringServiceTest {

    private final DeterministicLeadScoringService service = new DeterministicLeadScoringService();

    @Test
    void scoresStrongLeadWithExplainableReasonCodes() {
        var input = new DeterministicLeadScoringService.LeadScoringInput(
                "tenant-a",
                "lead-100",
                "buyer@example.com",
                "+966500000000",
                "Example Co",
                new BigDecimal("250000"),
                40,
                "referral");

        var result = service.score(input);

        assertThat(result.tenantId()).isEqualTo("tenant-a");
        assertThat(result.leadId()).isEqualTo("lead-100");
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.grade()).isEqualTo("A");
        assertThat(result.advisoryOnly()).isTrue();
        assertThat(result.modelReference()).isEqualTo("deterministic-fallback-v1");
        assertThat(result.reasonCodes()).containsExactly(
                "CONTACTABLE_EMAIL",
                "CONTACTABLE_PHONE",
                "COMPANY_IDENTIFIED",
                "REVENUE_THRESHOLD_MET",
                "EMPLOYEE_THRESHOLD_MET",
                "PREFERRED_SOURCE");
    }

    @Test
    void returnsStableLowSignalFallbackWithoutExternalProvider() {
        var input = new DeterministicLeadScoringService.LeadScoringInput(
                "tenant-b",
                "lead-200",
                null,
                null,
                null,
                null,
                null,
                "unknown");

        var first = service.score(input);
        var second = service.score(input);

        assertThat(first).isEqualTo(second);
        assertThat(first.score()).isEqualTo(10);
        assertThat(first.grade()).isEqualTo("D");
        assertThat(first.reasonCodes()).containsExactly("INSUFFICIENT_SIGNAL");
        assertThat(first.confidence()).isEqualTo(0.35);
    }

    @Test
    void rejectsMissingTenantOrLeadIdentity() {
        var missingTenant = new DeterministicLeadScoringService.LeadScoringInput(
                " ", "lead-1", null, null, null, null, null, null);
        var missingLead = new DeterministicLeadScoringService.LeadScoringInput(
                "tenant-a", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.score(missingTenant))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> service.score(missingLead))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leadId");
    }
}
