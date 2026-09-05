package com.sanad.platform.hr.compliance.application;

import com.sanad.platform.hr.compliance.domain.CountryOperatingMode;
import com.sanad.platform.hr.compliance.domain.ResolvedCountryPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class CountryPolicyResolver {

    private final JdbcTemplate jdbc;
    private final WorkerClassificationResolver workerClassificationResolver;

    public CountryPolicyResolver(JdbcTemplate jdbc, WorkerClassificationResolver workerClassificationResolver) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.workerClassificationResolver = Objects.requireNonNull(workerClassificationResolver, "workerClassificationResolver");
    }

    public ResolvedCountryPolicy resolve(UUID tenantId, UUID employmentId, LocalDate effectiveDate) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(employmentId, "employmentId");
        Objects.requireNonNull(effectiveDate, "effectiveDate");

        validateEmploymentAndLegalEntity(tenantId, employmentId);
        String jurisdiction = resolveJurisdiction(tenantId, employmentId, effectiveDate);
        Pack pack = resolveEffectivePack(jurisdiction, effectiveDate);

        if (pack == null) {
            return new ResolvedCountryPolicy(
                    jurisdiction,
                    CountryOperatingMode.GLOBAL,
                    null,
                    null,
                    workerClassificationResolver.resolve(tenantId, employmentId, CountryOperatingMode.GLOBAL),
                    effectiveDate);
        }

        return new ResolvedCountryPolicy(
                jurisdiction,
                CountryOperatingMode.LOCALIZED,
                pack.code(),
                pack.version(),
                workerClassificationResolver.resolve(tenantId, employmentId, CountryOperatingMode.LOCALIZED),
                effectiveDate);
    }

    private void validateEmploymentAndLegalEntity(UUID tenantId, UUID employmentId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_employees e " +
                        "JOIN legal_entities le ON le.id = e.legal_entity_id AND le.tenant_id = e.tenant_id " +
                        "WHERE e.tenant_id = ? AND e.id = ? AND le.status = 'ACTIVE'",
                Integer.class, tenantId, employmentId);
        if (count == null || count != 1) {
            throw new IllegalStateException("HRM_LEGAL_REVIEW_REQUIRED: employment or active legal entity not found");
        }
    }

    private String resolveJurisdiction(UUID tenantId, UUID employmentId, LocalDate effectiveDate) {
        List<String> rows = jdbc.query(
                "SELECT BTRIM(labor_jurisdiction) " +
                        "FROM hr_employment_jurisdiction_periods " +
                        "WHERE tenant_id = ? AND employment_id = ? AND approval_status = 'APPROVED' " +
                        "AND effective_from <= ? AND (effective_to IS NULL OR effective_to >= ?) " +
                        "ORDER BY effective_from DESC, id DESC LIMIT 1",
                (rs, rowNum) -> rs.getString(1), tenantId, employmentId, effectiveDate, effectiveDate);
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).isBlank()) {
            throw new IllegalStateException("HRM_LEGAL_REVIEW_REQUIRED: employment labor jurisdiction is missing");
        }
        return rows.get(0);
    }

    private Pack resolveEffectivePack(String jurisdiction, LocalDate effectiveDate) {
        List<Pack> rows = jdbc.query(
                "SELECT pack_code, pack_version FROM hr_country_packs " +
                        "WHERE country_code = ? AND status IN ('ACTIVE','CERTIFIED') " +
                        "AND effective_from <= ? AND (effective_to IS NULL OR effective_to >= ?) " +
                        "AND legal_reviewed_at IS NOT NULL " +
                        "AND NULLIF(BTRIM(legal_reviewed_by), '') IS NOT NULL " +
                        "AND NULLIF(BTRIM(certification_reference), '') IS NOT NULL " +
                        "ORDER BY CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END, effective_from DESC, pack_version DESC " +
                        "LIMIT 1",
                (rs, rowNum) -> new Pack(rs.getString("pack_code"), rs.getString("pack_version")),
                jurisdiction, effectiveDate, effectiveDate);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private record Pack(String code, String version) { }
}
