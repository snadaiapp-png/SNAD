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

    /**
     * HRM-G1 T3 — opening-scoped jurisdiction resolution (additive; the
     * employment path above is unchanged and remains the G0 authority).
     *
     * <p>Chain: org_unit → organization → ACTIVE effective-dated
     * organization↔legal-entity binding → {@code legal_entities.registered_country_code}.
     * An opening whose organization has no ACTIVE legal-entity binding FAILS
     * CLOSED (HRM_LEGAL_REVIEW_REQUIRED); a jurisdiction without an
     * authoritative pack resolves to GLOBAL mode and the engine decides.</p>
     */
    public ResolvedCountryPolicy resolveForOpening(UUID tenantId, UUID orgUnitId, LocalDate effectiveDate) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(orgUnitId, "orgUnitId");
        Objects.requireNonNull(effectiveDate, "effectiveDate");

        validateOpeningOrgUnit(tenantId, orgUnitId);
        String jurisdiction = resolveOpeningJurisdiction(tenantId, orgUnitId, effectiveDate);
        Pack pack = resolveEffectivePack(jurisdiction, effectiveDate);
        if (pack == null) {
            return new ResolvedCountryPolicy(jurisdiction, CountryOperatingMode.GLOBAL,
                    null, null, null, effectiveDate);
        }
        return new ResolvedCountryPolicy(jurisdiction, CountryOperatingMode.LOCALIZED,
                pack.code(), pack.version(), null, effectiveDate);
    }

    private void validateOpeningOrgUnit(UUID tenantId, UUID orgUnitId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_org_units ou " +
                        "JOIN organizations o ON o.id = ou.organization_id " +
                        "WHERE ou.tenant_id = ? AND ou.id = ? AND o.status = 'ACTIVE'",
                Integer.class, tenantId, orgUnitId);
        if (count == null || count != 1) {
            throw new IllegalStateException(
                    "HRM_LEGAL_REVIEW_REQUIRED: opening org unit is missing or organization inactive");
        }
    }

    private String resolveOpeningJurisdiction(UUID tenantId, UUID orgUnitId, LocalDate effectiveDate) {
        List<String> rows = jdbc.query(
                "SELECT le.registered_country_code " +
                        "FROM hr_org_units ou " +
                        "JOIN organization_legal_entities ole " +
                        "  ON ole.organization_id = ou.organization_id AND ole.tenant_id = ou.tenant_id " +
                        "JOIN legal_entities le " +
                        "  ON le.id = ole.legal_entity_id AND le.tenant_id = ole.tenant_id " +
                        " AND le.status = 'ACTIVE' " +
                        "WHERE ou.tenant_id = ? AND ou.id = ? AND ole.status = 'ACTIVE' " +
                        "AND ole.effective_from <= ? AND (ole.effective_to IS NULL OR ole.effective_to >= ?) " +
                        "ORDER BY ole.effective_from DESC, ole.id DESC LIMIT 1",
                (rs, rowNum) -> rs.getString(1), tenantId, orgUnitId, effectiveDate, effectiveDate);
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).isBlank()) {
            throw new IllegalStateException(
                    "HRM_LEGAL_REVIEW_REQUIRED: opening jurisdiction is unresolved (no ACTIVE legal entity)");
        }
        return rows.get(0);
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
