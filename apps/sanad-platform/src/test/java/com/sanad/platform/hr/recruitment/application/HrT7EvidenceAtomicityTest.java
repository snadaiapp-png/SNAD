package com.sanad.platform.hr.recruitment.application;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.recruitment.infrastructure.JdbcHrOfferRepository;
import com.sanad.platform.workflow.application.WorkflowApprovalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G1 T7 CLOSURE BATTERY (directive §9 — AUDIT/OUTBOX ATOMICITY,
 * T7-A23/T7-A24): audit write, outbox write and business state persistence are
 * ONE governed transactional mutation boundary.
 *
 * <p>Failure injection is real and DB-level: a BEFORE INSERT trigger on
 * {@code hr_audit_ledger} (resp. {@code hr_domain_event_outbox}) scoped to the
 * probe aggregate raises inside the mutation transaction. Every test proves
 * the full rollback (business state, correlation, audit, outbox) and then —
 * after removing the probe — that the same logical mutation succeeds cleanly,
 * which also proves no partial evidence survived the failed attempt.</p>
 *
 * <p>Forbidden outcomes (all asserted): business state committed without
 * audit/outbox; audit without business state; outbox without business state.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
class HrT7EvidenceAtomicityTest {

    private static final String CAP_APPROVE = "HRM.RECRUITMENT.OFFER.APPROVE";
    private static final String AUDIT_PROBE_FN = "hrm_t7_probe_audit_block_fn";
    private static final String AUDIT_PROBE_TRG = "hrm_t7_probe_audit_block_trg";
    private static final String OUTBOX_PROBE_FN = "hrm_t7_probe_outbox_block_fn";
    private static final String OUTBOX_PROBE_TRG = "hrm_t7_probe_outbox_block_trg";

    @Autowired
    private HrOfferService offerService;

    @Autowired
    private HrJobOpeningService openingService;

    @Autowired
    private WorkflowApprovalService approvalService;

    @Autowired
    private javax.sql.DataSource dataSource;

    private UUID tenantId;
    private UUID submitterId;
    private UUID approverUserId;
    private UUID applicationId;
    private UUID publisherId;
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

    private void executeDdl(String sql) {
        try (java.sql.Connection c = dataSource.getConnection()) {
            try (var st = c.createStatement()) {
                st.execute(sql);
            }
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
        publisherId = UUID.randomUUID();

        seed("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                + "VALUES (?, 'T7 Atomicity', ?, 'ACTIVE', ?, ?)",
                tenantId, "t7at-" + tenantId.toString().substring(0, 8), now, now);
        for (UUID u : new UUID[]{submitterId, approverUserId, publisherId}) {
            seed("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at) "
                            + "VALUES (?, ?, ?, 'Atomicity Actor', 'ACTIVE', 'x', ?, ?)",
                    u, tenantId, "t7ap-" + u.toString().substring(0, 8) + "@t", now, now);
        }
        seed("""
                INSERT INTO hr_employees (id, tenant_id, user_id, employee_number, first_name, last_name,
                    display_name, employment_type, status, created_at, updated_at)
                VALUES (?, ?, ?, 'E-T7AT', 'Atomicity', 'Approver', 'Atomicity Approver', 'FULL_TIME', 'ACTIVE', ?, ?)
                """, UUID.randomUUID(), tenantId, approverUserId, now, now);
        seed("""
                INSERT INTO hr_employees (id, tenant_id, user_id, employee_number, first_name, last_name,
                    display_name, employment_type, status, created_at, updated_at)
                VALUES (?, ?, ?, 'E-T7ATP', 'Atomicity', 'Publisher', 'Atomicity Publisher', 'FULL_TIME', 'ACTIVE', ?, ?)
                """, UUID.randomUUID(), tenantId, publisherId, now, now);
        for (String cap : new String[]{CAP_APPROVE, "HRM.RECRUITMENT.OFFER.MANAGE",
                "HRM.RECRUITMENT.OFFER.EXTEND", "HRM.RECRUITMENT.OPENING.MANAGE",
                "HRM.RECRUITMENT.OPENING.PUBLISH"}) {
            seed("""
                    INSERT INTO access_capabilities (id, code, name, description, created_at, updated_at)
                    VALUES (?, ?, 'T7 Atomicity Cap', 'test', NOW(), NOW())
                    ON CONFLICT (code) DO NOTHING
                    """, UUID.nameUUIDFromBytes(("cap:" + cap).getBytes()), cap);
        }
        UUID role = UUID.randomUUID();
        seed("INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'T7_ATOMICITY_ROLE', 'Atomicity Role', 'ACTIVE', ?, ?)",
                role, tenantId, now, now);
        seed("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), ?::uuid, ?::uuid, ac.id, NOW() FROM access_capabilities ac
                WHERE ac.code IN ('HRM.RECRUITMENT.OFFER.APPROVE', 'HRM.RECRUITMENT.OFFER.MANAGE',
                                  'HRM.RECRUITMENT.OFFER.EXTEND', 'HRM.RECRUITMENT.OPENING.MANAGE',
                                  'HRM.RECRUITMENT.OPENING.PUBLISH')
                """, tenantId, role);
        for (UUID u : new UUID[]{submitterId, approverUserId, publisherId}) {
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
        seedComplianceCompliantJurisdiction();
        applicationId = seedCandidateOpeningApplication();
        bindTenantToSecurityContext();
        bindPoolToTenant();
    }

    @AfterEach
    void dropProbesAndReset() throws Exception {
        executeDdl("DROP TRIGGER IF EXISTS " + AUDIT_PROBE_TRG + " ON hr_audit_ledger");
        executeDdl("DROP FUNCTION IF EXISTS " + AUDIT_PROBE_FN + "()");
        executeDdl("DROP TRIGGER IF EXISTS " + OUTBOX_PROBE_TRG + " ON hr_domain_event_outbox");
        executeDdl("DROP FUNCTION IF EXISTS " + OUTBOX_PROBE_FN + "()");
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
                """, planId, "T7AT_" + UUID.randomUUID().toString().substring(0, 8),
                "T7 atomicity plan", "ACTIVE");
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

    private void seedComplianceCompliantJurisdiction() {
        UUID organizationId = UUID.randomUUID();
        seed("INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) "
                + "VALUES (?, ?, 'T7 Atomicity Org', 'ACTIVE', ?, ?)", organizationId, tenantId, now, now);
        UUID legalEntityId = UUID.randomUUID();
        seed("INSERT INTO legal_entities (id, tenant_id, code, name, registered_country_code, "
                        + "statutory_country_code, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'LE-T7AT', 'LE', 'SA', 'SA', 'ACTIVE', ?, ?)",
                legalEntityId, tenantId, now, now);
        seed("INSERT INTO organization_legal_entities (tenant_id, organization_id, legal_entity_id, "
                        + "effective_from, status) VALUES (?, ?, ?, CURRENT_DATE, 'ACTIVE')",
                tenantId, organizationId, legalEntityId);
        seed("INSERT INTO hr_country_packs (country_code, pack_code, pack_version, status, "
                        + "effective_from, legal_reviewed_at, legal_reviewed_by, certification_reference) "
                        + "VALUES ('SA', 'PACK-SA', 'v1', 'CERTIFIED', CURRENT_DATE, NOW(), 'legal', 'CERT-T7AT') "
                        + "ON CONFLICT (country_code, pack_code, pack_version) DO NOTHING");
        seed("INSERT INTO hr_jobs (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'JOB-T7AT', ?)", UUID.randomUUID(), tenantId, organizationId, now);
        seed("INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'OU-T7AT', ?)", UUID.randomUUID(), tenantId, organizationId, now);
    }

    private UUID seedCandidateOpeningApplication() {
        UUID candidateId = UUID.randomUUID();
        seed("INSERT INTO hr_candidates (id, tenant_id, candidate_number, display_name, pool_state, version) "
                + "VALUES (?, ?, 'CAND-T7AT', 'T7 Atomicity Candidate', 'ACTIVE', 0)", candidateId, tenantId);
        UUID openingId = UUID.randomUUID();
        seed("""
                INSERT INTO hr_job_openings (id, tenant_id, opening_number, job_id, org_unit_id,
                    state, requested_headcount, version)
                SELECT ?::uuid, j.tenant_id, 'OFF-T7AT-1', j.id, u.id, 'OPEN', 1, 0
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

    private Map<String, Object> firstRequest(UUID instanceId) {
        try (java.sql.Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (var st = c.createStatement()) {
                st.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
            }
            try (var ps = c.prepareStatement(
                    "SELECT id, requested_from_user_id, version FROM workflow_approval_requests "
                            + "WHERE workflow_instance_id = ? ORDER BY created_at LIMIT 1")) {
                ps.setObject(1, instanceId);
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    Map<String, Object> row = Map.of(
                            "id", (UUID) rs.getObject("id"),
                            "requested_from_user_id", (UUID) rs.getObject("requested_from_user_id"),
                            "version", rs.getLong("version"));
                    c.commit();
                    return row;
                }
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String offerState(UUID offerId) {
        return qObj("SELECT state FROM hr_offers WHERE id = ?", String.class, offerId);
    }

    private String openingState(UUID openingId) {
        return qObj("SELECT state FROM hr_job_openings WHERE id = ?", String.class, openingId);
    }

    // ===== pool/security binding mirrors of the existing T7 conventions =====

    private void bindTenantToSecurityContext() {
        var token = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "t7-atomicity-actor", "n/a", java.util.List.of());
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

    /** Drives a real offer to PENDING_APPROVAL with the Y2 cycle APPROVED. */
    private UUID approvedPendingOffer() {
        UUID offerId = offerService.createOffer(ctx(submitterId), applicationId,
                "{\"base_salary\":333000,\"currency\":\"SAR\"}", "{\"band\":\"D1\"}", null);
        UUID instanceId = offerService.submitForApproval(ctx(submitterId), offerId);
        Map<String, Object> row = firstRequest(instanceId);
        approvalService.approve(tenantId, (UUID) row.get("id"), approverUserId,
                ((Number) row.get("version")).longValue(), "atomicity approve");
        assertThat(offerState(offerId)).isEqualTo("PENDING_APPROVAL");
        return offerId;
    }

    /** Drives a real opening to PENDING_APPROVAL with the Y2 cycle APPROVED. */
    private UUID approvedPendingOpening() {
        UUID openingId = openingService.create(ctx(submitterId),
                qObj("SELECT id FROM hr_jobs WHERE tenant_id = ? LIMIT 1", UUID.class, tenantId),
                null,
                qObj("SELECT id FROM hr_org_units WHERE tenant_id = ? LIMIT 1", UUID.class, tenantId),
                null, 1, null, null);
        UUID instanceId = openingService.submit(ctx(submitterId), openingId);
        Map<String, Object> row = firstRequest(instanceId);
        approvalService.approve(tenantId, (UUID) row.get("id"), publisherId,
                ((Number) row.get("version")).longValue(), "atomicity approve");
        assertThat(openingState(openingId)).isEqualTo("PENDING_APPROVAL");
        return openingId;
    }

    // ============ T7-A23: audit write failure ⇒ full rollback ============

    @Test
    void auditWriteFailure_rollsBackTheEntireOfferExtension() {
        UUID offerId = approvedPendingOffer();

        executeDdl("CREATE FUNCTION " + AUDIT_PROBE_FN + "() RETURNS trigger AS $t$ "
                + "BEGIN RAISE EXCEPTION 'T7_ATOMICITY_PROBE_AUDIT'; END $t$ LANGUAGE plpgsql");
        executeDdl("CREATE TRIGGER " + AUDIT_PROBE_TRG + " BEFORE INSERT ON hr_audit_ledger "
                + "FOR EACH ROW WHEN (NEW.action = '" + JdbcHrOfferRepository.ACTION_EXTENDED + "' "
                + "AND NEW.resource_id::text = '" + offerId + "') "
                + "EXECUTE FUNCTION " + AUDIT_PROBE_FN + "()");

        try {
            assertThatThrownBy(() -> offerService.extendFromApproval(ctx(submitterId), offerId,
                    qObj("SELECT pending_workflow_instance_id FROM hr_offers WHERE id = ?",
                            UUID.class, offerId)))
                    .as("the injected audit-write failure surfaces")
                    .isInstanceOf(RuntimeException.class);

            // FULL rollback: no business state, no correlation loss, no evidence
            assertThat(offerState(offerId)).isEqualTo("PENDING_APPROVAL");
            assertThat(qObj("SELECT pending_workflow_instance_id FROM hr_offers WHERE id = ?",
                    UUID.class, offerId)).isNotNull();
            assertThat(qObj("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? AND action = ?",
                    Integer.class, offerId, JdbcHrOfferRepository.ACTION_EXTENDED)).isZero();
            assertThat(qObj("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = ? "
                    + "AND event_type = ?", Integer.class, offerId,
                    JdbcHrOfferRepository.ACTION_EXTENDED)).isZero();

            // after removing the probe the same logical mutation succeeds — the
            // cycle was left healthy, exactly as a full rollback implies
            executeDdl("DROP TRIGGER IF EXISTS " + AUDIT_PROBE_TRG + " ON hr_audit_ledger");
            assertThatCode(() -> offerService.extendFromApproval(ctx(submitterId), offerId,
                    qObj("SELECT pending_workflow_instance_id FROM hr_offers WHERE id = ?",
                            UUID.class, offerId))).doesNotThrowAnyException();
            assertThat(offerState(offerId)).isEqualTo("EXTENDED");
            assertThat(qObj("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? AND action = ?",
                    Integer.class, offerId, JdbcHrOfferRepository.ACTION_EXTENDED)).isEqualTo(1);
            assertThat(qObj("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = ? "
                    + "AND event_type = ?", Integer.class, offerId,
                    JdbcHrOfferRepository.ACTION_EXTENDED)).isEqualTo(1);
        } finally {
            executeDdl("DROP TRIGGER IF EXISTS " + AUDIT_PROBE_TRG + " ON hr_audit_ledger");
            executeDdl("DROP FUNCTION IF EXISTS " + AUDIT_PROBE_FN + "()");
        }
    }

    // ============ T7-A24: outbox write failure ⇒ full rollback ============

    @Test
    void outboxWriteFailure_rollsBackTheEntireOfferExtension() {
        UUID offerId = approvedPendingOffer();

        executeDdl("CREATE FUNCTION " + OUTBOX_PROBE_FN + "() RETURNS trigger AS $t$ "
                + "BEGIN RAISE EXCEPTION 'T7_ATOMICITY_PROBE_OUTBOX'; END $t$ LANGUAGE plpgsql");
        executeDdl("CREATE TRIGGER " + OUTBOX_PROBE_TRG + " BEFORE INSERT ON hr_domain_event_outbox "
                + "FOR EACH ROW WHEN (NEW.event_type = '" + JdbcHrOfferRepository.ACTION_EXTENDED + "' "
                + "AND NEW.aggregate_id::text = '" + offerId + "') "
                + "EXECUTE FUNCTION " + OUTBOX_PROBE_FN + "()");

        try {
            assertThatThrownBy(() -> offerService.extendFromApproval(ctx(submitterId), offerId,
                    qObj("SELECT pending_workflow_instance_id FROM hr_offers WHERE id = ?",
                            UUID.class, offerId)))
                    .as("the injected outbox-write failure surfaces")
                    .isInstanceOf(RuntimeException.class);

            assertThat(offerState(offerId)).isEqualTo("PENDING_APPROVAL");
            assertThat(qObj("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? AND action = ?",
                    Integer.class, offerId, JdbcHrOfferRepository.ACTION_EXTENDED)).isZero();
            assertThat(qObj("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = ? "
                    + "AND event_type = ?", Integer.class, offerId,
                    JdbcHrOfferRepository.ACTION_EXTENDED)).isZero();

            executeDdl("DROP TRIGGER IF EXISTS " + OUTBOX_PROBE_TRG + " ON hr_domain_event_outbox");
            assertThatCode(() -> offerService.extendFromApproval(ctx(submitterId), offerId,
                    qObj("SELECT pending_workflow_instance_id FROM hr_offers WHERE id = ?",
                            UUID.class, offerId))).doesNotThrowAnyException();
            assertThat(offerState(offerId)).isEqualTo("EXTENDED");
        } finally {
            executeDdl("DROP TRIGGER IF EXISTS " + OUTBOX_PROBE_TRG + " ON hr_domain_event_outbox");
            executeDdl("DROP FUNCTION IF EXISTS " + OUTBOX_PROBE_FN + "()");
        }
    }

    // ===== the same atomicity proof for the opening publication mutation =====

    @Test
    void evidenceWriteFailure_rollsBackTheEntireOpeningPublication() {
        UUID openingId = approvedPendingOpening();
        UUID instanceId = qObj("SELECT pending_workflow_instance_id FROM hr_job_openings WHERE id = ?",
                UUID.class, openingId);

        // one probe blocks BOTH evidence surfaces for this publication
        executeDdl("CREATE FUNCTION " + AUDIT_PROBE_FN + "() RETURNS trigger AS $t$ "
                + "BEGIN RAISE EXCEPTION 'T7_ATOMICITY_PROBE_AUDIT'; END $t$ LANGUAGE plpgsql");
        executeDdl("CREATE TRIGGER " + AUDIT_PROBE_TRG + " BEFORE INSERT ON hr_audit_ledger "
                + "FOR EACH ROW WHEN (NEW.action = 'HRM.RECRUITMENT.OPENING_PUBLISHED' "
                + "AND NEW.resource_id::text = '" + openingId + "') "
                + "EXECUTE FUNCTION " + AUDIT_PROBE_FN + "()");

        try {
            assertThatThrownBy(() -> openingService.approve(ctx(publisherId), openingId))
                    .as("the injected audit-write failure surfaces")
                    .isInstanceOf(RuntimeException.class);

            assertThat(openingState(openingId)).isEqualTo("PENDING_APPROVAL");
            assertThat(qObj("SELECT pending_workflow_instance_id FROM hr_job_openings WHERE id = ?",
                    UUID.class, openingId)).isEqualTo(instanceId);
            assertThat(qObj("SELECT COUNT(*) FROM hr_audit_ledger WHERE resource_id = ? "
                    + "AND action = 'HRM.RECRUITMENT.OPENING_PUBLISHED'",
                    Integer.class, openingId)).isZero();
            assertThat(qObj("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = ? "
                    + "AND event_type = 'HRM.RECRUITMENT.OPENING_PUBLISHED'",
                    Integer.class, openingId)).isZero();

            executeDdl("DROP TRIGGER IF EXISTS " + AUDIT_PROBE_TRG + " ON hr_audit_ledger");
            assertThatCode(() -> openingService.approve(ctx(publisherId), openingId))
                    .doesNotThrowAnyException();
            assertThat(openingState(openingId)).isEqualTo("OPEN");
            assertThat(qObj("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE aggregate_id = ? "
                    + "AND event_type = 'HRM.RECRUITMENT.OPENING_PUBLISHED'",
                    Integer.class, openingId)).isEqualTo(1);
        } finally {
            executeDdl("DROP TRIGGER IF EXISTS " + AUDIT_PROBE_TRG + " ON hr_audit_ledger");
            executeDdl("DROP FUNCTION IF EXISTS " + AUDIT_PROBE_FN + "()");
        }
    }
}
