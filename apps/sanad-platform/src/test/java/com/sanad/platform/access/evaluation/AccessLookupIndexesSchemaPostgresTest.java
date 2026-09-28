package com.sanad.platform.access.evaluation;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessLookupIndexesSchemaPostgresTest {

    private static final String SOURCE_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "AccessLookupIndexesSchemaPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is required for AccessLookupIndexesSchemaPostgresTest");
        MigrationTestSchemaSupport.ensureDatabase(SOURCE_URL, USER, PASSWORD);
    }

    @Test
    void createsAllDecisionHotPathIndexes() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(isolatedUrl(), USER, PASSWORD)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();

        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD)) {
            assertIndex(connection, "user_permission_overrides", "idx_upo_lookup",
                    List.of("tenant_id", "user_id", "capability_id"));
            assertIndex(connection, "subject_relationships", "idx_rel_lookup",
                    List.of("tenant_id", "subject_user_id", "relationship_type"));
            assertIndex(connection, "effective_permission_projection", "idx_epp_user",
                    List.of("tenant_id", "user_id"));
        }
    }

    private static String isolatedUrl() {
        return MigrationTestSchemaSupport.getIsolatedJdbcUrl(SOURCE_URL);
    }

    private static void assertIndex(Connection connection, String table, String index, List<String> columns)
            throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT indexdef
                  FROM pg_indexes
                 WHERE schemaname = 'public'
                   AND tablename = ?
                   AND indexname = ?
                """)) {
            ps.setString(1, table);
            ps.setString(2, index);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as(index).isTrue();
                String definition = rs.getString(1).toLowerCase();
                for (String column : columns) {
                    assertThat(definition).contains(column);
                }
                assertThat(rs.next()).isFalse();
            }
        }
    }
}
