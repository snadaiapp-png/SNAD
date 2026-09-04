package com.sanad.platform.hr.compliance.application;

import com.sanad.platform.hr.compliance.domain.ResolvedCountryPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 5 — tenant-bound compliance context resolution
 * (GET /api/v2/hr/compliance/context).
 *
 * <p>Mirrors the WS3 service pattern: resolution runs inside a transaction
 * with the tenant GUC bound first, so the fail-closed RLS fabric governs
 * every underlying read. Policy metadata only — never employee PII.
 */
@Service
public class HrComplianceContextService {

    private final CountryPolicyResolver policyResolver;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;

    public HrComplianceContextService(CountryPolicyResolver policyResolver,
                                      JdbcTemplate jdbc,
                                      PlatformTransactionManager transactionManager) {
        this.policyResolver = Objects.requireNonNull(policyResolver, "policyResolver");
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
    }

    public ResolvedCountryPolicy resolve(UUID tenantId, UUID employmentId, LocalDate effectiveDate) {
        Objects.requireNonNull(tenantId, "tenantId");
        return transactionTemplate.execute(status -> {
            bindTenant(tenantId);
            return policyResolver.resolve(tenantId, employmentId, effectiveDate);
        });
    }

    private void bindTenant(UUID tenantId) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
    }
}
