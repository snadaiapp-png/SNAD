package com.sanad.platform.subscription.billing;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class R0C13G02SchemaPostgresTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static final String ISOLATED_URL = MigrationTestSchemaSupport.getIsolatedJdbcUrl(DB_URL);

    private static final List<String> G02_TABLES = List.of(
            "subscription_billing_provider_customers",
            "subscription_billing_payment_attempts",
            "subscription_billing_provider_events",
            "subscription_billing_finance_links",
            "subscription_billing_outbox",
            "subscription_billing_reconciliation_runs",
            "subscription_billing_reconciliation_items");

    private static DriverManagerDataSource dataSource;
    private Connection connection;

    @BeforeAll
    static void migrateFreshPostgreSqlChain() {
        boolean postgresAvailable;
        try {
            postgresAvailable = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "R0C13G02SchemaPostgresTest");
        } catch (Throwable ignored) {
            postgresAvailable = false;
        }
        Assumptions.assumeTrue(postgresAvailable,
                "PostgreSQL Direct is required for R13-G02 acceptance.");
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
            try {
                connection.rollback();
            } finally {
                connection.close();
            }
        }
    }

    @Test
    void freshFlywayChainIncludesG02FoundationAndEndsAtCurrentR0c13Head() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT version FROM flyway_schema_history "
                        + "WHERE success = TRUE AND version IS NOT NULL "
                        + "ORDER BY installed_rank DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("20260911.2");
        }
    }

    @Test
    void allG02TablesAreTenantScopedAndForceRls() throws SQLException {
        for (String table : G02_TABLES) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT c.relrowsecurity, c.relforcerowsecurity, col.is_nullable "
                            + "FROM pg_class c "
                            + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                            + "JOIN information_schema.columns col "
                            + "ON col.table_schema = n.nspname AND col.table_name = c.relname "
                            + "WHERE n.nspname = 'public' AND c.relname = ? "
                            + "AND col.column_name = 'tenant_id'")) {
                ps.setString(1, table);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("%s must exist with tenant_id", table).isTrue();
                    assertThat(rs.getBoolean("relrowsecurity")).as("%s must ENABLE RLS", table).isTrue();
                    assertThat(rs.getBoolean("relforcerowsecurity")).as("%s must FORCE RLS", table).isTrue();
                    assertThat(rs.getString("is_nullable")).as("%s.tenant_id must be NOT NULL", table).isEqualTo("NO");
                }
            }
        }
    }

    @Test
    void providerBoundaryMoneyUsesBigintMinorUnits() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_schema='public' "
                        + "AND table_name='subscription_billing_payment_attempts' "
                        + "AND column_name='amount_minor'");
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("data_type")).isEqualTo("bigint");
        }
    }

    @Test
    void ownTenantCanInsertProviderBinding() throws SQLException {
        UUID tenant = seedTenant();
        setTenant(tenant);
        insertProviderCustomer(tenant, "STRIPE", "cus_" + compact(tenant));
        assertThat(countProviderCustomers()).isEqualTo(1);
    }

    @Test
    void noTenantContextFailsClosedForReadsAndWrites() throws SQLException {
        UUID tenant = seedTenant();
        setTenant(tenant);
        insertProviderCustomer(tenant, "STRIPE", "cus_noctx_visible_" + compact(tenant));
        assertThat(countProviderCustomers()).isEqualTo(1);

        clearTenant();
        assertThat(countProviderCustomers())
                .as("R0C13 tenant-scoped rows must be invisible without app.tenant_id")
                .isZero();

        Throwable thrown = catchThrowable(() ->
                insertProviderCustomer(tenant, "STRIPE", "cus_noctx_write_" + compact(tenant)));
        assertThat(thrown).isInstanceOf(SQLException.class);
        assertThat(((SQLException) thrown).getSQLState()).isEqualTo("42501");
    }

    @Test
    void wrongTenantCannotReadOrWriteAnotherTenantsRow() throws SQLException {
        UUID tenantA = seedTenant();
        UUID tenantB = seedTenant();

        setTenant(tenantA);
        insertProviderCustomer(tenantA, "STRIPE", "cus_cross_" + compact(tenantA));
        assertThat(countProviderCustomers()).isEqualTo(1);

        setTenant(tenantB);
        assertThat(countProviderCustomers())
                .as("tenant B must not read tenant A's provider binding")
                .isZero();

        Throwable thrown = catchThrowable(() ->
                insertProviderCustomer(tenantA, "STRIPE", "cus_cross_write_" + compact(tenantA)));
        assertThat(thrown).isInstanceOf(SQLException.class);
        assertThat(((SQLException) thrown).getSQLState()).isEqualTo("42501");
    }

    @Test
    void g02SchemaContainsNoCardholderOrSecretColumns() throws SQLException {
        String tables = G02_TABLES.stream()
                .map(name -> "'" + name + "'")
                .collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = 'public' "
                + "AND table_name IN (" + tables + ") "
                + "AND lower(column_name) IN "
                + "('card_number','pan','cvc','cvv','track_data','pin',"
                + "'secret','api_key','token','authorization')";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong(1))
                    .as("R0C13 billing tables must not introduce cardholder/secret columns")
                    .isZero();
        }
    }

    @Test
    void providerEventAndOutboxIdempotencyAreDatabaseEnforced() throws SQLException {
        UUID tenant = seedTenant();
        setTenant(tenant);
        String eventId = "evt_" + compact(tenant);
        insertProviderEvent(tenant, eventId);
        Throwable duplicateEvent = catchThrowable(() -> insertProviderEvent(tenant, eventId));
        assertThat(duplicateEvent).isInstanceOf(SQLException.class);
        assertThat(((SQLException) duplicateEvent).getSQLState()).isEqualTo("23505");

        connection.rollback();
        seedTenant(tenant);
        setTenant(tenant);
        String key = "billing-test-" + compact(tenant);
        insertOutbox(tenant, key);
        Throwable duplicateKey = catchThrowable(() -> insertOutbox(tenant, key));
        assertThat(duplicateKey).isInstanceOf(SQLException.class);
        assertThat(((SQLException) duplicateKey).getSQLState()).isEqualTo("23505");
    }

    private UUID seedTenant() throws SQLException {
        UUID tenant = UUID.randomUUID();
        seedTenant(tenant);
        return tenant;
    }

    private void seedTenant(UUID tenant) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            ps.setObject(1, tenant);
            ps.setString(2, "r13-g02-" + compact(tenant));
            ps.setString(3, "g02-" + compact(tenant));
            ps.executeUpdate();
        }
    }

    private void setTenant(UUID tenant) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenant.toString());
            try (ResultSet ignored = ps.executeQuery()) { }
        }
    }

    private void clearTenant() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', '', true)");
             ResultSet ignored = ps.executeQuery()) { }
    }

    private void insertProviderCustomer(UUID tenant, String provider, String providerRef) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO subscription_billing_provider_customers "
                        + "(id,tenant_id,provider,provider_customer_ref) VALUES (?, ?, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenant);
            ps.setString(3, provider);
            ps.setString(4, providerRef);
            ps.executeUpdate();
        }
    }

    private int countProviderCustomers() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM subscription_billing_provider_customers");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void insertProviderEvent(UUID tenant, String eventId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO subscription_billing_provider_events "
                        + "(id,tenant_id,provider,provider_event_id,event_type,payload_sha256) "
                        + "VALUES (?, ?, 'STRIPE', ?, 'payment_intent.succeeded', "
                        + "'0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef')")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenant);
            ps.setString(3, eventId);
            ps.executeUpdate();
        }
    }

    private void insertOutbox(UUID tenant, String key) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO subscription_billing_outbox "
                        + "(event_id,tenant_id,event_type,aggregate_type,idempotency_key) "
                        + "VALUES (?, ?, 'BILLING.PAYMENT_PENDING.v1', 'BILLING_INVOICE', ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenant);
            ps.setString(3, key);
            ps.executeUpdate();
        }
    }

    private static String compact(UUID id) {
        return id.toString().replace("-", "").substring(0, 12);
    }
}
