package com.sanad.platform.ai;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Database / RLS forensics for the AI Module. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class AiDatabaseForensicsTest {

    @Autowired private JdbcTemplate jdbc;

    @Test
    void allAiTablesExist() {
        var tables = jdbc.queryForList(
                "SELECT LOWER(table_name) FROM information_schema.tables "
                        + "WHERE LOWER(table_name) LIKE 'ai_%'",
                String.class);
        assertThat(tables).contains("ai_agents", "ai_inference_log");
    }

    @Test
    void tenantIdNotNullOnAllAiTables() {
        for (var table : List.of("ai_agents", "ai_inference_log")) {
            var rows = jdbc.queryForList(
                    "SELECT is_nullable FROM information_schema.columns "
                            + "WHERE LOWER(table_name) = ? AND LOWER(column_name) = 'tenant_id'",
                    String.class, table);
            assertThat(rows).as("table " + table + " must have tenant_id").isNotEmpty();
            for (var nullable : rows) {
                assertThat(nullable).as("tenant_id on " + table + " must be NOT NULL").isEqualTo("NO");
            }
        }
    }

    @Test
    void advisoryDefaultsToTrue() {
        var rows = jdbc.queryForList(
                "SELECT column_default FROM information_schema.columns "
                        + "WHERE LOWER(table_name) = 'ai_inference_log' AND LOWER(column_name) = 'advisory'");
        assertThat(rows).isNotEmpty();
        var def = rows.get(0).values().iterator().next().toString();
        assertThat(def).contains("true");
    }

    @Test
    void flywayHistoryContainsAiMigrations() {
        var versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE version LIKE '20260815.1%' ORDER BY version",
                String.class);
        assertThat(versions).contains("20260815.14", "20260815.15");
    }

    @Test
    void aiCapabilitiesSeeded() {
        var caps = jdbc.queryForList(
                "SELECT code FROM access_capabilities WHERE code LIKE 'AI.%'",
                String.class);
        assertThat(caps).contains("AI.VIEW", "AI.WRITE", "AI.ADMIN", "AI.EXECUTE");
    }
}
