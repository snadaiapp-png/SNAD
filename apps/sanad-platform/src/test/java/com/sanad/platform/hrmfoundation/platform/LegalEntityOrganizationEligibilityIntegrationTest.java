package com.sanad.platform.hrmfoundation.platform;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.Connection;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegalEntityOrganizationEligibilityIntegrationTest {
    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;
    private static String ISOLATED_URL;

    private static final String DB_URL = System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

    @BeforeAll
    static void requirePostgreSql() {
        boolean postgresAvailable = false;
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
            try (Connection conn = ds.getConnection()) { postgresAvailable = conn.isValid(5); }
        } catch (Throwable ignored) { postgresAvailable = false; }
        Assumptions.assumeTrue(postgresAvailable, "PostgreSQL Direct is not available");
        MigrationTestSchemaSupport.ensureDatabase(DB_URL, DB_USER, DB_PASSWORD);
        ISOLATED_URL = MigrationTestSchemaSupport.getIsolatedJdbcUrl(DB_URL);
    }

    @BeforeEach
    void migrateAndSeed() {
        dataSource = new DriverManagerDataSource(ISOLATED_URL, DB_USER, DB_PASSWORD);
        jdbc = new JdbcTemplate(dataSource);
        Flyway flyway = Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration,classpath:db/vendor/{vendor}")
                .baselineOnMigrate(false)
                .cleanDisabled(false)
                .validateOnMigrate(false)
                .load();
        flyway.clean();
        flyway.migrate();
    }

    @Test
    void overlappingActiveIntervalIsRejectedByDatabase() {
        UUID tenantId = UUID.randomUUID();
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId);
        insertEligibility(tenantId, orgId, leId, "2026-01-01", null, "ACTIVE");
        assertThatThrownBy(() -> insertEligibility(tenantId, orgId, leId, "2026-06-01", null, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("organization_legal_entities");
    }

    @Test
    void overlappingClosedIntervalIsRejectedByDatabase() {
        UUID tenantId = UUID.randomUUID();
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId);
        insertEligibility(tenantId, orgId, leId, "2026-01-01", "2026-06-30", "ACTIVE");
        assertThatThrownBy(() -> insertEligibility(tenantId, orgId, leId, "2026-06-01", null, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nonOverlappingIntervalIsAccepted() {
        UUID tenantId = UUID.randomUUID();
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId);
        insertEligibility(tenantId, orgId, leId, "2026-01-01", "2026-05-31", "ACTIVE");
        insertEligibility(tenantId, orgId, leId, "2026-06-01", null, "ACTIVE");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM organization_legal_entities WHERE organization_id = ? AND legal_entity_id = ?", Integer.class, orgId, leId);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void inactiveIntervalDoesNotBlockNewActiveInterval() {
        UUID tenantId = UUID.randomUUID();
        UUID orgId = seedOrganization(tenantId);
        UUID leId = seedLegalEntity(tenantId);
        insertEligibility(tenantId, orgId, leId, "2026-01-01", null, "INACTIVE");
        insertEligibility(tenantId, orgId, leId, "2026-06-01", null, "ACTIVE");
        Integer activeCount = jdbc.queryForObject("SELECT COUNT(*) FROM organization_legal_entities WHERE organization_id = ? AND status = 'ACTIVE'", Integer.class, orgId);
        assertThat(activeCount).isEqualTo(1);
    }

    private UUID seedOrganization(UUID tenantId) {
        UUID orgId = UUID.randomUUID();
        jdbc.update("INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) VALUES (?, ?, 'Test Org', 'ACTIVE', NOW(), NOW())", orgId, tenantId);
        return orgId;
    }

    private UUID seedLegalEntity(UUID tenantId) {
        UUID leId = UUID.randomUUID();
        jdbc.update("INSERT INTO legal_entities (id, tenant_id, code, name, registered_country_code, statutory_country_code, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'SA', 'SA', 'ACTIVE', NOW(), NOW())", leId, tenantId, "LE-" + leId.toString().substring(0, 8), "Test LE");
        return leId;
    }

    private void insertEligibility(UUID tenantId, UUID orgId, UUID leId, String from, String to, String status) {
        jdbc.update("INSERT INTO organization_legal_entities (id, tenant_id, organization_id, legal_entity_id, effective_from, effective_to, status, created_at) VALUES (gen_random_uuid(), ?, ?, ?, DATE ?, ?::date, ?, NOW())", tenantId, orgId, leId, from, to != null ? to : null, status);
    }
}
