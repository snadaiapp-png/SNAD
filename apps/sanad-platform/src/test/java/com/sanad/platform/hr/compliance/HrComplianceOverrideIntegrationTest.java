package com.sanad.platform.hr.compliance;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G0 / WS3 / Task 1 RED contract for tenant-owned compliance evidence
 * and database-level four-eyes invariants.
 *
 * <p>WS3 Task 4 extends this suite with the governed compliance override
 * workflow and four-eyes approval matrix (request/approve/reject/revoke/
 * execute, expiration, rule revalidation, tenant binding, concurrency).
 * Task 4 behavior is exercised through reflection so a RED run fails only
 * because the Task 4 application classes are missing — never because of a
 * compilation error (same clean-RED convention as HrComplianceEngineTest).</p>
 */
class HrComplianceOverrideIntegrationTest {

    private static final String OVERRIDE_APP = "com.sanad.platform.hr.compliance.application";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static String isolatedUrl;

    private Connection connection;
    private DataSource dataSource;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available = false;
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
            try (Connection c = ds.getConnection()) {
                available = c.isValid(5);
            }
        } catch (Throwable ignored) {
        }
        Assumptions.assumeTrue(available, "PostgreSQL Direct is not available");
        MigrationTestSchemaSupport.ensureDatabase(DB_URL, DB_USER, DB_PASSWORD);
        isolatedUrl = MigrationTestSchemaSupport.getIsolatedJdbcUrl(DB_URL);
    }

    @BeforeEach
    void migrateFreshDatabase() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(isolatedUrl, DB_USER, DB_PASSWORD);
        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .baselineOnMigrate(true)
                .cleanDisabled(false)
                .validateOnMigrate(false)
                .load();
        flyway.clean();
        flyway.migrate();
        connection = ds.getConnection();
        connection.setAutoCommit(true);
        dataSource = ds;
    }

    @Test
    void tenantOwnedComplianceTablesUseForcedFailClosedRls() throws Exception {
        for (String table : new String[]{"hr_compliance_decisions", "hr_compliance_override_requests"}) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = ?")) {
                ps.setString(1, table);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("%s must exist", table).isTrue();
                    assertThat(rs.getBoolean("relrowsecurity")).as("%s ENABLE RLS", table).isTrue();
                    assertThat(rs.getBoolean("relforcerowsecurity")).as("%s FORCE RLS", table).isTrue();
                }
            }
        }
    }

    @Test
    void overrideFourEyesConstraintRejectsRequesterAsApprover() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID requester = UUID.randomUUID();
        UUID ruleId = seedTenantUserAndRule(tenantId, requester);
        setTenant(tenantId);

        assertThatThrownBy(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_compliance_override_requests " +
                            "(tenant_id, compliance_rule_id, resource_type, resource_id, " +
                            "requested_value_redacted, compliant_value_redacted, requester_user_id, " +
                            "justification, approved_by, valid_from, status) " +
                            "VALUES (?, ?, 'EMPLOYMENT', ?, '{}'::jsonb, '{}'::jsonb, ?, 'test', ?, ?, 'APPROVED')")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, ruleId);
                ps.setObject(3, UUID.randomUUID());
                ps.setObject(4, requester);
                ps.setObject(5, requester);
                ps.setObject(6, LocalDate.of(2026, 1, 1));
                ps.executeUpdate();
            }
        }).isInstanceOf(SQLException.class)
          .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
    }

    private UUID seedTenantUserAndRule(UUID tenantId, UUID userId) throws Exception {
        resetTenant();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "WS3-" + tenantId);
            ps.setString(3, "ws3-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO users (id, tenant_id, email, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, userId);
            ps.setObject(2, tenantId);
            ps.setString(3, userId + "@example.invalid");
            ps.executeUpdate();
        }
        UUID packId = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_country_packs (id, country_code, pack_code, pack_version, status, effective_from) " +
                        "VALUES (?, 'SA', 'OVERRIDE_TEST', '1', 'ACTIVE', DATE '2026-01-01')")) {
            ps.setObject(1, packId);
            ps.executeUpdate();
        }
        UUID ruleId = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_compliance_rules " +
                        "(id, country_pack_id, rule_code, rule_version, operation_code, enforcement_level, " +
                        "exception_allowed, official_source_uri, legal_citation, source_snapshot_sha256, " +
                        "effective_from, last_legal_review_at, reviewed_by, status) " +
                        "VALUES (?, ?, 'TEST_RULE', '1', 'TEST_OPERATION', 'MANDATORY_WITH_EXCEPTION', TRUE, " +
                        "'https://example.invalid/source', 'TEST', ?, DATE '2026-01-01', NOW(), 'test-reviewer', 'ACTIVE')")) {
            ps.setObject(1, ruleId);
            ps.setObject(2, packId);
            ps.setString(3, "0".repeat(64));
            ps.executeUpdate();
        }
        return ruleId;
    }

    private void setTenant(UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }

    private void resetTenant() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', '', false)")) {
            ps.execute();
        }
    }

    // ============================================================
    // WS3 TASK 4 — GOVERNED OVERRIDE / FOUR-EYES RED MATRIX
    // ============================================================

    @Test
    void requesterCannotApproveOwnOverride() throws Throwable {
        UUID tenantId = seedOverrideTenant();
        UUID requester = seedOverrideUser(tenantId);
        seedOverrideUser(tenantId); // independent approver exists
        UUID packId = seedActiveOverridePack("SA", "WS3-T4-PACK-A");
        UUID ruleId = seedRuleWith(packId, "MANDATORY_WITH_EXCEPTION", true);
        setTenant(tenantId);
        Object service = newOverrideService(allowAllAuthorizationPort(), noopAuditPort(), noopEventPort());

        UUID requestId = requestOverride(service, tenantId, requester, ruleId);

        assertThatThrownBy(() -> approve(service, tenantId, requestId, requester, "self approval attempt"))
                .as("four-eyes: requester must never approve own override")
                .hasMessageContaining("HRM_OVERRIDE_SELF_APPROVAL_DENIED");
        assertThat(overrideStatus(requestId)).isEqualTo("PENDING_APPROVAL");
        assertThat(overrideApprovedBy(requestId)).isNull();
    }

    @Test
    void hardRuleCannotCreateOverrideRequest() throws Throwable {
        UUID tenantId = seedOverrideTenant();
        UUID requester = seedOverrideUser(tenantId);
        UUID packId = seedActiveOverridePack("SA", "WS3-T4-PACK-B");
        UUID ruleId = seedRuleWith(packId, "MANDATORY_HARD", false);
        setTenant(tenantId);
        Object service = newOverrideService(allowAllAuthorizationPort(), noopAuditPort(), noopEventPort());

        assertThatThrownBy(() -> requestOverride(service, tenantId, requester, ruleId))
                .as("MANDATORY_HARD rules can never enter the governed override flow")
                .hasMessageContaining("HRM_COMPLIANCE_BLOCKED");
        assertThat(countOverrideRequests(tenantId)).isZero();
    }

    @Test
    void mandatoryWithExceptionWithExceptionAllowedEntersGovernedFlow() throws Throwable {
        UUID tenantId = seedOverrideTenant();
        UUID requester = seedOverrideUser(tenantId);
        UUID packId = seedActiveOverridePack("SA", "WS3-T4-PACK-C");
        UUID ruleId = seedRuleWith(packId, "MANDATORY_WITH_EXCEPTION", true);
        setTenant(tenantId);
        Object service = newOverrideService(allowAllAuthorizationPort(), noopAuditPort(), noopEventPort());

        UUID requestId = requestOverride(service, tenantId, requester, ruleId);

        assertThat(requestId).isNotNull();
        assertThat(overrideStatus(requestId)).isEqualTo("PENDING_APPROVAL");
        assertThat(overrideRuleId(requestId)).isEqualTo(ruleId);
    }

    @Test
    void mandatoryWithExceptionWithoutExceptionPathIsBlocked() throws Throwable {
        UUID tenantId = seedOverrideTenant();
        UUID requester = seedOverrideUser(tenantId);
        UUID packId = seedActiveOverridePack("SA", "WS3-T4-PACK-D");
        UUID ruleId = seedRuleWith(packId, "MANDATORY_WITH_EXCEPTION", false);
        setTenant(tenantId);
        Object service = newOverrideService(allowAllAuthorizationPort(), noopAuditPort(), noopEventPort());

        assertThatThrownBy(() -> requestOverride(service, tenantId, requester, ruleId))
                .as("exception_allowed=false must not permit the override path")
                .hasMessageContaining("HRM_COMPLIANCE_BLOCKED");
        assertThat(countOverrideRequests(tenantId)).isZero();
    }

    @Test
    void approvalWithoutApproveCapabilityIsDenied() throws Throwable {
        UUID tenantId = seedOverrideTenant();
        UUID requester = seedOverrideUser(tenantId);
        UUID approver = seedOverrideUser(tenantId);
        UUID packId = seedActiveOverridePack("SA", "WS3-T4-PACK-E");
        UUID ruleId = seedRuleWith(packId, "MANDATORY_WITH_EXCEPTION", true);
        setTenant(tenantId);
        Object service = newOverrideService(denyingAuthorizationPort(), noopAuditPort(), noopEventPort());

        UUID requestId = requestOverride(service, tenantId, requester, ruleId);

        assertThatThrownBy(() -> approve(service, tenantId, requestId, approver, "no capability"))
                .as("approval requires HRM.COMPLIANCE_OVERRIDE.APPROVE through the authorization port")
                .hasMessageContaining("HRM_SCOPE_DENIED");
        assertThat(overrideStatus(requestId)).isEqualTo("PENDING_APPROVAL");
        assertThat(overrideApprovedBy(requestId)).isNull();
    }

    @Test
    void approvalByDifferentAuthorizedUserIsApproved() throws Throwable {
        UUID tenantId = seedOverrideTenant();
        UUID requester = seedOverrideUser(tenantId);
        UUID approver = seedOverrideUser(tenantId);
        UUID packId = seedActiveOverridePack("SA", "WS3-T4-PACK-F");
        UUID ruleId = seedRuleWith(packId, "MANDATORY_WITH_EXCEPTION", true);
        setTenant(tenantId);
        Object service = newOverrideService(allowAllAuthorizationPort(), noopAuditPort(), noopEventPort());

        UUID requestId = requestOverride(service, tenantId, requester, ruleId);
        Object approved = approve(service, tenantId, requestId, approver, "governed exception approved");

        assertThat(overrideStatus(requestId)).isEqualTo("APPROVED");
        assertThat(overrideApprovedBy(requestId)).isEqualTo(approver);
        assertThat(overrideApprovalComment(requestId)).isEqualTo("governed exception approved");
        assertThat(statusOfReturnedRequest(approved)).isEqualTo("APPROVED");
    }

    @Test
    void expiredOverrideCannotAuthorizeAction() throws Throwable {
        UUID tenantId = seedOverrideTenant();
        UUID requester = seedOverrideUser(tenantId);
        UUID approver = seedOverrideUser(tenantId);
        UUID packId = seedActiveOverridePack("SA", "WS3-T4-PACK-G");
        UUID ruleId = seedRuleWith(packId, "MANDATORY_WITH_EXCEPTION", true);
        setTenant(tenantId);
        Object service = newOverrideService(allowAllAuthorizationPort(), noopAuditPort(), noopEventPort());

        UUID requestId = requestOverrideWindowed(service, tenantId, requester, ruleId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        approve(service, tenantId, requestId, approver, "approved then expired");

        boolean stillAuthorizes = authorizes(service, tenantId, requestId, ruleId,
                "EMPLOYMENT", UUID.randomUUID(), LocalDate.of(2026, 2, 1));

        assertThat(stillAuthorizes).as("expired override cannot authorize action").isFalse();
        assertThat(overrideStatus(requestId)).as("APPROVED -> EXPIRED transition must be recorded").isEqualTo("EXPIRED");
    }

    @Test
    void revokedOverrideCannotAuthorizeAction() throws Throwable {
        UUID tenantId = seedOverrideTenant();
        UUID requester = seedOverrideUser(tenantId);
        UUID approver = seedOverrideUser(tenantId);
        UUID packId = seedActiveOverridePack("SA", "WS3-T4-PACK-H");
        UUID ruleId = seedRuleWith(packId, "MANDATORY_WITH_EXCEPTION", true);
        setTenant(tenantId);
        Object service = newOverrideService(allowAllAuthorizationPort(), noopAuditPort(), noopEventPort());

        UUID requestId = requestOverride(service, tenantId, requester, ruleId);
        approve(service, tenantId, requestId, approver, "approve then revoke");
        revoke(service, tenantId, requestId, approver, "withdrawn by governance");

        assertThat(authorizes(service, tenantId, requestId, ruleId,
                "EMPLOYMENT", UUID.randomUUID(), LocalDate.of(2026, 9, 15))).isFalse();
        assertThat(overrideStatus(requestId)).isEqualTo("REVOKED");
    }

    @Test
    void rejectedOverrideCannotAuthorizeAction() throws Throwable {
        UUID tenantId = seedOverrideTenant();
        UUID requester = seedOverrideUser(tenantId);
        UUID approver = seedOverrideUser(tenantId);
        UUID packId = seedActiveOverridePack("SA", "WS3-T4-PACK-I");
        UUID ruleId = seedRuleWith(packId, "MANDATORY_WITH_EXCEPTION", true);
        setTenant(tenantId);
        Object service = newOverrideService(allowAllAuthorizationPort(), noopAuditPort(), noopEventPort());

        UUID requestId = requestOverride(service, tenantId, requester, ruleId);
        reject(service, tenantId, requestId, approver, "not justified");

        assertThat(authorizes(service, tenantId, requestId, ruleId,
                "EMPLOYMENT", UUID.randomUUID(), LocalDate.of(2026, 9, 15))).isFalse();
        assertThat(overrideStatus(requestId)).isEqualTo("REJECTED");
    }

    @Test
    void pendingOverrideCannotAuthorizeAction() throws Throwable {
        UUID tenantId = seedOverrideTenant();
        UUID requester = seedOverrideUser(tenantId);
        UUID packId = seedActiveOverridePack("SA", "WS3-T4-PACK-J");
        UUID ruleId = seedRuleWith(packId, "MANDATORY_WITH_EXCEPTION", true);
        setTenant(tenantId);
        Object service = newOverrideService(allowAllAuthorizationPort(), noopAuditPort(), noopEventPort());

        UUID requestId = requestOverride(service, tenantId, requester, ruleId);

        assertThat(authorizes(service, tenantId, requestId, ruleId,
                "EMPLOYMENT", UUID.randomUUID(), LocalDate.of(2026, 9, 15))).isFalse();
        assertThat(overrideStatus(requestId)).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void overrideAppliesOnlyToExactTenantRuleResourceAndWindow() throws Throwable {
        UUID tenantId = seedOverrideTenant();
        UUID requester = seedOverrideUser(tenantId);
        UUID approver = seedOverrideUser(tenantId);
        UUID packId = seedActiveOverridePack("SA", "WS3-T4-PACK-K");
        UUID ruleId = seedRuleWith(packId, "MANDATORY_WITH_EXCEPTION", true);
        setTenant(tenantId);
        Object service = newOverrideService(allowAllAuthorizationPort(), noopAuditPort(), noopEventPort());

        UUID resourceId = UUID.randomUUID();
        UUID requestId = requestOverrideForResource(service, tenantId, requester, ruleId,
                "EMPLOYMENT", resourceId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        approve(service, tenantId, requestId, approver, "scoped");

        assertThat(authorizes(service, tenantId, requestId, ruleId, "EMPLOYMENT", resourceId,
                LocalDate.of(2026, 9, 15))).as("exact match authorizes").isTrue();
        assertThat(authorizes(service, tenantId, requestId, ruleId, "EMPLOYMENT", UUID.randomUUID(),
                LocalDate.of(2026, 9, 15))).as("different resource must not match").isFalse();
        assertThat(authorizes(service, tenantId, requestId, UUID.randomUUID(), "EMPLOYMENT", resourceId,
                LocalDate.of(2026, 9, 15))).as("different rule must not match").isFalse();
        assertThat(authorizes(service, tenantId, requestId, ruleId, "PAYROLL", resourceId,
                LocalDate.of(2026, 9, 15))).as("different resource type must not match").isFalse();
    }

    @Test
    void overrideFromAnotherTenantIsInvisibleAndDenied() throws Throwable {
        UUID tenantA = seedOverrideTenant();
        UUID requesterA = seedOverrideUser(tenantA);
        UUID approverA = seedOverrideUser(tenantA);
        UUID packId = seedActiveOverridePack("SA", "WS3-T4-PACK-L");
        UUID ruleId = seedRuleWith(packId, "MANDATORY_WITH_EXCEPTION", true);
        setTenant(tenantA);
        Object service = newOverrideService(allowAllAuthorizationPort(), noopAuditPort(), noopEventPort());

        UUID requestId = requestOverride(service, tenantA, requesterA, ruleId);
        approve(service, tenantA, requestId, approverA, "tenant A approval");

        UUID tenantB = seedOverrideTenant();
        UUID approverB = seedOverrideUser(tenantB);
        setTenant(tenantB);

        assertThat(authorizes(service, tenantB, requestId, ruleId, "EMPLOYMENT", UUID.randomUUID(),
                LocalDate.of(2026, 9, 15))).as("cross-tenant override must be invisible").isFalse();
        assertThatThrownBy(() -> approve(service, tenantB, requestId, approverB, "cross tenant"))
                .as("cross-tenant approval must fail closed").hasMessageContaining("HRM_OVERRIDE_NOT_FOUND");
    }

    @Test
    void ruleBecomingHardBeforeExecutionInvalidatesPriorApproval() throws Throwable {
        UUID tenantId = seedOverrideTenant();
        UUID requester = seedOverrideUser(tenantId);
        UUID approver = seedOverrideUser(tenantId);
        UUID packId = seedActiveOverridePack("SA", "WS3-T4-PACK-M");
        UUID ruleId = seedRuleWith(packId, "MANDATORY_WITH_EXCEPTION", true);
        setTenant(tenantId);
        Object service = newOverrideService(allowAllAuthorizationPort(), noopAuditPort(), noopEventPort());

        UUID requestId = requestOverride(service, tenantId, requester, ruleId);
        approve(service, tenantId, requestId, approver, "approved while exception was legal");

        // Underlying rule hardens before execution.
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE hr_compliance_rules SET enforcement_level = 'MANDATORY_HARD' WHERE id = ?")) {
            ps.setObject(1, ruleId);
            ps.executeUpdate();
        }

        assertThat(authorizes(service, tenantId, requestId, ruleId, "EMPLOYMENT", UUID.randomUUID(),
                LocalDate.of(2026, 9, 15))).as("prior approval must NOT bypass a hardened rule").isFalse();
    }

    @Test
    void suspendedRuleOrSuspendedPackInvalidatesPriorApproval() throws Throwable {
        UUID tenantId = seedOverrideTenant();
        UUID requester = seedOverrideUser(tenantId);
        UUID approver = seedOverrideUser(tenantId);
        UUID packId = seedActiveOverridePack("SA", "WS3-T4-PACK-N");
        UUID ruleId = seedRuleWith(packId, "MANDATORY_WITH_EXCEPTION", true);
        setTenant(tenantId);
        Object service = newOverrideService(allowAllAuthorizationPort(), noopAuditPort(), noopEventPort());

        UUID requestId = requestOverride(service, tenantId, requester, ruleId);
        approve(service, tenantId, requestId, approver, "approved while active");

        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE hr_compliance_rules SET status = 'SUSPENDED' WHERE id = ?")) {
            ps.setObject(1, ruleId);
            ps.executeUpdate();
        }
        assertThat(authorizes(service, tenantId, requestId, ruleId, "EMPLOYMENT", UUID.randomUUID(),
                LocalDate.of(2026, 9, 15))).as("suspended rule requires a fresh compliance decision").isFalse();

        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE hr_compliance_rules SET status = 'ACTIVE' WHERE id = ?")) {
            ps.setObject(1, ruleId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE hr_country_packs SET status = 'SUSPENDED' WHERE id = ?")) {
            ps.setObject(1, packId);
            ps.executeUpdate();
        }
        assertThat(authorizes(service, tenantId, requestId, ruleId, "EMPLOYMENT", UUID.randomUUID(),
                LocalDate.of(2026, 9, 15))).as("suspended pack requires a fresh compliance decision").isFalse();
    }

    @Test
    void actionDateOutsideApprovedValidityWindowIsDenied() throws Throwable {
        UUID tenantId = seedOverrideTenant();
        UUID requester = seedOverrideUser(tenantId);
        UUID approver = seedOverrideUser(tenantId);
        UUID packId = seedActiveOverridePack("SA", "WS3-T4-PACK-O");
        UUID ruleId = seedRuleWith(packId, "MANDATORY_WITH_EXCEPTION", true);
        setTenant(tenantId);
        Object service = newOverrideService(allowAllAuthorizationPort(), noopAuditPort(), noopEventPort());

        UUID resourceId = UUID.randomUUID();
        UUID requestId = requestOverrideForResource(service, tenantId, requester, ruleId,
                "EMPLOYMENT", resourceId, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 20));
        approve(service, tenantId, requestId, approver, "windowed");

        assertThat(authorizes(service, tenantId, requestId, ruleId, "EMPLOYMENT", UUID.randomUUID(),
                LocalDate.of(2026, 9, 1))).as("before valid_from must be denied").isFalse();
        assertThat(authorizes(service, tenantId, requestId, ruleId, "EMPLOYMENT", resourceId,
                LocalDate.of(2026, 9, 15))).as("inside window must authorize").isTrue();
    }

    @Test
    void duplicateApprovalMustNotCreateTwoLogicalApprovals() throws Throwable {
        UUID tenantId = seedOverrideTenant();
        UUID requester = seedOverrideUser(tenantId);
        UUID approver = seedOverrideUser(tenantId);
        UUID packId = seedActiveOverridePack("SA", "WS3-T4-PACK-P");
        UUID ruleId = seedRuleWith(packId, "MANDATORY_WITH_EXCEPTION", true);
        setTenant(tenantId);
        Object service = newOverrideService(allowAllAuthorizationPort(), noopAuditPort(), noopEventPort());

        UUID requestId = requestOverride(service, tenantId, requester, ruleId);
        approve(service, tenantId, requestId, approver, "first approval");

        assertThatThrownBy(() -> approve(service, tenantId, requestId, approver, "duplicate approval"))
                .as("duplicate approval must resolve to a deterministic domain conflict")
                .hasMessageContaining("HRM_OVERRIDE_INVALID_TRANSITION");
        assertThat(overrideStatus(requestId)).isEqualTo("APPROVED");
        assertThat(overrideApprovalComment(requestId)).isEqualTo("first approval");
        assertThat(overrideApproverCount(requestId)).isEqualTo(1);
    }

    // ============================================================
    // WS3 TASK 4 — REFLECTION PLUMBING + FIXTURES
    // ============================================================

    private Object newOverrideService(Object authorizationPort, Object auditPort, Object eventPort) throws Exception {
        Class<?> serviceClass = Class.forName(OVERRIDE_APP + ".ComplianceOverrideService");
        Class<?> authClass = Class.forName(OVERRIDE_APP + ".ComplianceOverrideAuthorizationPort");
        Class<?> auditClass = Class.forName(OVERRIDE_APP + ".ComplianceAuditPort");
        Class<?> eventClass = Class.forName(OVERRIDE_APP + ".ComplianceEventPort");
        return serviceClass.getConstructor(DataSource.class, authClass, auditClass, eventClass)
                .newInstance(dataSource, authorizationPort, auditPort, eventPort);
    }

    private Object allowAllAuthorizationPort() throws Exception {
        Class<?> portClass = Class.forName(OVERRIDE_APP + ".ComplianceOverrideAuthorizationPort");
        return Proxy.newProxyInstance(portClass.getClassLoader(), new Class<?>[]{portClass},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "requireApprovalAuthorization": return null;
                        case "toString": return "AllowAllAuthorizationPort";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        default: throw new IllegalStateException("Unexpected: " + method.getName());
                    }
                });
    }

    private Object denyingAuthorizationPort() throws Exception {
        Class<?> portClass = Class.forName(OVERRIDE_APP + ".ComplianceOverrideAuthorizationPort");
        return Proxy.newProxyInstance(portClass.getClassLoader(), new Class<?>[]{portClass},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "requireApprovalAuthorization":
                            throw new IllegalStateException("HRM_SCOPE_DENIED: capability/scope denied by test");
                        case "toString": return "DenyingAuthorizationPort";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        default: throw new IllegalStateException("Unexpected: " + method.getName());
                    }
                });
    }

    private Object noopAuditPort() throws Exception {
        Class<?> portClass = Class.forName(OVERRIDE_APP + ".ComplianceAuditPort");
        return Proxy.newProxyInstance(portClass.getClassLoader(), new Class<?>[]{portClass},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "recordOverrideAction": return null;
                        case "toString": return "NoopAuditPort";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        default: throw new IllegalStateException("Unexpected: " + method.getName());
                    }
                });
    }

    private Object noopEventPort() throws Exception {
        Class<?> portClass = Class.forName(OVERRIDE_APP + ".ComplianceEventPort");
        return Proxy.newProxyInstance(portClass.getClassLoader(), new Class<?>[]{portClass},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "recordOverrideEvent": return null;
                        case "toString": return "NoopEventPort";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        default: throw new IllegalStateException("Unexpected: " + method.getName());
                    }
                });
    }

    private UUID requestOverride(Object service, UUID tenantId, UUID requester, UUID ruleId) throws Throwable {
        return requestOverrideForResource(service, tenantId, requester, ruleId, "EMPLOYMENT",
                UUID.randomUUID(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    }

    private UUID requestOverrideWindowed(Object service, UUID tenantId, UUID requester, UUID ruleId,
                                         LocalDate validFrom, LocalDate validUntil) throws Throwable {
        return requestOverrideForResource(service, tenantId, requester, ruleId, "EMPLOYMENT",
                UUID.randomUUID(), validFrom, validUntil);
    }

    private UUID requestOverrideForResource(Object service, UUID tenantId, UUID requester, UUID ruleId,
                                            String resourceType, UUID resourceId,
                                            LocalDate validFrom, LocalDate validUntil) throws Throwable {
        Class<?> serviceClass = service.getClass();
        Class<?> contextClass = Class.forName("com.sanad.platform.hr.compliance.domain.HrCommandContext");
        Class<?> resourceClass = Class.forName("com.sanad.platform.hr.compliance.domain.ComplianceResource");
        Object context = contextClass.getDeclaredConstructor(UUID.class, UUID.class, UUID.class, UUID.class)
                .newInstance(tenantId, UUID.randomUUID(), requester, null);
        Object resource = resourceClass.getDeclaredConstructor(String.class, UUID.class)
                .newInstance(resourceType, resourceId);
        Method request = serviceClass.getMethod("requestOverride",
                contextClass, UUID.class, resourceClass,
                String.class, String.class, JsonNode.class, JsonNode.class,
                LocalDate.class, LocalDate.class);
        try {
            return (UUID) request.invoke(service, context, ruleId, resource,
                    "operational necessity documented by test", "TEST-EVIDENCE-001",
                    JSON.readTree("{\"maxHoursPerWeek\":48}"), JSON.readTree("{\"maxHoursPerWeek\":40}"),
                    validFrom, validUntil);
        } catch (InvocationTargetException e) {
            throw e.getCause() == null ? e : e.getCause();
        }
    }

    private Object approve(Object service, UUID tenantId, UUID requestId, UUID approver, String comment) throws Throwable {
        Method approve = service.getClass().getMethod("approve", UUID.class, UUID.class, UUID.class, String.class);
        try {
            return approve.invoke(service, tenantId, requestId, approver, comment);
        } catch (InvocationTargetException e) {
            throw e.getCause() == null ? e : e.getCause();
        }
    }

    private Object reject(Object service, UUID tenantId, UUID requestId, UUID actor, String comment) throws Throwable {
        Method reject = service.getClass().getMethod("reject", UUID.class, UUID.class, UUID.class, String.class);
        try {
            return reject.invoke(service, tenantId, requestId, actor, comment);
        } catch (InvocationTargetException e) {
            throw e.getCause() == null ? e : e.getCause();
        }
    }

    private Object revoke(Object service, UUID tenantId, UUID requestId, UUID actor, String comment) throws Throwable {
        Method revoke = service.getClass().getMethod("revoke", UUID.class, UUID.class, UUID.class, String.class);
        try {
            return revoke.invoke(service, tenantId, requestId, actor, comment);
        } catch (InvocationTargetException e) {
            throw e.getCause() == null ? e : e.getCause();
        }
    }

    private boolean authorizes(Object service, UUID tenantId, UUID requestId, UUID ruleId,
                               String resourceType, UUID resourceId, LocalDate actionDate) throws Throwable {
        Method authorizes = service.getClass().getMethod("authorizes",
                UUID.class, UUID.class, UUID.class, String.class, UUID.class, LocalDate.class);
        try {
            return (Boolean) authorizes.invoke(service, tenantId, requestId, ruleId, resourceType, resourceId, actionDate);
        } catch (InvocationTargetException e) {
            throw e.getCause() == null ? e : e.getCause();
        }
    }

    private String statusOfReturnedRequest(Object request) throws Exception {
        Object status = request.getClass().getMethod("status").invoke(request);
        return String.valueOf(status);
    }

    private String overrideStatus(UUID requestId) throws Exception {
        return queryScalar("SELECT status FROM hr_compliance_override_requests WHERE id = '" + requestId + "'");
    }

    private UUID overrideApprovedBy(UUID requestId) throws Exception {
        String raw = queryScalar("SELECT approved_by::text FROM hr_compliance_override_requests WHERE id = '" + requestId + "'");
        return raw == null ? null : UUID.fromString(raw);
    }

    private String overrideApprovalComment(UUID requestId) throws Exception {
        return queryScalar("SELECT approval_comment FROM hr_compliance_override_requests WHERE id = '" + requestId + "'");
    }

    private UUID overrideRuleId(UUID requestId) throws Exception {
        return UUID.fromString(queryScalar(
                "SELECT compliance_rule_id::text FROM hr_compliance_override_requests WHERE id = '" + requestId + "'"));
    }

    private int overrideApproverCount(UUID requestId) throws Exception {
        return Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_compliance_override_requests WHERE id = '" + requestId + "' AND approved_by IS NOT NULL"));
    }

    private int countOverrideRequests(UUID tenantId) throws Exception {
        return Integer.parseInt(queryScalar(
                "SELECT COUNT(*) FROM hr_compliance_override_requests WHERE tenant_id = '" + tenantId + "'"));
    }

    private String queryScalar(String sql) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getString(1);
        }
    }

    private UUID seedOverrideTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        resetTenant();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "WS3-T4-" + tenantId);
            ps.setString(3, "ws3t4-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        setTenant(tenantId);
        return tenantId;
    }

    private UUID seedOverrideUser(UUID tenantId) throws Exception {
        UUID userId = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO users (id, tenant_id, email, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, userId);
            ps.setObject(2, tenantId);
            ps.setString(3, userId + "@override-test.example.invalid");
            ps.executeUpdate();
        }
        return userId;
    }

    private UUID seedActiveOverridePack(String country, String packCode) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_country_packs (country_code, pack_code, pack_version, status, effective_from, " +
                        "legal_reviewed_at, legal_reviewed_by, certification_reference) " +
                        "VALUES (?, ?, '1', 'ACTIVE', DATE '2026-01-01', NOW(), 'legal-review', 'TEST-CERT') RETURNING id")) {
            ps.setString(1, country);
            ps.setString(2, packCode);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return UUID.fromString(rs.getString(1));
            }
        }
    }

    private UUID seedRuleWith(UUID packId, String enforcementLevel, boolean exceptionAllowed) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_compliance_rules (country_pack_id, rule_code, rule_version, operation_code, " +
                        "enforcement_level, exception_allowed, parameters, official_source_uri, legal_citation, " +
                        "source_snapshot_sha256, effective_from, last_legal_review_at, reviewed_by, status) " +
                        "VALUES (?, ?, '1', 'HRM.STATUTORY.LOCAL_ACTION', ?, ?, '{}'::jsonb, " +
                        "'https://official-source.test/rule', 'Test citation', REPEAT('a', 64), " +
                        "DATE '2026-01-01', NOW(), 'legal-review', 'ACTIVE') RETURNING id")) {
            ps.setObject(1, packId);
            ps.setString(2, "T4_RULE_" + packId.toString().substring(0, 8));
            ps.setString(3, enforcementLevel);
            ps.setBoolean(4, exceptionAllowed);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return UUID.fromString(rs.getString(1));
            }
        }
    }
}
