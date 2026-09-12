package com.sanad.platform.workflow;

import com.sanad.platform.crm.email.domain.EmailPort;
import com.sanad.platform.crm.email.domain.EmailMessage;
import com.sanad.platform.crm.email.domain.EmailSendResult;
import com.sanad.platform.crm.email.infrastructure.EmailProperties;
import com.sanad.platform.workflow.analytics.WorkflowAnalyticsProjectionService;
import com.sanad.platform.workflow.application.WorkflowNotificationService;
import com.sanad.platform.workflow.notification.EmailNotificationProvider;
import com.sanad.platform.workflow.notification.InAppNotificationProvider;
import com.sanad.platform.workflow.notification.PushNotificationProvider;
import com.sanad.platform.workflow.notification.WebhookNotificationProvider;
import com.sanad.platform.workflow.notification.WhatsAppNotificationProvider;
import com.sanad.platform.workflow.notification.WorkflowChannel;
import com.sanad.platform.workflow.notification.WorkflowChannelProvider;
import com.sanad.platform.workflow.notification.WorkflowChannelRegistry;
import com.sanad.platform.workflow.notification.WorkflowDeliveryRequest;
import com.sanad.platform.workflow.notification.WorkflowDeliveryResult;
import com.sanad.platform.workflow.notification.WorkflowExternalRecipientResolver;
import com.sanad.platform.workflow.notification.WorkflowNotificationDispatcher;
import com.sanad.platform.workflow.notification.WorkflowNotificationPolicyService;
import com.sun.net.httpserver.HttpServer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R2 MATRIX B — notification delivery foundation (GATES R2.3/R2.4/R2.5/
 * R2.6/R2.7/R2.8/R2.9/R2.10). PostgreSQL Direct. Covers: policy model
 * validation (fail-closed), rich intent enqueue + dedup identity, provider
 * SPI registry (duplicate -> FAIL_STARTUP, unknown -> fail-closed), IN_APP
 * materialization + dedup, dispatcher lifecycle (PENDING -> DELIVERED,
 * retryable -> RETRY_WAIT + backoff, terminal failures, attempt cap),
 * EMAIL adapter contract (local stub transport), PUSH fail-closed +
 * registry hygiene, WHATSAPP fail-closed + CRM verified-contact
 * resolution, WEBHOOK governance (HTTPS-only, SSRF block, HMAC), and the
 * AD-16 decoupling invariant (provider failure never reverses committed
 * workflow state).
 */
class WorkflowR2NotificationFoundationTest {

    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static PlatformTransactionManager transactionManager;
    private static WorkflowNotificationService notifications;
    private static WorkflowNotificationPolicyService policies;
    private static WorkflowNotificationDispatcher dispatcher;
    private static InAppNotificationProvider inApp;
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
            System.err.println("[WorkflowR2NotificationFoundationTest] PostgreSQL Direct "
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
        policies = new WorkflowNotificationPolicyService(jdbc);
        inApp = new InAppNotificationProvider(jdbc);
        WorkflowChannelRegistry registry = new WorkflowChannelRegistry(
                List.of(inApp, stubEmailProvider(), disabledPushProvider()));
        dispatcher = new WorkflowNotificationDispatcher(jdbc, registry, true);
    }

    // ===== stub providers (sandbox mock transport contracts) =====

    private static WorkflowChannelProvider stubEmailProvider() {
        return new WorkflowChannelProvider() {
            @Override
            public WorkflowChannel channel() {
                return WorkflowChannel.EMAIL;
            }

            @Override
            public String providerType() {
                return "stub-email";
            }

            @Override
            public WorkflowDeliveryResult deliver(WorkflowDeliveryRequest request) {
                if (request.recipientAddress() == null
                        || !request.recipientAddress().contains("@")) {
                    return WorkflowDeliveryResult.terminalFailure(
                            WorkflowDeliveryResult.FC_INVALID_RECIPIENT);
                }
                return WorkflowDeliveryResult.success("stub-email-id");
            }
        };
    }

    private static PushNotificationProvider disabledPushProvider() {
        return new PushNotificationProvider(jdbc, false, "http://127.0.0.1:1/stub", 2);
    }

    private record Fixture(UUID tenant, UUID user, UUID instance) {}

    private Fixture fixture(String tag) {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)", tenant, "R2Notif-" + tag,
                "r2-notif-" + tag + "-" + tenant.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,"
                        + "created_at,updated_at) VALUES (?, ?, ?, 'R2 User', 'ACTIVE', 'dummy', ?, ?)",
                user, tenant, "r2-notif-" + user.toString().substring(0, 8) + "@test", now, now);
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                    business_entity_id, status, started_by, started_at, engine_generation,
                    context_json, context_schema_version, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', ?, NOW(), 'Y2',
                          CAST('{}' AS jsonb), 1, 0, ?, ?)
                """, instance, tenant, y2Definition(tenant, user), user, now, now);
        createdTenants.add(tenant);
        return new Fixture(tenant, user, instance);
    }

    private UUID y2Definition(UUID tenant, UUID user) {
        UUID definition = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-R2-NOTIF', 'R2Notif', 'GENERAL', 1, 'ACTIVE',
                          'EVENT', ?, 0, 'Y2', 'PUBLISHED', 1, ?, ?)
                """, definition, tenant, definition, user, now, now);
        return definition;
    }

    // ===== GATE R2.3: policy model =====

    @Test
    void policyCreateResolveAndFailClosedValidation() {
        Fixture fx = fixture("policy");
        var policy = policies.create(fx.tenant(), "critical-email",
                "EMAIL", "TASK_ASSIGNED", "USER", "task-assigned", "en",
                "IMMEDIATE", null, "STANDARD", "PER_EVENT", "NONE", "IN_APP", "HIGH");
        assertThat(policy.id()).isNotNull();
        assertThat(policy.enabled()).isTrue();
        var resolved = policies.resolve(fx.tenant(), "TASK_ASSIGNED", WorkflowChannel.EMAIL);
        assertThat(resolved).isPresent();
        assertThat(resolved.get().id()).isEqualTo(policy.id());
        // disabled policies do not resolve
        policies.setEnabled(fx.tenant(), policy.id(), false);
        assertThat(policies.resolve(fx.tenant(), "TASK_ASSIGNED", WorkflowChannel.EMAIL))
                .isEmpty();
        // fail-closed validation: bad fallback channel, bad locale, bad enum
        assertThatThrownBy(() -> policies.create(fx.tenant(), "bad-fallback", "EMAIL",
                "TASK_ASSIGNED", "USER", null, "ar", "IMMEDIATE", null, "STANDARD",
                "PER_EVENT", "NONE", "WHATSAPP", "NORMAL"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policies.create(fx.tenant(), "bad-locale", "EMAIL",
                "TASK_ASSIGNED", "USER", null, "engl", "IMMEDIATE", null, "STANDARD",
                "PER_EVENT", "NONE", null, "NORMAL"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policies.create(fx.tenant(), "bad-timing", "EMAIL",
                "TASK_ASSIGNED", "USER", null, "ar", "SOMEDAY", null, "STANDARD",
                "PER_EVENT", "NONE", null, "NORMAL"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policies.create(fx.tenant(), "bad-event", "EMAIL",
                null, "USER", null, "ar", "IMMEDIATE", null, "STANDARD",
                "PER_EVENT", "NONE", null, "NORMAL"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===== GATE R2.4: registry fail-closed invariants =====

    @Test
    void registryDuplicateProviderFailsStartupAndUnknownChannelFailsClosed() {
        // duplicate registration -> FAIL_STARTUP
        assertThatThrownBy(() -> new WorkflowChannelRegistry(
                List.of(inApp, new InAppNotificationProvider(jdbc))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FAIL_STARTUP");
        // unknown channel at dispatch -> intent fails closed (CONFIG_ERROR terminal)
        Fixture fx = fixture("unknown-channel");
        UUID intentId = tx.execute(s -> notifications.enqueue(fx.tenant(),
                new WorkflowNotificationService.NotificationIntentRequest(
                        "TASK_ASSIGNED", fx.instance(), null, null, fx.user(),
                        null, null, "WHATSAPP", "r2-unknown-" + fx.instance(),
                        "t", "b", null, null, null, null, null, null, null, null)));
        int processed = dispatcher.dispatchBatch();
        assertThat(processed).isGreaterThanOrEqualTo(0);
        Map<String, Object> intent = jdbc.queryForMap("""
                SELECT delivery_status, failure_category FROM workflow_notification_intents
                 WHERE tenant_id = ? AND id = ?
                """, fx.tenant(), intentId);
        // No WHATSAPP provider registered -> fail closed, never silently dropped
        assertThat(intent.get("delivery_status")).isEqualTo("FAILED_TERMINAL");
        assertThat(intent.get("failure_category")).isEqualTo("CONFIG_ERROR");
    }

    // ===== GATE R2.5: lifecycle + dedup + rich enqueue =====

    @Test
    void richEnqueueDedupIdentityAndLifecycle() {
        Fixture fx = fixture("lifecycle");
        String dedupKey = "r2-lifecycle-" + fx.instance();
        UUID first = tx.execute(s -> notifications.enqueue(fx.tenant(),
                new WorkflowNotificationService.NotificationIntentRequest(
                        "TASK_ASSIGNED", fx.instance(), null, null, fx.user(),
                        null, null, "IN_APP", dedupKey,
                        "Task assigned", "You have a new task", null, "ar", "HIGH",
                        "/work-items", null, fx.instance(), dedupKey,
                        Map.of("k", "v"))));
        UUID replay = tx.execute(s -> notifications.enqueue(fx.tenant(),
                new WorkflowNotificationService.NotificationIntentRequest(
                        "TASK_ASSIGNED", fx.instance(), null, null, fx.user(),
                        null, null, "IN_APP", dedupKey,
                        "Task assigned", "You have a new task", null, "ar", "HIGH",
                        "/work-items", null, fx.instance(), dedupKey,
                        Map.of("k", "v"))));
        assertThat(replay).isEqualTo(first);
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT event_type, title, priority, deep_link, payload::text AS payload_text
                  FROM workflow_notification_intents WHERE tenant_id = ? AND id = ?
                """, fx.tenant(), first);
        assertThat(row.get("event_type")).isEqualTo("TASK_ASSIGNED");
        assertThat(row.get("title")).isEqualTo("Task assigned");
        assertThat(row.get("priority")).isEqualTo("HIGH");
        assertThat(row.get("deep_link")).isEqualTo("/work-items");
        assertThat(String.valueOf(row.get("payload_text"))).contains("\"k\"");

        // dispatcher: PENDING -> PROCESSING -> DELIVERED + IN_APP feed row
        int processed = dispatcher.dispatchBatch();
        assertThat(processed).isGreaterThanOrEqualTo(1);
        Map<String, Object> delivered = jdbc.queryForMap("""
                SELECT delivery_status, delivered_at, provider_message_id
                  FROM workflow_notification_intents WHERE tenant_id = ? AND id = ?
                """, fx.tenant(), first);
        assertThat(delivered.get("delivery_status")).isEqualTo("DELIVERED");
        assertThat(delivered.get("delivered_at")).isNotNull();
        Map<String, Object> feedRow = jdbc.queryForMap("""
                SELECT COUNT(*) AS n FROM workflow_user_notifications
                 WHERE tenant_id = ? AND recipient_user_id = ? AND intent_id = ?
                """, fx.tenant(), fx.user(), first);
        assertThat(((Number) feedRow.get("n")).longValue()).isEqualTo(1);
    }

    @Test
    void dispatcherRetryableAndTerminalFailureClassification() {
        Fixture fx = fixture("retry");
        // EMAIL intent without a recipient address -> INVALID_RECIPIENT terminal
        UUID terminal = tx.execute(s -> notifications.enqueue(fx.tenant(),
                new WorkflowNotificationService.NotificationIntentRequest(
                        "TASK_ASSIGNED", fx.instance(), null, null, null,
                        null, null, "EMAIL", "r2-terminal-" + fx.instance(),
                        null, null, null, null, null, null, null, null, null, null)));
        // retryable: stub provider that fails transiently twice then succeeds
        UUID retryable = tx.execute(s -> notifications.enqueue(fx.tenant(),
                new WorkflowNotificationService.NotificationIntentRequest(
                        "TASK_ASSIGNED", fx.instance(), null, null, fx.user(),
                        null, null, "IN_APP", "r2-retry-" + fx.instance(),
                        null, null, null, null, null, null, null, null, null, null)));
        WorkflowChannelProvider flaky = new WorkflowChannelProvider() {
            private final AtomicInteger calls = new AtomicInteger();

            @Override
            public WorkflowChannel channel() {
                return WorkflowChannel.WEBHOOK;
            }

            @Override
            public String providerType() {
                return "flaky";
            }

            @Override
            public WorkflowDeliveryResult deliver(WorkflowDeliveryRequest request) {
                if (request.intentId().equals(retryable)
                        && calls.incrementAndGet() <= 2) {
                    return WorkflowDeliveryResult.retryableFailure("PROVIDER_TRANSIENT");
                }
                if (request.intentId().equals(retryable)) {
                    return WorkflowDeliveryResult.success("flaky-ok");
                }
                return WorkflowDeliveryResult.terminalFailure("PROVIDER_REJECTED");
            }
        };
        WorkflowNotificationDispatcher custom = new WorkflowNotificationDispatcher(
                jdbc, new WorkflowChannelRegistry(List.of(inApp, flaky)), true);
        custom.dispatchBatch();
        Map<String, Object> terminalRow = jdbc.queryForMap("""
                SELECT delivery_status, failure_category FROM workflow_notification_intents
                 WHERE tenant_id = ? AND id = ?
                """, fx.tenant(), terminal);
        assertThat(terminalRow.get("delivery_status")).isEqualTo("FAILED_TERMINAL");
        assertThat(terminalRow.get("failure_category")).isEqualTo("INVALID_RECIPIENT");
        Map<String, Object> retryRow = jdbc.queryForMap("""
                SELECT delivery_status, attempt_count, next_attempt_at
                  FROM workflow_notification_intents WHERE tenant_id = ? AND id = ?
                """, fx.tenant(), retryable);
        assertThat(retryRow.get("delivery_status")).isEqualTo("RETRY_WAIT");
        assertThat(((Number) retryRow.get("attempt_count")).intValue()).isEqualTo(1);
        assertThat(retryRow.get("next_attempt_at")).isNotNull();
        // retry arrives after backoff -> success on the third attempt
        jdbc.update("""
                UPDATE workflow_notification_intents SET next_attempt_at = NOW() - INTERVAL '1s'
                 WHERE tenant_id = ? AND id = ?
                """, fx.tenant(), retryable);
        custom.dispatchBatch();
        custom.dispatchBatch();
        Map<String, Object> delivered = jdbc.queryForMap("""
                SELECT delivery_status, attempt_count FROM workflow_notification_intents
                 WHERE tenant_id = ? AND id = ?
                """, fx.tenant(), retryable);
        assertThat(delivered.get("delivery_status")).isEqualTo("DELIVERED");
        assertThat(((Number) delivered.get("attempt_count")).intValue()).isEqualTo(3);
    }

    // ===== AD-16: provider failure never reverses committed state =====

    @Test
    void workflowStateCommitSurvivesProviderFailure() {
        Fixture fx = fixture("decouple");
        UUID intentId = tx.execute(s -> {
            UUID id = notifications.enqueue(fx.tenant(),
                    new WorkflowNotificationService.NotificationIntentRequest(
                            "TASK_ASSIGNED", fx.instance(), null, null, fx.user(),
                            null, null, "IN_APP", "r2-decouple-" + fx.instance(),
                            null, null, null, null, null, null, null, null, null, null));
            // committed state mutation in the SAME transaction
            jdbc.update("UPDATE workflow_instances SET version = version + 1, "
                    + "updated_at = NOW() WHERE tenant_id = ? AND id = ?",
                    fx.tenant(), fx.instance());
            return id;
        });
        // the commit survives: intent + instance state both persisted
        Integer intentCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_notification_intents WHERE id = ?",
                Integer.class, intentId);
        Integer version = jdbc.queryForObject(
                "SELECT version FROM workflow_instances WHERE tenant_id = ? AND id = ?",
                Integer.class, fx.tenant(), fx.instance());
        assertThat(intentCount).isEqualTo(1);
        assertThat(version).isEqualTo(1);
        // a provider outage after the fact updates only the intent
        WorkflowChannelProvider failing = new WorkflowChannelProvider() {
            @Override
            public WorkflowChannel channel() {
                return WorkflowChannel.IN_APP;
            }

            @Override
            public String providerType() {
                return "outage";
            }

            @Override
            public WorkflowDeliveryResult deliver(WorkflowDeliveryRequest request) {
                throw new RuntimeException("provider outage");
            }
        };
        WorkflowNotificationDispatcher outage = new WorkflowNotificationDispatcher(
                jdbc, new WorkflowChannelRegistry(List.of(failing)), true);
        outage.dispatchBatch();
        Integer versionAfterOutage = jdbc.queryForObject(
                "SELECT version FROM workflow_instances WHERE tenant_id = ? AND id = ?",
                Integer.class, fx.tenant(), fx.instance());
        assertThat(versionAfterOutage).isEqualTo(1);
    }

    // ===== GATE R2.7/2.8/2.9: provider reality classes =====

    @Test
    void emailPushWhatsappFailClosedWithoutCredentials() {
        Fixture fx = fixture("reality");
        // PUSH without enabled provider -> CHANNEL_DISABLED terminal (never fake LIVE)
        WorkflowDeliveryResult pushResult = disabledPushProvider().deliver(
                new WorkflowDeliveryRequest(fx.tenant(), UUID.randomUUID(), "E",
                        fx.user(), null, null, null, null, "t", "b", null, "NORMAL",
                        null, null, null, null, null, null, null));
        assertThat(pushResult.delivered()).isFalse();
        assertThat(pushResult.failureCategory()).isEqualTo("CHANNEL_DISABLED");
        // WHATSAPP without provider config -> CONFIG_ERROR terminal
        WhatsAppNotificationProvider whatsapp = new WhatsAppNotificationProvider(
                new WorkflowExternalRecipientResolver(jdbc), false, "", "", 2);
        WorkflowDeliveryResult waResult = whatsapp.deliver(
                new WorkflowDeliveryRequest(fx.tenant(), UUID.randomUUID(), "E",
                        null, UUID.randomUUID(), null, null, null, "t", "b", null,
                        "NORMAL", null, null, null, null, null, null, null));
        assertThat(waResult.failureCategory()).isEqualTo("CHANNEL_DISABLED");
        // WHATSAPP enabled but unverified CRM contact -> INVALID_RECIPIENT
        WhatsAppNotificationProvider waEnabled = new WhatsAppNotificationProvider(
                new WorkflowExternalRecipientResolver(jdbc), true,
                "http://127.0.0.1:1/stub", "token", 2);
        UUID participant = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_external_participants (
                    id, tenant_id, participant_type, source_module, source_entity_type,
                    source_entity_id, communication_reference, created_at)
                VALUES (?, ?, 'CUSTOMER', 'CRM', 'account', ?, 'display', NOW())
                """, participant, fx.tenant(), UUID.randomUUID());
        WorkflowDeliveryResult unverified = waEnabled.deliver(
                new WorkflowDeliveryRequest(fx.tenant(), UUID.randomUUID(), "E",
                        null, participant, null, null, null, "t", "b", null,
                        "NORMAL", null, null, null, null, null, null, null));
        assertThat(unverified.failureCategory()).isEqualTo("INVALID_RECIPIENT");
        // EMAIL adapter: local stub port contract
        EmailNotificationProvider emailProvider = new EmailNotificationProvider(
                new ObjectProvider<EmailPort>() {
                    @Override
                    public EmailPort getIfAvailable() {
                        return new EmailPort() {
                            @Override
                            public EmailSendResult send(UUID tenantId, EmailMessage message) {
                                return EmailSendResult.success(UUID.randomUUID(),
                                        "local-id", "local");
                            }

                            @Override
                            public boolean isAvailable() {
                                return true;
                            }

                            @Override
                            public String providerName() {
                                return "local";
                            }
                        };
                    }
                },
                emailProperties("noreply@sanad.test"));
        WorkflowDeliveryResult emailOk = emailProvider.deliver(
                new WorkflowDeliveryRequest(fx.tenant(), UUID.randomUUID(), "E",
                        null, null, "dest@sanad.test", null, null, "Subject", "Body",
                        null, "NORMAL", null, null, null, null, null, null, null));
        assertThat(emailOk.delivered()).isTrue();
        // missing from-address -> CONFIG_ERROR (fail closed)
        EmailNotificationProvider emailNoFrom = new EmailNotificationProvider(
                new ObjectProvider<EmailPort>() {
                    @Override
                    public EmailPort getIfAvailable() {
                        return null;
                    }
                },
                emailProperties(""));
        WorkflowDeliveryResult emailBad = emailNoFrom.deliver(
                new WorkflowDeliveryRequest(fx.tenant(), UUID.randomUUID(), "E",
                        null, null, "dest@sanad.test", null, null, null, null, null,
                        "NORMAL", null, null, null, null, null, null, null));
        assertThat(emailBad.failureCategory()).isEqualTo("CONFIG_ERROR");
    }

    private static EmailProperties emailProperties(String from) {
        EmailProperties properties = new EmailProperties();
        properties.setFromAddress(from);
        return properties;
    }

    // ===== GATE R2.10: webhook governance =====

    @Test
    void webhookGovernanceHttpsSsrfAndHmacContract() throws Exception {
        // non-HTTPS endpoint creation rejected by the registry service
        var webhookService = new com.sanad.platform.workflow.notification.WorkflowWebhookEndpointService(jdbc);
        assertThatThrownBy(() -> webhookService.create(UUID.randomUUID(), "bad",
                "http://example.com/hook", null, List.of("TASK_ASSIGNED"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> webhookService.create(UUID.randomUUID(), "bad2",
                "https://example.com/hook", null, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
        // endpoint secret stored ONLY as SHA-256 hash
        Fixture fx = fixture("webhook");
        var created = webhookService.create(fx.tenant(), "primary",
                "https://hooks.example.test/sanad", "desc",
                List.of("TASK_ASSIGNED"), fx.user());
        String storedHash = jdbc.queryForObject(
                "SELECT secret_hash FROM workflow_webhook_endpoints WHERE id = ?",
                String.class, created.id());
        assertThat(storedHash).isNotEqualTo(created.plainSecret());
        assertThat(storedHash).hasSize(64);
        // HMAC contract: deterministic known-shape signature
        String sig = WebhookNotificationProvider.hmacSha256Hex("secret", "ts.payload");
        assertThat(sig).isEqualTo(WebhookNotificationProvider.hmacSha256Hex(
                "secret", "ts.payload"));
        assertThat(sig).hasSize(64);
        // SSRF protection: loopback https destination rejected at delivery
        WebhookNotificationProvider provider = new WebhookNotificationProvider(jdbc, 2, 65536);
        WorkflowDeliveryResult ssrf = provider.deliver(
                new WorkflowDeliveryRequest(fx.tenant(), UUID.randomUUID(), "E",
                        null, null, "https://127.0.0.1/stub", null, null, null, null,
                        null, "NORMAL", null, null, null, null, null, null, null));
        assertThat(ssrf.delivered()).isFalse();
        assertThat(ssrf.failureCategory()).isEqualTo("CONFIG_ERROR");
        // non-HTTPS destination rejected at delivery (defense in depth)
        WorkflowDeliveryResult plainHttp = provider.deliver(
                new WorkflowDeliveryRequest(fx.tenant(), UUID.randomUUID(), "E",
                        null, null, "http://hooks.example.test/x", null, null, null,
                        null, null, "NORMAL", null, null, null, null, null, null, null));
        assertThat(plainHttp.failureCategory()).isEqualTo("CONFIG_ERROR");
    }

    // ===== GATE R2.6: feed semantics =====

    @Test
    void inAppFeedDedupAndReadSemantics() {
        Fixture fx = fixture("feed");
        String dedupKey = "feed-dedup-" + fx.instance();
        WorkflowDeliveryRequest request = new WorkflowDeliveryRequest(
                fx.tenant(), UUID.randomUUID(), "TASK_ASSIGNED",
                fx.user(), null, null, null, null, "Title", "Body",
                "/work-items/x", "NORMAL", fx.instance(), null, null,
                dedupKey, null, null, null);
        WorkflowDeliveryResult first = inApp.deliver(request);
        WorkflowDeliveryResult second = inApp.deliver(request);
        assertThat(first.delivered()).isTrue();
        assertThat(second.delivered()).isTrue();
        // one user-visible row despite two deliveries (dedup)
        Integer rows = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_user_notifications
                 WHERE tenant_id = ? AND recipient_user_id = ?
                """, Integer.class, fx.tenant(), fx.user());
        assertThat(rows).isEqualTo(1);
        // unread -> read
        Map<String, Object> feedRow = jdbc.queryForMap("""
                SELECT id, read_at FROM workflow_user_notifications
                 WHERE tenant_id = ? AND recipient_user_id = ?
                """, fx.tenant(), fx.user());
        assertThat(feedRow.get("read_at")).isNull();
        jdbc.update("""
                UPDATE workflow_user_notifications SET read_at = NOW()
                 WHERE tenant_id = ? AND id = ? AND recipient_user_id = ?
                """, fx.tenant(), feedRow.get("id"), fx.user());
        Integer unread = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_user_notifications
                 WHERE tenant_id = ? AND recipient_user_id = ? AND read_at IS NULL
                """, Integer.class, fx.tenant(), fx.user());
        assertThat(unread).isEqualTo(0);
    }
}
