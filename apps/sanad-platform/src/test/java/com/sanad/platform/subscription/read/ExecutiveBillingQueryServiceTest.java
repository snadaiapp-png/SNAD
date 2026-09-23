package com.sanad.platform.subscription.read;

import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutiveBillingQueryServiceTest {

    @Test
    void exposesProjectionFinanceSettlementAndReconciliationWithoutInferringMissingTruth() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantRlsTransactionContext rls = mock(TenantRlsTransactionContext.class);
        UUID tenantId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID invoiceId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID subscriptionId = UUID.fromString("30000000-0000-0000-0000-000000000001");

        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.ofEntries(
                Map.entry("id", invoiceId),
                Map.entry("tenant_id", tenantId),
                Map.entry("tenant_name", "Acme"),
                Map.entry("subscription_id", subscriptionId),
                Map.entry("invoice_number", "INV-100"),
                Map.entry("status", "OPEN"),
                Map.entry("currency_code", "SAR"),
                Map.entry("subtotal_minor", 10_000L),
                Map.entry("credit_applied_minor", 1_000L),
                Map.entry("tax_minor", 1_350L),
                Map.entry("total_minor", 10_350L),
                Map.entry("amount_paid_minor", 4_000L),
                Map.entry("period_start", Timestamp.from(Instant.parse("2026-09-01T00:00:00Z"))),
                Map.entry("period_end", Timestamp.from(Instant.parse("2026-10-01T00:00:00Z"))),
                Map.entry("due_at", Timestamp.from(Instant.parse("2026-09-10T00:00:00Z"))),
                Map.entry("created_at", Timestamp.from(Instant.parse("2026-09-01T00:00:00Z")))
        )));

        var rows = new ExecutiveBillingQueryService(jdbc, rls).list(tenantId);

        verify(rls).applyForCurrentTransaction(tenantId);
        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.outstandingMinor()).isEqualTo(6_350L);
        assertThat(row.accountingSourceOfTruth()).isEqualTo("FINANCE");
        assertThat(row.projectionSource()).isEqualTo("SCP_BILLING_PROJECTION");
        assertThat(row.financeLinkId()).isNull();
        assertThat(row.financeInvoiceId()).isNull();
        assertThat(row.financeStatus()).isEqualTo("UNLINKED");
        assertThat(row.settlementState()).isEqualTo("UNKNOWN");
        assertThat(row.reconciliationState()).isEqualTo("UNRECONCILED");
    }

    @Test
    void queriesCanonicalPaymentAttemptStateColumn() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantRlsTransactionContext rls = mock(TenantRlsTransactionContext.class);
        UUID tenantId = UUID.fromString("10000000-0000-0000-0000-000000000002");
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        new ExecutiveBillingQueryService(jdbc, rls).list(tenantId);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(Object[].class));
        assertThat(sql.getValue())
                .contains("SELECT pa.state")
                .doesNotContain("pa.payment_state");
    }
}
