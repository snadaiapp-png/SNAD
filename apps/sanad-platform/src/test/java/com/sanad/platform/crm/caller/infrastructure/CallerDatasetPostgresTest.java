package com.sanad.platform.crm.caller.infrastructure;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.caller.application.CallerDatasetService;
import com.sanad.platform.crm.caller.application.CallerDatasetTokenProvider;
import com.sanad.platform.crm.caller.CallerPhoneVectorsParityTest;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline caller dataset projection tests on PostgreSQL Direct (G8-03 §63):
 * snapshot, tombstones, pagination (no dup/skip), RESTRICTED minimisation,
 * tenant isolation, dataset key issuance.
 */
class CallerDatasetPostgresTest {

    private static final String MASTER_KEY = "g8-test-master-key";

    private JdbcTemplate jdbc;
    private CallerDatasetService service;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("testClassName");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping CallerDatasetPostgresTest.");
    }

    @BeforeEach
    void migrateAndSeed() {
        Flyway flyway = Flyway.configure()
                .dataSource(System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load();
        // flyway.clean() removed — was destroying shared CI schema; replaced with flyway.migrate() (idempotent, non-destructive)
        flyway.migrate();
        flyway.validate();

        DriverManagerDataSource ds = new DriverManagerDataSource(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        ds.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(ds);
        service = new CallerDatasetService(new NamedParameterJdbcTemplate(ds),
                new CallerDatasetTokenProvider(MASTER_KEY),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    @Test
    void snapshotContainsEligibleEntriesWithTokensAndStripsRestrictedPii() {
        UUID tenant = tenant("ds-snap");
        UUID contactId = contact(tenant, "محمد أحمد");
        method(tenant, contactId, "+966541234567", "ACTIVE", "INTERNAL");
        UUID restrictedContact = contact(tenant, "مقيد جدا");
        method(tenant, restrictedContact, "+966599999999", "ACTIVE", "RESTRICTED");

        CallerDatasetService.CallerDatasetDelta delta = service.delta(tenant, 0, null, 500, false);

        assertThat(delta.entries()).hasSize(2);
        CallerDatasetService.CallerDatasetRecord mohammed = delta.entries().stream()
                .filter(e -> e.displayName() != null).findFirst().orElseThrow();
        assertThat(mohammed.lookupToken()).isEqualTo(CallerPhoneVectorsParityTest.hmacSha256Hex(
                CallerPhoneVectorsParityTest.hmacSha256Hex(MASTER_KEY, tenant.toString()),
                "+966541234567"));
        // The dataset never carries plaintext phones.
        assertThat(delta.entries().stream().anyMatch(e ->
                String.valueOf(e).contains("+966541234567"))).isFalse();

        CallerDatasetService.CallerDatasetRecord restricted = delta.entries().stream()
                .filter(e -> "RESTRICTED".equals(e.privacyLevel())).findFirst().orElseThrow();
        assertThat(restricted.displayName()).isNull();
        assertThat(restricted.accountName()).isNull();
        assertThat(restricted.entityId()).isNull();
    }

    @Test
    void archivedMethodEmitsTombstoneOnNextDelta() throws Exception {
        UUID tenant = tenant("ds-tomb");
        UUID contactId = contact(tenant, "عميل مؤرشف");
        UUID methodId = method(tenant, contactId, "+966555555555", "ACTIVE", "INTERNAL");

        CallerDatasetService.CallerDatasetDelta first = service.delta(tenant, 0, null, 500, false);
        assertThat(first.entries()).hasSize(1);
        assertThat(first.entries().get(0).deleted()).isFalse();
        // A resume cursor is always provided while entries exist.
        assertThat(first.nextCursor()).isNotNull();

        // Archive the method (updated_at moves forward).
        jdbc.update("UPDATE crm_communication_methods SET status='ARCHIVED', updated_at=? " +
                        "WHERE id=? AND tenant_id=?", java.sql.Timestamp.from(Instant.now().plusSeconds(5)),
                methodId, tenant);

        String[] parts = new String(Base64.getUrlDecoder().decode(first.nextCursor())).split(":", 2);
        long cursorMs = Long.parseLong(parts[0]);
        UUID cursorId = UUID.fromString(parts[1]);
        CallerDatasetService.CallerDatasetDelta second = service.delta(tenant, cursorMs, cursorId, 500, false);

        assertThat(second.entries()).hasSize(1);
        assertThat(second.entries().get(0).deleted()).isTrue();
        assertThat(second.entries().get(0).lookupToken()).isEqualTo(first.entries().get(0).lookupToken());
    }

    @Test
    void paginationHasNoDuplicatesOrSkips() {
        UUID tenant = tenant("ds-page");
        for (int i = 0; i < 7; i++) {
            UUID c = contact(tenant, "عميل " + i);
            method(tenant, c, "+9665" + String.format("%08d", i + 1), "ACTIVE", "INTERNAL",
                    Instant.parse("2026-08-20T10:00:0" + i + "Z"));
        }
        Set<String> tokens = new HashSet<>();
        long cursorMs = 0;
        UUID cursorId = null;
        int pages = 0;
        do {
            CallerDatasetService.CallerDatasetDelta page = service.delta(tenant, cursorMs, cursorId, 3, false);
            for (CallerDatasetService.CallerDatasetRecord record : page.entries()) {
                assertThat(tokens.add(record.lookupToken())).as("duplicate across pages").isTrue();
            }
            pages++;
            if (page.nextCursor() == null) break;
            String[] parts = new String(Base64.getUrlDecoder().decode(page.nextCursor())).split(":", 2);
            cursorMs = Long.parseLong(parts[0]);
            cursorId = UUID.fromString(parts[1]);
        } while (pages < 20);
        assertThat(tokens).hasSize(7);
    }

    @Test
    void datasetIsTenantIsolated() {
        UUID tenantA = tenant("ds-iso-a");
        UUID tenantB = tenant("ds-iso-b");
        UUID contactA = contact(tenantA, "عميل أ");
        method(tenantA, contactA, "+966541234567", "ACTIVE", "INTERNAL");
        UUID contactB = contact(tenantB, "عميل ب");
        method(tenantB, contactB, "+966541234567", "ACTIVE", "INTERNAL");

        CallerDatasetService.CallerDatasetDelta onlyA = service.delta(tenantA, 0, null, 500, false);
        CallerDatasetService.CallerDatasetDelta onlyB = service.delta(tenantB, 0, null, 500, false);

        assertThat(onlyA.entries()).hasSize(1);
        assertThat(onlyA.entries().get(0).entityId()).isEqualTo(contactA);
        assertThat(onlyB.entries()).hasSize(1);
        assertThat(onlyB.entries().get(0).entityId()).isEqualTo(contactB);
        assertThat(onlyA.entries().get(0).lookupToken())
                .isNotEqualTo(onlyB.entries().get(0).lookupToken());
    }

    @Test
    void datasetKeyIssuedOnFirstSyncOnlyAndFailsClosedWithoutMasterKey() {
        UUID tenant = tenant("ds-key");
        UUID contactId = contact(tenant, "عميل المفتاح");
        method(tenant, contactId, "+966577777777", "ACTIVE", "INTERNAL");

        CallerDatasetService.CallerDatasetDelta first = service.delta(tenant, 0, null, 500, true);
        assertThat(first.datasetKey()).isEqualTo(
                CallerPhoneVectorsParityTest.hmacSha256Hex(MASTER_KEY, tenant.toString()));

        CallerDatasetService.CallerDatasetDelta second = service.delta(tenant, 0, null, 500, false);
        assertThat(second.datasetKey()).isNull();

        // Fail closed when no master key is configured.
        CallerDatasetService unconfigured = new CallerDatasetService(
                new NamedParameterJdbcTemplate(ds()),
                new CallerDatasetTokenProvider(null),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                unconfigured.delta(tenant, 0, null, 500, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("master key");
    }

    private DriverManagerDataSource ds() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }

    private UUID tenant(String key) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) " +
                        "VALUES (?,?,?,'ACTIVE',?,?)",
                id, key, key + "-" + id.toString().substring(0, 8),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        return id;
    }

    private UUID contact(UUID tenantId, String displayName) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO crm_contacts (id,tenant_id,version,account_id,given_name,family_name,display_name," +
                        "normalized_name,preferred_locale,time_zone,lifecycle_status,owner_user_id,consent_summary," +
                        "created_by,updated_by,created_at,updated_at) " +
                        "VALUES (?,?,0,NULL,?,?,?,?, 'ar-SA','Asia/Riyadh','ACTIVE',?, 'GRANTED',?,?,?,?)",
                id, tenantId, displayName.substring(0, 1),
                displayName.substring(Math.min(1, displayName.length() - 1)),
                displayName, displayName.toLowerCase(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        return id;
    }

    private UUID method(UUID tenantId, UUID contactId, String phone, String status, String privacy) {
        return method(tenantId, contactId, phone, status, privacy, Instant.now());
    }

    private UUID method(UUID tenantId, UUID contactId, String phone, String status, String privacy,
                        Instant at) {
        UUID id = UUID.randomUUID();
        Instant now = at;
        jdbc.update("INSERT INTO crm_communication_methods (id,tenant_id,version,owner_type,owner_id,account_id," +
                        "contact_id,method_type,raw_value,normalized_value,display_value,label,preferred,preferred_slot," +
                        "verified,verification_status,privacy_classification,consent_state_reference,usage_purpose,status," +
                        "created_by,updated_by,created_at,updated_at) " +
                        "VALUES (?,?,0,'PERSON',?,NULL,?, 'MOBILE',?,?,?, 'Mobile', FALSE, NULL, " +
                        "FALSE, 'UNVERIFIED', ?, 'C-REF-G8', 'BUSINESS', ?,?,?,?,?)",
                id, tenantId, contactId, contactId, phone, phone, phone, privacy, status,
                UUID.randomUUID(), UUID.randomUUID(),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        return id;
    }
}
