package com.sanad.platform.crm.caller.infrastructure;

import com.sanad.platform.crm.caller.domain.CallerCandidate;
import com.sanad.platform.crm.caller.domain.CallerIdentificationRepository;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G8 caller-candidate repository tests on PostgreSQL Direct (G8-02 §40).
 *
 * <p>Runs ONLY against a real PostgreSQL (CI service container or local dev);
 * skipped gracefully otherwise (mandatory in CI), per the established
 * Crm009 test-environment gate. No Testcontainers.
 */
class JdbcCallerIdentificationRepositoryPostgresTest {

    private static final String PHONE = "+966541234567";

    private JdbcTemplate jdbc;
    private CallerIdentificationRepository repository;

    @BeforeAll
    static void requirePostgreSql() {
        boolean postgresAvailable;
        try {
            postgresAvailable = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("testClassName");
        } catch (Throwable ignored) {
            postgresAvailable = false;
        }
        Assumptions.assumeTrue(postgresAvailable,
                "PostgreSQL Direct is not available — skipping JdbcCallerIdentificationRepositoryPostgresTest.");
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
        repository = new JdbcCallerIdentificationRepository(new NamedParameterJdbcTemplate(ds));
    }

    @Test
    void exactNormalizedLookupFindsVerifiedContact() {
        UUID tenant = tenant("caller-a");
        UUID contactId = contact(tenant, "محمد أحمد", UUID.randomUUID());
        UUID methodId = communicationMethod(tenant, contactId, PHONE, true, "VERIFIED", true, "INTERNAL");

        List<CallerCandidate> candidates = repository.findActiveCallerCandidates(tenant, PHONE);

        assertThat(candidates).hasSize(1);
        CallerCandidate winner = candidates.get(0);
        assertThat(winner.communicationMethodId()).isEqualTo(methodId);
        assertThat(winner.ownerType()).isEqualTo("PERSON");
        assertThat(winner.contactId()).isEqualTo(contactId);
        assertThat(winner.displayName()).isEqualTo("محمد أحمد");
        assertThat(winner.verified()).isTrue();
        assertThat(winner.matchSource()).isEqualTo(CallerCandidate.SOURCE_CANONICAL);
    }

    @Test
    void sameNumberTwoTenantsAreIsolated() {
        UUID tenantA = tenant("caller-iso-a");
        UUID tenantB = tenant("caller-iso-b");
        UUID contactA = contact(tenantA, "عميل أ", UUID.randomUUID());
        UUID contactB = contact(tenantB, "عميل ب", UUID.randomUUID());
        communicationMethod(tenantA, contactA, PHONE, false, "UNVERIFIED", false, "INTERNAL");
        communicationMethod(tenantB, contactB, PHONE, false, "UNVERIFIED", false, "INTERNAL");

        List<CallerCandidate> onlyA = repository.findActiveCallerCandidates(tenantA, PHONE);
        List<CallerCandidate> onlyB = repository.findActiveCallerCandidates(tenantB, PHONE);

        assertThat(onlyA).hasSize(1);
        assertThat(onlyA.get(0).contactId()).isEqualTo(contactA);
        assertThat(onlyB).hasSize(1);
        assertThat(onlyB.get(0).contactId()).isEqualTo(contactB);
        assertThat(onlyA.get(0).contactId()).isNotEqualTo(onlyB.get(0).contactId());
    }

    @Test
    void methodTypeFilteringExcludesFaxAndSms() {
        UUID tenant = tenant("caller-mt");
        UUID contactId = contact(tenant, "شخص فاكس", UUID.randomUUID());
        UUID mobile = communicationMethodType(tenant, contactId, "MOBILE", PHONE);
        UUID fax = communicationMethodType(tenant, contactId, "FAX", PHONE);
        communicationMethodType(tenant, contactId, "SMS", PHONE);

        List<CallerCandidate> candidates = repository.findActiveCallerCandidates(tenant, PHONE);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).communicationMethodId()).isEqualTo(mobile);
        assertThat(fax).isNotNull();
    }

    @Test
    void archivedAndInactiveRowsAreIgnored() {
        UUID tenant = tenant("caller-st");
        UUID contactId = contact(tenant, "شخص معطل", UUID.randomUUID());
        communicationMethodStatus(tenant, contactId, PHONE, "ARCHIVED");
        communicationMethodStatus(tenant, contactId, PHONE, "INACTIVE");

        assertThat(repository.findActiveCallerCandidates(tenant, PHONE)).isEmpty();
    }

    @Test
    void inactiveOwnerIsIgnored() {
        UUID tenant = tenant("caller-owner");
        UUID inactiveContact = contact(tenant, "شخص شبيه محذوف", UUID.randomUUID());
        jdbc.update("UPDATE crm_contacts SET lifecycle_status='INACTIVE' WHERE id=? AND tenant_id=?", inactiveContact, tenant);
        communicationMethod(tenant, inactiveContact, PHONE, false, "UNVERIFIED", false, "INTERNAL");

        assertThat(repository.findActiveCallerCandidates(tenant, PHONE)).isEmpty();
    }

    @Test
    void duplicateSameTenantReturnsBothRows() {
        UUID tenant = tenant("caller-dup");
        UUID first = contact(tenant, "أحمد الأول", UUID.randomUUID());
        UUID second = contact(tenant, "أحمد الثاني", UUID.randomUUID());
        communicationMethod(tenant, first, PHONE, true, "VERIFIED", false, "INTERNAL");
        communicationMethod(tenant, second, PHONE, true, "VERIFIED", false, "INTERNAL");

        List<CallerCandidate> candidates = repository.findActiveCallerCandidates(tenant, PHONE);

        assertThat(candidates).hasSize(2);
        // Deterministic repository ordering: same rank → updated_at ASC, id ASC.
        assertThat(candidates.get(0).contactId()).isNotEqualTo(candidates.get(1).contactId());
    }

    @Test
    void lookupQueryUsesTheCommittedIndex() {
        UUID tenant = tenant("caller-plan");
        // Enough rows that PostgreSQL chooses the index for an exact equality.
        for (int i = 0; i < 2000; i++) {
            UUID c = contact(tenant, "كثافة " + i, UUID.randomUUID());
            communicationMethod(tenant, c, "+9665" + String.format("%08d", (i % 89999999) + 10000000),
                    false, "UNVERIFIED", false, "INTERNAL");
        }
        UUID target = contact(tenant, "الهدف", UUID.randomUUID());
        communicationMethod(tenant, target, PHONE, true, "VERIFIED", true, "INTERNAL");

        // Deterministic planner state: the shared table accumulates rows from
        // other suites, so without ANALYZE the planner picks between the two
        // committed index candidates (lookup vs privacy) based on drifted
        // statistics — CI flake observed during G8-04 reintegration.
        jdbc.update("ANALYZE crm_communication_methods");
        List<Map<String, Object>> plan = jdbc.queryForList(
                "EXPLAIN SELECT cm.id FROM crm_communication_methods cm " +
                        "WHERE cm.tenant_id = ? AND cm.method_type IN ('PHONE','MOBILE') " +
                        "AND cm.normalized_value = ? AND cm.status = 'ACTIVE'",
                tenant, PHONE);
        String planText = plan.toString();
        assertThat(planText)
                .as("query plan must be served by a committed index on crm_communication_methods: %s", planText)
                .containsPattern("Index Scan using idx_crm_communication_methods_(lookup|privacy)");
        assertThat(planText).doesNotContain("Seq Scan");
    }

    @Test
    void leadFallbackMatchesExactLegacyFormsOnly() {
        UUID tenant = tenant("caller-lead");
        lead(tenant, "عميل محتمل", "شركة ناشئة", "0541234567", "NEW");
        lead(tenant, "مستبعد", "شركة مؤرشفة", PHONE, "ARCHIVED");

        List<CallerCandidate> leads = repository.findActiveLeadCandidates(tenant, PHONE);

        assertThat(leads).hasSize(1);
        assertThat(leads.get(0).displayName()).isEqualTo("عميل محتمل");
        assertThat(leads.get(0).matchSource()).isEqualTo(CallerCandidate.SOURCE_LEGACY_LEAD_PHONE);
        assertThat(leads.get(0).accountName()).isEqualTo("شركة ناشئة");
    }

    @Test
    void legacyPhoneFormsDerivationIsDeterministic() {
        // Saudi numbers derive four exact legacy representations.
        assertThat(CallerIdentificationRepository.legacyLeadPhoneForms("+966541234567"))
                .containsExactly("+966541234567", "966541234567", "541234567", "0541234567");
        // Non-966 numbers have no known national/CC split: the deterministic set
        // is {E.164, digits-with-CC, zero-prefixed digits} — exact equality only.
        assertThat(CallerIdentificationRepository.legacyLeadPhoneForms("+971501234567"))
                .containsExactly("+971501234567", "971501234567", "0971501234567");
        assertThat(CallerIdentificationRepository.legacyLeadPhoneForms(null)).isEmpty();
    }

    // ===== fixtures =====

    private UUID tenant(String key) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) " +
                        "VALUES (?,?,?,'ACTIVE',?,?)",
                id, key, key + "-" + id.toString().substring(0, 8),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        return id;
    }

    private UUID contact(UUID tenantId, String displayName, UUID userId) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO crm_contacts (id,tenant_id,version,account_id,given_name,family_name,display_name," +
                        "normalized_name,preferred_locale,time_zone,lifecycle_status,owner_user_id,consent_summary," +
                        "created_by,updated_by,created_at,updated_at) " +
                        "VALUES (?,?,0,NULL,?,?,?,?,'ar-SA','Asia/Riyadh','ACTIVE',?,'GRANTED',?,?,?,?)",
                id, tenantId, displayName.substring(0, 1), displayName.substring(Math.min(1, displayName.length() - 1)),
                displayName, displayName.toLowerCase(), userId, userId, userId,
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        return id;
    }

    private UUID communicationMethod(UUID tenantId, UUID contactId, String phone,
                                     boolean verified, String verification, boolean preferred, String privacy) {
        return communicationMethod(tenantId, contactId, "MOBILE", phone, "ACTIVE",
                verified, verification, preferred, privacy);
    }

    private UUID communicationMethodType(UUID tenantId, UUID contactId, String type, String phone) {
        return communicationMethod(tenantId, contactId, type, phone, "ACTIVE",
                false, "UNVERIFIED", false, "INTERNAL");
    }

    private UUID communicationMethodStatus(UUID tenantId, UUID contactId, String phone, String status) {
        return communicationMethod(tenantId, contactId, "MOBILE", phone, status,
                false, "UNVERIFIED", false, "INTERNAL");
    }

    private UUID communicationMethod(UUID tenantId, UUID contactId, String type, String phone, String status,
                                     boolean verified, String verification, boolean preferred, String privacy) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        java.sql.Timestamp timestamp = java.sql.Timestamp.from(now);
        UUID actor = UUID.randomUUID();
        UUID updater = UUID.randomUUID();
        jdbc.update("INSERT INTO crm_communication_methods (id,tenant_id,version,owner_type,owner_id,account_id," +
                        "contact_id,method_type,raw_value,normalized_value,display_value,label,preferred,preferred_slot," +
                        "verified,verification_status,privacy_classification,consent_state_reference,usage_purpose,status," +
                        "created_by,updated_by,created_at,updated_at) " +
                        "VALUES (?,?,0,'PERSON',?,NULL,?,?,?,?,?,'Mobile',?,?,?,?,?,?,?,?,?,?,?,?)",
                id, tenantId, contactId, contactId, type, phone, phone, phone,
                preferred, preferred ? 1 : null, verified, verification, privacy, "C-REF-G8", "BUSINESS",
                status, actor, updater, timestamp, timestamp);
        return id;
    }

    private UUID lead(UUID tenantId, String displayName, String company, String phone, String status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO crm_leads (id,tenant_id,version,display_name,normalized_name,company_name,phone," +
                        "source,status,owner_user_id,created_by,updated_by,created_at,updated_at) " +
                        "VALUES (?,?,0,?,?,?,?,'PHONE_CALL',?,?,?,?,?,?)",
                id, tenantId, displayName, displayName.toLowerCase(), company, phone, status,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        return id;
    }
}
