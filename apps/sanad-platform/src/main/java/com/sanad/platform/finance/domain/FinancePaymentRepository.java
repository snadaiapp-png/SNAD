package com.sanad.platform.finance.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository interface for {@link FinancePayment}. */
public interface FinancePaymentRepository {
    FinancePayment save(FinancePayment payment);
    Optional<FinancePayment> findById(UUID tenantId, UUID id);
    List<FinancePayment> findByTenant(UUID tenantId, int limit);
    List<FinancePayment> findByInvoice(UUID tenantId, UUID invoiceId);
    long countCompletedThisMonth(UUID tenantId);
}
