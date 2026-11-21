package com.sanad.platform.subscription.catalog;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED/GREEN regression for the application-catalog INSERT jsonb cast shift
 * (defect discovered by automated UAT-04, 2026-11-16).
 *
 * <p>Defect: {@code ApplicationRepository.insert} placed the {@code ::jsonb}
 * casts at VALUES positions 11-12 while the jsonb columns
 * ({@code supported_countries}, {@code dependencies}) sit at positions 12-13.
 * With non-null {@code supportedCountries}/{@code dependencies} the insert
 * failed with {@code column "dependencies" is of type jsonb but expression is
 * of type character varying}. Existing PG-direct catalog tests passed only
 * because they exercised rows with null lists (NULL bypasses the mismatched
 * cast ordering).</p>
 */
@SpringBootTest
@ActiveProfiles("local")
class ApplicationInsertJsonbPostgresTest {

    @Autowired private ApplicationCatalogService catalogService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                "ApplicationInsertJsonbPostgresTest");
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is required for ApplicationInsertJsonbPostgresTest");
    }

    private ApplicationEntity entity(String code, List<String> countries, List<String> deps) {
        ApplicationEntity e = new ApplicationEntity();
        e.setCode(code);
        e.setName("JSONB Cast Probe " + code);
        e.setStatus("ACTIVE");
        e.setCategory("MODULE");
        e.setProvisioningMode("IMMEDIATE");
        e.setSupportedCountries(countries);
        e.setDependencies(deps);
        e.setDisplayOrder(0);
        return e;
    }

    @Test
    void insertWithNonNullSupportedCountriesAndDependenciesPersists() {
        String code = "JSONB_PROBE_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ApplicationEntity created = catalogService.create(entity(code, List.of("SA", "AE"), List.of("CRM")));

        var row = jdbc.queryForMap(
                "SELECT code, supported_countries::text AS sc, dependencies::text AS deps "
                        + "FROM applications WHERE id = ?",
                created.getId());
        assertThat(row.get("code")).isEqualTo(code);
        assertThat((String) row.get("sc")).contains("SA").contains("AE");
        assertThat((String) row.get("deps")).contains("CRM");
    }

    @Test
    void insertWithNullListsStillPersists() {
        String code = "JSONB_NULL_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ApplicationEntity created = catalogService.create(entity(code, null, null));
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM applications WHERE id = ?", Integer.class, created.getId());
        assertThat(count).isEqualTo(1);
    }
}
