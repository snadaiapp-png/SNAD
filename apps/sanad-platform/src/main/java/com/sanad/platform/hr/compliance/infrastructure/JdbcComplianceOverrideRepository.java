package com.sanad.platform.hr.compliance.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.hr.compliance.domain.ComplianceOverrideRequest;
import com.sanad.platform.hr.compliance.domain.ComplianceOverrideStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.util.List;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for the governed compliance override workflow (WS3 Task 4).
 *
 * <p>All state transitions use conditional UPDATEs (tenant-bound + status
 * bound) so concurrent lifecycle actions resolve to exactly one winner —
 * race-safe four-eyes and state-machine enforcement at the persistence
 * layer. All reads/writes run on the caller's transaction-bound
 * {@link JdbcTemplate} (the tenant RLS GUC is set by the service first).</p>
 */
@Repository
public class JdbcComplianceOverrideRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Effective-rule state used for request-time gating and execution-time revalidation. */
    public record OverrideRuleState(
            UUID ruleId,
            String ruleCode,
            String ruleVersion,
            String enforcementLevel,
            boolean exceptionAllowed,
            String ruleStatus,
            LocalDate ruleEffectiveFrom,
            LocalDate ruleEffectiveTo,
            String packStatus,
            boolean packLegallyReviewed) {
    }

    private final JdbcTemplate jdbc;

    public JdbcComplianceOverrideRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    public Optional<OverrideRuleState> loadRuleState(UUID ruleId) {
        return jdbc.query(
                "SELECT r.id, r.rule_code, r.rule_version, r.enforcement_level, r.exception_allowed, " +
                        "r.status AS rule_status, r.effective_from AS rule_from, r.effective_to AS rule_to, " +
                        "p.status AS pack_status, p.legal_reviewed_at, p.legal_reviewed_by, p.certification_reference " +
                        "FROM hr_compliance_rules r JOIN hr_country_packs p ON p.id = r.country_pack_id " +
                        "WHERE r.id = ?",
                (ResultSet rs) -> rs.next() ? Optional.of(mapRuleState(rs)) : Optional.<OverrideRuleState>empty(),
                Objects.requireNonNull(ruleId, "ruleId"));
    }

    /** Tenant-scoped listing of override requests, newest first (WS5 Task 5). */
    public List<ComplianceOverrideRequest> listByTenant(UUID tenantId, int limit) {
        return jdbc.query(
                "SELECT * FROM hr_compliance_override_requests WHERE tenant_id = ? " +
                        "ORDER BY created_at DESC LIMIT ?",
                (rs, rowNum) -> mapRequest(rs), tenantId, limit);
    }

    public Optional<ComplianceOverrideRequest> findById(UUID tenantId, UUID requestId) {
        return jdbc.query(
                "SELECT id, tenant_id, compliance_rule_id, resource_type, resource_id, " +
                        "requested_value_redacted, compliant_value_redacted, requester_user_id, justification, " +
                        "evidence_reference, approved_by, approval_comment, valid_from, valid_until, status, " +
                        "executed_at, audit_reference, created_at, updated_at " +
                        "FROM hr_compliance_override_requests WHERE tenant_id = ? AND id = ?",
                (ResultSet rs) -> rs.next() ? Optional.of(mapRequest(rs)) : Optional.<ComplianceOverrideRequest>empty(),
                Objects.requireNonNull(tenantId, "tenantId"),
                Objects.requireNonNull(requestId, "requestId"));
    }

    public UUID insertRequest(
            UUID tenantId, UUID complianceRuleId, String resourceType, UUID resourceId,
            JsonNode requestedValueRedacted, JsonNode compliantValueRedacted,
            UUID requesterUserId, String justification, String evidenceReference,
            LocalDate validFrom, LocalDate validUntil) {
        UUID requestId = UUID.randomUUID();
        jdbc.update("INSERT INTO hr_compliance_override_requests " +
                        "(id, tenant_id, compliance_rule_id, resource_type, resource_id, " +
                        "requested_value_redacted, compliant_value_redacted, requester_user_id, justification, " +
                        "evidence_reference, valid_from, valid_until, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, 'PENDING_APPROVAL')",
                requestId, tenantId, complianceRuleId, resourceType, resourceId,
                requestedValueRedacted == null ? "{}" : requestedValueRedacted.toString(),
                compliantValueRedacted == null ? "{}" : compliantValueRedacted.toString(),
                requesterUserId, justification, evidenceReference, validFrom, validUntil);
        return requestId;
    }

    /** Conditional PENDING_APPROVAL -> APPROVED. Returns affected rows (0 = lost race / state moved). */
    public int approveIfPending(UUID tenantId, UUID requestId, UUID approverUserId, String comment) {
        return jdbc.update("UPDATE hr_compliance_override_requests " +
                        "SET status = 'APPROVED', approved_by = ?, approval_comment = ?, updated_at = NOW() " +
                        "WHERE tenant_id = ? AND id = ? AND status = 'PENDING_APPROVAL'",
                approverUserId, comment, tenantId, requestId);
    }

    public int rejectIfPending(UUID tenantId, UUID requestId, String comment) {
        return jdbc.update("UPDATE hr_compliance_override_requests " +
                        "SET status = 'REJECTED', approval_comment = ?, updated_at = NOW() " +
                        "WHERE tenant_id = ? AND id = ? AND status = 'PENDING_APPROVAL'",
                comment, tenantId, requestId);
    }

    public int revokeIfApproved(UUID tenantId, UUID requestId, String comment) {
        return jdbc.update("UPDATE hr_compliance_override_requests " +
                        "SET status = 'REVOKED', approval_comment = ?, updated_at = NOW() " +
                        "WHERE tenant_id = ? AND id = ? AND status = 'APPROVED'",
                comment, tenantId, requestId);
    }

    public int executeIfApproved(UUID tenantId, UUID requestId, String auditReference) {
        return jdbc.update("UPDATE hr_compliance_override_requests " +
                        "SET status = 'EXECUTED', executed_at = NOW(), audit_reference = ?, updated_at = NOW() " +
                        "WHERE tenant_id = ? AND id = ? AND status = 'APPROVED'",
                auditReference, tenantId, requestId);
    }

    /** Lazy APPROVED -> EXPIRED based on the current date. Returns affected rows. */
    public int expireIfPastValidity(UUID tenantId, UUID requestId, LocalDate asOfDate) {
        return jdbc.update("UPDATE hr_compliance_override_requests " +
                        "SET status = 'EXPIRED', updated_at = NOW() " +
                        "WHERE tenant_id = ? AND id = ? AND status = 'APPROVED' " +
                        "AND valid_until IS NOT NULL AND valid_until < ?",
                tenantId, requestId, asOfDate);
    }

    private OverrideRuleState mapRuleState(ResultSet rs) throws SQLException {
        return new OverrideRuleState(
                UUID.fromString(rs.getString("id")),
                rs.getString("rule_code"),
                rs.getString("rule_version"),
                rs.getString("enforcement_level"),
                rs.getBoolean("exception_allowed"),
                rs.getString("rule_status"),
                rs.getDate("rule_from") == null ? null : rs.getDate("rule_from").toLocalDate(),
                rs.getDate("rule_to") == null ? null : rs.getDate("rule_to").toLocalDate(),
                rs.getString("pack_status"),
                rs.getTimestamp("legal_reviewed_at") != null
                        && rs.getString("legal_reviewed_by") != null
                        && !rs.getString("legal_reviewed_by").isBlank()
                        && rs.getString("certification_reference") != null
                        && !rs.getString("certification_reference").isBlank());
    }

    private ComplianceOverrideRequest mapRequest(ResultSet rs) throws SQLException {
        return new ComplianceOverrideRequest(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("tenant_id")),
                UUID.fromString(rs.getString("compliance_rule_id")),
                rs.getString("resource_type"),
                rs.getString("resource_id") == null ? null : UUID.fromString(rs.getString("resource_id")),
                readTree(rs.getString("requested_value_redacted")),
                readTree(rs.getString("compliant_value_redacted")),
                UUID.fromString(rs.getString("requester_user_id")),
                rs.getString("justification"),
                rs.getString("evidence_reference"),
                rs.getString("approved_by") == null ? null : UUID.fromString(rs.getString("approved_by")),
                rs.getString("approval_comment"),
                rs.getDate("valid_from") == null ? null : rs.getDate("valid_from").toLocalDate(),
                rs.getDate("valid_until") == null ? null : rs.getDate("valid_until").toLocalDate(),
                ComplianceOverrideStatus.valueOf(rs.getString("status")),
                toInstant(rs.getTimestamp("executed_at")),
                rs.getString("audit_reference"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private JsonNode readTree(String raw) {
        if (raw == null || raw.isBlank()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            return OBJECT_MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HRM_LEGAL_REVIEW_REQUIRED: unreadable override JSON snapshot", e);
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}