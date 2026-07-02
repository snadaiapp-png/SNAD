package com.sanad.platform.crm.web;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class CrmPostgresMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("sanad_crm_migration")
            .withUsername("sanad")
            .withPassword("sanad_test_only");

    @Test
    void upgradesExistingPlatformSchemaToUnifiedCrmCore() {
        Flyway beforeCrm = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("20260629.2"))
                .cleanDisabled(false)
                .load();

        beforeCrm.clean();
        beforeCrm.migrate();

        JdbcTemplate jdbc = jdbc();
        assertThat(latestVersion(jdbc)).isEqualTo("20260629.2");
        assertThat(tableExists(jdbc, "crm_accounts")).isFalse();

        Flyway crmUpgrade = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        crmUpgrade.validate();
        crmUpgrade.migrate();

        assertThat(latestVersion(jdbc)).isEqualTo("20260702.1");
        assertThat(existingTables(jdbc)).containsExactlyInAnyOrder(
                "crm_accounts",
                "crm_contacts",
                "crm_pipelines",
                "crm_pipeline_stages",
                "crm_leads",
                "crm_opportunities",
                "crm_opportunity_stage_history",
                "crm_activities",
                "crm_timeline_events",
                "crm_import_jobs",
                "crm_custom_field_definitions"
        );
        assertThat(constraintExists(jdbc, "fk_crm_contacts_account_same_tenant")).isTrue();
        assertThat(constraintExists(jdbc, "fk_crm_opportunities_pipeline_same_tenant")).isTrue();
        assertThat(constraintExists(jdbc, "fk_crm_leads_converted_opportunity_same_tenant")).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM access_capabilities WHERE code LIKE 'CRM.%' AND status='ACTIVE'",
                Long.class)).isEqualTo(14L);
    }

    @Test
    void installsUnifiedCrmCoreOnCleanPostgresDatabase() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        flyway.clean();
        flyway.migrate();

        JdbcTemplate jdbc = jdbc();
        assertThat(latestVersion(jdbc)).isEqualTo("20260702.1");
        assertThat(tableExists(jdbc, "crm_accounts")).isTrue();
        assertThat(tableExists(jdbc, "crm_opportunities")).isTrue();
        assertThat(tableExists(jdbc, "crm_timeline_events")).isTrue();
    }

    private JdbcTemplate jdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        return new JdbcTemplate(dataSource);
    }

    private String latestVersion(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success=TRUE ORDER BY installed_rank DESC LIMIT 1",
                String.class);
    }

    private boolean tableExists(JdbcTemplate jdbc, String table) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name=?",
                Long.class,
                table);
        return count != null && count == 1L;
    }

    private List<String> existingTables(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'crm_%' ORDER BY table_name",
                String.class);
    }

    private boolean constraintExists(JdbcTemplate jdbc, String constraint) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema='public' AND constraint_name=?",
                Long.class,
                constraint);
        return count != null && count == 1L;
    }
}
