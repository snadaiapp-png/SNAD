package com.sanad.platform.hr.time;

import com.sanad.platform.hr.time.application.HrEmploymentScopeResolver;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.access.AccessDeniedException;

import java.sql.Connection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PostgreSQL Direct reproduction of the G2 canonical identity contract. */
class HrEmploymentScopeResolverPostgresTest {
    private static final String DB_URL = System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");
    private static String isolatedUrl;
    private Connection connection;
    private JdbcTemplate jdbc;
    private HrEmploymentScopeResolver resolver;

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available = false;
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
            try (Connection c = ds.getConnection()) { available = c.isValid(5); }
        } catch (Throwable ignored) { }
        Assumptions.assumeTrue(available, "PostgreSQL Direct is not available");
        MigrationTestSchemaSupport.ensureDatabase(DB_URL, DB_USER, DB_PASSWORD);
        isolatedUrl = MigrationTestSchemaSupport.getIsolatedJdbcUrl(DB_URL);
    }

    @BeforeEach
    void migrateFreshPostgreSql() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(isolatedUrl, DB_USER, DB_PASSWORD);
        Flyway flyway = Flyway.configure().dataSource(ds)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .baselineOnMigrate(true).cleanDisabled(false).validateOnMigrate(false).load();
        flyway.clean();
        flyway.migrate();
        connection = ds.getConnection();
        connection.setAutoCommit(true);
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        resolver = new HrEmploymentScopeResolver(jdbc);
    }

    @AfterEach
    void closeConnection() throws Exception { if (connection != null) connection.close(); }

    @Test
    void legacyOnlyEmployeeUserLinkMustNotResolveAsCanonicalSelf() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID employmentId = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                tenantId, "Legacy tenant", "legacy-" + tenantId.toString().substring(0, 8));
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, false)", String.class, tenantId.toString());
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) VALUES (?,?,?,?,'ACTIVE','test-hash',NOW(),NOW())",
                userId, tenantId, "legacy@example.test", "Legacy");
        jdbc.update("INSERT INTO hr_employees (id,tenant_id,user_id,employee_number,first_name,last_name,display_name,email,employment_type,status,hire_date,created_at,updated_at) VALUES (?,?,?,'LEGACY-001','Legacy','Only','Legacy Only','legacy@example.test','FULL_TIME','ACTIVE',CURRENT_DATE,NOW(),NOW())",
                employmentId, tenantId, userId);
        assertThatThrownBy(() -> resolver.requireSelfEmployment(tenantId, userId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("active HR employment");
    }

    @Test
    void canonicalPersonEmploymentMustResolveOnRealPostgreSql() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID personId = UUID.randomUUID();
        UUID employmentId = UUID.randomUUID();
        UUID legalEntityId = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                tenantId, "Canonical tenant", "canonical-" + tenantId.toString().substring(0, 8));
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, false)", String.class, tenantId.toString());
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) VALUES (?,?,?,?,'ACTIVE','test-hash',NOW(),NOW())",
                userId, tenantId, "canonical@example.test", "Canonical");
        jdbc.update("INSERT INTO legal_entities (id,tenant_id,code,name,registered_country_code,statutory_country_code,status,created_at,updated_at) VALUES (?,?,?,?,'SA','SA','ACTIVE',NOW(),NOW())",
                legalEntityId, tenantId, "LE-" + legalEntityId.toString().substring(0,8), "Canonical LE");
        jdbc.update("INSERT INTO hr_people (id,tenant_id,user_id,first_name,last_name,display_name,version,created_at,updated_at) VALUES (?,?,?,'Canonical','Employee','Canonical Employee',0,NOW(),NOW())",
                personId, tenantId, userId);
        jdbc.update("INSERT INTO hr_employees (id,tenant_id,user_id,person_id,legal_entity_id,worker_classification_code,employee_number,first_name,last_name,display_name,email,employment_type,status,hire_date,created_at,updated_at) VALUES (?,?,?,?,?,'FULL_TIME','CAN-001','Canonical','Employee','Canonical Employee','canonical@example.test','FULL_TIME','ACTIVE',CURRENT_DATE,NOW(),NOW())",
                employmentId, tenantId, userId, personId, legalEntityId);
        assertThat(resolver.requireSelfEmployment(tenantId, userId)).isEqualTo(employmentId);
    }
}
