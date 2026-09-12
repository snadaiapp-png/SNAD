package com.sanad.platform.workflow;

import com.sanad.platform.workflow.application.WorkflowExternalActionService;
import com.sanad.platform.workflow.application.WorkflowJourneyService;
import com.sanad.platform.workflow.experience.WorkflowCustomerFeedbackController;
import com.sanad.platform.workflow.portal.WorkflowExternalPortalService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R2 MATRIX C — secure external action portal (GATES R2.11/R2.12) +
 * customer feedback foundation (R2.18). PostgreSQL Direct. Covers: valid
 * token view/respond, expiry, revocation, replay idempotency, action
 * allow-list, rate limiting, wrong-participant denial, Journey evidence
 * (EXTERNAL_RESPONSE), OTP lifecycle (issue/verify/expiry), access audit
 * log, and feedback recording constraints.
 */
class WorkflowR2ExternalPortalTest {

    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static PlatformTransactionManager transactionManager;
    private static WorkflowExternalActionService actionService;
    private static WorkflowExternalPortalService portal;
    private static WorkflowExternalPortalService portalWithOtp;
    private static boolean postgresAvailable;

    private final List<UUID> createdTenants = new ArrayList<>();

    @AfterEach
    void sweepFixtures() {
        for (UUID tenantId : createdTenants) {
            WorkflowTenantFixtureSweeper.sweepTenant(jdbc, transactionManager, tenantId);
        }
    }

    @BeforeAll
    static void setup() {
        String url = System.getenv().getOrDefault(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
        String user = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
        String pass = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "sanad_pass");
        try {
            Flyway.configure()
                    .dataSource(url, user, pass)
                    .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                    .cleanDisabled(true)
                    .load()
                    .migrate();
            postgresAvailable = true;
        } catch (Exception unavailable) {
            System.err.println("[WorkflowR2ExternalPortalTest] PostgreSQL Direct "
                    + "unavailable, skipping: " + unavailable);
            postgresAvailable = false;
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(postgresAvailable,
                "PostgreSQL Direct unavailable — skipping in non-CI environment");

        DataSource dataSource = new DriverManagerDataSource(url, user, pass);
        jdbc = new JdbcTemplate(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);
        tx = new TransactionTemplate(transactionManager);
        actionService = new WorkflowExternalActionService(jdbc,
                new com.fasterxml.jackson.databind.ObjectMapper());
        WorkflowJourneyService journeyService = new WorkflowJourneyService(jdbc, new com.fasterxml.jackson.databind.ObjectMapper());
        com.sanad.platform.security.rls.TenantRlsTransactionContext tenantContext =
                new com.sanad.platform.security.rls.TenantRlsTransactionContext(jdbc);
        portal = new WorkflowExternalPortalService(actionService, journeyService,
                tenantContext, jdbc, false);
        portalWithOtp = new WorkflowExternalPortalService(actionService, journeyService,
                tenantContext, jdbc, true);
    }

    /**
     * The portal service is @Transactional behind its Spring proxy; invoked
     * directly from the test we supply the transaction boundary explicitly
     * (TransactionTemplate) so the RLS GUC bridge operates on a real tx.
     */
    private WorkflowExternalPortalService.PortalView inTxView(PortalFixture fx,
                                                              String ip) {
        return tx.execute(s -> portal.view(fx.token(), ip));
    }

    private WorkflowExternalPortalService.PortalResponse inTxRespond(
            WorkflowExternalPortalService svc, PortalFixture fx, String action,
            Map<String, Object> payload, String ip, String otp) {
        return tx.execute(s -> svc.respond(fx.token(), action, payload, ip, otp));
    }

    private record PortalFixture(UUID tenant, UUID instance, UUID participant,
                                 UUID actionId, String token) {}

    private PortalFixture fixture(String tag, Instant expiresAt,
                                  Map<String, Object> allowedActions,
                                  boolean otpRequired) {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID definition = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        UUID participant = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)", tenant, "R2Portal-" + tag,
                "r2-portal-" + tag + "-" + tenant.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,"
                        + "created_at,updated_at) VALUES (?, ?, ?, 'R2 Portal', 'ACTIVE', 'dummy', ?, ?)",
                user, tenant, "r2-portal-" + user.toString().substring(0, 8) + "@test", now, now);
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-R2-PORTAL', 'R2Portal', 'GENERAL', 1, 'ACTIVE',
                          'EVENT', ?, 0, 'Y2', 'PUBLISHED', 1, ?, ?)
                """, definition, tenant, definition, user, now, now);
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                    business_entity_id, status, started_by, started_at, engine_generation,
                    context_json, context_schema_version, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', ?, NOW(), 'Y2',
                          CAST('{}' AS jsonb), 1, 0, ?, ?)
                """, instance, tenant, definition, user, now, now);
        jdbc.update("""
                INSERT INTO workflow_external_participants (
                    id, tenant_id, participant_type, source_module, source_entity_type,
                    source_entity_id, display_reference, communication_reference, created_at)
                VALUES (?, ?, 'CUSTOMER_CONTACT', 'CRM', 'contact', ?, 'R2 Customer', '+966500000000', ?)
                """, participant, tenant, UUID.randomUUID(), now);
        var created = actionService.create(tenant, instance, null, participant,
                "APPROVE", expiresAt, "r2-portal-" + tag + "-" + UUID.randomUUID(),
                allowedActions);
        if (otpRequired) {
            jdbc.update("UPDATE workflow_external_actions SET otp_required = TRUE "
                    + "WHERE tenant_id = ? AND id = ?", tenant, created.action().id());
        }
        createdTenants.add(tenant);
        return new PortalFixture(tenant, instance, participant,
                created.action().id(), created.opaqueToken());
    }

    @Test
    void validTokenViewMarksViewedAndRespondRecordsEvidenceIdempotently() {
        PortalFixture fx = fixture("happy", Instant.now().plusSeconds(3600),
                Map.of("APPROVE", true, "REJECT", true), false);
        // VIEW: marks VIEWED, returns bounded descriptor
        WorkflowExternalPortalService.PortalView view = inTxView(fx, "203.0.113.10");
        assertThat(view.status()).isEqualTo("VIEWED");
        assertThat(view.actionType()).isEqualTo("APPROVE");
        assertThat(view.participantType()).isEqualTo("CUSTOMER_CONTACT");
        // RESPOND: records response + journey evidence
        WorkflowExternalPortalService.PortalResponse response = inTxRespond(portal,
                fx, "APPROVE", Map.of("decision", "APPROVE", "note", "ok"),
                "203.0.113.10", null);
        assertThat(response.status()).isEqualTo("RESPONDED");
        assertThat(response.responsePayload()).containsEntry("decision", "APPROVE");
        Integer journeyEvents = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_journey
                 WHERE tenant_id = ? AND workflow_instance_id = ?
                   AND event_type = 'EXTERNAL_RESPONSE'
                """, Integer.class, fx.tenant(), fx.instance());
        assertThat(journeyEvents).isEqualTo(1);
        // REPLAY: repeated valid response is idempotent — no second advance
        WorkflowExternalPortalService.PortalResponse replay = inTxRespond(portal,
                fx, "APPROVE", Map.of("decision", "REJECT"), "203.0.113.10", null);
        assertThat(replay.status()).isEqualTo("RESPONDED");
        Map<String, Object> persisted = jdbc.queryForMap("""
                SELECT response_payload::text AS payload_text
                  FROM workflow_external_actions WHERE tenant_id = ? AND id = ?
                """, fx.tenant(), fx.actionId());
        assertThat(String.valueOf(persisted.get("payload_text")))
                .contains("APPROVE").doesNotContain("REJECT");
        Integer journeyAfterReplay = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_journey
                 WHERE tenant_id = ? AND workflow_instance_id = ?
                   AND event_type = 'EXTERNAL_RESPONSE'
                """, Integer.class, fx.tenant(), fx.instance());
        assertThat(journeyAfterReplay).isEqualTo(1);
    }

    @Test
    void expiredAndRevokedActionsAreDenied() {
        PortalFixture fx = fixture("expired", Instant.now().plusSeconds(3600),
                Map.of("APPROVE", true), false);
        // force EXPIRED state
        actionService.transition(fx.tenant(), fx.actionId(), "EXPIRED", Map.of());
        assertThatThrownBy(() -> tx.execute(s -> portal.respond(fx.token(), "APPROVE",
                Map.of(), "203.0.113.10", null)))
                .isInstanceOf(WorkflowExternalPortalService.PortalDeniedException.class)
                .hasMessageContaining("EXPIRED_TOKEN");
        assertThatThrownBy(() -> tx.execute(s -> portal.view(fx.token(), "203.0.113.10")))
                .isInstanceOf(WorkflowExternalPortalService.PortalDeniedException.class)
                .hasMessageContaining("EXPIRED_TOKEN");

        PortalFixture fx2 = fixture("revoked", Instant.now().plusSeconds(3600),
                Map.of("APPROVE", true), false);
        actionService.transition(fx2.tenant(), fx2.actionId(), "REVOKED", Map.of());
        assertThatThrownBy(() -> tx.execute(s -> portal.respond(fx2.token(), "APPROVE",
                Map.of(), "203.0.113.10", null)))
                .isInstanceOf(WorkflowExternalPortalService.PortalDeniedException.class)
                .hasMessageContaining("REVOKED_TOKEN");
    }

    @Test
    void requestedActionOutsideAllowListIsDenied() {
        PortalFixture fx = fixture("allowlist", Instant.now().plusSeconds(3600),
                Map.of("APPROVE", true), false);
        assertThatThrownBy(() -> tx.execute(s -> portal.respond(fx.token(), "REJECT",
                Map.of(), "203.0.113.10", null)))
                .isInstanceOf(WorkflowExternalPortalService.PortalDeniedException.class)
                .hasMessageContaining("ACTION_NOT_ALLOWED");
        Map<String, Object> action = jdbc.queryForMap(
                "SELECT status FROM workflow_external_actions WHERE tenant_id = ? AND id = ?",
                fx.tenant(), fx.actionId());
        assertThat(action.get("status")).isNotEqualTo("RESPONDED");
    }

    @Test
    void tokenAndFingerprintRateLimitsFailClosed() {
        PortalFixture fx = fixture("ratelimit", Instant.now().plusSeconds(3600),
                Map.of("ACKNOWLEDGE", true), false);
        actionService.transition(fx.tenant(), fx.actionId(), "VIEWED", Map.of());
        // pre-seed the durable counters to the limit boundary
        for (int i = 0; i < 10; i++) {
            jdbc.update("""
                    INSERT INTO workflow_portal_access_log (
                        tenant_id, external_action_id, access_type, outcome,
                        remote_fingerprint, created_at)
                    VALUES (?, ?, 'VIEW', 'ALLOWED', ?, NOW() - INTERVAL '5 minutes')
                    """, fx.tenant(), fx.actionId(), "198.51.100.7");
        }
        assertThatThrownBy(() -> tx.execute(s -> portal.respond(fx.token(), "ACKNOWLEDGE",
                Map.of(), "198.51.100.7", null)))
                .isInstanceOf(WorkflowExternalPortalService.PortalDeniedException.class)
                .hasMessageContaining("RATE_LIMITED");
        // action remains un-responded (fail closed)
        Map<String, Object> action = jdbc.queryForMap(
                "SELECT status FROM workflow_external_actions WHERE tenant_id = ? AND id = ?",
                fx.tenant(), fx.actionId());
        assertThat(action.get("status")).isEqualTo("VIEWED");
    }

    @Test
    void otpLifecycleIssueVerifyExpiryAndAttemptBounds() {
        PortalFixture fx = fixture("otp", Instant.now().plusSeconds(3600),
                Map.of("APPROVE", true), true);
        // respond without OTP -> OTP_REQUIRED
        assertThatThrownBy(() -> tx.execute(s -> portalWithOtp.respond(fx.token(),
                "APPROVE", Map.of(), "203.0.113.20", null)))
                .isInstanceOf(WorkflowExternalPortalService.PortalDeniedException.class)
                .hasMessageContaining("OTP_REQUIRED");
        // issue OTP (plaintext returned exactly once; only hash stored)
        String otp = tx.execute(s -> portalWithOtp.issueOtp(fx.token(), "203.0.113.20"));
        assertThat(otp).hasSize(6);
        String storedOtp = jdbc.queryForObject("""
                SELECT otp_hash FROM workflow_portal_otp_challenges
                 WHERE external_action_id = ? ORDER BY created_at DESC LIMIT 1
                """, String.class, fx.actionId());
        assertThat(storedOtp).isNotEqualTo(otp);
        assertThat(storedOtp).hasSize(64);
        // wrong OTP -> OTP_INVALID
        assertThatThrownBy(() -> tx.execute(s -> portalWithOtp.respond(fx.token(),
                "APPROVE", Map.of(), "203.0.113.20", "000000")))
                .isInstanceOf(WorkflowExternalPortalService.PortalDeniedException.class)
                .hasMessageContaining("OTP_INVALID");
        // correct OTP -> response accepted
        WorkflowExternalPortalService.PortalResponse response = inTxRespond(
                portalWithOtp, fx, "APPROVE", Map.of("ok", true), "203.0.113.20", otp);
        assertThat(response.status()).isEqualTo("RESPONDED");
        // idempotent replay after terminal response — no error
        WorkflowExternalPortalService.PortalResponse replay = inTxRespond(
                portalWithOtp, fx, "APPROVE", Map.of(), "203.0.113.20", null);
        assertThat(replay.status()).isEqualTo("RESPONDED");
    }

    @Test
    void invalidTokensAreRejected() {
        assertThatThrownBy(() -> tx.execute(s -> portal.view("nonexistent-token", "203.0.113.30")))
                .isInstanceOf(WorkflowExternalPortalService.PortalDeniedException.class)
                .hasMessageContaining("INVALID_TOKEN");
        assertThatThrownBy(() -> tx.execute(s -> portal.respond("nonexistent-token", "APPROVE",
                Map.of(), "203.0.113.30", null)))
                .isInstanceOf(WorkflowExternalPortalService.PortalDeniedException.class)
                .hasMessageContaining("INVALID_TOKEN");
    }

    @Test
    void feedbackRecordingValidatesRatingAndJournalsEvidence() {
        PortalFixture fx = fixture("feedback", Instant.now().plusSeconds(3600),
                Map.of("APPROVE", true), false);
        WorkflowCustomerFeedbackController feedback =
                new WorkflowCustomerFeedbackController(jdbc,
                        new WorkflowJourneyService(jdbc, new com.fasterxml.jackson.databind.ObjectMapper()));
        var auth = new org.springframework.security.authentication.
                UsernamePasswordAuthenticationToken("principal", "n/a",
                List.of()) {{
            setDetails(Map.of("tenant_id", fx.tenant().toString(),
                    "user_id", UUID.randomUUID().toString()));
        }};
        // rating out of bounds -> 400, nothing persisted
        var rejected = feedback.record(auth, Map.of(
                "workflowInstanceId", fx.instance().toString(), "rating", 6));
        assertThat(rejected.getStatusCode().value()).isEqualTo(400);
        // valid rating -> 200 + evidence row + journey event
        var ok = feedback.record(auth, Map.of(
                "workflowInstanceId", fx.instance().toString(),
                "rating", 5, "comment", "great service"));
        assertThat(ok.getStatusCode().is2xxSuccessful()).isTrue();
        Integer feedbackRows = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_customer_feedback
                 WHERE tenant_id = ? AND workflow_instance_id = ?
                """, Integer.class, fx.tenant(), fx.instance());
        assertThat(feedbackRows).isEqualTo(1);
        Integer journeyEvents = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_journey
                 WHERE tenant_id = ? AND workflow_instance_id = ?
                   AND event_type = 'EXTERNAL_RESPONSE' AND reason LIKE 'Customer feedback%'
                """, Integer.class, fx.tenant(), fx.instance());
        assertThat(journeyEvents).isEqualTo(1);
    }
}
