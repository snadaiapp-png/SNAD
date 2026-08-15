package com.sanad.platform.finance;

import com.sanad.platform.finance.application.FinanceAccountService;
import com.sanad.platform.finance.application.FinanceInvoiceService;
import com.sanad.platform.finance.application.FinancePaymentService;
import com.sanad.platform.finance.domain.*;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the Finance Module.
 *
 * Covers: account lifecycle, invoice lifecycle, payment lifecycle,
 * cross-tenant isolation, duplicate code rejection.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class FinanceModuleIntegrationTest {

    @Autowired private FinanceAccountService accountService;
    @Autowired private FinanceInvoiceService invoiceService;
    @Autowired private FinancePaymentService paymentService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE finance_payments, finance_invoice_lines, finance_invoices, "
                + "finance_journal_lines, finance_journal_entries, finance_accounts RESTART IDENTITY CASCADE");

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                + "VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "fin-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                + "VALUES (?, ?, ?, 'User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "fin-" + userId.toString().substring(0, 8) + "@test", now, now);
        var roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) "
                + "VALUES (?, ?, 'ADMIN', 'Admin', 'Test', 'ACTIVE', ?, ?)",
                roleId, tenantId, now, now);
        var caps = jdbc.queryForList("SELECT id FROM access_capabilities WHERE code LIKE 'FINANCE.%'");
        for (var cap : caps) {
            jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) "
                    + "VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), tenantId, roleId, cap.get("id"), now);
        }
    }

    // ===== ACCOUNT LIFECYCLE =====

    @Test
    void accountLifecycle_createDeactivateArchive() {
        var account = FinanceAccount.create(
                tenantId, "1000", "Cash", FinanceAccount.AccountType.ASSET,
                null, "SAR", "Main cash account");
        var created = accountService.create(account);
        assertThat(created.status()).isEqualTo(FinanceAccount.Status.ACTIVE);

        var deactivated = accountService.deactivate(tenantId, created.id());
        assertThat(deactivated.status()).isEqualTo(FinanceAccount.Status.INACTIVE);

        var archived = accountService.archive(tenantId, created.id());
        assertThat(archived.status()).isEqualTo(FinanceAccount.Status.ARCHIVED);
    }

    @Test
    void accountLifecycle_duplicateCodeRejected() {
        var account1 = FinanceAccount.create(
                tenantId, "DUP-1001", "First", FinanceAccount.AccountType.ASSET, null, "SAR", null);
        accountService.create(account1);

        var account2 = FinanceAccount.create(
                tenantId, "DUP-1001", "Second", FinanceAccount.AccountType.ASSET, null, "SAR", null);
        assertThatThrownBy(() -> accountService.create(account2))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void accountLifecycle_cannotArchiveFromActive() {
        var account = FinanceAccount.create(
                tenantId, "2000", "Bank", FinanceAccount.AccountType.ASSET, null, "SAR", null);
        var created = accountService.create(account);

        assertThatThrownBy(() -> accountService.archive(tenantId, created.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot archive from ACTIVE");
    }

    // ===== INVOICE LIFECYCLE =====

    @Test
    void invoiceLifecycle_createIssueMarkPaid() {
        var invoice = FinanceInvoice.create(
                tenantId, "INV-001", "MANUAL", null, "Test Customer",
                LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 15), "SAR", "Test invoice");
        var created = invoiceService.create(invoice);
        assertThat(created.status()).isEqualTo(FinanceInvoice.Status.DRAFT);

        var issued = invoiceService.issue(tenantId, created.id());
        assertThat(issued.status()).isEqualTo(FinanceInvoice.Status.ISSUED);

        var paid = invoiceService.markPaid(tenantId, created.id());
        assertThat(paid.status()).isEqualTo(FinanceInvoice.Status.PAID);
    }

    @Test
    void invoiceLifecycle_cannotIssueFromPaid() {
        var invoice = FinanceInvoice.create(
                tenantId, "INV-002", "MANUAL", null, "Test Customer",
                LocalDate.of(2026, 1, 15), null, "SAR", null);
        var created = invoiceService.create(invoice);
        invoiceService.issue(tenantId, created.id());
        invoiceService.markPaid(tenantId, created.id());

        assertThatThrownBy(() -> invoiceService.issue(tenantId, created.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot issue from PAID");
    }

    // ===== PAYMENT LIFECYCLE =====

    @Test
    void paymentLifecycle_createCompleteRefund() {
        var payment = FinancePayment.create(
                tenantId, "PAY-001", LocalDate.of(2026, 1, 20),
                FinancePayment.PaymentMethod.BANK_TRANSFER,
                new BigDecimal("1500.00"), "SAR", null, "Test payment");
        var created = paymentService.create(payment);
        assertThat(created.status()).isEqualTo(FinancePayment.Status.PENDING);

        var completed = paymentService.complete(tenantId, created.id());
        assertThat(completed.status()).isEqualTo(FinancePayment.Status.COMPLETED);

        var refunded = paymentService.refund(tenantId, created.id());
        assertThat(refunded.status()).isEqualTo(FinancePayment.Status.REFUNDED);
    }

    @Test
    void paymentLifecycle_cannotCompleteFromCompleted() {
        var payment = FinancePayment.create(
                tenantId, "PAY-002", LocalDate.of(2026, 1, 20),
                FinancePayment.PaymentMethod.CASH,
                new BigDecimal("500.00"), "SAR", null, null);
        var created = paymentService.create(payment);
        paymentService.complete(tenantId, created.id());

        assertThatThrownBy(() -> paymentService.complete(tenantId, created.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot complete from COMPLETED");
    }

    @Test
    void paymentLifecycle_zeroAmountRejected() {
        assertThatThrownBy(() -> FinancePayment.create(
                tenantId, "PAY-003", LocalDate.of(2026, 1, 20),
                FinancePayment.PaymentMethod.CASH,
                BigDecimal.ZERO, "SAR", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be positive");
    }

    // ===== CROSS-TENANT ISOLATION =====

    @Test
    void crossTenant_accountReadReturnsEmpty() {
        var account = FinanceAccount.create(
                tenantId, "XT-1000", "Test", FinanceAccount.AccountType.ASSET, null, "SAR", null);
        var saved = accountService.create(account);

        var otherTenant = UUID.randomUUID();
        var found = accountService.findById(otherTenant, saved.id());
        assertThat(found).isEmpty();
    }

    @Test
    void crossTenant_invoiceReadReturnsEmpty() {
        var invoice = FinanceInvoice.create(
                tenantId, "XT-INV-001", "MANUAL", null, "Test",
                LocalDate.of(2026, 1, 15), null, "SAR", null);
        var saved = invoiceService.create(invoice);

        var otherTenant = UUID.randomUUID();
        var found = invoiceService.findById(otherTenant, saved.id());
        assertThat(found).isEmpty();
    }

    // ===== QUOTA =====

    @Test
    void quotaCount_incrementsAfterCompletedPayment() {
        var before = paymentService.countCompletedThisMonth(tenantId);
        assertThat(before).isZero();

        var payment = FinancePayment.create(
                tenantId, "QUOTA-001", LocalDate.of(2026, 1, 20),
                FinancePayment.PaymentMethod.CASH,
                new BigDecimal("100.00"), "SAR", null, null);
        var created = paymentService.create(payment);
        paymentService.complete(tenantId, created.id());

        assertThat(paymentService.countCompletedThisMonth(tenantId)).isEqualTo(1);
    }
}
