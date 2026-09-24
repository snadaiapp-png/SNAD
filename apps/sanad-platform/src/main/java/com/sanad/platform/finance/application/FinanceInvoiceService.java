package com.sanad.platform.finance.application;

import com.sanad.platform.finance.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for {@link FinanceInvoice} lifecycle management.
 *
 * <p>State machine: DRAFT → ISSUED → PARTIALLY_PAID → PAID
 *                  DRAFT → CANCELLED
 */
@Service
public class FinanceInvoiceService {

    private static final Logger log = LoggerFactory.getLogger(FinanceInvoiceService.class);
    private final FinanceInvoiceRepository invoiceRepo;

    public FinanceInvoiceService(FinanceInvoiceRepository invoiceRepo) {
        this.invoiceRepo = invoiceRepo;
    }

    @Transactional
    public FinanceInvoice create(FinanceInvoice invoice) {
        var saved = invoiceRepo.save(invoice);
        log.info("FinanceInvoice created: tenant={} number={} customer={}", saved.tenantId(), saved.invoiceNumber(), saved.customerName());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<FinanceInvoice> findById(UUID tenantId, UUID id) {
        return invoiceRepo.findById(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<FinanceInvoice> findByTenant(UUID tenantId, int limit) {
        return invoiceRepo.findByTenant(tenantId, limit);
    }

    @Transactional(readOnly = true)
    public List<FinanceInvoice> findByStatus(UUID tenantId, FinanceInvoice.Status status, int limit) {
        return invoiceRepo.findByTenantAndStatus(tenantId, status, limit);
    }

    @Transactional
    public FinanceInvoice issue(UUID tenantId, UUID id) {
        var invoice = load(tenantId, id);
        var updated = invoiceRepo.save(invoice.issue());
        log.info("FinanceInvoice issued: tenant={} number={}", tenantId, updated.invoiceNumber());
        return updated;
    }

    @Transactional
    public FinanceInvoice cancel(UUID tenantId, UUID id) {
        var invoice = load(tenantId, id);
        var updated = invoiceRepo.save(invoice.cancel());
        log.info("FinanceInvoice cancelled: tenant={} number={}", tenantId, updated.invoiceNumber());
        return updated;
    }

    @Transactional
    public FinanceInvoice markPaid(UUID tenantId, UUID id) {
        var invoice = load(tenantId, id);
        var updated = invoiceRepo.save(invoice.markPaid());
        log.info("FinanceInvoice marked PAID: tenant={} number={}", tenantId, updated.invoiceNumber());
        return updated;
    }

    private FinanceInvoice load(UUID tenantId, UUID id) {
        return invoiceRepo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("FinanceInvoice not found: " + id));
    }
}
