package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Shared JDBC support for CRM-008B ownership persistence.
 *
 * <p>Provides RowMappers, SQL parameter helpers, and common constants.
 * All RowMappers enforce tenant_id presence at the query level (callers
 * must always include AND tenant_id = :tenantId in WHERE clauses).</p>
 */
public final class OwnershipJdbcSupport {

    private OwnershipJdbcSupport() {}

    // ============================================================
    // RowMappers
    // ============================================================

    public static RowMapper<SalesTeam> salesTeamMapper() {
        return (rs, rowNum) -> new SalesTeam(
                getUuid(rs, "id"),
                getUuid(rs, "tenant_id"),
                rs.getString("code"),
                rs.getString("display_name"),
                rs.getString("description"),
                TeamStatus.valueOf(rs.getString("status")),
                getUuid(rs, "manager_user_id"),
                getUuid(rs, "default_queue_id"),
                getUuid(rs, "default_territory_id"),
                getInstant(rs, "created_at"),
                getInstant(rs, "updated_at"),
                getUuid(rs, "created_by"),
                getUuid(rs, "updated_by")
        );
    }

    public static RowMapper<TeamMembership> teamMembershipMapper() {
        return (rs, rowNum) -> new TeamMembership(
                getUuid(rs, "id"),
                getUuid(rs, "tenant_id"),
                getUuid(rs, "team_id"),
                getUuid(rs, "user_id"),
                MembershipRole.valueOf(rs.getString("role")),
                rs.getBoolean("is_primary"),
                MembershipStatus.valueOf(rs.getString("status")),
                getInstant(rs, "joined_at"),
                getInstant(rs, "left_at"),
                rs.getString("left_reason"),
                rs.getInt("capacity_max"),
                rs.getString("metadata"),
                getInstant(rs, "created_at"),
                getInstant(rs, "updated_at"),
                getUuid(rs, "created_by"),
                getUuid(rs, "updated_by")
        );
    }

    public static RowMapper<Queue> queueMapper() {
        return (rs, rowNum) -> new Queue(
                getUuid(rs, "id"),
                getUuid(rs, "tenant_id"),
                rs.getString("code"),
                rs.getString("display_name"),
                QueueRecordType.valueOf(rs.getString("record_type")),
                rs.getString("description"),
                QueueStatus.valueOf(rs.getString("status")),
                rs.getInt("max_items_per_user"),
                getInteger(rs, "sla_minutes"),
                getUuid(rs, "escalation_target_queue_id"),
                getUuid(rs, "default_owner_id"),
                getInstant(rs, "created_at"),
                getInstant(rs, "updated_at"),
                getUuid(rs, "created_by"),
                getUuid(rs, "updated_by")
        );
    }

    public static RowMapper<QueueMembership> queueMembershipMapper() {
        return (rs, rowNum) -> new QueueMembership(
                getUuid(rs, "id"),
                getUuid(rs, "tenant_id"),
                getUuid(rs, "queue_id"),
                getUuid(rs, "user_id"),
                MembershipStatus.valueOf(rs.getString("status")),
                getInstant(rs, "added_at"),
                getInstant(rs, "removed_at"),
                rs.getString("removed_reason"),
                getInstant(rs, "created_at"),
                getInstant(rs, "updated_at"),
                getUuid(rs, "created_by"),
                getUuid(rs, "updated_by")
        );
    }

    public static RowMapper<Territory> territoryMapper() {
        return (rs, rowNum) -> new Territory(
                getUuid(rs, "id"),
                getUuid(rs, "tenant_id"),
                rs.getString("code"),
                rs.getString("display_name"),
                getUuid(rs, "parent_id"),
                rs.getString("description"),
                TerritoryStatus.valueOf(rs.getString("status")),
                TerritoryRuleType.valueOf(rs.getString("rule_type")),
                rs.getString("rule_definition"),
                rs.getInt("priority"),
                getInstant(rs, "created_at"),
                getInstant(rs, "updated_at"),
                getUuid(rs, "created_by"),
                getUuid(rs, "updated_by")
        );
    }

    public static RowMapper<TerritoryAssignment> territoryAssignmentMapper() {
        return (rs, rowNum) -> new TerritoryAssignment(
                getUuid(rs, "id"),
                getUuid(rs, "tenant_id"),
                getUuid(rs, "territory_id"),
                AssigneeType.valueOf(rs.getString("assignee_type")),
                getUuid(rs, "assignee_id"),
                TerritoryAssignmentRole.valueOf(rs.getString("role")),
                rs.getInt("priority"),
                TerritoryAssignmentStatus.valueOf(rs.getString("status")),
                getInstant(rs, "effective_from"),
                getInstant(rs, "effective_to"),
                getInstant(rs, "created_at"),
                getInstant(rs, "updated_at"),
                getUuid(rs, "created_by"),
                getUuid(rs, "updated_by")
        );
    }

    public static RowMapper<AssignmentRule> assignmentRuleMapper() {
        return (rs, rowNum) -> new AssignmentRule(
                getUuid(rs, "id"),
                getUuid(rs, "tenant_id"),
                rs.getString("code"),
                rs.getInt("current_version"),
                RuleStatus.valueOf(rs.getString("status")),
                getInstant(rs, "created_at"),
                getInstant(rs, "updated_at"),
                getUuid(rs, "created_by"),
                getUuid(rs, "updated_by")
        );
    }

    public static RowMapper<AssignmentRuleVersion> assignmentRuleVersionMapper() {
        return (rs, rowNum) -> new AssignmentRuleVersion(
                getUuid(rs, "id"),
                getUuid(rs, "tenant_id"),
                getUuid(rs, "rule_id"),
                rs.getInt("version"),
                rs.getString("display_name"),
                rs.getString("description"),
                AssignmentRecordType.valueOf(rs.getString("record_type")),
                rs.getInt("priority"),
                rs.getString("match_conditions"),
                DistributionMethod.valueOf(rs.getString("distribution_method")),
                getUuid(rs, "target_owner_id"),
                getUuid(rs, "target_team_id"),
                getUuid(rs, "target_queue_id"),
                getUuid(rs, "fallback_owner_id"),
                getInstant(rs, "effective_from"),
                getInstant(rs, "effective_to"),
                RuleStatus.valueOf(rs.getString("status")),
                getUuid(rs, "created_by"),
                getInstant(rs, "created_at")
        );
    }

    public static RowMapper<Assignment> assignmentMapper() {
        return (rs, rowNum) -> new Assignment(
                getUuid(rs, "id"),
                getUuid(rs, "tenant_id"),
                rs.getLong("version"),
                rs.getString("subject_type"),
                getUuid(rs, "subject_id"),
                getUuid(rs, "assigned_user_id"),
                rs.getString("assignment_role"),
                AssignmentStatus.valueOf(rs.getString("status")),
                getInstant(rs, "starts_at"),
                getInstant(rs, "ends_at"),
                rs.getString("reason"),
                rs.getString("owner_type") != null ? OwnerType.valueOf(rs.getString("owner_type")) : null,
                getUuid(rs, "owner_user_id"),
                getUuid(rs, "owner_team_id"),
                getUuid(rs, "owner_queue_id"),
                rs.getString("record_type") != null ? AssignmentRecordType.valueOf(rs.getString("record_type")) : null,
                getUuid(rs, "record_id"),
                getUuid(rs, "assigned_by_rule_id"),
                getUuid(rs, "assigned_by_user_id"),
                getUuid(rs, "correlation_id"),
                rs.getString("workflow_result"),
                getInstant(rs, "effective_from"),
                getInstant(rs, "effective_to"),
                getInstant(rs, "created_at"),
                getInstant(rs, "updated_at"),
                getUuid(rs, "created_by"),
                getUuid(rs, "updated_by")
        );
    }

    public static RowMapper<OwnershipHistory> ownershipHistoryMapper() {
        return (rs, rowNum) -> new OwnershipHistory(
                getUuid(rs, "id"),
                getUuid(rs, "tenant_id"),
                AssignmentRecordType.valueOf(rs.getString("record_type")),
                getUuid(rs, "record_id"),
                rs.getString("from_owner_type") != null ? OwnerType.valueOf(rs.getString("from_owner_type")) : null,
                getUuid(rs, "from_owner_user_id"),
                getUuid(rs, "from_owner_team_id"),
                getUuid(rs, "from_owner_queue_id"),
                OwnerType.valueOf(rs.getString("to_owner_type")),
                getUuid(rs, "to_owner_user_id"),
                getUuid(rs, "to_owner_team_id"),
                getUuid(rs, "to_owner_queue_id"),
                ChangeType.valueOf(rs.getString("change_type")),
                TriggerSource.valueOf(rs.getString("trigger_source")),
                getUuid(rs, "trigger_reference_id"),
                getUuid(rs, "actor_user_id"),
                rs.getString("reason"),
                getUuid(rs, "correlation_id"),
                getInstant(rs, "effective_at"),
                getInstant(rs, "recorded_at")
        );
    }

    public static RowMapper<TransferRequest> transferRequestMapper() {
        return (rs, rowNum) -> new TransferRequest(
                getUuid(rs, "id"),
                getUuid(rs, "tenant_id"),
                AssignmentRecordType.valueOf(rs.getString("record_type")),
                parseUuidArray(rs.getString("record_ids")),
                getUuid(rs, "requester_user_id"),
                getUuid(rs, "current_owner_user_id"),
                getUuid(rs, "proposed_owner_user_id"),
                getUuid(rs, "proposed_owner_team_id"),
                TransferType.valueOf(rs.getString("transfer_type")),
                getInstant(rs, "temporary_end_date"),
                rs.getString("reason"),
                TransferPolicy.valueOf(rs.getString("policy")),
                TransferState.valueOf(rs.getString("state")),
                getInteger(rs, "current_approval_step"),
                getUuid(rs, "workflow_run_id"),
                getInstant(rs, "executed_at"),
                getUuid(rs, "executed_by_user_id"),
                rs.getString("failure_reason"),
                getInstant(rs, "created_at"),
                getInstant(rs, "updated_at")
        );
    }

    public static RowMapper<TransferStep> transferStepMapper() {
        return (rs, rowNum) -> new TransferStep(
                getUuid(rs, "id"),
                getUuid(rs, "tenant_id"),
                getUuid(rs, "transfer_request_id"),
                rs.getInt("step_number"),
                getUuid(rs, "approver_user_id"),
                rs.getString("decision") != null ? TransferStepDecision.valueOf(rs.getString("decision")) : null,
                getInstant(rs, "decided_at"),
                rs.getString("comment"),
                getInstant(rs, "created_at")
        );
    }

    public static RowMapper<AssignmentRuleCounter> assignmentRuleCounterMapper() {
        return (rs, rowNum) -> new AssignmentRuleCounter(
                getUuid(rs, "id"),
                getUuid(rs, "tenant_id"),
                getUuid(rs, "rule_id"),
                rs.getLong("counter"),
                getInstant(rs, "updated_at")
        );
    }

    // ============================================================
    // Helpers
    // ============================================================

    static UUID getUuid(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value != null ? UUID.fromString(value) : null;
    }

    static Instant getInstant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts != null ? ts.toInstant() : null;
    }

    static Integer getInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static java.util.List<UUID> parseUuidArray(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) {
            return java.util.List.of();
        }
        // Simple parse: ["uuid1","uuid2"] -> List<UUID>
        String stripped = json.replaceAll("[\\[\\]\"\\s]", "");
        if (stripped.isEmpty()) return java.util.List.of();
        return java.util.Arrays.stream(stripped.split(","))
                .map(UUID::fromString)
                .toList();
    }

    static String toUuidArrayJson(java.util.List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return "[]";
        return ids.stream()
                .map(id -> "\"" + id + "\"")
                .reduce("[", (a, b) -> a.equals("[") ? a + b : a + "," + b) + "]";
    }

    static SqlParameterSource tenantParam(UUID tenantId) {
        return new MapSqlParameterSource("tenantId", tenantId);
    }
}
