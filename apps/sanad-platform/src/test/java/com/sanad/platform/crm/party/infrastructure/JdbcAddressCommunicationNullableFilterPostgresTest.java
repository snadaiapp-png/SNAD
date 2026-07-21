package com.sanad.platform.crm.party.infrastructure;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class JdbcAddressCommunicationNullableFilterPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void requireDockerAndMigrate() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(dockerAvailable,
                "Docker is required to verify nullable CRM SQL parameters against PostgreSQL.");

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load()
                .migrate();
    }

    @Test
    void bindsNullableAddressCursorWithExplicitPostgresTypes() {
        NamedParameterJdbcTemplate jdbc = jdbc();
        MapSqlParameterSource params = baseParams()
                .addValue("beforeTime", null, Types.TIMESTAMP)
                .addValue("beforeId", null, Types.OTHER)
                .addValue("limit", 10);

        assertThat(jdbc.queryForList("""
                SELECT id FROM crm_party_addresses
                WHERE tenant_id=:tenantId AND owner_type=:ownerType AND owner_id=:ownerId
                  AND (CAST(:beforeTime AS TIMESTAMP) IS NULL OR updated_at<:beforeTime
                       OR (updated_at=:beforeTime AND (CAST(:beforeId AS UUID) IS NULL OR id<:beforeId)))
                ORDER BY updated_at DESC,id DESC LIMIT :limit
                """, params)).isEmpty();
    }

    @Test
    void bindsNullableCommunicationFiltersAndCursorWithExplicitPostgresTypes() {
        NamedParameterJdbcTemplate jdbc = jdbc();
        MapSqlParameterSource params = baseParams()
                .addValue("methodType", null, Types.VARCHAR)
                .addValue("verificationStatus", null, Types.VARCHAR)
                .addValue("beforeTime", null, Types.TIMESTAMP)
                .addValue("beforeId", null, Types.OTHER)
                .addValue("limit", 10);

        assertThat(jdbc.queryForList("""
                SELECT id FROM crm_communication_methods
                WHERE tenant_id=:tenantId AND owner_type=:ownerType AND owner_id=:ownerId
                  AND (CAST(:methodType AS VARCHAR) IS NULL OR method_type=:methodType)
                  AND (CAST(:verificationStatus AS VARCHAR) IS NULL OR verification_status=:verificationStatus)
                  AND (CAST(:beforeTime AS TIMESTAMP) IS NULL OR updated_at<:beforeTime
                       OR (updated_at=:beforeTime AND (CAST(:beforeId AS UUID) IS NULL OR id<:beforeId)))
                ORDER BY updated_at DESC,id DESC LIMIT :limit
                """, params)).isEmpty();
    }

    @Test
    void bindsNullableMutationExclusionsWithExplicitPostgresUuidType() {
        NamedParameterJdbcTemplate jdbc = jdbc();
        MapSqlParameterSource params = baseParams()
                .addValue("actorId", UUID.randomUUID())
                .addValue("now", java.sql.Timestamp.from(java.time.Instant.now()), Types.TIMESTAMP)
                .addValue("addressType", "WORK")
                .addValue("methodType", "EMAIL")
                .addValue("normalizedValue", "nullable-exclusion@example.invalid")
                .addValue("exceptId", null, Types.OTHER);

        assertThat(jdbc.update("""
                UPDATE crm_party_addresses
                   SET primary_address=FALSE,primary_slot=NULL,updated_by=:actorId,updated_at=:now,version=version+1
                 WHERE tenant_id=:tenantId AND owner_type=:ownerType AND owner_id=:ownerId
                   AND address_type=:addressType AND primary_address=TRUE
                   AND (CAST(:exceptId AS UUID) IS NULL OR id<>:exceptId)
                """, params)).isZero();

        assertThat(jdbc.update("""
                UPDATE crm_communication_methods
                   SET preferred=FALSE,preferred_slot=NULL,updated_by=:actorId,updated_at=:now,version=version+1
                 WHERE tenant_id=:tenantId AND owner_type=:ownerType AND owner_id=:ownerId
                   AND method_type=:methodType AND preferred=TRUE
                   AND (CAST(:exceptId AS UUID) IS NULL OR id<>:exceptId)
                """, params)).isZero();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM crm_communication_methods
                 WHERE tenant_id=:tenantId AND owner_type=:ownerType AND owner_id=:ownerId
                   AND method_type=:methodType AND normalized_value=:normalizedValue AND status<>'ARCHIVED'
                   AND (CAST(:exceptId AS UUID) IS NULL OR id<>:exceptId)
                """, params, Long.class)).isZero();
    }

    @Test
    void productionRepositoryDeclaresAllNullableSqlTypes() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/sanad/platform/crm/party/infrastructure/JdbcAddressCommunicationRepository.java"));

        assertThat(source)
                .contains("addValue(\"beforeTime\", timestamp(beforeUpdatedAt), Types.TIMESTAMP)")
                .contains("addValue(\"beforeId\", beforeId, Types.OTHER)")
                .contains("addValue(\"methodType\", methodType, Types.VARCHAR)")
                .contains("addValue(\"verificationStatus\", verificationStatus, Types.VARCHAR)")
                .contains("CAST(:beforeTime AS TIMESTAMP) IS NULL")
                .contains("CAST(:beforeId AS UUID) IS NULL")
                .contains("CAST(:methodType AS VARCHAR) IS NULL")
                .contains("CAST(:verificationStatus AS VARCHAR) IS NULL")
                .contains("CAST(:exceptId AS UUID) IS NULL")
                .contains("addValue(\"exceptId\", exceptId, Types.OTHER)");
    }

    private static MapSqlParameterSource baseParams() {
        return new MapSqlParameterSource()
                .addValue("tenantId", UUID.randomUUID())
                .addValue("ownerType", "ACCOUNT")
                .addValue("ownerId", UUID.randomUUID());
    }

    private static NamedParameterJdbcTemplate jdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
