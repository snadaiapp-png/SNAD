package com.sanad.platform.management;

import com.sanad.platform.management.application.FinanceManagementIntegrationService;
import com.sanad.platform.security.RequireCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Import(com.sanad.platform.security.SecurityPermitAllTestConfig.class)
class FinanceManagementIntegrationTest {

    @Autowired private FinanceManagementIntegrationService financeIntegrationService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?, 'Finance Mgmt Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "fmi-" + tenantId.toString().substring(0, 8), now, now);
    }

    @Test
    void overview_emptyTenant_returnsZeroMetrics() {
        var overview = financeIntegrationService.getOverview(tenantId);

        assertThat(overview.get("totalInvoices")).isEqualTo(0);
        assertThat(overview.get("totalPayments")).isEqualTo(0);
        assertThat(overview.get("invoiceTotalValue")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(overview.get("collectedRevenue")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(overview.get("outstandingAmount")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void overview_aggregatesInvoiceStatusesAndValues() {
        insertInvoice("INV-1", "ISSUED", "100.00", "0.00");
        insertInvoice("INV-2", "PAID", "250.00", "250.00");
        insertInvoice("INV-3", "OVERDUE", "75.00", "25.00");

        var overview = financeIntegrationService.getOverview(tenantId);

        assertThat(overview.get("totalInvoices")).isEqualTo(3);
        assertThat(overview.get("invoiceTotalValue")).isEqualByComparingTo("425.00");
        assertThat(overview.get("invoiceStatusCounts").toString()).contains("ISSUED", "PAID", "OVERDUE");
    }

    @Test
    void overview_aggregatesCompletedPaymentsAndOutstandingAmount() {
        var invoiceId = insertInvoice("INV-4", "PARTIALLY_PAID", "500.00", "200.00");
        insertPayment(invoiceId, "PAY-1", "COMPLETED", "200.00");
        insertPayment(invoiceId, "PAY-2", "PENDING", "50.00");

        var overview = financeIntegrationService.getOverview(tenantId);

        assertThat(overview.get("totalPayments")).isEqualTo(2);
        assertThat(overview.get("collectedRevenue")).isEqualByComparingTo("200.00");
        assertThat(overview.get("outstandingAmount")).isEqualByComparingTo("300.00");
    }

    @Test
    void overview_isTenantScoped() {
        insertInvoice("OWN-1", "ISSUED", "900.00", "0.00");
        UUID otherTenant = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?, 'Other Finance', ?, 'ACTIVE', ?, ?)",
                otherTenant, "fmo-" + otherTenant.toString().substring(0, 8), now, now);
        insertInvoiceForTenant(otherTenant, "OTHER-1", "ISSUED", "9000.00", "0.00");

        var overview = financeIntegrationService.getOverview(tenantId);

        assertThat(overview.get("totalInvoices")).isEqualTo(1);
        assertThat(overview.get("invoiceTotalValue")).isEqualByComparingTo("900.00");
    }

    @Test
    void managementEndpoint_requiresExecutiveCommandCenterView() throws Exception {
        var method = Class.forName("com.sanad.platform.management.api.CommandCenterController")
                .getDeclaredMethod("financeOverview", org.springframework.security.core.Authentication.class);
        var annotation = method.getAnnotation(RequireCapability.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("EXECUTIVE_COMMAND_CENTER.VIEW");
    }

    private UUID insertInvoice(String number, String status, String total, String paid) {
        return insertInvoiceForTenant(tenantId, number, status, total, paid);
    }

    private UUID insertInvoiceForTenant(UUID tenant, String number, String status, String total, String paid) {
        UUID id = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO finance_invoices (id,tenant_id,invoice_number,customer_type,issue_date,currency,subtotal,tax_amount,total_amount,paid_amount,status,version,created_at,updated_at) VALUES (?, ?, ?, 'MANUAL', CURRENT_DATE, 'SAR', ?, 0, ?, ?, ?, 0, ?, ?)",
                id, tenant, number, new BigDecimal(total), new BigDecimal(total), new BigDecimal(paid), status, now, now);
        return id;
    }

    private void insertPayment(UUID invoiceId, String number, String status, String amount) {
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO finance_payments (id,tenant_id,payment_number,payment_date,payment_method,amount,currency,invoice_id,status,version,created_at,updated_at) VALUES (?, ?, ?, CURRENT_DATE, 'CASH', ?, 'SAR', ?, ?, 0, ?, ?)",
                UUID.randomUUID(), tenantId, number, new BigDecimal(amount), invoiceId, status, now, now);
    }
}
