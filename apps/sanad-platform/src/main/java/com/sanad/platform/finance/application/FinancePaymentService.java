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
 * Application service for {@link FinancePayment} lifecycle management.
 *
 * <p>State machine: PENDING → COMPLETED | FAILED | CANCELLED
 *                  COMPLETED → REFUNDED
 */
@Service
public class FinancePaymentService {

    private static final Logger log = LoggerFactory.getLogger(FinancePaymentService.class);
    private final FinancePaymentRepository paymentRepo;

    public FinancePaymentService(FinancePaymentRepository paymentRepo) {
        this.paymentRepo = paymentRepo;
    }

    @Transactional
    public FinancePayment create(FinancePayment payment) {
        var saved = paymentRepo.save(payment);
        log.info("FinancePayment created: tenant={} number={} amount={}", saved.tenantId(), saved.paymentNumber(), saved.amount());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<FinancePayment> findById(UUID tenantId, UUID id) {
        return paymentRepo.findById(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<FinancePayment> findByTenant(UUID tenantId, int limit) {
        return paymentRepo.findByTenant(tenantId, limit);
    }

    @Transactional(readOnly = true)
    public List<FinancePayment> findByInvoice(UUID tenantId, UUID invoiceId) {
        return paymentRepo.findByInvoice(tenantId, invoiceId);
    }

    @Transactional
    public FinancePayment complete(UUID tenantId, UUID id) {
        var payment = load(tenantId, id);
        var updated = paymentRepo.save(payment.complete());
        log.info("FinancePayment completed: tenant={} number={}", tenantId, updated.paymentNumber());
        return updated;
    }

    @Transactional
    public FinancePayment fail(UUID tenantId, UUID id) {
        var payment = load(tenantId, id);
        var updated = paymentRepo.save(payment.fail());
        log.info("FinancePayment failed: tenant={} number={}", tenantId, updated.paymentNumber());
        return updated;
    }

    @Transactional
    public FinancePayment refund(UUID tenantId, UUID id) {
        var payment = load(tenantId, id);
        var updated = paymentRepo.save(payment.refund());
        log.info("FinancePayment refunded: tenant={} number={}", tenantId, updated.paymentNumber());
        return updated;
    }

    @Transactional(readOnly = true)
    public long countCompletedThisMonth(UUID tenantId) {
        return paymentRepo.countCompletedThisMonth(tenantId);
    }

    private FinancePayment load(UUID tenantId, UUID id) {
        return paymentRepo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("FinancePayment not found: " + id));
    }
}
