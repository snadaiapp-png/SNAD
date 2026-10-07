package com.sanad.platform.hr.payroll.application;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * G4-T7 HRM -> Accounting application boundary for an approved payroll export.
 *
 * <p>HR owns payroll preparation and the request contract only. Accounting owns
 * journal/GL construction, posting, ledger persistence and financial system-of-record
 * semantics. Implementations of this port must be tenant-safe and idempotent.</p>
 *
 * <p>The contract is deliberately amount-minimized. Detailed payroll monetary data,
 * account codes, debit/credit instructions, bank data and statutory payment data are
 * not part of this boundary.</p>
 */
public interface PayrollAccountingExportPort {

    /**
     * Requests downstream Accounting processing for an already-approved payroll run.
     *
     * <p>The {@code idempotencyKey} is part of the business request identity. Replaying
     * the same request must return the same logical receipt and must never cause a
     * duplicate Accounting posting.</p>
     */
    ExportReceipt requestExport(ExportRequest request);

    record ExportRequest(
            UUID tenantId,
            UUID payrollRunId,
            UUID legalEntityId,
            LocalDate periodStart,
            LocalDate periodEnd,
            String currencyCode,
            long approvedVersion,
            UUID correlationId,
            UUID requestId,
            String idempotencyKey
    ) {
        public ExportRequest {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(payrollRunId, "payrollRunId");
            Objects.requireNonNull(legalEntityId, "legalEntityId");
            Objects.requireNonNull(periodStart, "periodStart");
            Objects.requireNonNull(periodEnd, "periodEnd");
            Objects.requireNonNull(correlationId, "correlationId");
            Objects.requireNonNull(requestId, "requestId");
            requireText(currencyCode, "currencyCode");
            requireText(idempotencyKey, "idempotencyKey");

            if (periodEnd.isBefore(periodStart)) {
                throw new IllegalArgumentException("HRM_PAYROLL_EXPORT_PERIOD_INVALID");
            }
            if (!currencyCode.matches("^[A-Z]{3}$")) {
                throw new IllegalArgumentException("HRM_PAYROLL_EXPORT_CURRENCY_INVALID");
            }
            if (approvedVersion < 0) {
                throw new IllegalArgumentException("HRM_PAYROLL_EXPORT_VERSION_INVALID");
            }
        }
    }

    /**
     * Evidence returned to HR. It is not a journal or posting result.
     */
    record ExportReceipt(
            UUID correlationId,
            String externalReference,
            String status
    ) {
        public ExportReceipt {
            Objects.requireNonNull(correlationId, "correlationId");
            requireText(externalReference, "externalReference");
            requireText(status, "status");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "HRM_PAYROLL_EXPORT_INVALID: " + field + " is required");
        }
    }
}
