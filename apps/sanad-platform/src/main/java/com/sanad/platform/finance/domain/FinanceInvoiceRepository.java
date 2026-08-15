package com.sanad.platform.finance.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository interface for {@link FinanceInvoice}. */
public interface FinanceInvoiceRepository {
    FinanceInvoice save(FinanceInvoice invoice);
    Optional<FinanceInvoice> findById(UUID tenantId, UUID id);
    Optional<FinanceInvoice> findByNumber(UUID tenantId, String invoiceNumber);
    List<FinanceInvoice> findByTenant(UUID tenantId, int limit);
    List<FinanceInvoice> findByTenantAndStatus(UUID tenantId, FinanceInvoice.Status status, int limit);
}
