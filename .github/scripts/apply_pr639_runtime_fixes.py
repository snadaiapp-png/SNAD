from pathlib import Path


next_config = Path("apps/web/next.config.ts")
next_config.write_text(
    '''import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async redirects() {
    return [
      {
        source: "/crm",
        destination: "/crm/overview",
        permanent: false,
      },
    ];
  },
};

export default nextConfig;
''',
    encoding="utf-8",
)

Path("apps/web/next-config-redirect.test.ts").write_text(
    '''import { describe, expect, it } from "vitest";

import nextConfig from "./next.config";

describe("Next.js HTTP redirects", () => {
  it("redirects /crm to /crm/overview before the authenticated SPA boots", async () => {
    const redirects = await nextConfig.redirects?.();

    expect(redirects).toEqual(
      expect.arrayContaining([
        {
          source: "/crm",
          destination: "/crm/overview",
          permanent: false,
        },
      ]),
    );
  });
});
''',
    encoding="utf-8",
)

repository = Path(
    "apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/infrastructure/"
    "JdbcAddressCommunicationRepository.java"
)
source = repository.read_text(encoding="utf-8")
original = source
source = source.replace(
    "import java.sql.Timestamp;\n",
    "import java.sql.Timestamp;\nimport java.sql.Types;\n",
    1,
)
assert source != original, "Types import was not inserted"

replacements = {
    '.addValue("beforeTime", timestamp(beforeUpdatedAt))':
        '.addValue("beforeTime", timestamp(beforeUpdatedAt), Types.TIMESTAMP)',
    '.addValue("beforeId", beforeId)':
        '.addValue("beforeId", beforeId, Types.OTHER)',
}
for old, new in replacements.items():
    count = source.count(old)
    assert count == 2, f"expected 2 occurrences of {old}, found {count}"
    source = source.replace(old, new)

old_filters = '''.addValue("ownerId", ownerId).addValue("methodType", methodType)
                        .addValue("verificationStatus", verificationStatus)
                        .addValue("beforeTime", timestamp(beforeUpdatedAt), Types.TIMESTAMP)'''
new_filters = '''.addValue("ownerId", ownerId).addValue("methodType", methodType, Types.VARCHAR)
                        .addValue("verificationStatus", verificationStatus, Types.VARCHAR)
                        .addValue("beforeTime", timestamp(beforeUpdatedAt), Types.TIMESTAMP)'''
assert source.count(old_filters) == 1, (
    "communication list filter parameter block changed unexpectedly"
)
source = source.replace(old_filters, new_filters, 1)
repository.write_text(source, encoding="utf-8")

test_path = Path(
    "apps/sanad-platform/src/test/java/com/sanad/platform/crm/party/infrastructure/"
    "JdbcAddressCommunicationNullableFilterPostgresTest.java"
)
test_path.parent.mkdir(parents=True, exist_ok=True)
test_path.write_text(
    '''package com.sanad.platform.crm.party.infrastructure;

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
                "Docker is required to verify nullable CRM list filters against PostgreSQL.");

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
                  AND (:beforeTime IS NULL OR updated_at<:beforeTime
                       OR (updated_at=:beforeTime AND (:beforeId IS NULL OR id<:beforeId)))
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
                  AND (:methodType IS NULL OR method_type=:methodType)
                  AND (:verificationStatus IS NULL OR verification_status=:verificationStatus)
                  AND (:beforeTime IS NULL OR updated_at<:beforeTime
                       OR (updated_at=:beforeTime AND (:beforeId IS NULL OR id<:beforeId)))
                ORDER BY updated_at DESC,id DESC LIMIT :limit
                """, params)).isEmpty();
    }

    @Test
    void productionRepositoryDeclaresAllNullableSqlTypes() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/sanad/platform/crm/party/infrastructure/JdbcAddressCommunicationRepository.java"));

        assertThat(source)
                .contains("addValue(\\\"beforeTime\\\", timestamp(beforeUpdatedAt), Types.TIMESTAMP)")
                .contains("addValue(\\\"beforeId\\\", beforeId, Types.OTHER)")
                .contains("addValue(\\\"methodType\\\", methodType, Types.VARCHAR)")
                .contains("addValue(\\\"verificationStatus\\\", verificationStatus, Types.VARCHAR)");
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
''',
    encoding="utf-8",
)

Path(".github/workflows/apply-pr639-runtime-fixes.yml").unlink()
Path(".github/scripts/apply_pr639_runtime_fixes.py").unlink()
