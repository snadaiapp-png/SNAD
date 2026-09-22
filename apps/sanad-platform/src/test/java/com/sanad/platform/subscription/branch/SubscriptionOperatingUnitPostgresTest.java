package com.sanad.platform.subscription.branch;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.*;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class SubscriptionOperatingUnitPostgresTest {
    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static final String ISOLATED_URL = MigrationTestSchemaSupport.getIsolatedJdbcUrl(DB_URL);
    private static final UUID STARTER_PLAN =
            UUID.fromString("c3000000-0000-0000-0000-000000000001");

    private static DriverManagerDataSource dataSource;
    private Connection connection;

    @BeforeAll
    static void migrateFreshPostgreSqlChain() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "SubscriptionOperatingUnitPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available, "PostgreSQL Direct is required for WS8 acceptance.");
        MigrationTestSchemaSupport.ensureDatabase(DB_URL, DB_USER, DB_PASSWORD);
        dataSource = new DriverManagerDataSource(ISOLATED_URL, DB_USER, DB_PASSWORD);
        dataSource.setDriverClassName("org.postgresql.Driver");
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();
    }

    @BeforeEach
    void openTransaction() throws SQLException {
        connection = dataSource.getConnection();
        connection.setAutoCommit(false);
    }

    @AfterEach
    void rollback() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            try { connection.rollback(); } finally { connection.close(); }
        }
    }

    @Test
    void ws8TablesAreForceRlsProtected() throws SQLException {
        String[] tables = {
                "subscription_operating_units",
                "subscription_unit_applications",
                "subscription_billing_profiles",
                "subscription_resource_bindings",
                "usage_operating_unit_aggregates"
        };
        for (String table : tables) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT c.relrowsecurity, c.relforcerowsecurity "
                            + "FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace "
                            + "WHERE n.nspname='public' AND c.relname=?")) {
                ps.setString(1, table);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as(table + " exists").isTrue();
                    assertThat(rs.getBoolean(1)).as(table + " RLS enabled").isTrue();
                    assertThat(rs.getBoolean(2)).as(table + " FORCE RLS").isTrue();
                }
            }
        }
    }

    @Test
    void operatingUnitRowsAreTenantIsolatedAndCrossTenantWritesFailClosed() throws SQLException {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID subA = UUID.randomUUID();
        UUID subB = UUID.randomUUID();
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        seedTenant(tenantA, "a");
        seedTenant(tenantB, "b");
        seedSubscription(subA, tenantA);
        seedSubscription(subB, tenantB);
        seedOrganization(orgA, tenantA, "BRANCH");
        seedOrganization(orgB, tenantB, "BRANCH");

        setTenant(tenantA);
        insertOperatingUnit(tenantA, subA, orgA, "CONSOLIDATED");
        assertThat(count("SELECT COUNT(*) FROM subscription_operating_units")).isEqualTo(1);

        setTenant(tenantB);
        assertThat(count("SELECT COUNT(*) FROM subscription_operating_units")).isZero();

        setTenant(tenantA);
        assertSqlFailure(() -> insertOperatingUnit(
                tenantB, subB, orgB, "CONSOLIDATED"));
        assertSqlFailure(() -> insertOperatingUnit(
                tenantA, subA, orgB, "CONSOLIDATED"));
    }

    @Test
    void billingProfileScopeAndCurrencyShapeAreStorageEnforced() throws SQLException {
        UUID tenant = UUID.randomUUID();
        UUID subscription = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        seedTenant(tenant, "billing");
        seedSubscription(subscription, tenant);
        seedOrganization(branch, tenant, "BRANCH");
        setTenant(tenant);
        insertOperatingUnit(tenant, subscription, branch, "CONSOLIDATED");

        insertBillingProfile(tenant, subscription, null, "CONSOLIDATED", "SAR");
        insertBillingProfile(tenant, subscription, branch, "SEPARATE", "SAR");

        assertSqlFailure(() -> insertBillingProfile(
                tenant, subscription, null, "SEPARATE", "SAR"));
        assertSqlFailure(() -> insertBillingProfile(
                tenant, subscription, branch, "CONSOLIDATED", "SAR"));
        assertSqlFailure(() -> insertBillingProfile(
                tenant, subscription, null, "CONSOLIDATED", "S4R"));
    }

    private void seedTenant(UUID tenant, String suffix) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            ps.setObject(1, tenant);
            ps.setString(2, "ws8-" + suffix);
            ps.setString(3, "ws8-" + suffix + "-" + tenant.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    private void seedSubscription(UUID subscription, UUID tenant) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenant_subscriptions "
                        + "(id,tenant_id,plan_id,status,billing_cycle,seat_quantity,credit_balance_minor,"
                        + "started_at,current_period_start,current_period_end,cancel_at_period_end,created_at,updated_at) "
                        + "VALUES (?,?,?,'ACTIVE','MONTHLY',1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,"
                        + "CURRENT_TIMESTAMP + INTERVAL '1 month',FALSE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
            ps.setObject(1, subscription);
            ps.setObject(2, tenant);
            ps.setObject(3, STARTER_PLAN);
            ps.executeUpdate();
        }
    }

    private void seedOrganization(UUID organization, UUID tenant, String unitType) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO organizations (id,tenant_id,name,status,unit_type,created_at,updated_at) "
                        + "VALUES (?,?,?,'ACTIVE',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
            ps.setObject(1, organization);
            ps.setObject(2, tenant);
            ps.setString(3, "Branch " + organization.toString().substring(0, 8));
            ps.setString(4, unitType);
            ps.executeUpdate();
        }
    }

    private void insertOperatingUnit(
            UUID tenant, UUID subscription, UUID organization, String mode) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO subscription_operating_units "
                        + "(id,tenant_id,subscription_id,organization_id,status,billing_mode,created_at,updated_at) "
                        + "VALUES (?,?,?,?,'ACTIVE',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenant);
            ps.setObject(3, subscription);
            ps.setObject(4, organization);
            ps.setString(5, mode);
            ps.executeUpdate();
        }
    }

    private void insertBillingProfile(
            UUID tenant, UUID subscription, UUID organization, String mode, String currency) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO subscription_billing_profiles "
                        + "(id,tenant_id,subscription_id,organization_id,profile_name,currency_code,billing_mode,status,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?, ?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenant);
            ps.setObject(3, subscription);
            ps.setObject(4, organization);
            ps.setString(5, "Profile " + UUID.randomUUID().toString().substring(0, 6));
            ps.setString(6, currency);
            ps.setString(7, mode);
            ps.executeUpdate();
        }
    }

    @FunctionalInterface
    private interface SqlAction { void run() throws SQLException; }

    private void assertSqlFailure(SqlAction action) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        Throwable failure = catchThrowable(action::run);
        assertThat(failure).isInstanceOf(SQLException.class);
        connection.rollback(savepoint);
    }

    private void setTenant(UUID tenant) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenant.toString());
            try (ResultSet ignored = ps.executeQuery()) { }
        }
    }

    private int count(String sql) throws SQLException {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
