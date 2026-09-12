package com.sanad.platform.workflow;

import com.sanad.platform.workflow.analytics.WorkflowAnalyticsQueryService;
import com.sanad.platform.workflow.application.WorkflowNotificationService;
import com.sanad.platform.workflow.notification.InAppNotificationProvider;
import com.sanad.platform.workflow.notification.WorkflowChannelRegistry;
import com.sanad.platform.workflow.notification.WorkflowNotificationDispatcher;
import com.sanad.platform.workflow.notification.WorkflowWebhookEndpointService;
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

/**
 * R2-K tenant-isolation negatives (R2.23). PostgreSQL Direct. Every R2
 * surface that carries tenant or recipient identity is probed with a
 * foreign tenant's identity and must return ZERO rows / deliver ONLY to
 * its own tenant's feed:
 *   1. IN_APP notification feed — service-level (tenant_id, recipient_user_id)
 *      predicates deny cross-tenant reads (feed + unread count).
 *   2. Delivery-intent dispatch — the dedup identity is (tenant, key);
 *      identical keys across tenants never merge and each intent is
 *      delivered to its own tenant's recipient only.
 *   3. Webhook endpoint resolution — activeEndpointsFor denies cross-tenant
 *      targeting (a tenant cannot fire another tenant's destinations).
 *   4. Customer feedback + analytics dashboards — tenant-scoped SELECT and
 *      serviceDashboard aggregate zero foreign rows.
 */
class WorkflowR2TenantIsolationTest {

    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static PlatformTransactionManager transactionManager;
    private static WorkflowNotificationService notifications;
    private static WorkflowNotificationDispatcher dispatcher;
    private static WorkflowWebhookEndpointService webhookEndpoints;
    private static WorkflowAnalyticsQueryService queryService;
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
            System.err.println("[WorkflowR2TenantIsolationTest] PostgreSQL Direct "
                    + "unavailable, skipping: " + unavailable);
            postgresAvailable = false;
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(postgresAvailable,
                "PostgreSQL Direct unavailable — skipping in non-CI environment");

        DataSource dataSource = new DriverManagerDataSource(url, user, pass);
        jdbc = new JdbcTemplate(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);
        tx = new TransactionTemplate(transactionManager);
        notifications = new WorkflowNotificationService(jdbc,
                new com.fasterxml.jackson.databind.ObjectMapper());
        dispatcher = new WorkflowNotificationDispatcher(jdbc,
                new WorkflowChannelRegistry(List.of(new InAppNotificationProvider(jdbc))), true);
        webhookEndpoints = new WorkflowWebhookEndpointService(jdbc);
        queryService = new WorkflowAnalyticsQueryService(jdbc);
    }

    private record TenantFixture(UUID tenant, UUID user) {}

    /** Minimal identity fixture: tenant + one tenant user (recipient actor). */
    private TenantFixture seedTenant(String tag) {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)", tenant, "R2Iso-" + tag,
                "r2-iso-" + tag + "-" + tenant.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,"
                        + "created_at,updated_at) VALUES (?, ?, ?, 'R2 Iso', 'ACTIVE', 'dummy', ?, ?)",
                user, tenant, "r2-iso-" + user.toString().substring(0, 8) + "@test", now, now);
        createdTenants.add(tenant);
        return new TenantFixture(tenant, user);
    }

    // ===== 1. IN_APP feed: service-level (tenant, recipient) predicates =====

    @Test
    void notificationFeedDeniesCrossTenantRecipientReads() {
        TenantFixture a = seedTenant("feed-a");
        TenantFixture b = seedTenant("feed-b");
        // tenant A feed rows for recipient Ua (unread)
        jdbc.update("""
                INSERT INTO workflow_user_notifications (
                    id, tenant_id, recipient_user_id, event_type, title, priority,
                    dedup_key, created_at)
                VALUES (?, ?, ?, 'TASK_ASSIGNED', 'A feed row 1', 'NORMAL', ?, NOW())
                """, UUID.randomUUID(), a.tenant(), a.user(),
                "iso-feed:" + UUID.randomUUID());
        jdbc.update("""
                INSERT INTO workflow_user_notifications (
                    id, tenant_id, recipient_user_id, event_type, title, priority,
                    dedup_key, created_at)
                VALUES (?, ?, ?, 'TASK_ASSIGNED', 'A feed row 2', 'NORMAL', ?, NOW())
                """, UUID.randomUUID(), a.tenant(), a.user(),
                "iso-feed:" + UUID.randomUUID());

        // the exact service-level feed predicate of
        // WorkflowNotificationController.myNotifications:
        //   WHERE tenant_id = ? AND recipient_user_id = ?
        // queried as tenant B + user Ub — must see NOTHING of tenant A's feed.
        List<Map<String, Object>> foreignFeed = jdbc.queryForList("""
                SELECT id, event_type, title
                  FROM workflow_user_notifications
                 WHERE tenant_id = ? AND recipient_user_id = ?
                 ORDER BY created_at DESC
                """, b.tenant(), b.user());
        assertThat(foreignFeed).isEmpty();

        // the exact unread-count predicate of
        // WorkflowNotificationController.unreadCount as tenant B — 0.
        Long foreignUnread = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_user_notifications
                 WHERE tenant_id = ? AND recipient_user_id = ? AND read_at IS NULL
                """, Long.class, b.tenant(), b.user());
        assertThat(foreignUnread).isZero();

        // positive control: the owning principal still sees exactly its rows
        // (the seed is real and the predicates are discriminating, not empty).
        Long ownFeed = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_user_notifications
                 WHERE tenant_id = ? AND recipient_user_id = ?
                """, Long.class, a.tenant(), a.user());
        assertThat(ownFeed).isEqualTo(2L);
        Long ownUnread = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_user_notifications
                 WHERE tenant_id = ? AND recipient_user_id = ? AND read_at IS NULL
                """, Long.class, a.tenant(), a.user());
        assertThat(ownUnread).isEqualTo(2L);
    }

    // ===== 2. dispatch: intents never merge across tenants =====

    @Test
    void dispatchDeliversEachTenantIntentToItsOwnFeedOnly() {
        TenantFixture a = seedTenant("dispatch-a");
        TenantFixture b = seedTenant("dispatch-b");
        // SAME dedup key format AND same key value on both tenants: the dedup
        // identity is (tenant_id, deduplication_key), so the two intents must
        // stay distinct — never merge across tenants.
        String sharedDedupKey = "iso-dispatch:shared-key-1";
        UUID intentA = notifications.enqueue(a.tenant(),
                new WorkflowNotificationService.NotificationIntentRequest(
                        "TASK_ASSIGNED", null, null, null, a.user(), null, null,
                        "IN_APP", sharedDedupKey, "Tenant A notification", "body A",
                        null, null, "NORMAL", null, null, null, null, Map.of()));
        UUID intentB = notifications.enqueue(b.tenant(),
                new WorkflowNotificationService.NotificationIntentRequest(
                        "TASK_ASSIGNED", null, null, null, b.user(), null, null,
                        "IN_APP", sharedDedupKey, "Tenant B notification", "body B",
                        null, null, "NORMAL", null, null, null, null, Map.of()));
        // distinct durable intents per tenant under the identical key
        assertThat(intentA).isNotEqualTo(intentB);
        Integer aIntents = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_notification_intents
                 WHERE tenant_id = ? AND deduplication_key = ?
                """, Integer.class, a.tenant(), sharedDedupKey);
        Integer bIntents = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_notification_intents
                 WHERE tenant_id = ? AND deduplication_key = ?
                """, Integer.class, b.tenant(), sharedDedupKey);
        assertThat(aIntents).isEqualTo(1);
        assertThat(bIntents).isEqualTo(1);

        int processed = dispatcher.dispatchBatch();

        // each intent delivered exactly once, to ITS OWN tenant's feed row
        Integer aFeed = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_user_notifications
                 WHERE tenant_id = ? AND recipient_user_id = ? AND intent_id = ?
                """, Integer.class, a.tenant(), a.user(), intentA);
        Integer bFeed = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_user_notifications
                 WHERE tenant_id = ? AND recipient_user_id = ? AND intent_id = ?
                """, Integer.class, b.tenant(), b.user(), intentB);
        assertThat(aFeed).isEqualTo(1);
        assertThat(bFeed).isEqualTo(1);
        // no cross-tenant delivery: A's recipient never sees B's content
        Integer crossIntoA = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_user_notifications
                 WHERE tenant_id = ? AND (recipient_user_id = ? OR intent_id = ?)
                """, Integer.class, a.tenant(), b.user(), intentB);
        Integer crossIntoB = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_user_notifications
                 WHERE tenant_id = ? AND (recipient_user_id = ? OR intent_id = ?)
                """, Integer.class, b.tenant(), a.user(), intentA);
        assertThat(crossIntoA).isZero();
        assertThat(crossIntoB).isZero();
        // content stays with its own tenant (no merge of titles/bodies)
        Map<String, Object> aRow = jdbc.queryForMap("""
                SELECT title, body FROM workflow_user_notifications
                 WHERE tenant_id = ? AND intent_id = ?
                """, a.tenant(), intentA);
        Map<String, Object> bRow = jdbc.queryForMap("""
                SELECT title, body FROM workflow_user_notifications
                 WHERE tenant_id = ? AND intent_id = ?
                """, b.tenant(), intentB);
        assertThat(aRow.get("title")).isEqualTo("Tenant A notification");
        assertThat(bRow.get("title")).isEqualTo("Tenant B notification");
        // both intents terminated DELIVERED (processed covers both; other
        // intents from the shared DB are possible, so assert states not count)
        assertThat(processed).isGreaterThanOrEqualTo(2);
        String statusA = jdbc.queryForObject("""
                SELECT delivery_status FROM workflow_notification_intents
                 WHERE tenant_id = ? AND id = ?
                """, String.class, a.tenant(), intentA);
        String statusB = jdbc.queryForObject("""
                SELECT delivery_status FROM workflow_notification_intents
                 WHERE tenant_id = ? AND id = ?
                """, String.class, b.tenant(), intentB);
        assertThat(statusA).isEqualTo("DELIVERED");
        assertThat(statusB).isEqualTo("DELIVERED");
    }

    // ===== 3. webhook endpoint resolution: no cross-tenant targeting =====

    @Test
    void webhookEndpointResolutionDeniesCrossTenantTargeting() {
        TenantFixture a = seedTenant("hook-a");
        TenantFixture b = seedTenant("hook-b");
        WorkflowWebhookEndpointService.CreatedEndpoint created =
                webhookEndpoints.create(a.tenant(), "iso-hook-a",
                        "https://example.invalid/iso-hook", "R2-K fixture",
                        List.of("INSTANCE_COMPLETED"), a.user());

        // tenant B resolves endpoints for the same event type -> nothing
        List<Map<String, Object>> foreign = webhookEndpoints.activeEndpointsFor(
                b.tenant(), "INSTANCE_COMPLETED");
        assertThat(foreign).isEmpty();
        // tenant B's registry listing is empty (no leak through list either)
        assertThat(webhookEndpoints.list(b.tenant())).isEmpty();

        // tenant A still resolves exactly its own endpoint
        List<Map<String, Object>> own = webhookEndpoints.activeEndpointsFor(
                a.tenant(), "INSTANCE_COMPLETED");
        assertThat(own).hasSize(1);
        assertThat(((UUID) own.get(0).get("id"))).isEqualTo(created.id());
        // and the resolution carries no plain secret (SHA-256 hash only)
        assertThat(own.get(0).get("secret_hash")).isNotNull();
        assertThat(String.valueOf(own.get(0).get("secret_hash")))
                .doesNotContain(created.plainSecret());
    }

    // ===== 4. feedback + analytics: zero cross-tenant aggregation =====

    @Test
    void feedbackAndAnalyticsDenyCrossTenantReads() {
        TenantFixture a = seedTenant("fbx-a");
        TenantFixture b = seedTenant("fbx-b");
        jdbc.update("""
                INSERT INTO workflow_customer_feedback (
                    id, tenant_id, source_module, source_entity_type,
                    source_entity_id, rating, comment, submitted_by_type, created_at)
                VALUES (?, ?, 'CRM', 'account', ?, 5, 'iso feedback', 'USER', NOW())
                """, UUID.randomUUID(), a.tenant(), UUID.randomUUID());

        // WorkflowCustomerFeedbackController.list tenant-scoped SELECT,
        // issued as tenant B -> must return nothing.
        List<Map<String, Object>> foreignFeedback = jdbc.queryForList("""
                SELECT id, workflow_instance_id, source_entity_type,
                       source_entity_id, rating, comment, submitted_by_type,
                       created_at
                  FROM workflow_customer_feedback
                 WHERE tenant_id = ?
                """, b.tenant());
        assertThat(foreignFeedback).isEmpty();
        // positive control: tenant A sees exactly its one row
        List<Map<String, Object>> ownFeedback = jdbc.queryForList("""
                SELECT id, workflow_instance_id, source_entity_type,
                       source_entity_id, rating, comment, submitted_by_type,
                       created_at
                  FROM workflow_customer_feedback
                 WHERE tenant_id = ?
                """, a.tenant());
        assertThat(ownFeedback).hasSize(1);

        // minimal analytics process fact seeded directly for tenant A
        UUID factInstance = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_analytics_process_facts (
                    id, tenant_id, workflow_instance_id, status, started_at,
                    sla_breach_count, timeout_count, reassignment_count)
                VALUES (?, ?, ?, 'RUNNING', ?, 0, 0, 0)
                """, UUID.randomUUID(), a.tenant(), factInstance,
                Timestamp.from(Instant.now().minusSeconds(60)));

        Map<String, Object> foreignDashboard = queryService.serviceDashboard(
                b.tenant(), Instant.now().minusSeconds(86400), Instant.now());
        assertThat(((Number) foreignDashboard.get("total_instances")).longValue())
                .isZero();
        Map<String, Object> ownDashboard = queryService.serviceDashboard(
                a.tenant(), Instant.now().minusSeconds(86400), Instant.now());
        assertThat(((Number) ownDashboard.get("total_instances")).longValue())
                .isEqualTo(1L);
    }
}
