package com.sanad.platform.hr.recruitment.application;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.workflow.application.WorkflowExecutionService;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G1 T7 CLOSURE BATTERY (directive §8/§10 — T7-A26, RBAC/SoD matrix):
 *
 * <ul>
 *   <li><b>T7-A26</b> same idempotency key + DIFFERENT fingerprint → fail
 *       closed at the engine boundary: the engine's partial unique idempotency
 *       index must refuse a second workflow instance for the same
 *       (tenant, trigger, definition, key) — no cross-entity reuse, no silent
 *       second instance;</li>
 *   <li><b>§8 RBAC</b> OFFER.MANAGE / OFFER.EXTEND / OFFER.APPROVE are
 *       pairwise non-implicating; the approval capability is NOT an HRM-side
 *       mutation shortcut; OPENING.MANAGE and OPENING.PUBLISH are
 *       non-implicating;</li>
 *   <li><b>§8 SoD</b> an approver-only principal cannot extend; a
 *       cross-tenant principal cannot reuse its authority.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("local")
class HrT7IdempotencyConflictAndRbacTest {

    private static final String CAP_APPROVE = "HRM.RECRUITMENT.OFFER.APPROVE";

    @Autowired
    private HrOfferService offerService;

    @Autowired
    private HrJobOpeningService openingService;

    @Autowired
    private WorkflowExecutionService executionService;

    @Autowired
    private javax.sql.DataSource dataSource;

    private UUID tenantId;
    private UUID submitterId;
    private UUID approverUserId;
    private UUID applicationId;
    private final Timestamp now = Timestamp.from(Instant.now());

    // ===== tenant-scoped seed/query helpers (committed; FORCE-RLS GUC set) =====

    private void seed(String sql, Object... args) {
        try (java.sql.Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (var st = c.createStatement()) {
                st.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
            }
            try (var ps = c.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) {
                    ps.setObject(i + 1, args[i]);
                }
                ps.executeUpdate();
            }
            c.commit();
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T qObj(String sql, Class<T> type, Object... args) {
        try (java.sql.Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (var st = c.createStatement()) {
                st.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
            }
            try (var ps = c.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) {
                    ps.setObject(i + 1, args[i]);
                }
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    Object raw = rs.getObject(1);
                    if (raw == null) {
                        c.commit();
                        return null;
                    }
                    T value;
                    if (type == Integer.class) {
                        value = (T) Integer.valueOf(((Number) raw).intValue());
                    } else {
                        value = type.cast(raw);
                    }
                    c.commit();
                    return value;
                }
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        tenantId = UUID.randomUUID();
        submitterId = UUID.randomUUID();
        approverUserId = UUID.randomUUID();

        seed("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                + "VALUES (?, 'T7 Idem RBAC', ?, 'ACTIVE', ?, ?)",
                tenantId, "t7ir-" + tenantId.toString().substring(0, 8), now, now);
        for (UUID u : new UUID[]{submitterId, approverUserId}) {
            seed("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at) "
                            + "VALUES (?, ?, ?, 'Idem RBAC Actor', 'ACTIVE', 'x', ?, ?)",
                    u, tenantId, "t7q-" + u.toString().substring(0, 8) + "@t", now, now);
        }
        seed("""
                INSERT INTO hr_employees (id, tenant_id, user_id, employee_number, first_name, last_name,
                    display_name, employment_type, status, created_at, updated_at)
                VALUES (?, ?, ?, 'E-T7IR', 'Idem', 'Approver', 'Idem Approver', 'FULL_TIME', 'ACTIVE', ?, ?)
                """, UUID.randomUUID(), tenantId, approverUserId, now, now);
        for (String cap : new String[]{CAP_APPROVE, "HRM.RECRUITMENT.OFFER.MANAGE",
                "HRM.RECRUITMENT.OFFER.EXTEND", "HRM.RECRUITMENT.OPENING.MANAGE",
                "HRM.RECRUITMENT.OPENING.PUBLISH"}) {
            seed("""
                    INSERT INTO access_capabilities (id, code, name, description, created_at, updated_at)
                    VALUES (?, ?, 'T7 IR Cap', 'test', NOW(), NOW())
                    ON CONFLICT (code) DO NOTHING
                    """, UUID.nameUUIDFromBytes(("cap:" + cap).getBytes()), cap);
        }
        UUID role = UUID.randomUUID();
        seed("INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'T7_IR_ROLE', 'Idem RBAC Role', 'ACTIVE', ?, ?)",
                role, tenantId, now, now);
        seed("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), ?::uuid, ?::uuid, ac.id, NOW() FROM access_capabilities ac
                WHERE ac.code IN ('HRM.RECRUITMENT.OFFER.APPROVE', 'HRM.RECRUITMENT.OFFER.MANAGE',
                                  'HRM.RECRUITMENT.OFFER.EXTEND', 'HRM.RECRUITMENT.OPENING.MANAGE',
                                  'HRM.RECRUITMENT.OPENING.PUBLISH')
                """, tenantId, role);
        for (UUID u : new UUID[]{submitterId, approverUserId}) {
            seed("INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                    UUID.randomUUID(), tenantId, u, role, now, now);
        }
        seed("""
                INSERT INTO access_scope_grants (id, tenant_id, role_id, capability_id, scope_type, status, effective_from)
                SELECT gen_random_uuid(), ?::uuid, rc.role_id, rc.capability_id, 'TENANT', 'ACTIVE', NOW()
                FROM role_capabilities rc WHERE rc.tenant_id = ?::uuid
                ON CONFLICT DO NOTHING
                """, tenantId, tenantId);
        grantWorkflowEntitlement();
        applicationId = seedCandidateOpeningApplication();
        bindTenantToSecurityContext();
        bindPoolToTenant();
    }

    @AfterEach
    void resetPool() throws Exception {
        resetPoolTenant();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private void grantWorkflowEntitlement() {
        UUID planId = UUID.randomUUID();
        seed("""
                INSERT INTO saas_plans (id, code, name, status, currency_code, monthly_price_minor,
                     annual_price_minor, trial_days, max_users, max_organizations, storage_mb,
                     created_at, updated_at)
                VALUES (?,?,?,?, 'SAR', 0, 0, 0, 10, 1, 0, NOW(), NOW())
                """, planId, "T7IR_" + UUID.randomUUID().toString().substring(0, 8),
                "T7 idem plan", "ACTIVE");
        seed("""
                INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, status, billing_cycle,
                     seat_quantity, credit_balance_minor, started_at, current_period_start,
                     current_period_end, cancel_at_period_end, created_at, updated_at)
                VALUES (?,?,?, 'ACTIVE', 'MONTHLY', 5, 0, NOW(), NOW(),
                        NOW() + INTERVAL '30 days', false, NOW(), NOW())
                """, UUID.randomUUID(), tenantId, planId);
        seed("""
                INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled,
                     capability_code, created_at, updated_at)
                SELECT ?, ?, id, true, NULL, NOW(), NOW() FROM modules WHERE code = 'WORKFLOW'
                """, UUID.randomUUID(), planId);
    }

    private UUID seedCandidateOpeningApplication() {
        UUID candidateId = UUID.randomUUID();
        seed("INSERT INTO hr_candidates (id, tenant_id, candidate_number, display_name, pool_state, version) "
                + "VALUES (?, ?, 'CAND-T7IR', 'T7 Idem Candidate', 'ACTIVE', 0)", candidateId, tenantId);
        UUID organizationId = UUID.randomUUID();
        seed("INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) "
                + "VALUES (?, ?, 'T7 IR Org', 'ACTIVE', ?, ?)", organizationId, tenantId, now, now);
        seed("INSERT INTO hr_jobs (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'JOB-T7IR', ?)", UUID.randomUUID(), tenantId, organizationId, now);
        seed("INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'OU-T7IR', ?)", UUID.randomUUID(), tenantId, organizationId, now);
        UUID openingId = UUID.randomUUID();
        seed("""
                INSERT INTO hr_job_openings (id, tenant_id, opening_number, job_id, org_unit_id,
                    state, requested_headcount, version)
                SELECT ?::uuid, j.tenant_id, 'OFF-T7IR-1', j.id, u.id, 'OPEN', 1, 0
                FROM hr_jobs j, hr_org_units u WHERE j.tenant_id = ?::uuid AND u.tenant_id = ?::uuid LIMIT 1
                """, openingId, tenantId, tenantId);
        UUID appId = UUID.randomUUID();
        seed("INSERT INTO hr_applications (id, tenant_id, candidate_id, job_opening_id, state, version) "
                + "VALUES (?, ?, ?, ?, 'OFFER', 0)", appId, tenantId, candidateId, openingId);
        return appId;
    }

    private HrCommandContext ctx(UUID actor) {
        return new HrCommandContext(tenantId, null, actor, UUID.randomUUID());
    }

    /** Reads the authoritative engine fields of a started instance. */
    private Map<String, Object> instanceFields(UUID instanceId) {
        try (java.sql.Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (var st = c.createStatement()) {
                st.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
            }
            try (var ps = c.prepareStatement(
                    "SELECT workflow_definition_id, workflow_version, definition_family_id, "
                            + "definition_version_id, current_step_key, idempotency_key "
                            + "FROM workflow_instances WHERE id = ?")) {
                ps.setObject(1, instanceId);
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    Map<String, Object> row = new java.util.HashMap<>();
                    var meta = rs.getMetaData();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    c.commit();
                    return row;
                }
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private WorkflowInstance probeInstance(Map<String, Object> fields, String entityType, UUID entityId,
                                           String idempotencyKey) {
        return WorkflowInstance.startY2(
                tenantId,
                (UUID) fields.get("definition_family_id"),
                (UUID) fields.get("definition_version_id"),
                ((Number) fields.get("workflow_version")).intValue(),
                entityType, entityId,
                (String) fields.get("current_step_key"),
                submitterId, entityId,
                null, null, idempotencyKey, null, null);
    }

    // ===== T7-A26: same key + different fingerprint → fail closed =====

    @Test
    void offerApproval_sameIdempotencyKey_differentFingerprint_isRefusedAtTheEngine() {
        UUID offerId = offerService.createOffer(ctx(submitterId), applicationId,
                "{\"base_salary\":444000,\"currency\":\"SAR\"}", "{\"band\":\"E1\"}", null);
        UUID instanceId = offerService.submitForApproval(ctx(submitterId), offerId);
        Map<String, Object> fields = instanceFields(instanceId);
        String key = (String) fields.get("idempotency_key");
        assertThat(key).as("the adapter pins a non-null idempotency key").isNotBlank();

        // same engine fingerprint slot, DIFFERENT business entity: the index
        // must refuse the second instance — no cross-entity reuse or duplicate
        UUID foreignEntityId = UUID.randomUUID();
        assertThatThrownBy(() -> executionService.startWorkflow(
                probeInstance(fields, "HR_OFFER", foreignEntityId, key), submitterId))
                .as("same idempotency key + different fingerprint must conflict, never reuse or duplicate")
                .isInstanceOf(RuntimeException.class);

        assertThat(qObj("SELECT COUNT(*) FROM workflow_instances WHERE tenant_id = ?::uuid "
                + "AND idempotency_key = ?", Integer.class, tenantId, key))
                .as("exactly one logical mutation exists for the key").isEqualTo(1);
        assertThat(qObj("SELECT COUNT(*) FROM workflow_instances WHERE business_entity_id = ?",
                Integer.class, foreignEntityId)).isZero();
    }

    @Test
    void openingApproval_sameIdempotencyKey_differentFingerprint_isRefusedAtTheEngine() {
        UUID openingId = openingService.create(ctx(submitterId),
                qObj("SELECT id FROM hr_jobs WHERE tenant_id = ? LIMIT 1", UUID.class, tenantId),
                null,
                qObj("SELECT id FROM hr_org_units WHERE tenant_id = ? LIMIT 1", UUID.class, tenantId),
                null, 1, null, null);
        UUID instanceId = openingService.submit(ctx(submitterId), openingId);
        Map<String, Object> fields = instanceFields(instanceId);
        String key = (String) fields.get("idempotency_key");
        assertThat(key).isNotBlank();

        UUID foreignEntityId = UUID.randomUUID();
        assertThatThrownBy(() -> executionService.startWorkflow(
                probeInstance(fields, "HR_JOB_OPENING", foreignEntityId, key), submitterId))
                .isInstanceOf(RuntimeException.class);

        assertThat(qObj("SELECT COUNT(*) FROM workflow_instances WHERE tenant_id = ?::uuid "
                + "AND idempotency_key = ?", Integer.class, tenantId, key)).isEqualTo(1);
        assertThat(qObj("SELECT COUNT(*) FROM workflow_instances WHERE business_entity_id = ?",
                Integer.class, foreignEntityId)).isZero();
    }

    // ===== §8: RBAC implication matrix (deny-by-default, no implications) =====

    @Test
    void rbacMatrix_manageExtendApprove_arePairwiseNonImplicating() {
        UUID offerId = offerService.createOffer(ctx(submitterId), applicationId,
                "{\"base_salary\":555000,\"currency\":\"SAR\"}", "{\"band\":\"F1\"}", null);

        // APPROVE-only principal: the approval capability is NOT an HRM-side
        // mutation shortcut (extender != approver at the command boundary)
        UUID approveOnly = seedPrincipalWithCapabilities("T7_APPROVE_ONLY", CAP_APPROVE);
        assertThatThrownBy(() -> offerService.submitForApproval(ctx(approveOnly), offerId))
                .as("OFFER.APPROVE does not imply OFFER.EXTEND")
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> offerService.find(ctx(approveOnly), offerId))
                .as("OFFER.APPROVE does not imply OFFER.MANAGE")
                .isInstanceOf(RuntimeException.class);

        // EXTEND-only principal: can submit, cannot read/manage
        UUID extendOnly = seedPrincipalWithCapabilities("T7_EXTEND_ONLY", "HRM.RECRUITMENT.OFFER.EXTEND");
        assertThat(offerService.submitForApproval(ctx(extendOnly), offerId)).isNotNull();
        assertThatThrownBy(() -> offerService.find(ctx(extendOnly), offerId))
                .as("OFFER.EXTEND does not imply OFFER.MANAGE")
                .isInstanceOf(RuntimeException.class);

        // MANAGE-only principal: can read, cannot submit/extend
        UUID manageOnly = seedPrincipalWithCapabilities("T7_MANAGE_ONLY", "HRM.RECRUITMENT.OFFER.MANAGE");
        assertThat(offerService.find(ctx(manageOnly), offerId)).isPresent();
        assertThatThrownBy(() -> offerService.submitForApproval(ctx(manageOnly), offerId))
                .as("OFFER.MANAGE does not imply OFFER.EXTEND")
                .isInstanceOf(RuntimeException.class);

        // OPENING pair: MANAGE and PUBLISH are non-implicating
        UUID openingManageOnly = seedPrincipalWithCapabilities("T7_OPEN_MGR_ONLY",
                "HRM.RECRUITMENT.OPENING.MANAGE");
        UUID openingPublishOnly = seedPrincipalWithCapabilities("T7_OPEN_PUB_ONLY",
                "HRM.RECRUITMENT.OPENING.PUBLISH");
        UUID openingId = openingService.create(ctx(openingManageOnly),
                qObj("SELECT id FROM hr_jobs WHERE tenant_id = ? LIMIT 1", UUID.class, tenantId),
                null,
                qObj("SELECT id FROM hr_org_units WHERE tenant_id = ? LIMIT 1", UUID.class, tenantId),
                null, 1, null, null);
        assertThatThrownBy(() -> openingService.approve(ctx(openingManageOnly), openingId))
                .as("OPENING.MANAGE does not imply OPENING.PUBLISH at apply time")
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> openingService.create(ctx(openingPublishOnly),
                qObj("SELECT id FROM hr_jobs WHERE tenant_id = ? LIMIT 1", UUID.class, tenantId),
                null,
                qObj("SELECT id FROM hr_org_units WHERE tenant_id = ? LIMIT 1", UUID.class, tenantId),
                null, 1, null, null))
                .as("OPENING.PUBLISH does not imply OPENING.MANAGE")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void crossTenantAuthority_cannotBeReused() {
        // a foreign principal that does not exist in this tenant has NO authority
        UUID foreignActor = UUID.randomUUID();
        UUID offerId = offerService.createOffer(ctx(submitterId), applicationId,
                "{\"base_salary\":666000,\"currency\":\"SAR\"}", "{\"band\":\"G1\"}", null);
        assertThatThrownBy(() -> offerService.submitForApproval(ctx(foreignActor), offerId))
                .as("a cross-tenant principal cannot reuse any capability here")
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> offerService.find(ctx(foreignActor), offerId))
                .isInstanceOf(RuntimeException.class);
    }

    /** Creates a user+role with EXACTLY the given capabilities (no implications). */
    private UUID seedPrincipalWithCapabilities(String roleCode, String... capabilities) {
        UUID userId = UUID.randomUUID();
        seed("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', 'x', ?, ?)",
                userId, tenantId, roleCode.toLowerCase() + "-" + userId.toString().substring(0, 8) + "@t",
                roleCode, now, now);
        for (String cap : capabilities) {
            seed("""
                    INSERT INTO access_capabilities (id, code, name, description, created_at, updated_at)
                    VALUES (?, ?, ?, 'test', NOW(), NOW())
                    ON CONFLICT (code) DO NOTHING
                    """, UUID.nameUUIDFromBytes(("cap:" + cap).getBytes()), cap, roleCode + " cap");
        }
        UUID role = UUID.randomUUID();
        seed("INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                role, tenantId, roleCode, roleCode, now, now);
        for (String cap : capabilities) {
            seed("""
                    INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                    SELECT gen_random_uuid(), ?::uuid, ?::uuid, ac.id, NOW()
                    FROM access_capabilities ac WHERE ac.code = ?
                    """, tenantId, role, cap);
        }
        seed("INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                UUID.randomUUID(), tenantId, userId, role, now, now);
        seed("""
                INSERT INTO access_scope_grants (id, tenant_id, role_id, capability_id, scope_type, status, effective_from)
                SELECT gen_random_uuid(), ?::uuid, ?::uuid, ac.id, 'TENANT', 'ACTIVE', NOW()
                FROM access_capabilities ac WHERE ac.code = ANY(?::text[])
                """, tenantId, role, "{" + String.join(",", capabilities) + "}");
        return userId;
    }

    // ===== pool/security binding mirrors of the existing T7 conventions =====

    private void bindTenantToSecurityContext() {
        var token = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "t7-idem-rbac-actor", "n/a", java.util.List.of());
        token.setDetails(java.util.Map.of("tenant_id", tenantId.toString(),
                "user_id", tenantId.toString()));
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(token);
    }

    private void bindPoolToTenant() throws Exception {
        java.util.List<java.sql.Connection> held = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            try {
                java.sql.Connection c = dataSource.getConnection();
                held.add(c);
                try (var st = c.createStatement()) {
                    st.execute("SET app.tenant_id = '" + tenantId + "'");
                }
            } catch (Exception e) {
                break;
            }
        }
        for (java.sql.Connection c : held) {
            c.close();
        }
    }

    private void resetPoolTenant() throws Exception {
        java.util.List<java.sql.Connection> held = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            try {
                java.sql.Connection c = dataSource.getConnection();
                held.add(c);
                try (var st = c.createStatement()) {
                    st.execute("RESET app.tenant_id");
                }
            } catch (Exception e) {
                break;
            }
        }
        for (java.sql.Connection c : held) {
            c.close();
        }
    }
}
