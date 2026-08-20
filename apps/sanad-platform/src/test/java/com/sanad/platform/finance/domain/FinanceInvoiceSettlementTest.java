package com.sanad.platform.finance.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinanceInvoiceSettlementTest {

    @Test
    void markPaidWithAmount_persistsActualAmountAndPaidState() {
        FinanceInvoice invoice = issuedInvoice(new BigDecimal("250.00"));

        FinanceInvoice paid = invoice.markPaidWithAmount(new BigDecimal("250.00"));

        assertThat(paid.status()).isEqualTo(FinanceInvoice.Status.PAID);
        assertThat(paid.paidAmount()).isEqualByComparingTo("250.00");
        assertThat(paid.version()).isEqualTo(invoice.version() + 1);
        assertThat(paid.updatedAt()).isAfterOrEqualTo(invoice.updatedAt());
    }

    @Test
    void markPaidWithAmount_rejectsNonPositiveOrWrongFullSettlementAmount() {
        FinanceInvoice invoice = issuedInvoice(new BigDecimal("250.00"));

        assertThatThrownBy(() -> invoice.markPaidWithAmount(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> invoice.markPaidWithAmount(new BigDecimal("249.99")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private FinanceInvoice issuedInvoice(BigDecimal total) {
        Instant now = Instant.parse("2026-08-20T12:00:00Z");
        return new FinanceInvoice(
                UUID.randomUUID(), UUID.randomUUID(), "INV-202608-00001",
                "MANUAL", null, "Commerce Customer",
                LocalDate.of(2026, 8, 20), null, "SAR",
                new BigDecimal("217.39"), new BigDecimal("32.61"), total,
                BigDecimal.ZERO, FinanceInvoice.Status.ISSUED, null,
                3L, now, now);
    }
}
