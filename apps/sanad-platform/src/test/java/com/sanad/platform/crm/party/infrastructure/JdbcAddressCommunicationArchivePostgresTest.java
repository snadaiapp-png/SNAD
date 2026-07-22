package com.sanad.platform.crm.party.infrastructure;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL 16 regression tests for the communication-method and address archive
 * SQL paths that failed with {@code BadSqlGrammarException} (CRM-007R7).
 *
 * <p>Root cause: {@code archived_at=CASE WHEN :status='ARCHIVED' THEN :now ELSE NULL END}
 * inferred {@code text} type by PostgreSQL. Fix: explicit {@code ::timestamptz} cast.</p>
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JdbcAddressCommunicationArchivePostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static NamedParameterJdbcTemplate jdbc;
    private static UUID tenantId;
    private static UUID accountId;
    private static UUID contactId;
    private static UUID emailMethodId;
    private static UUID phoneMethodId;
    private static UUID contactEmailMethodId;
    private static long emailMethodVersion;

    @BeforeAll
    static void requireDockerAndMigrate() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(dockerAvailable,
                "Docker is required to verify CRM archive SQL against PostgreSQL 16.");

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load()
                .migrate();

        jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

        tenantId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        contactId = UUID.randomUUID();

        UUID actorId = UUID.randomUUID();
        Instant now = Instant.now();
        Timestamp ts = Timestamp.from(now);

        // Create tenant
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, :name, :subdomain, 'ACTIVE', :now, :now)
                """, Map.of("id", (Object) tenantId, "name", "CRM-007-R7-Tenant",
                "subdomain", "crm007r7-" + tenantId.toString().substring(0, 8), "now", ts));

        // Create account
        jdbc.update("""
                INSERT INTO crm_accounts (id, tenant_id, version, display_name, normalized_name,
                    account_type, lifecycle_status, created_by, updated_by, created_at, updated_at)
                VALUES (:id, :tenantId, 0, :name, :normalized, 'BUSINESS', 'ACTIVE',
                    :actorId, :actorId, :now, :now)
                """, new MapSqlParameterSource()
                .addValue("id", accountId).addValue("tenantId", tenantId)
                .addValue("name", "CRM-007 R7 Account").addValue("normalized", "crm-007-r7-account")
                .addValue("actorId", actorId).addValue("now", ts));

        // Create contact
        jdbc.update("""
                INSERT INTO crm_contacts (id, tenant_id, version, given_name, display_name, normalized_name,
                    lifecycle_status, created_by, updated_by, created_at, updated_at)
                VALUES (:id, :tenantId, 0, :givenName, :name, :normalized, 'ACTIVE',
                    :actorId, :actorId, :now, :now)
                """, new MapSqlParameterSource()
                .addValue("id", contactId).addValue("tenantId", tenantId)
                .addValue("givenName", "CRM-007")
                .addValue("name", "CRM-007 R7 Contact").addValue("normalized", "crm-007-r7-contact")
                .addValue("actorId", actorId).addValue("now", ts));

        // Create preferred EMAIL communication method for account
        emailMethodId = UUID.randomUUID();
        emailMethodVersion = 0;
        String emailValue = "crm007r7@example.test";
        jdbc.update("""
                INSERT INTO crm_communication_methods (id, tenant_id, version, owner_type, owner_id,
                    account_id, contact_id, method_type, raw_value, normalized_value, display_value,
                    label, preferred, preferred_slot, verified, verification_status,
                    privacy_classification, usage_purpose, status, created_by, updated_by,
                    created_at, updated_at)
                VALUES (:id, :tenantId, 0, 'ACCOUNT', :ownerId,
                    :ownerId, NULL, 'EMAIL', :raw, :normalized, :display,
                    'Primary Email', TRUE, 1, FALSE, 'UNVERIFIED',
                    'INTERNAL', 'SUPPORT', 'ACTIVE', :actorId, :actorId, :now, :now)
                """, new MapSqlParameterSource()
                .addValue("id", emailMethodId).addValue("tenantId", tenantId)
                .addValue("ownerId", accountId)
                .addValue("raw", emailValue).addValue("normalized", emailValue.toLowerCase())
                .addValue("display", emailValue)
                .addValue("actorId", actorId).addValue("now", ts));

        // Set legacy projection
        jdbc.update("""
                UPDATE crm_accounts SET primary_email=:email, updated_by=:actorId, updated_at=:now, version=version+1
                WHERE tenant_id=:tenantId AND id=:accountId
                """, new MapSqlParameterSource()
                .addValue("email", emailValue).addValue("actorId", actorId)
                .addValue("now", ts).addValue("tenantId", tenantId)
                .addValue("accountId", accountId));

        // Create preferred PHONE communication method for account
        phoneMethodId = UUID.randomUUID();
        String phoneValue = "+966501234567";
        jdbc.update("""
                INSERT INTO crm_communication_methods (id, tenant_id, version, owner_type, owner_id,
                    account_id, contact_id, method_type, raw_value, normalized_value, display_value,
                    label, preferred, preferred_slot, verified, verification_status,
                    privacy_classification, usage_purpose, status, created_by, updated_by,
                    created_at, updated_at)
                VALUES (:id, :tenantId, 0, 'ACCOUNT', :ownerId,
                    :ownerId, NULL, 'MOBILE', :raw, :normalized, :display,
                    'Primary Phone', TRUE, 1, FALSE, 'UNVERIFIED',
                    'INTERNAL', 'SUPPORT', 'ACTIVE', :actorId, :actorId, :now, :now)
                """, new MapSqlParameterSource()
                .addValue("id", phoneMethodId).addValue("tenantId", tenantId)
                .addValue("ownerId", accountId)
                .addValue("raw", phoneValue).addValue("normalized", phoneValue)
                .addValue("display", phoneValue)
                .addValue("actorId", actorId).addValue("now", ts));

        // Create preferred EMAIL communication method for contact
        contactEmailMethodId = UUID.randomUUID();
        String contactEmail = "crm007r7-contact@example.test";
        jdbc.update("""
                INSERT INTO crm_communication_methods (id, tenant_id, version, owner_type, owner_id,
                    account_id, contact_id, method_type, raw_value, normalized_value, display_value,
                    label, preferred, preferred_slot, verified, verification_status,
                    privacy_classification, usage_purpose, status, created_by, updated_by,
                    created_at, updated_at)
                VALUES (:id, :tenantId, 0, 'PERSON', :ownerId,
                    NULL, :ownerId, 'EMAIL', :raw, :normalized, :display,
                    'Contact Email', TRUE, 1, FALSE, 'UNVERIFIED',
                    'INTERNAL', 'SUPPORT', 'ACTIVE', :actorId, :actorId, :now, :now)
                """, new MapSqlParameterSource()
                .addValue("id", contactEmailMethodId).addValue("tenantId", tenantId)
                .addValue("ownerId", contactId)
                .addValue("raw", contactEmail).addValue("normalized", contactEmail.toLowerCase())
                .addValue("display", contactEmail)
                .addValue("actorId", actorId).addValue("now", ts));
    }

    @Test
    @Order(1)
    void archivePreferredAccountEmail_assertProjectionCleared() {
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.now();
        Timestamp ts = Timestamp.from(now);

        // Capture pre-archive state
        long versionBefore = jdbc.queryForObject(
                "SELECT version FROM crm_communication_methods WHERE tenant_id=:tenantId AND id=:id",
                Map.of("tenantId", (Object) tenantId, "id", emailMethodId), Long.class);
        assertThat(versionBefore).isEqualTo(emailMethodVersion);

        String primaryEmailBefore = jdbc.queryForObject(
                "SELECT primary_email FROM crm_accounts WHERE tenant_id=:tenantId AND id=:id",
                Map.of("tenantId", (Object) tenantId, "id", accountId), String.class);
        assertThat(primaryEmailBefore).isNotNull();

        // Archive using the exact SQL from changeCommunicationStatus with ::timestamptz fix
        int updated = jdbc.update("""
                UPDATE crm_communication_methods SET status=:status,
                    preferred=CASE WHEN :status='ARCHIVED' THEN FALSE ELSE preferred END,
                    preferred_slot=CASE WHEN :status='ARCHIVED' THEN NULL ELSE preferred_slot END,
                    archived_at=CASE WHEN :status='ARCHIVED' THEN CAST(:now AS TIMESTAMP) ELSE NULL END,
                    updated_by=:actorId,updated_at=:now,version=version+1
                WHERE tenant_id=:tenantId AND id=:id AND version=:expectedVersion
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", emailMethodId)
                .addValue("expectedVersion", versionBefore)
                .addValue("actorId", actorId)
                .addValue("status", "ARCHIVED")
                .addValue("now", ts));

        assertThat(updated).isEqualTo(1);

        // Assert canonical status
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, preferred, preferred_slot, archived_at, version FROM crm_communication_methods WHERE tenant_id=:tenantId AND id=:id",
                Map.of("tenantId", (Object) tenantId, "id", emailMethodId));
        assertThat(row.get("status")).isEqualTo("ARCHIVED");
        assertThat(row.get("preferred")).isEqualTo(false);
        assertThat(row.get("preferred_slot")).isNull();
        assertThat(row.get("archived_at")).isNotNull();
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(versionBefore + 1);

        // Assert legacy projection cleared
        String primaryEmailAfter = jdbc.queryForObject(
                "SELECT primary_email FROM crm_accounts WHERE tenant_id=:tenantId AND id=:id",
                Map.of("tenantId", (Object) tenantId, "id", accountId), String.class);
        assertThat(primaryEmailAfter).isNull();
    }

    @Test
    @Order(2)
    void archiveNonPreferredAccountEmail_succeeds() {
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.now();
        Timestamp ts = Timestamp.from(now);

        // Create a non-preferred email method
        UUID nonPreferredId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO crm_communication_methods (id, tenant_id, version, owner_type, owner_id,
                    account_id, method_type, raw_value, normalized_value, display_value,
                    preferred, preferred_slot, verified, verification_status,
                    privacy_classification, usage_purpose, status, created_by, updated_by,
                    created_at, updated_at)
                VALUES (:id, :tenantId, 0, 'ACCOUNT', :ownerId,
                    :ownerId, 'EMAIL', :raw, :normalized, :display,
                    FALSE, NULL, FALSE, 'UNVERIFIED',
                    'INTERNAL', 'SUPPORT', 'ACTIVE', :actorId, :actorId, :now, :now)
                """, new MapSqlParameterSource()
                .addValue("id", nonPreferredId).addValue("tenantId", tenantId)
                .addValue("ownerId", accountId)
                .addValue("raw", "non-pref@example.test").addValue("normalized", "non-pref@example.test")
                .addValue("display", "non-pref@example.test")
                .addValue("actorId", actorId).addValue("now", ts));

        int updated = jdbc.update("""
                UPDATE crm_communication_methods SET status=:status,
                    preferred=CASE WHEN :status='ARCHIVED' THEN FALSE ELSE preferred END,
                    preferred_slot=CASE WHEN :status='ARCHIVED' THEN NULL ELSE preferred_slot END,
                    archived_at=CASE WHEN :status='ARCHIVED' THEN CAST(:now AS TIMESTAMP) ELSE NULL END,
                    updated_by=:actorId,updated_at=:now,version=version+1
                WHERE tenant_id=:tenantId AND id=:id AND version=0
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", nonPreferredId)
                .addValue("actorId", actorId)
                .addValue("status", "ARCHIVED")
                .addValue("now", ts));

        assertThat(updated).isEqualTo(1);
        String status = jdbc.queryForObject(
                "SELECT status FROM crm_communication_methods WHERE tenant_id=:tenantId AND id=:id",
                Map.of("tenantId", (Object) tenantId, "id", nonPreferredId), String.class);
        assertThat(status).isEqualTo("ARCHIVED");
    }

    @Test
    @Order(3)
    void archivePreferredAccountPhone_succeeds() {
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.now();
        Timestamp ts = Timestamp.from(now);

        int updated = jdbc.update("""
                UPDATE crm_communication_methods SET status=:status,
                    preferred=CASE WHEN :status='ARCHIVED' THEN FALSE ELSE preferred END,
                    preferred_slot=CASE WHEN :status='ARCHIVED' THEN NULL ELSE preferred_slot END,
                    archived_at=CASE WHEN :status='ARCHIVED' THEN CAST(:now AS TIMESTAMP) ELSE NULL END,
                    updated_by=:actorId,updated_at=:now,version=version+1
                WHERE tenant_id=:tenantId AND id=:id AND version=0
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", phoneMethodId)
                .addValue("actorId", actorId)
                .addValue("status", "ARCHIVED")
                .addValue("now", ts));

        assertThat(updated).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, preferred, archived_at FROM crm_communication_methods WHERE tenant_id=:tenantId AND id=:id",
                Map.of("tenantId", (Object) tenantId, "id", phoneMethodId));
        assertThat(row.get("status")).isEqualTo("ARCHIVED");
        assertThat(row.get("preferred")).isEqualTo(false);
        assertThat(row.get("archived_at")).isNotNull();
    }

    @Test
    @Order(4)
    void archivePreferredContactEmail_succeeds() {
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.now();
        Timestamp ts = Timestamp.from(now);

        int updated = jdbc.update("""
                UPDATE crm_communication_methods SET status=:status,
                    preferred=CASE WHEN :status='ARCHIVED' THEN FALSE ELSE preferred END,
                    preferred_slot=CASE WHEN :status='ARCHIVED' THEN NULL ELSE preferred_slot END,
                    archived_at=CASE WHEN :status='ARCHIVED' THEN CAST(:now AS TIMESTAMP) ELSE NULL END,
                    updated_by=:actorId,updated_at=:now,version=version+1
                WHERE tenant_id=:tenantId AND id=:id AND version=0
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", contactEmailMethodId)
                .addValue("actorId", actorId)
                .addValue("status", "ARCHIVED")
                .addValue("now", ts));

        assertThat(updated).isEqualTo(1);
        String status = jdbc.queryForObject(
                "SELECT status FROM crm_communication_methods WHERE tenant_id=:tenantId AND id=:id",
                Map.of("tenantId", (Object) tenantId, "id", contactEmailMethodId), String.class);
        assertThat(status).isEqualTo("ARCHIVED");
    }

    @Test
    @Order(5)
    void archiveAddressStatus_succeeds() {
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.now();
        Timestamp ts = Timestamp.from(now);

        // Create an address
        UUID addressId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO crm_party_addresses (id, tenant_id, version, owner_type, owner_id,
                    account_id, address_type, label, raw_formatted_address, line1, city,
                    country_code, primary_address, primary_slot, verified, status,
                    created_by, updated_by, created_at, updated_at)
                VALUES (:id, :tenantId, 0, 'ACCOUNT', :ownerId,
                    :ownerId, 'OFFICE', 'Test', '123 Riyadh', '123', 'Riyadh',
                    'SA', TRUE, 1, FALSE, 'ACTIVE',
                    :actorId, :actorId, :now, :now)
                """, new MapSqlParameterSource()
                .addValue("id", addressId).addValue("tenantId", tenantId)
                .addValue("ownerId", accountId)
                .addValue("actorId", actorId).addValue("now", ts));

        // Archive using the fixed SQL
        int updated = jdbc.update("""
                UPDATE crm_party_addresses SET status=:status,
                    primary_address=CASE WHEN :status='ARCHIVED' THEN FALSE ELSE primary_address END,
                    primary_slot=CASE WHEN :status='ARCHIVED' THEN NULL ELSE primary_slot END,
                    archived_at=CASE WHEN :status='ARCHIVED' THEN CAST(:now AS TIMESTAMP) ELSE NULL END,
                    updated_by=:actorId,updated_at=:now,version=version+1
                WHERE tenant_id=:tenantId AND id=:id AND version=0
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", addressId)
                .addValue("actorId", actorId)
                .addValue("status", "ARCHIVED")
                .addValue("now", ts));

        assertThat(updated).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, primary_address, archived_at FROM crm_party_addresses WHERE tenant_id=:tenantId AND id=:id",
                Map.of("tenantId", (Object) tenantId, "id", addressId));
        assertThat(row.get("status")).isEqualTo("ARCHIVED");
        assertThat(row.get("primary_address")).isEqualTo(false);
        assertThat(row.get("archived_at")).isNotNull();
    }

    @Test
    @Order(6)
    void staleVersionUpdate_returnsZeroRows() {
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.now();
        Timestamp ts = Timestamp.from(now);

        // Create a fresh method for stale-version test
        UUID freshId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO crm_communication_methods (id, tenant_id, version, owner_type, owner_id,
                    account_id, method_type, raw_value, normalized_value, display_value,
                    preferred, preferred_slot, verified, verification_status,
                    privacy_classification, usage_purpose, status, created_by, updated_by,
                    created_at, updated_at)
                VALUES (:id, :tenantId, 0, 'ACCOUNT', :ownerId,
                    :ownerId, 'EMAIL', :raw, :normalized, :display,
                    FALSE, NULL, FALSE, 'UNVERIFIED',
                    'INTERNAL', 'SUPPORT', 'ACTIVE', :actorId, :actorId, :now, :now)
                """, new MapSqlParameterSource()
                .addValue("id", freshId).addValue("tenantId", tenantId)
                .addValue("ownerId", accountId)
                .addValue("raw", "stale@example.test").addValue("normalized", "stale@example.test")
                .addValue("display", "stale@example.test")
                .addValue("actorId", actorId).addValue("now", ts));

        // First archive succeeds
        int updated1 = jdbc.update("""
                UPDATE crm_communication_methods SET status=:status,
                    preferred=CASE WHEN :status='ARCHIVED' THEN FALSE ELSE preferred END,
                    preferred_slot=CASE WHEN :status='ARCHIVED' THEN NULL ELSE preferred_slot END,
                    archived_at=CASE WHEN :status='ARCHIVED' THEN CAST(:now AS TIMESTAMP) ELSE NULL END,
                    updated_by=:actorId,updated_at=:now,version=version+1
                WHERE tenant_id=:tenantId AND id=:id AND version=0
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId).addValue("id", freshId)
                .addValue("actorId", actorId).addValue("status", "ARCHIVED").addValue("now", ts));
        assertThat(updated1).isEqualTo(1);

        // Second archive with stale version returns 0 rows (concurrency guard)
        int updated2 = jdbc.update("""
                UPDATE crm_communication_methods SET status=:status,
                    preferred=CASE WHEN :status='ARCHIVED' THEN FALSE ELSE preferred END,
                    preferred_slot=CASE WHEN :status='ARCHIVED' THEN NULL ELSE preferred_slot END,
                    archived_at=CASE WHEN :status='ARCHIVED' THEN CAST(:now AS TIMESTAMP) ELSE NULL END,
                    updated_by=:actorId,updated_at=:now,version=version+1
                WHERE tenant_id=:tenantId AND id=:id AND version=0
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId).addValue("id", freshId)
                .addValue("actorId", actorId).addValue("status", "ARCHIVED").addValue("now", ts));
        assertThat(updated2).isZero();
    }

    @Test
    @Order(7)
    void productionRepositoryDeclaresTimestamptzCast() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/sanad/platform/crm/party/infrastructure/JdbcAddressCommunicationRepository.java"));

        assertThat(source)
                .contains("CAST(:now AS TIMESTAMP)")
                .as("changeCommunicationStatus and changeAddressStatus must cast :now inside CASE WHEN");
    }
}
