package com.sanad.platform.finance.integration;

import com.sanad.platform.finance.domain.FinanceInvoice;
import com.sanad.platform.finance.domain.FinanceInvoiceRepository;
import com.sanad.platform.finance.domain.FinancePayment;
import com.sanad.platform.finance.domain.FinancePaymentRepository;
import com.sanad.platform.subscription.billing.domain.SubscriptionFinancePort;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

/**
 * Finance-owned implementation of the R0C13 subscription billing integration
 * boundary.
 *
 * <p>Finance invoices/payments remain authoritative. The subscription package
 * never writes Finance tables directly. Stable deterministic identifiers and
 * database unique constraints make invoice/payment replay idempotent.</p>
 */
@Component
public class SubscriptionFinanceAdapter implements SubscriptionFinancePort {

    private static final String EXTERNAL_REFERENCE_PREFIX = "SCP_INVOICE:";
    private static final String PAYMENT_REFERENCE_TYPE = "SCP_SETTLEMENT";

    private final JdbcTemplate jdbc;
    private final FinanceInvoiceRepository invoiceRepository;
    private final FinancePaymentRepository paymentRepository;

    public SubscriptionFinanceAdapter(
            JdbcTemplate jdbc,
            FinanceInvoiceRepository invoiceRepository,
            FinancePaymentRepository paymentRepository
    ) {
        this.jdbc = jdbc;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    @Transactional
    public FinanceInvoiceLink ensureInvoice(UUID tenantId, UUID billingInvoiceId) {
        requireIds(tenantId, billingInvoiceId);
        BillingInvoiceSnapshot billing = loadBillingInvoice(tenantId, billingInvoiceId);
        validateBillingAmounts(billing);

        String externalReference = EXTERNAL_REFERENCE_PREFIX + billingInvoiceId;
        LinkSnapshot linked = findLink(tenantId, billingInvoiceId);
        if (linked != null) {
            if (!externalReference.equals(linked.externalReference())
                    || !billing.subscriptionId().equals(linked.subscriptionId())) {
                throw new IllegalStateException(
                        "Existing subscription/Finance link does not match billing invoice " + billingInvoiceId);
            }
            FinanceInvoice invoice = loadFinanceInvoice(tenantId, linked.financeInvoiceId());
            assertInvoiceMatchesBilling(invoice, billing);
            assertLineMatchesBilling(tenantId, invoice.id(), billing);
            return toLink(billingInvoiceId, invoice, externalReference);
        }

        UUID existingFinanceInvoiceId =
                findFinanceInvoiceIdByExternalReference(tenantId, externalReference);
        FinanceInvoice financeInvoice;
        if (existingFinanceInvoiceId != null) {
            financeInvoice = loadFinanceInvoice(tenantId, existingFinanceInvoiceId);
            assertInvoiceMatchesBilling(financeInvoice, billing);
        } else {
            financeInvoice = createFinanceInvoice(billing, externalReference);
        }

        ensureFinanceInvoiceExternalReference(tenantId, financeInvoice.id(), externalReference);
        ensureInvoiceLine(tenantId, financeInvoice.id(), billing);
        ensureLink(tenantId, billing, financeInvoice.id(), externalReference);

        LinkSnapshot resolved = findLink(tenantId, billingInvoiceId);
        if (resolved == null
                || !financeInvoice.id().equals(resolved.financeInvoiceId())
                || !billing.subscriptionId().equals(resolved.subscriptionId())
                || !externalReference.equals(resolved.externalReference())) {
            throw new IllegalStateException(
                    "Subscription/Finance link could not be resolved deterministically for " + billingInvoiceId);
        }

        FinanceInvoice persisted = loadFinanceInvoice(tenantId, financeInvoice.id());
        assertInvoiceMatchesBilling(persisted, billing);
        assertLineMatchesBilling(tenantId, persisted.id(), billing);
        return toLink(billingInvoiceId, persisted, externalReference);
    }

    @Override
    @Transactional
    public SettlementLink recordSettlement(
            UUID tenantId,
            UUID billingInvoiceId,
            UUID settlementId,
            long amountMinor,
            String currencyCode
    ) {
        requireIds(tenantId, billingInvoiceId);
        if (settlementId == null) {
            throw new IllegalArgumentException("settlementId must not be null");
        }

        BillingInvoiceSnapshot billing = loadBillingInvoice(tenantId, billingInvoiceId);
        validateBillingAmounts(billing);
        String normalizedCurrency = normalizeCurrency(currencyCode);

        if (amountMinor != billing.totalMinor()) {
            throw new IllegalStateException(
                    "Finance settlement amount mismatch: expected "
                            + billing.totalMinor() + " minor units but got " + amountMinor);
        }
        if (!billing.currencyCode().equals(normalizedCurrency)) {
            throw new IllegalStateException(
                    "Finance settlement currency mismatch: expected "
                            + billing.currencyCode() + " but got " + normalizedCurrency);
        }
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("settlement amount must be positive");
        }

        FinanceInvoiceLink link = ensureInvoice(tenantId, billingInvoiceId);
        FinanceInvoice invoice = loadFinanceInvoice(tenantId, link.financeInvoiceId());
        assertInvoiceMatchesBilling(invoice, billing);

        BigDecimal amount = minorToMajor(amountMinor, normalizedCurrency);
        String paymentNumber = paymentNumber(settlementId);

        UUID existingPaymentId = findPaymentIdByNumber(tenantId, paymentNumber);
        FinancePayment payment;
        if (existingPaymentId != null) {
            payment = paymentRepository.findById(tenantId, existingPaymentId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Finance payment disappeared during replay: " + existingPaymentId));
            assertPaymentMatches(payment, invoice.id(), settlementId, amount, normalizedCurrency);

            if (payment.status() == FinancePayment.Status.COMPLETED) {
                markInvoicePaidIdempotently(tenantId, invoice, amount);
                return new SettlementLink(
                        billingInvoiceId, invoice.id(), payment.id(), payment.paymentNumber());
            }
            if (payment.status() != FinancePayment.Status.PENDING) {
                throw new IllegalStateException(
                        "Finance payment replay is not allowed from status " + payment.status());
            }
            payment = paymentRepository.save(payment.complete());
        } else {
            Instant now = Instant.now();
            UUID paymentId = deterministicUuid(
                    "SCP_PAYMENT:" + tenantId + ":" + settlementId);
            FinancePayment pending = new FinancePayment(
                    paymentId,
                    tenantId,
                    paymentNumber,
                    LocalDate.now(ZoneOffset.UTC),
                    FinancePayment.PaymentMethod.ONLINE,
                    amount,
                    normalizedCurrency,
                    PAYMENT_REFERENCE_TYPE,
                    settlementId,
                    invoice.id(),
                    FinancePayment.Status.PENDING,
                    "R0C13 settlement for " + link.externalReference(),
                    0,
                    now,
                    now
            );
            paymentRepository.save(pending);
            payment = paymentRepository.save(pending.complete());
        }

        markInvoicePaidIdempotently(tenantId, invoice, amount);
        return new SettlementLink(
                billingInvoiceId, invoice.id(), payment.id(), payment.paymentNumber());
    }

    private FinanceInvoice createFinanceInvoice(
            BillingInvoiceSnapshot billing,
            String externalReference
    ) {
        UUID financeInvoiceId = deterministicUuid(
                "SCP_FINANCE_INVOICE:" + billing.tenantId() + ":" + billing.id());
        Instant now = Instant.now();
        BigDecimal subtotal = minorToMajor(
                billing.subtotalMinor() - billing.creditAppliedMinor(), billing.currencyCode());
        BigDecimal tax = minorToMajor(billing.taxMinor(), billing.currencyCode());
        BigDecimal total = minorToMajor(billing.totalMinor(), billing.currencyCode());

        FinanceInvoice invoice = new FinanceInvoice(
                financeInvoiceId,
                billing.tenantId(),
                financeInvoiceNumber(billing.id()),
                "MANUAL",
                null,
                "Subscription " + shortId(billing.subscriptionId()),
                billing.periodStart().atZone(ZoneOffset.UTC).toLocalDate(),
                billing.dueAt().atZone(ZoneOffset.UTC).toLocalDate(),
                billing.currencyCode(),
                subtotal,
                tax,
                total,
                BigDecimal.ZERO,
                FinanceInvoice.Status.ISSUED,
                "R0C13 Finance invoice for " + externalReference,
                0,
                now,
                now
        );
        return invoiceRepository.save(invoice);
    }

    private void ensureFinanceInvoiceExternalReference(
            UUID tenantId,
            UUID financeInvoiceId,
            String externalReference
    ) {
        jdbc.update(
                "UPDATE finance_invoices SET external_reference = ? "
                        + "WHERE tenant_id = ? AND id = ? "
                        + "AND (external_reference IS NULL OR external_reference = ?)",
                externalReference, tenantId, financeInvoiceId, externalReference);

        String actual;
        try {
            actual = jdbc.queryForObject(
                    "SELECT external_reference FROM finance_invoices "
                            + "WHERE tenant_id = ? AND id = ?",
                    String.class, tenantId, financeInvoiceId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalStateException(
                    "Finance invoice not visible after persistence: " + financeInvoiceId, e);
        }
        if (!externalReference.equals(actual)) {
            throw new IllegalStateException(
                    "Finance invoice external reference mismatch for " + financeInvoiceId);
        }
    }

    private void ensureInvoiceLine(
            UUID tenantId,
            UUID financeInvoiceId,
            BillingInvoiceSnapshot billing
    ) {
        UUID lineId = deterministicUuid(
                "SCP_FINANCE_LINE:" + tenantId + ":" + billing.id());
        BigDecimal netSubtotal = minorToMajor(
                billing.subtotalMinor() - billing.creditAppliedMinor(), billing.currencyCode());

        jdbc.update(
                "INSERT INTO finance_invoice_lines "
                        + "(id, tenant_id, invoice_id, line_number, description, quantity, "
                        + "unit_price, tax_rate, line_total, version, created_at) "
                        + "VALUES (?, ?, ?, 1, ?, 1, ?, 0, ?, 0, ?) "
                        + "ON CONFLICT (id) DO NOTHING",
                lineId,
                tenantId,
                financeInvoiceId,
                billing.description() == null || billing.description().isBlank()
                        ? "Subscription billing " + shortId(billing.id())
                        : billing.description(),
                netSubtotal,
                netSubtotal,
                Timestamp.from(Instant.now()));

        assertLineMatchesBilling(tenantId, financeInvoiceId, billing);
    }

    private void assertLineMatchesBilling(
            UUID tenantId,
            UUID financeInvoiceId,
            BillingInvoiceSnapshot billing
    ) {
        UUID lineId = deterministicUuid(
                "SCP_FINANCE_LINE:" + tenantId + ":" + billing.id());
        BigDecimal expected = minorToMajor(
                billing.subtotalMinor() - billing.creditAppliedMinor(), billing.currencyCode());

        LineSnapshot line;
        try {
            line = jdbc.queryForObject(
                    "SELECT tenant_id, invoice_id, unit_price, line_total "
                            + "FROM finance_invoice_lines WHERE id = ? AND tenant_id = ?",
                    (rs, rowNum) -> new LineSnapshot(
                            rs.getObject("tenant_id", UUID.class),
                            rs.getObject("invoice_id", UUID.class),
                            rs.getBigDecimal("unit_price"),
                            rs.getBigDecimal("line_total")),
                    lineId, tenantId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalStateException(
                    "Finance invoice line missing for billing invoice " + billing.id(), e);
        }
        if (line == null
                || !tenantId.equals(line.tenantId())
                || !financeInvoiceId.equals(line.financeInvoiceId())
                || line.unitPrice().compareTo(expected) != 0
                || line.lineTotal().compareTo(expected) != 0) {
            throw new IllegalStateException(
                    "Finance invoice line mismatch for billing invoice " + billing.id());
        }
    }

    private void ensureLink(
            UUID tenantId,
            BillingInvoiceSnapshot billing,
            UUID financeInvoiceId,
            String externalReference
    ) {
        UUID linkId = deterministicUuid(
                "SCP_FINANCE_LINK:" + tenantId + ":" + billing.id());
        jdbc.update(
                "INSERT INTO subscription_billing_finance_links "
                        + "(id, tenant_id, subscription_id, billing_invoice_id, "
                        + "finance_invoice_id, external_reference) "
                        + "VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenant_id, billing_invoice_id) DO NOTHING",
                linkId,
                tenantId,
                billing.subscriptionId(),
                billing.id(),
                financeInvoiceId,
                externalReference);
    }

    private void markInvoicePaidIdempotently(
            UUID tenantId,
            FinanceInvoice invoice,
            BigDecimal amount
    ) {
        FinanceInvoice current = invoiceRepository.findById(tenantId, invoice.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Finance invoice missing during settlement: " + invoice.id()));

        if (current.status() == FinanceInvoice.Status.PAID) {
            if (current.paidAmount() != null
                    && current.paidAmount().compareTo(amount) == 0) {
                return;
            }
            throw new IllegalStateException(
                    "Finance invoice already PAID with a different amount: " + current.id());
        }
        invoiceRepository.save(current.markPaidWithAmount(amount));
    }

    private BillingInvoiceSnapshot loadBillingInvoice(UUID tenantId, UUID billingInvoiceId) {
        try {
            BillingInvoiceSnapshot result = jdbc.queryForObject(
                    "SELECT id, tenant_id, subscription_id, currency_code, "
                            + "subtotal_minor, credit_applied_minor, tax_minor, total_minor, "
                            + "description, period_start, period_end, due_at "
                            + "FROM billing_invoices WHERE tenant_id = ? AND id = ?",
                    (rs, rowNum) -> new BillingInvoiceSnapshot(
                            rs.getObject("id", UUID.class),
                            rs.getObject("tenant_id", UUID.class),
                            rs.getObject("subscription_id", UUID.class),
                            normalizeCurrency(rs.getString("currency_code")),
                            rs.getLong("subtotal_minor"),
                            rs.getLong("credit_applied_minor"),
                            rs.getLong("tax_minor"),
                            rs.getLong("total_minor"),
                            rs.getString("description"),
                            rs.getTimestamp("period_start").toInstant(),
                            rs.getTimestamp("period_end").toInstant(),
                            rs.getTimestamp("due_at").toInstant()),
                    tenantId, billingInvoiceId);
            if (result == null) {
                throw new IllegalArgumentException(
                        "Billing invoice not found: " + billingInvoiceId);
            }
            return result;
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException(
                    "Billing invoice not found: " + billingInvoiceId, e);
        }
    }

    private FinanceInvoice loadFinanceInvoice(UUID tenantId, UUID financeInvoiceId) {
        return invoiceRepository.findById(tenantId, financeInvoiceId)
                .orElseThrow(() -> new IllegalStateException(
                        "Linked Finance invoice not found: " + financeInvoiceId));
    }

    private LinkSnapshot findLink(UUID tenantId, UUID billingInvoiceId) {
        try {
            return jdbc.queryForObject(
                    "SELECT subscription_id, finance_invoice_id, external_reference "
                            + "FROM subscription_billing_finance_links "
                            + "WHERE tenant_id = ? AND billing_invoice_id = ?",
                    (rs, rowNum) -> new LinkSnapshot(
                            rs.getObject("subscription_id", UUID.class),
                            rs.getObject("finance_invoice_id", UUID.class),
                            rs.getString("external_reference")),
                    tenantId, billingInvoiceId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private UUID findFinanceInvoiceIdByExternalReference(
            UUID tenantId,
            String externalReference
    ) {
        try {
            return jdbc.queryForObject(
                    "SELECT id FROM finance_invoices "
                            + "WHERE tenant_id = ? AND external_reference = ?",
                    UUID.class, tenantId, externalReference);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private UUID findPaymentIdByNumber(UUID tenantId, String paymentNumber) {
        try {
            return jdbc.queryForObject(
                    "SELECT id FROM finance_payments "
                            + "WHERE tenant_id = ? AND payment_number = ?",
                    UUID.class, tenantId, paymentNumber);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private void assertInvoiceMatchesBilling(
            FinanceInvoice invoice,
            BillingInvoiceSnapshot billing
    ) {
        BigDecimal expectedSubtotal = minorToMajor(
                billing.subtotalMinor() - billing.creditAppliedMinor(), billing.currencyCode());
        BigDecimal expectedTax = minorToMajor(billing.taxMinor(), billing.currencyCode());
        BigDecimal expectedTotal = minorToMajor(billing.totalMinor(), billing.currencyCode());

        if (!billing.tenantId().equals(invoice.tenantId())
                || !billing.currencyCode().equals(invoice.currency())
                || invoice.subtotal().compareTo(expectedSubtotal) != 0
                || invoice.taxAmount().compareTo(expectedTax) != 0
                || invoice.totalAmount().compareTo(expectedTotal) != 0) {
            throw new IllegalStateException(
                    "Finance invoice amount/currency mismatch for billing invoice " + billing.id());
        }
        if (invoice.status() == FinanceInvoice.Status.CANCELLED) {
            throw new IllegalStateException(
                    "Finance invoice is CANCELLED for billing invoice " + billing.id());
        }
    }

    private void assertPaymentMatches(
            FinancePayment payment,
            UUID financeInvoiceId,
            UUID settlementId,
            BigDecimal amount,
            String currency
    ) {
        if (!financeInvoiceId.equals(payment.invoiceId())
                || !PAYMENT_REFERENCE_TYPE.equals(payment.referenceType())
                || !settlementId.equals(payment.referenceId())
                || payment.amount().compareTo(amount) != 0
                || !currency.equals(payment.currency())) {
            throw new IllegalStateException(
                    "Finance payment replay mismatch for settlement " + settlementId);
        }
    }

    private void validateBillingAmounts(BillingInvoiceSnapshot billing) {
        if (billing.subtotalMinor() < 0
                || billing.creditAppliedMinor() < 0
                || billing.taxMinor() < 0
                || billing.totalMinor() < 0
                || billing.creditAppliedMinor() > billing.subtotalMinor()) {
            throw new IllegalStateException(
                    "Invalid billing invoice monetary values for " + billing.id());
        }
        long expectedTotal;
        try {
            expectedTotal = Math.addExact(
                    Math.subtractExact(
                            billing.subtotalMinor(), billing.creditAppliedMinor()),
                    billing.taxMinor());
        } catch (ArithmeticException e) {
            throw new IllegalStateException(
                    "Billing invoice monetary overflow for " + billing.id(), e);
        }
        if (expectedTotal != billing.totalMinor()) {
            throw new IllegalStateException(
                    "Billing invoice total invariant mismatch for " + billing.id());
        }
    }

    private FinanceInvoiceLink toLink(
            UUID billingInvoiceId,
            FinanceInvoice invoice,
            String externalReference
    ) {
        return new FinanceInvoiceLink(
                billingInvoiceId,
                invoice.id(),
                externalReference,
                invoice.invoiceNumber());
    }

    private static BigDecimal minorToMajor(long minorUnits, String currencyCode) {
        Currency currency;
        try {
            currency = Currency.getInstance(normalizeCurrency(currencyCode));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unsupported ISO currency: " + currencyCode, e);
        }
        int fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits < 0) {
            throw new IllegalArgumentException(
                    "Currency has no defined minor-unit scale: " + currencyCode);
        }
        return BigDecimal.valueOf(minorUnits, fractionDigits);
    }

    private static String normalizeCurrency(String currencyCode) {
        if (currencyCode == null) {
            throw new IllegalArgumentException("currencyCode must not be null");
        }
        String normalized = currencyCode.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException(
                    "currencyCode must be a three-letter ISO code");
        }
        return normalized;
    }

    private static String financeInvoiceNumber(UUID billingInvoiceId) {
        return "SCP-" + billingInvoiceId;
    }

    private static String paymentNumber(UUID settlementId) {
        return "SCP-PAY-" + settlementId;
    }

    private static UUID deterministicUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String shortId(UUID id) {
        return id.toString().replace("-", "").substring(0, 12);
    }

    private static void requireIds(UUID tenantId, UUID billingInvoiceId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        if (billingInvoiceId == null) {
            throw new IllegalArgumentException("billingInvoiceId must not be null");
        }
    }

    private record BillingInvoiceSnapshot(
            UUID id,
            UUID tenantId,
            UUID subscriptionId,
            String currencyCode,
            long subtotalMinor,
            long creditAppliedMinor,
            long taxMinor,
            long totalMinor,
            String description,
            Instant periodStart,
            Instant periodEnd,
            Instant dueAt
    ) {}

    private record LinkSnapshot(
            UUID subscriptionId,
            UUID financeInvoiceId,
            String externalReference
    ) {}

    private record LineSnapshot(
            UUID tenantId,
            UUID financeInvoiceId,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {}
}
