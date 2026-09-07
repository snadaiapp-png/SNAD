package com.sanad.platform.hr.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * Employment-to-IAM access policy (WS4 Task 7).
 *
 * <p>Hard invariant: employment status is NOT user-account status. Only an
 * ACTIVE binding in {@code hr_iam_access_bindings} with
 * {@code access_mode = 'HR_MANAGED'} permits the HR employment lifecycle to
 * affect the linked IAM account. Missing binding FAILS CLOSED. If the linked
 * user has ANOTHER active HR-managed employment requiring access, the account
 * is preserved (deterministic multi-employment rule).</p>
 *
 * <p>All binding/employment reads run in a SHORT tenant-scoped transaction
 * ({@code SET LOCAL app.tenant_id}) so FORCE RLS stays fully enforced — a
 * cross-tenant event can never observe, and therefore never act on, another
 * tenant's bindings.</p>
 */
@Service
public class HrmIamAccessPolicy {

    /** Events this policy evaluates. */
    public static final String EMPLOYEE_ACTIVATED = "HRM.EMPLOYEE.ACTIVATED.v1";
    public static final String EMPLOYEE_SUSPENDED = "HRM.EMPLOYEE.SUSPENDED.v1";
    public static final String EMPLOYEE_TERMINATED = "HRM.EMPLOYEE.TERMINATED.v1";
    public static final String EMPLOYEE_USER_LINKED = "HRM.EMPLOYEE.USER_LINKED.v1";

    /** Deterministic policy outcome. */
    public enum Outcome {
        /** Disable (suspend) the IAM account. */
        DISABLE,
        /** Enable (activate) the IAM account. */
        ENABLE,
        /** No IAM change (unmanaged binding or preserved by another employment). */
        NO_OP,
        /** Missing/inconsistent binding — fail closed, no IAM change. */
        FAIL_CLOSED
    }

    /** Policy decision with evidence. */
    public record Decision(Outcome outcome, UUID bindingId, String reason) {
    }

    private final DataSource dataSource;

    @Autowired
    public HrmIamAccessPolicy(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /**
     * Decides whether the employment lifecycle event may affect the linked
     * IAM account, and how.
     */
    public Decision decide(UUID tenantId, UUID personId, UUID userId, String eventType) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(personId, "personId");
        Objects.requireNonNull(userId, "userId");
        boolean granting = EMPLOYEE_ACTIVATED.equals(eventType) || EMPLOYEE_USER_LINKED.equals(eventType);
        boolean revoking = EMPLOYEE_SUSPENDED.equals(eventType) || EMPLOYEE_TERMINATED.equals(eventType);
        if (!granting && !revoking) {
            throw new IllegalArgumentException("HRM_IAM_POLICY_EVENT_UNSUPPORTED: " + eventType);
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                Decision decision = revoking
                        ? decideRevocation(connection, tenantId, personId, userId)
                        : decideGrant(connection, tenantId, personId, userId);
                connection.commit();
                return decision;
            } catch (SQLException e) {
                connection.rollback();
                throw new IllegalStateException("HRM_IAM_POLICY_EVALUATION_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_IAM_POLICY_EVALUATION_FAILED: " + e.getMessage(), e);
        }
    }

    private Decision decideGrant(Connection connection, UUID tenantId, UUID personId, UUID userId)
            throws SQLException {
        Binding binding = activeBinding(connection, tenantId, personId, userId);
        if (binding == null) {
            return new Decision(Outcome.FAIL_CLOSED, null,
                    "HRM_IAM_BINDING_MISSING: no active hr_iam_access_bindings row for the event subject");
        }
        if (!"HR_MANAGED".equals(binding.accessMode())) {
            return new Decision(Outcome.NO_OP, binding.id(),
                    "HRM_IAM_BINDING_UNMANAGED: employment lifecycle must not affect unmanaged accounts");
        }
        return new Decision(Outcome.ENABLE, binding.id(), "HRM_IAM_BINDING_MANAGED: activation grants access");
    }

    private Decision decideRevocation(Connection connection, UUID tenantId, UUID personId, UUID userId)
            throws SQLException {
        Binding binding = activeBinding(connection, tenantId, personId, userId);
        if (binding == null) {
            return new Decision(Outcome.FAIL_CLOSED, null,
                    "HRM_IAM_BINDING_MISSING: no active hr_iam_access_bindings row for the event subject");
        }
        if (!"HR_MANAGED".equals(binding.accessMode())) {
            return new Decision(Outcome.NO_OP, binding.id(),
                    "HRM_IAM_BINDING_UNMANAGED: unmanaged user is never disabled by HR lifecycle");
        }
        int otherManagedEmployments = countOtherActiveManagedEmployments(connection, tenantId, personId, userId);
        if (otherManagedEmployments > 0) {
            return new Decision(Outcome.NO_OP, binding.id(),
                    "HRM_IAM_ACCESS_PRESERVED: another active HR-managed employment requires the account");
        }
        return new Decision(Outcome.DISABLE, binding.id(),
                "HRM_IAM_BINDING_MANAGED: no other active HR-managed employment requires the account");
    }

    private Binding activeBinding(Connection connection, UUID tenantId, UUID personId, UUID userId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, access_mode FROM hr_iam_access_bindings "
                        + "WHERE tenant_id = ? AND user_id = ? AND person_id = ? AND status = 'ACTIVE' "
                        + "ORDER BY effective_from DESC LIMIT 1")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, userId);
            ps.setObject(3, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Binding(UUID.fromString(rs.getString("id")), rs.getString("access_mode"));
            }
        }
    }

    private int countOtherActiveManagedEmployments(Connection connection, UUID tenantId, UUID personId, UUID userId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM hr_iam_access_bindings b "
                        + "JOIN hr_employees e ON e.tenant_id = b.tenant_id AND e.person_id = b.person_id "
                        + "WHERE b.tenant_id = ? AND b.user_id = ? AND b.person_id <> ? "
                        + "AND b.status = 'ACTIVE' AND b.access_mode = 'HR_MANAGED' AND e.status = 'ACTIVE'")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, userId);
            ps.setObject(3, personId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void setTenantLocal(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }

    private record Binding(UUID id, String accessMode) {
    }
}
