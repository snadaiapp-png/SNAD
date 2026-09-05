package com.sanad.platform.hr.integration;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G0 / Master Task 4 / WS4 Task 7 RED contract — employment-derived IAM
 * policy consumer.
 *
 * <p>Hard invariants under test: employment status is NOT user-account
 * status; only {@code hr_iam_access_bindings.access_mode = 'HR_MANAGED'}
 * allows HR lifecycle to affect IAM; an unmanaged user is never disabled by
 * termination; another active HR-managed employment preserves required
 * access; duplicate at-least-once delivery has no duplicate side effect; a
 * cross-tenant event cannot affect another tenant; missing binding fails
 * closed; HR never writes IAM/user tables directly (source scan).</p>
 *
 * <p>WS4 Task 7 behavior is exercised through reflection so a RED run fails
 * only because the Task 7 application classes are missing — never because of
 * a compilation error (same clean-RED convention as HrAuditOutboxAtomicityIntegrationTest).</p>
 */
class HrIamPolicyConsumerIntegrationTest {

    private static final String IAM_PORT = "com.sanad.platform.hr.integration.IamEmploymentAccessPort";
    private static final String IAM_POLICY = "com.sanad.platform.hr.integration.HrmIamAccessPolicy";
    private static final String IAM_CONSUMER = "com.sanad.platform.hr.integration.HrmIamEventConsumer";

    private static final String EVENT_ACTIVATED = "HRM.EMPLOYEE.ACTIVATED.v1";
    private static final String EVENT_SUSPENDED = "HRM.EMPLOYEE.SUSPENDED.v1";
    private static final String EVENT_TERMINATED = "HRM.EMPLOYEE.TERMINATED.v1";
    private static final String EVENT_USER_LINKED = "HRM.EMPLOYEE.USER_LINKED.v1";

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static String isolatedUrl;

    private Connection connection;
    private DriverManagerDataSource dataSource;
    private Class<?> portClass;
    private Class<?> policyClass;
    private Object consumer;
    private RecordingPort recordingPort;
    private UUID legalEntityId = UUID.randomUUID();
    private UUID tenantId;
    private UUID personId;
    private UUID userId;
    private UUID employmentId;

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

        tenantId = UUID.randomUUID();
        personId = UUID.randomUUID();
        userId = UUID.randomUUID();
        employmentId = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "WS4-T7-" + tenantId);
            ps.setString(3, "ws4t7-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        setTenant(tenantId);
        insertLegalEntity(tenantId);
        insertPerson(personId, tenantId);
        insertEmployment(employmentId, personId, tenantId, "ACTIVE");

        portClass = Class.forName(IAM_PORT);
        policyClass = Class.forName(IAM_POLICY);
        recordingPort = new RecordingPort(portClass);
        consumer = newConsumer();
    }

    // ==================== RED: discovery ====================

    @Test
    void iamPolicyClassesAreDiscoverable() {
        assertThat(portClass).as("IamEmploymentAccessPort must exist").isNotNull();
        assertThat(policyClass).as("HrmIamAccessPolicy must exist").isNotNull();
        assertThat(consumer).as("HrmIamEventConsumer must exist and be constructible").isNotNull();
        assertThat(portClass.isInstance(recordingPort.proxy))
                .as("recording port must implement the IAM access port")
                .isTrue();
    }

    // ==================== policy invariants ====================

    @Test
    void unmanagedUserIsNeverDisabledByTermination() throws Exception {
        insertBinding(personId, userId, "NON_HR_MANAGED");
        consume(event(EVENT_TERMINATED, personId, userId));

        assertThat(recordingPort.calls).as("unmanaged binding must produce NO IAM side effect").isEmpty();
        assertThat(decisionOutcome(EVENT_TERMINATED, personId, userId)).isEqualTo("NO_OP");
    }

    @Test
    void missingBindingFailsClosed() throws Exception {
        consume(event(EVENT_TERMINATED, personId, userId));

        assertThat(recordingPort.calls).as("missing binding must produce NO IAM side effect").isEmpty();
        assertThat(decisionOutcome(EVENT_TERMINATED, personId, userId)).isEqualTo("FAIL_CLOSED");
    }

    @Test
    void managedUserIsDisabledByTermination() throws Exception {
        insertBinding(personId, userId, "HR_MANAGED");
        consume(event(EVENT_TERMINATED, personId, userId));

        assertThat(recordingPort.calls).hasSize(1);
        Call call = recordingPort.calls.get(0);
        assertThat(call.method).isEqualTo("disableUserAccount");
        assertThat(call.args.get(1)).isEqualTo(userId.toString());
        assertThat(decisionOutcome(EVENT_TERMINATED, personId, userId)).isEqualTo("DISABLE");
    }

    @Test
    void activationEnablesManagedAccountOnly() throws Exception {
        insertBinding(personId, userId, "NON_HR_MANAGED");
        consume(event(EVENT_ACTIVATED, personId, userId));
        assertThat(recordingPort.calls).as("activation of unmanaged binding must not touch IAM").isEmpty();

        UUID managedUser = UUID.randomUUID();
        UUID managedPerson = UUID.randomUUID();
        insertBinding(managedPerson, managedUser, "HR_MANAGED");
        consume(event(EVENT_ACTIVATED, managedPerson, managedUser));

        assertThat(recordingPort.calls).hasSize(1);
        assertThat(recordingPort.calls.get(0).method).isEqualTo("enableUserAccount");
        assertThat(recordingPort.calls.get(0).args.get(1)).isEqualTo(managedUser.toString());
        assertThat(decisionOutcome(EVENT_USER_LINKED, managedPerson, managedUser)).isEqualTo("ENABLE");
    }

    @Test
    void anotherActiveManagedEmploymentPreservesRequiredAccess() throws Exception {
        // Person P1 (terminated) and person P2 (still ACTIVE) both HR-managed for the same user.
        UUID p2 = UUID.randomUUID();
        UUID e2 = UUID.randomUUID();
        insertPerson(p2, tenantId);
        insertBinding(personId, userId, "HR_MANAGED");
        insertBinding(p2, userId, "HR_MANAGED");
        insertEmployment(e2, p2, tenantId, "ACTIVE");

        consume(event(EVENT_TERMINATED, personId, userId));

        assertThat(recordingPort.calls)
                .as("another active HR-managed employment must preserve the required access")
                .isEmpty();
        assertThat(decisionOutcome(EVENT_TERMINATED, personId, userId)).isEqualTo("NO_OP");
    }

    // ==================== consumer semantics ====================

    @Test
    void duplicateEventHasNoDuplicateSideEffect() throws Exception {
        insertBinding(personId, userId, "HR_MANAGED");
        HrOutboxEvent first = event(EVENT_TERMINATED, personId, userId);
        HrOutboxEvent duplicate = new HrOutboxEvent(
                first.eventId(), first.tenantId(), first.eventType(), first.eventVersion(),
                first.aggregateType(), first.aggregateId(), first.organizationId(), first.actorUserId(),
                first.occurredAt(), first.correlationId(), first.causationId(), first.idempotencyKey(),
                first.dataClassification(), first.payload());

        consume(first);
        consume(duplicate);

        assertThat(recordingPort.calls)
                .as("at-least-once duplicate delivery must not repeat the side effect")
                .hasSize(1);
    }

    @Test
    void crossTenantEventCannotAffectAnotherTenant() throws Exception {
        UUID tenantB = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())")) {
            ps.setObject(1, tenantB);
            ps.setString(2, "WS4-T7-B-" + tenantB);
            ps.setString(3, "ws4t7b-" + tenantB.toString().substring(0, 8));
            ps.executeUpdate();
        }
        // A binding for the SAME user exists in tenant B (its own person row —
        // hr_people.id is globally unique), but the event arrives under tenant A
        // — tenant A has no binding, so the decision must fail closed and
        // tenant B's binding must remain untouched.
        setTenant(tenantB);
        UUID personB = UUID.randomUUID();
        insertPerson(personB, tenantB);
        insertBinding(tenantB, personB, userId, "HR_MANAGED");
        setTenant(tenantId);

        HrOutboxEvent foreignEvent = event(EVENT_TERMINATED, personId, userId);
        consume(foreignEvent);

        assertThat(recordingPort.calls)
                .as("a cross-tenant event must never act on another tenant's binding")
                .isEmpty();
        assertThat(queryScalar("SELECT COUNT(*) FROM hr_idempotency_records WHERE tenant_id = '" + tenantB + "'"))
                .as("no consumer claim may be created in the unaffected tenant")
                .isEqualTo("0");
        assertThat(queryScalar("SELECT COUNT(*) FROM hr_idempotency_records WHERE tenant_id = '" + tenantId + "'"))
                .isEqualTo("1");
    }

    @Test
    void ignoredEventTypesDoNotClaimOrAct() throws Exception {
        HrOutboxEvent unrelated = event("CRM.CUSTOMER.UPDATED.v1", personId, userId);
        consume(unrelated);
        assertThat(recordingPort.calls).isEmpty();
        assertThat(queryScalar("SELECT COUNT(*) FROM hr_idempotency_records"))
                .as("non-IAM events must not create consumer claims")
                .isEqualTo("0");
    }

    // ==================== boundary guard ====================

    @Test
    void hrNeverWritesIamTablesDirectly() throws Exception {
        Path hrMain = Path.of("src/main/java/com/sanad/platform/hr");
        assertThat(hrMain).exists();
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(hrMain)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                String content;
                try {
                    content = Files.readString(file);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                String upper = content.toUpperCase();
                for (String forbidden : new String[]{
                        "INSERT INTO USERS", "UPDATE USERS SET", "DELETE FROM USERS",
                        "INSERT INTO USER_", "UPDATE USER_CREDENTIALS", "UPDATE USER_ROLES"}) {
                    if (upper.contains(forbidden)) {
                        violations.add(file + " contains direct IAM write: " + forbidden);
                    }
                }
            });
        }
        assertThat(violations)
                .as("HR production code must never write IAM/user tables directly — the IAM port is the only path")
                .isEmpty();
    }

    // ==================== reflection plumbing ====================

    private Object newConsumer() throws Exception {
        Class<?> consumerClass = Class.forName(IAM_CONSUMER);
        Constructor<?> ctor = consumerClass.getConstructor(
                DataSource.class, com.fasterxml.jackson.databind.ObjectMapper.class,
                portClass, policyClass);
        return ctor.newInstance(dataSource, new com.fasterxml.jackson.databind.ObjectMapper(),
                recordingPort.proxy, newPolicy());
    }

    private Object newPolicy() throws Exception {
        Constructor<?> ctor = policyClass.getConstructor(DataSource.class);
        return ctor.newInstance(dataSource);
    }

    /** Calls the consumer's onEvent with reflection (consumer implements HrOutboxEventConsumer). */
    private void consume(HrOutboxEvent event) throws Exception {
        Method onEvent = consumer.getClass().getMethod("onEvent", HrOutboxEvent.class);
        onEvent.invoke(consumer, event);
    }

    private String decisionOutcome(String eventType, UUID person, UUID user) throws Exception {
        Method decide = policyClass.getMethod("decide", UUID.class, UUID.class, UUID.class, String.class);
        Object decision = decide.invoke(newPolicy(), tenantId, person, user, eventType);
        return String.valueOf(decision.getClass().getMethod("outcome").invoke(decision));
    }

    private HrOutboxEvent event(String eventType, UUID person, UUID user) {
        return new HrOutboxEvent(UUID.randomUUID(), tenantId, eventType, 1, "HR_EMPLOYMENT", employmentId,
                null, UUID.randomUUID(), java.time.Instant.now(), null, null, null, "OPERATIONAL",
                "{\"employmentId\":\"" + employmentId + "\",\"personId\":\"" + person
                        + "\",\"userId\":\"" + user + "\"}");
    }

    /** Recording IamEmploymentAccessPort stub (dynamic proxy over the reflection-loaded port). */
    private static class RecordingPort {
        final Object proxy;
        final List<Call> calls = new ArrayList<>();

        RecordingPort(Class<?> portClass) {
            InvocationHandler handler = (p, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return method.invoke(this, args);
                }
                List<String> argStrings = new ArrayList<>();
                if (args != null) {
                    for (Object a : args) {
                        argStrings.add(String.valueOf(a));
                    }
                }
                calls.add(new Call(method.getName(), argStrings));
                return method.getReturnType() == boolean.class ? false : null;
            };
            this.proxy = Proxy.newProxyInstance(portClass.getClassLoader(), new Class[]{portClass}, handler);
        }
    }

    private record Call(String method, List<String> args) {
    }

    // ==================== fixtures ====================

    private void insertLegalEntity(UUID tenant) throws Exception {
        executeUpdate(
                "INSERT INTO legal_entities (id, tenant_id, code, name, registered_country_code, statutory_country_code, status) "
                        + "VALUES (?,?,?,?,?,?,?)",
                ps -> {
                    ps.setObject(1, legalEntityId);
                    ps.setObject(2, tenant);
                    ps.setString(3, "LE-" + legalEntityId.toString().substring(0, 8));
                    ps.setString(4, "Test Legal Entity");
                    ps.setString(5, "SA");
                    ps.setString(6, "SA");
                    ps.setString(7, "ACTIVE");
                });
    }

    private void insertPerson(UUID person, UUID tenant) throws Exception {
        executeUpdate(
                "INSERT INTO hr_people (id, tenant_id, first_name, last_name, display_name) VALUES (?,?,?,?,?)",
                ps -> {
                    ps.setObject(1, person);
                    ps.setObject(2, tenant);
                    ps.setString(3, "Test");
                    ps.setString(4, "Person");
                    ps.setString(5, "Test Person");
                });
    }

    private void insertEmployment(UUID id, UUID person, UUID tenant, String status) throws Exception {
        executeUpdate(
                "INSERT INTO hr_employees (id, tenant_id, person_id, legal_entity_id, employee_number, "
                        + "first_name, last_name, display_name, employment_type, status, version, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?, 'Test','Employee','Test Employee','FULL_TIME',?,1,NOW(),NOW())",
                ps -> {
                    ps.setObject(1, id);
                    ps.setObject(2, tenant);
                    ps.setObject(3, person);
                    ps.setObject(4, legalEntityId);
                    ps.setString(5, "EMP-" + id.toString().substring(0, 8));
                    ps.setString(6, status);
                });
    }

    private void insertBinding(UUID person, UUID user, String accessMode) throws Exception {
        insertBinding(tenantId, person, user, accessMode);
    }

    private void insertBinding(UUID tenant, UUID person, UUID user, String accessMode) throws Exception {
        executeUpdate(
                "INSERT INTO hr_iam_access_bindings (tenant_id, person_id, user_id, access_mode, status) "
                        + "VALUES (?,?,?,?,'ACTIVE')",
                ps -> {
                    ps.setObject(1, tenant);
                    ps.setObject(2, person);
                    ps.setObject(3, user);
                    ps.setString(4, accessMode);
                });
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
