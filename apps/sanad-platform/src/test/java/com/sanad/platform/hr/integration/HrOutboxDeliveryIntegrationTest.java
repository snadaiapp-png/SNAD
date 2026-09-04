package com.sanad.platform.hr.integration;

import com.sanad.platform.audit.PlatformAuditSink;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G0 / Master Task 4 / WS4 Task 6 RED contract — at-least-once audit and
 * outbox delivery workers.
 *
 * <p>Required behavior: SHORT claim transaction → release the DB transaction →
 * external dispatch → SHORT finalize transaction. Two workers racing for the
 * same row leave exactly one valid claimant. Retry increments attempts and
 * applies backoff on {@code available_at}; exhausted attempts become
 * {@code DEAD_LETTER}; completed deliveries are never repeated; a stale claim
 * can be recovered; the consumer never receives raw restricted payload.</p>
 *
 * <p>WS4 Task 6 behavior is exercised through reflection so a RED run fails
 * only because the Task 6 application classes are missing — never because of
 * a compilation error (same clean-RED convention as HrAuditOutboxAtomicityIntegrationTest).</p>
 */
class HrOutboxDeliveryIntegrationTest {

    private static final String OUTBOX_WORKER = "com.sanad.platform.hr.integration.HrOutboxWorker";
    private static final String OUTBOX_CONSUMER = "com.sanad.platform.hr.integration.HrOutboxEventConsumer";
    private static final String AUDIT_WORKER = "com.sanad.platform.hr.audit.HrAuditDeliveryWorker";

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static String isolatedUrl;

    private Connection connection;
    private DriverManagerDataSource dataSource;
    private Class<?> workerClass;
    private Class<?> consumerClass;
    private Object outboxWorker;
    private Object auditWorker;
    private RecordingSink sink;
    private UUID tenantId;

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
                .javaMigrations(new com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities())
                .baselineOnMigrate(true)
                .cleanDisabled(false)
                .validateOnMigrate(false)
                .load();
        flyway.clean();
        flyway.migrate();
        connection = ds.getConnection();
        connection.setAutoCommit(true);
        dataSource = ds;

        tenantId = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "WS4-T6-" + tenantId);
            ps.setString(3, "ws4t6-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        setTenant(tenantId);

        workerClass = Class.forName(OUTBOX_WORKER);
        consumerClass = Class.forName(OUTBOX_CONSUMER);
        Class.forName(AUDIT_WORKER);

        sink = new RecordingSink();
        outboxWorker = newOutboxWorker("worker-A", List.of(newConsumerProxy(new ArrayList<>(), new AtomicReference<>(), null)));
        auditWorker = newAuditWorker("audit-worker-A");
    }

    // ==================== RED: discovery ====================

    @Test
    void deliveryWorkersAreDiscoverable() {
        assertThat(workerClass).as("HrOutboxWorker must exist and be constructible").isNotNull();
        assertThat(consumerClass).as("HrOutboxEventConsumer port must exist").isNotNull();
        assertThat(outboxWorker).isNotNull();
        assertThat(auditWorker).as("HrAuditDeliveryWorker must exist and be constructible").isNotNull();
    }

    // ==================== outbox worker ====================

    @Test
    void claimsAndDeliversReadyEventExactlyOnce() throws Exception {
        UUID eventId = insertOutboxEvent("HRM.EMPLOYEE.ACTIVATED.v1", "{\"note\":\"CLAIM-MARKER-XYZ\"}");
        List<Object> received = new ArrayList<>();
        Object worker = newOutboxWorker("worker-exactly-once",
                List.of(newConsumerProxy(received, new AtomicReference<>(), null)));

        assertThat(processOnce(worker)).as("first processOnce must claim and deliver").isTrue();
        assertThat(processOnce(worker)).as("delivered event must never be re-dispatched").isFalse();

        assertThat(received).hasSize(1);
        assertThat(queryScalar("SELECT status FROM hr_domain_event_outbox WHERE event_id = '" + eventId + "'"))
                .isEqualTo("DELIVERED");
        assertThat(queryScalar("SELECT delivered_at IS NOT NULL FROM hr_domain_event_outbox WHERE event_id = '" + eventId + "'"))
                .isEqualTo("t");
        assertThat(queryScalar("SELECT claim_token IS NULL FROM hr_domain_event_outbox WHERE event_id = '" + eventId + "'"))
                .as("finalize must clear the claim")
                .isEqualTo("t");
    }

    @Test
    void eventTypeTenantAndPayloadReachConsumer() throws Exception {
        UUID eventId = insertOutboxEvent("HRM.EMPLOYEE.TERMINATED.v1", "{\"employmentId\":\"" + UUID.randomUUID() + "\"}");
        List<Object> received = new ArrayList<>();
        Object worker = newOutboxWorker("worker-payload",
                List.of(newConsumerProxy(received, new AtomicReference<>(), null)));
        processOnce(worker);

        assertThat(received).hasSize(1);
        Object event = received.get(0);
        assertThat(eventString(event, "eventType")).isEqualTo("HRM.EMPLOYEE.TERMINATED.v1");
        assertThat(eventString(event, "payload")).contains("employmentId");
        assertThat(eventString(event, "tenantId")).isEqualTo(tenantId.toString());
        assertThat(eventString(event, "eventId")).isEqualTo(eventId.toString());
    }

    @Test
    void claimTransactionCommitsBeforeDispatch() throws Exception {
        UUID eventId = insertOutboxEvent("HRM.EMPLOYEE.ACTIVATED.v1", "{\"note\":\"claim-visibility\"}");
        AtomicReference<String> observedStatus = new AtomicReference<>();
        AtomicReference<Boolean> springTxActiveDuringDispatch = new AtomicReference<>();
        Consumer<Connection> inspector = second -> {
            try {
                // The inspector connection is a plain RLS-subject session — set the
                // tenant GUC so its SELECT can observe the claimed row.
                try (PreparedStatement g = second.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
                    g.setString(1, tenantId.toString());
                    g.execute();
                }
                ResultSet rs = second.createStatement().executeQuery(
                        "SELECT status, claimed_by FROM hr_domain_event_outbox WHERE event_id = '" + eventId + "'");
                rs.next();
                observedStatus.set(rs.getString(1) + ":" + rs.getString(2));
                springTxActiveDuringDispatch.set(TransactionSynchronizationManager.isActualTransactionActive());
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        };
        Object worker = newOutboxWorker("hr-outbox-worker-visibility",
                List.of(newConsumerProxy(new ArrayList<>(), new AtomicReference<>(), inspector)));
        processOnce(worker);

        assertThat(observedStatus.get())
                .as("the claim transaction must be COMMITTED before dispatch (row visible from a second connection)")
                .isEqualTo("CLAIMED:hr-outbox-worker-visibility");
        assertThat(springTxActiveDuringDispatch.get())
                .as("dispatch must run outside any (Spring) transaction")
                .isFalse();
    }

    @Test
    void twoWorkersRaceWithoutDoubleDelivery() throws Exception {
        UUID first = insertOutboxEvent("HRM.EMPLOYEE.ACTIVATED.v1", "{\"n\":1}");
        UUID second = insertOutboxEvent("HRM.EMPLOYEE.SUSPENDED.v1", "{\"n\":2}");
        List<Object> received = new ArrayList<>();
        Object workerA = newOutboxWorker("race-A", List.of(newConsumerProxy(received, new AtomicReference<>(), null)));
        Object workerB = newOutboxWorker("race-B", List.of(newConsumerProxy(received, new AtomicReference<>(), null)));

        processOnce(workerA);
        processOnce(workerB);

        assertThat(received).hasSize(2);
        assertThat(queryScalar("SELECT COUNT(*) FROM hr_domain_event_outbox WHERE status = 'DELIVERED'"))
                .isEqualTo("2");

        // Exclusivity: with one fresh READY event the second claimer gets nothing.
        UUID third = insertOutboxEvent("HRM.EMPLOYEE.USER_LINKED.v1", "{\"n\":3}");
        Object claimA = claimNext(workerA);
        Object claimB = claimNext(workerB);
        assertThat(claimA).as("first worker must win the claim").isNotNull();
        assertThat(claimB).as("second worker must not claim the same row while the claim is live").isNull();
        String claimedEvent = claimString(claimA, "eventId");
        assertThat(claimedEvent).isEqualTo(third.toString());
    }

    @Test
    void retryIncrementsAttemptsAndAppliesBackoff() throws Exception {
        UUID eventId = insertOutboxEvent("HRM.EMPLOYEE.ACTIVATED.v1", "{\"n\":1}");
        // fail the first TWO dispatch attempts, then succeed
        AtomicInteger failuresRemaining = new AtomicInteger(2);
        List<Object> received = new ArrayList<>();
        Object worker = newOutboxWorker("worker-retry", List.of(newConsumerProxy(received, failuresRemaining, null)));

        processOnce(worker);

        assertThat(queryScalar("SELECT status FROM hr_domain_event_outbox WHERE event_id = '" + eventId + "'"))
                .as("a failed dispatch must return to READY for retry")
                .isEqualTo("READY");
        assertThat(queryScalar("SELECT attempt_count FROM hr_domain_event_outbox WHERE event_id = '" + eventId + "'"))
                .as("retry semantics must increment attempts")
                .isEqualTo("1");
        assertThat(queryScalar("SELECT available_at > NOW() FROM hr_domain_event_outbox WHERE event_id = '" + eventId + "'"))
                .as("backoff must push available_at into the future")
                .isEqualTo("t");
        assertThat(queryScalar("SELECT claim_token IS NULL FROM hr_domain_event_outbox WHERE event_id = '" + eventId + "'"))
                .isEqualTo("t");

        // Release backoff and fail again — attempt count must grow.
        executeUpdate("UPDATE hr_domain_event_outbox SET available_at = NOW() - INTERVAL '1 second' WHERE event_id = ?",
                ps -> ps.setObject(1, eventId));
        processOnce(worker);
        assertThat(queryScalar("SELECT attempt_count FROM hr_domain_event_outbox WHERE event_id = '" + eventId + "'"))
                .isEqualTo("2");
    }

    @Test
    void exhaustedAttemptsBecomeDeadLetter() throws Exception {
        UUID eventId = insertOutboxEvent("HRM.EMPLOYEE.ACTIVATED.v1", "{\"n\":1}");
        executeUpdate("UPDATE hr_domain_event_outbox SET max_attempts = 1 WHERE event_id = ?",
                ps -> ps.setObject(1, eventId));
        AtomicReference<RuntimeException> failure = new AtomicReference<>(
                new RuntimeException("SIMULATED_DISPATCH_FAILURE"));
        Object worker = newOutboxWorker("worker-dead", List.of(newConsumerProxy(new ArrayList<>(), failure, null)));

        processOnce(worker);

        assertThat(queryScalar("SELECT status FROM hr_domain_event_outbox WHERE event_id = '" + eventId + "'"))
                .as("exhausted attempts must become DEAD_LETTER")
                .isEqualTo("DEAD_LETTER");
        assertThat(processOnce(worker))
                .as("DEAD_LETTER rows must never be claimed again")
                .isFalse();
    }

    @Test
    void staleClaimIsRecoverableAndOnlyValidClaimantFinalizes() throws Exception {
        UUID eventId = insertOutboxEvent("HRM.EMPLOYEE.ACTIVATED.v1", "{\"n\":1}");
        List<Object> received = new ArrayList<>();

        Object workerA = newOutboxWorker("stale-A", List.of(newConsumerProxy(new ArrayList<>(), new AtomicReference<>(), null)));
        Object workerB = newOutboxWorker("stale-B", List.of(newConsumerProxy(received, new AtomicReference<>(), null)));

        Object claimA = claimNext(workerA);
        assertThat(claimA).isNotNull();
        UUID tokenA = (UUID) claimObject(claimA, "claimToken");

        // Worker A dies mid-flight: its claim goes stale.
        executeUpdate("UPDATE hr_domain_event_outbox SET claim_expires_at = NOW() - INTERVAL '1 second' WHERE event_id = ?",
                ps -> ps.setObject(1, eventId));

        // Worker B recovers the stale claim through the full dispatch cycle.
        assertThat(processOnce(workerB)).isTrue();

        // The stale claimant must not be able to finalize with its expired token.
        boolean finalizedByA = (Boolean) workerClass
                .getMethod("finalizeDelivered", UUID.class, UUID.class, UUID.class)
                .invoke(workerA, tenantId, eventId, tokenA);
        assertThat(finalizedByA)
                .as("the stale claimant must not be able to finalize with its expired token")
                .isFalse();

        assertThat(received).hasSize(1);
        assertThat(queryScalar("SELECT status FROM hr_domain_event_outbox WHERE event_id = '" + eventId + "'"))
                .isEqualTo("DELIVERED");
    }

    // ==================== audit delivery worker ====================

    @Test
    void auditDeliveryClaimsAndShipsMetadataOnly() throws Exception {
        String sentinel = "SENTINEL-PII-VALUE-X";
        UUID auditId = insertAuditFixture(sentinel);
        List<PlatformAuditSink.AuditSinkRecord> delivered = sink.records;
        Object worker = newAuditWorker("audit-worker-meta");

        assertThat(processOnce(worker)).isTrue();
        assertThat(processOnce(worker)).as("completed audit delivery must not repeat").isFalse();

        assertThat(delivered).hasSize(1);
        PlatformAuditSink.AuditSinkRecord record = delivered.get(0);
        assertThat(record.tenantId()).isEqualTo(tenantId);
        assertThat(record.action()).isEqualTo("TEST.SENSITIVE.ACTION");
        assertThat(record.resourceType()).isEqualTo("HR_PERSON");
        assertThat(record.result()).isEqualTo("SUCCESS");
        assertThat(record.sanitizedDetails())
                .as("the platform sink must receive identifiers/classification only — never ledger state values")
                .doesNotContain(sentinel);
        assertThat(queryScalar("SELECT status FROM hr_audit_delivery WHERE audit_id = '" + auditId + "'"))
                .isEqualTo("DELIVERED");
    }

    @Test
    void auditDeliveryFailureRetriesWithBackoff() throws Exception {
        UUID auditId = insertAuditFixture("SENTINEL-RETRY");
        sink.failure.set(new RuntimeException("SIMULATED_SINK_FAILURE"));
        Object worker = newAuditWorker("audit-worker-retry");

        processOnce(worker);

        assertThat(queryScalar("SELECT status FROM hr_audit_delivery WHERE audit_id = '" + auditId + "'"))
                .as("failed delivery awaits retry in FAILED state")
                .isEqualTo("FAILED");
        assertThat(queryScalar("SELECT attempt_count FROM hr_audit_delivery WHERE audit_id = '" + auditId + "'"))
                .isEqualTo("1");
        assertThat(queryScalar("SELECT available_at > NOW() FROM hr_audit_delivery WHERE audit_id = '" + auditId + "'"))
                .as("backoff must push available_at into the future")
                .isEqualTo("t");
    }

    @Test
    void auditDeliveryExhaustedBecomesDeadLetter() throws Exception {
        UUID auditId = insertAuditFixture("SENTINEL-DEAD");
        executeUpdate("UPDATE hr_audit_delivery SET max_attempts = 1 WHERE audit_id = ?",
                ps -> ps.setObject(1, auditId));
        sink.failure.set(new RuntimeException("SIMULATED_SINK_FAILURE"));
        Object worker = newAuditWorker("audit-worker-dead");

        processOnce(worker);

        assertThat(queryScalar("SELECT status FROM hr_audit_delivery WHERE audit_id = '" + auditId + "'"))
                .isEqualTo("DEAD_LETTER");
        assertThat(processOnce(worker))
                .as("DEAD_LETTER audit rows must never be claimed again")
                .isFalse();
    }

    // ==================== consumer pipeline safety ====================

    @Test
    void rawRestrictedPayloadsCannotEnterOutboxPipeline() throws Exception {
        assertThatThrownBy(() -> insertOutboxEvent("HRM.EMPLOYEE.ACTIVATED.v1", "{\"national_id\":\"raw-value\"}"))
                .as("the DB-level redaction guard must make raw restricted payloads unrepresentable "
                        + "in the consumer-facing outbox pipeline")
                .isInstanceOf(SQLException.class);
    }

    // ==================== reflection plumbing ====================

    private Object newOutboxWorker(String workerId, List<Object> consumers) throws Exception {
        Constructor<?> ctor = workerClass.getConstructor(DataSource.class, List.class, String.class, int.class);
        return ctor.newInstance(dataSource, consumers, workerId, 60);
    }

    private Object newAuditWorker(String workerId) throws Exception {
        Class<?> auditClass = Class.forName(AUDIT_WORKER);
        Constructor<?> ctor = auditClass.getConstructor(
                DataSource.class, ObjectMapper.class, PlatformAuditSink.class, String.class, int.class);
        return ctor.newInstance(dataSource, new ObjectMapper(), sink, workerId, 60);
    }

    private Object newConsumerProxy(List<Object> received,
                                    Object failureControl,
                                    Consumer<Connection> duringDispatch) {
        return Proxy.newProxyInstance(consumerClass.getClassLoader(), new Class[]{consumerClass},
                (proxy, method, args) -> {
                    if ("onEvent".equals(method.getName())) {
                        received.add(args[0]);
                        if (duringDispatch != null) {
                            try (Connection second = dataSource.getConnection()) {
                                duringDispatch.accept(second);
                            }
                        }
                        if (failureControl instanceof AtomicReference<?> ref) {
                            RuntimeException f = ref.get() == null ? null : (RuntimeException) ((AtomicReference<RuntimeException>) ref).getAndSet(null);
                            if (f != null) {
                                throw f;
                            }
                        } else if (failureControl instanceof AtomicInteger remaining) {
                            if (remaining.getAndUpdate(p -> p > 0 ? p - 1 : p) > 0) {
                                throw new RuntimeException("SIMULATED_DISPATCH_FAILURE");
                            }
                        }
                    }
                    return null;
                });
    }

    private boolean processOnce(Object worker) throws Exception {
        return (Boolean) worker.getClass().getMethod("processOnce").invoke(worker);
    }

    private Object claimNext(Object worker) throws Exception {
        return worker.getClass().getMethod("claimNext").invoke(worker);
    }

    private Object claimObject(Object claim, String accessor) throws Exception {
        return claim.getClass().getMethod(accessor).invoke(claim);
    }

    private String claimString(Object claim, String accessor) throws Exception {
        Object value = claimObject(claim, accessor);
        return value == null ? null : value.toString();
    }

    private String eventString(Object event, String accessor) throws Exception {
        Object value = event.getClass().getMethod(accessor).invoke(event);
        return value == null ? null : value.toString();
    }

    /** Recording PlatformAuditSink stub with injectable failure. */
    private static class RecordingSink implements PlatformAuditSink {
        final List<AuditSinkRecord> records = new ArrayList<>();
        final AtomicReference<RuntimeException> failure = new AtomicReference<>();

        @Override
        public void accept(AuditSinkRecord record) {
            RuntimeException f = failure.getAndSet(null);
            if (f != null) {
                throw f;
            }
            records.add(record);
        }
    }

    private UUID insertOutboxEvent(String eventType, String payloadJson) throws Exception {
        UUID eventId = UUID.randomUUID();
        executeUpdate(
                "INSERT INTO hr_domain_event_outbox (event_id, tenant_id, event_type, event_version, "
                        + "aggregate_type, aggregate_id, actor_user_id, occurred_at, payload, status) "
                        + "VALUES (?,?,?,?,?,?,?,NOW(),?::jsonb,'READY')",
                ps -> {
                    ps.setObject(1, eventId);
                    ps.setObject(2, tenantId);
                    ps.setString(3, eventType);
                    ps.setInt(4, 1);
                    ps.setString(5, "HR_EMPLOYMENT");
                    ps.setObject(6, UUID.randomUUID());
                    ps.setObject(7, UUID.randomUUID());
                    ps.setString(8, payloadJson);
                });
        return eventId;
    }

    /** Inserts an audit ledger row (with a sentinel value inside before_state) + PENDING delivery row. */
    private UUID insertAuditFixture(String sentinelValue) throws Exception {
        UUID auditId = UUID.randomUUID();
        executeUpdate(
                "INSERT INTO hr_audit_ledger (id, tenant_id, actor_user_id, action, resource_type, resource_id, "
                        + "data_classification, reason, before_state, result, occurred_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?::jsonb,'SUCCESS',NOW())",
                ps -> {
                    ps.setObject(1, auditId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, UUID.randomUUID());
                    ps.setString(4, "TEST.SENSITIVE.ACTION");
                    ps.setString(5, "HR_PERSON");
                    ps.setObject(6, UUID.randomUUID());
                    ps.setString(7, "RESTRICTED");
                    ps.setString(8, "{\"reason\":\"fixture\"}");
                    ps.setString(9, "{\"note\":\"" + sentinelValue + "\"}");
                });
        executeUpdate("INSERT INTO hr_audit_delivery (audit_id, tenant_id, status) VALUES (?,?, 'PENDING')",
                ps -> {
                    ps.setObject(1, auditId);
                    ps.setObject(2, tenantId);
                });
        return auditId;
    }

    private void setTenant(UUID tenant) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenant.toString());
            ps.execute();
        }
    }

    private String queryScalar(String sql) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getString(1);
        }
    }

    private void executeUpdate(String sql, SqlBinder binder) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (binder != null) binder.bind(ps);
            ps.executeUpdate();
        }
    }

    private interface SqlBinder {
        void bind(PreparedStatement ps) throws Exception;
    }
}
