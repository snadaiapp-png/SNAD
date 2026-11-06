package com.sanad.platform.crm.integration;

import com.sanad.platform.test.TenantGucDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Regression contract for the G1 fail-closed RLS hardening on the 19 legacy
 * CRM tables (V20261104_2__crm_rls_fail_closed_force.sql, commit c63bcfde).
 *
 * <p>Why this exists: when that migration closed the historical fail-open
 * bypass ({@code current_setting('app.tenant_id', true) IS NULL OR ...} in
 * V20261020_2) and enabled FORCE ROW LEVEL SECURITY, fourteen CRM PostgreSQL
 * fixtures kept seeding rows over connections with no bound tenant identity
 * and failed with
 * {@code ERROR: new row violates row-level security policy} (SQLSTATE 42501,
 * surfaced by Spring's SQLStateSQLExceptionTranslator as a misleading
 * {@link BadSqlGrammarException}). The correct fix is to bind the legitimate
 * tenant identity through {@link TenantGucDataSource} — never to weaken the
 * policy. This class pins BOTH sides of that contract so the failure mode
 * cannot silently return in either direction:</p>
 *
 * <ul>
 *   <li>Fail-closed side: an INSERT over a connection with NO tenant binding
 *       must be REJECTED by the RLS WITH CHECK predicate — not admitted via
 *       any owner or empty-context bypass.</li>
 *   <li>Tenant-scoped side: the same INSERT over a connection bound to the
 *       owning tenant must SUCCEED, and the row must be invisible to a
 *       different tenant's binding.</li>
 * </ul>
 *
 * <p>If this test fails after a future change, the change weakened the G1
 * tenant-isolation contract or broke the tenant-binding fixture — do NOT
 * "fix" it by loosening RLS.</p>
 */
class CrmIntegrationRlsFailClosedContractPostgresTest {

    private static JdbcTemplate jdbc;
    private static TenantGucDataSource gucDs;
    private static JdbcTemplate unboundJdbc;

    private UUID tenantId;
    private UUID requestId;

    private static final String REQUEST_SEED_SQL = "INSERT INTO crm_integration_requests " +
            "(id, tenant_id, actor_id, integration_type, contract_name, contract_version, " +
            "correlation_id, causation_id, idempotency_key, source_entity_type, source_entity_id, " +
            "source_entity_version, required_capability, data_classification, requested_locale, " +
            "payload, status, requested_at, expires_at, created_at, updated_at, version) " +
            "VALUES (?, ?, ?, 'AI', 'crm.ai', '1.0', ?, ?, ?, 'ACCOUNT', ?, 0, " +
            "'CRM.AI.READ', 'INTERNAL', 'en-US', CAST('{}' AS jsonb), 'PENDING', ?, ?, ?, ?, 0)";

    @BeforeAll
    static void setup() {
        boolean postgresAvailable = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                "CrmIntegrationRlsFailClosedContractPostgresTest");
        Assumptions.assumeTrue(postgresAvailable,
                "PostgreSQL Direct unavailable — skipping in non-CI environment");

        Flyway.configure()
                .dataSource(System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false).validateOnMigrate(true).load().migrate();

        // Two fixtures over the same migrated schema: one that always binds the
        // current test tenant (positive path), and one raw/unbound fixture that
        // presents NO tenant identity at all (fail-closed negative path).
        DriverManagerDataSource rawDs = new DriverManagerDataSource(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        gucDs = new TenantGucDataSource(rawDs, null);
        jdbc = new JdbcTemplate(gucDs);
        unboundJdbc = new JdbcTemplate(rawDs);
    }

    @BeforeEach
    void seedTenant() {
        tenantId = UUID.randomUUID();
        // Bind the fixture to the freshly generated tenant — fail-closed
        // predicate unchanged, not a bypass.
        gucDs.setDefaultTenant(tenantId);
        requestId = UUID.randomUUID();
    }

    /**
     * Fail-closed side of the contract: with no app.tenant_id binding the
     * INSERT must be rejected by the RLS WITH CHECK predicate. The root
     * cause of the CI failure (run 42043678690) was exactly this rejection
     * hitting fixtures that never bound a tenant; the rejection itself is
     * the CORRECT security behavior and must stay.
     */
    @Test
    void insertWithoutTenantContextIsRejectedFailClosed() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        try {
            unboundJdbc.update(REQUEST_SEED_SQL,
                    requestId, tenantId, UUID.randomUUID(),
                    "corr-" + requestId, "caus-" + requestId,
                    "idem-" + requestId, UUID.randomUUID(),
                    java.sql.Timestamp.from(now),
                    java.sql.Timestamp.from(now.plus(30, ChronoUnit.SECONDS)),
                    java.sql.Timestamp.from(now),
                    java.sql.Timestamp.from(now));
            fail("INSERT without tenant context must be rejected by the fail-closed RLS policy");
        } catch (BadSqlGrammarException expected) {
            // Spring maps SQLSTATE class 42 (which includes 42501 access-rule
            // violations) to BadSqlGrammarException; prove the PostgreSQL root
            // cause is the RLS policy rejection, not actual SQL grammar.
            Throwable cause = expected;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertThat(cause.getMessage())
                    .contains("row-level security policy")
                    .contains("crm_integration_requests");
        }
    }

    /**
     * Tenant-scoped side of the contract: the same INSERT over a connection
     * bound to the owning tenant succeeds, and the seeded row is invisible
     * to a different tenant's binding (RLS USING predicate).
     */
    @Test
    void tenantBoundInsertSucceedsAndRowIsInvisibleToOtherTenants() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        jdbc.update(REQUEST_SEED_SQL,
                requestId, tenantId, UUID.randomUUID(),
                "corr-" + requestId, "caus-" + requestId,
                "idem-" + requestId, UUID.randomUUID(),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now.plus(30, ChronoUnit.SECONDS)),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now));

        Integer ownCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_integration_requests WHERE id = ?",
                Integer.class, requestId);
        assertThat(ownCount).isEqualTo(1);

        UUID otherTenant = UUID.randomUUID();
        Integer otherCount = TenantGucDataSource.readAsTenant(otherTenant, () ->
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM crm_integration_requests WHERE id = ?",
                        Integer.class, requestId));
        assertThat(otherCount).isZero();
    }
}
