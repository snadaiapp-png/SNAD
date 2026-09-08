package com.sanad.platform.hr.employment;

import com.sanad.platform.hr.identity.HrPerson;
import com.sanad.platform.hr.identity.HrPersonService;
import com.sanad.platform.hr.identity.IdentifierNormalizer;
import com.sanad.platform.hr.identity.JdbcHrPersonRepository;
import com.sanad.platform.hr.api.v2.dto.CreateEmploymentRequest;
import com.sanad.platform.hr.api.v2.dto.EmploymentResponse;
import com.sanad.platform.security.crypto.BlindIndex;
import com.sanad.platform.security.crypto.EncryptedValue;
import com.sanad.platform.security.crypto.PlatformCryptographyService;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G0 reconciliation regression — canonical employment write integrity.
 *
 * <p>Defect T-G0-DEF-1 (found by the G0 post-merge reconciliation): the
 * canonical {@code saveEmployment} INSERT hardcoded
 * {@code first_name='Test'}, {@code last_name='Employee'},
 * {@code display_name='Test Employee'} and
 * {@code employment_type='FULL_TIME'} — discarding the real person
 * identity and the real worker classification on the canonical v2 write
 * path.</p>
 *
 * <p>This test freezes the corrected contract:</p>
 * <ul>
 *   <li>Names persisted on {@code hr_employees} are projected from the
 *       canonical {@code hr_people} row — never placeholders.</li>
 *   <li>{@code employment_type} carries the real worker classification
 *       code instead of a silent FULL_TIME default.</li>
 *   <li>A missing canonical person fails the write (fail-closed) and
 *       leaves no {@code hr_employees} row behind.</li>
 *   <li>The end-to-end v2 creation path
 *       ({@code HrEmploymentV2Service.create}) persists real identity.</li>
 * </ul>
 */
class HrEmploymentCanonicalWriteIntegrityIntegrationTest {

    private Connection conn;
    private DriverManagerDataSource dataSource;
    private static String ISOLATED_URL;
    private static final String DB_URL = System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

    private EmploymentRepository employmentRepository;
    private HrPersonService hrPersonService;

    @BeforeAll
    static void requirePostgreSql() {
        boolean ok = false;
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
            try (Connection c = ds.getConnection()) { ok = c.isValid(5); }
        } catch (Throwable ignored) {}
        Assumptions.assumeTrue(ok, "PostgreSQL Direct is not available");
        MigrationTestSchemaSupport.ensureDatabase(DB_URL, DB_USER, DB_PASSWORD);
        ISOLATED_URL = MigrationTestSchemaSupport.getIsolatedJdbcUrl(DB_URL);
    }

    @BeforeEach
    void setup() throws Exception {
        dataSource = new DriverManagerDataSource(ISOLATED_URL, DB_USER, DB_PASSWORD);
        Flyway flyway = Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .baselineOnMigrate(true).cleanDisabled(false).validateOnMigrate(false).load();
        flyway.clean();
        flyway.migrate();
        conn = dataSource.getConnection();
        conn.setAutoCommit(true);

        InMemoryTestCryptoService cryptoService = new InMemoryTestCryptoService();
        JdbcHrPersonRepository personRepository = new JdbcHrPersonRepository(dataSource, cryptoService);
        hrPersonService = new HrPersonService(personRepository, cryptoService, new IdentifierNormalizer());
        employmentRepository = new JdbcEmploymentRepository(dataSource);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (conn != null && !conn.isClosed()) conn.close();
    }

    // ==================== FIXTURE HELPERS ====================

    private void seedTenant(UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) VALUES (?, 'Test Tenant', ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "t-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    private UUID seedLegalEntity(UUID tenantId, String code) throws Exception {
        UUID legalEntityId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO legal_entities (id, tenant_id, code, name, registered_country_code, statutory_country_code, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'SA', 'SA', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, legalEntityId);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, "Legal Entity " + code);
            ps.executeUpdate();
        }
        return legalEntityId;
    }

    private void setTenant(UUID tenantId) throws Exception {
        try (Statement s = conn.createStatement()) {
            s.execute("SET app.tenant_id = '" + tenantId + "'");
        }
    }

    private record PersistedRow(String firstName, String lastName, String displayName, String employmentType) {}

    private PersistedRow readPersistedRow(UUID tenantId, UUID employmentId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT first_name, last_name, display_name, employment_type FROM hr_employees "
                + "WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new PersistedRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4));
            }
        }
    }

    // ==================== TESTS ====================

    @Test
    void canonicalWriteProjectsRealPersonNamesInsteadOfPlaceholders() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID legalEntityId = seedLegalEntity(tenantId, "LE-" + tenantId.toString().substring(0, 8));
        HrPerson person = hrPersonService.createPerson(tenantId, "Salman", null, "Al-Otaibi");

        Employment employment = new Employment(
                UUID.randomUUID(), tenantId, person.id(), legalEntityId,
                "EMP-" + tenantId.toString().substring(0, 8),
                "FULL_TIME", EmploymentStatus.DRAFT,
                LocalDate.of(2026, 9, 1), null, null, 0L);

        setTenant(tenantId);
        employmentRepository.saveEmployment(employment);

        PersistedRow row = readPersistedRow(tenantId, employment.id());
        assertThat(row).isNotNull();
        // The defect persisted literal 'Test'/'Employee'/'Test Employee' — frozen contract: real identity.
        assertThat(row.firstName()).isEqualTo("Salman");
        assertThat(row.lastName()).isEqualTo("Al-Otaibi");
        assertThat(row.displayName()).isEqualTo("Salman Al-Otaibi");
        assertThat(row.displayName()).isNotEqualTo("Test Employee");
    }

    @Test
    void canonicalWritePersistsRealClassificationInsteadOfHardcodedFullTime() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID legalEntityId = seedLegalEntity(tenantId, "LE-" + tenantId.toString().substring(0, 8));
        HrPerson person = hrPersonService.createPerson(tenantId, "Noura", null, "Al-Qahtani");

        Employment employment = new Employment(
                UUID.randomUUID(), tenantId, person.id(), legalEntityId,
                "EMP-" + tenantId.toString().substring(0, 8),
                "PART_TIME", EmploymentStatus.DRAFT,
                LocalDate.of(2026, 9, 1), null, null, 0L);

        setTenant(tenantId);
        employmentRepository.saveEmployment(employment);

        PersistedRow row = readPersistedRow(tenantId, employment.id());
        assertThat(row).isNotNull();
        // The defect persisted literal 'FULL_TIME' regardless of classification — frozen contract: real value.
        assertThat(row.employmentType()).isEqualTo("PART_TIME");
    }

    @Test
    void canonicalWriteFailsClosedWhenCanonicalPersonIsMissing() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID legalEntityId = seedLegalEntity(tenantId, "LE-" + tenantId.toString().substring(0, 8));
        UUID missingPersonId = UUID.randomUUID();

        Employment employment = new Employment(
                UUID.randomUUID(), tenantId, missingPersonId, legalEntityId,
                "EMP-" + tenantId.toString().substring(0, 8),
                "FULL_TIME", EmploymentStatus.DRAFT,
                LocalDate.of(2026, 9, 1), null, null, 0L);

        setTenant(tenantId);
        assertThatThrownBy(() -> employmentRepository.saveEmployment(employment))
                .isInstanceOf(Exception.class)
                .hasMessageContaining(missingPersonId.toString());

        // Fail-closed: no placeholder row may survive.
        assertThat(readPersistedRow(tenantId, employment.id())).isNull();
    }

    @Test
    void v2CreationEndToEndPersistsRealIdentity() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID legalEntityId = seedLegalEntity(tenantId, "LE-" + tenantId.toString().substring(0, 8));
        HrPerson person = hrPersonService.createPerson(tenantId, "Fahad", null, "Al-Harbi");

        JdbcEmploymentCommandService commandService = new JdbcEmploymentCommandService(employmentRepository);
        HrEmploymentV2Service v2Service = new HrEmploymentV2Service(
                employmentRepository, commandService,
                new JdbcHrPersonRepository(dataSource, new InMemoryTestCryptoService()));

        CreateEmploymentRequest request = new CreateEmploymentRequest(
                person.id(), legalEntityId,
                "EMP-V2-" + tenantId.toString().substring(0, 8),
                LocalDate.of(2026, 9, 15),
                "SA",
                "CONTRACT");

        setTenant(tenantId);
        EmploymentResponse response = v2Service.create(tenantId, request);

        PersistedRow row = readPersistedRow(tenantId, response.employmentId());
        assertThat(row).isNotNull();
        assertThat(row.firstName()).isEqualTo("Fahad");
        assertThat(row.lastName()).isEqualTo("Al-Harbi");
        assertThat(row.employmentType()).isEqualTo("CONTRACT");
    }

    // ==================== TEST SUPPORT ====================

    /**
     * In-memory platform cryptography for tests — AES-256-GCM with fixed
     * test keys. Mirrors the proven harness used by
     * HrPersonServiceBehaviorIntegrationTest. Never used outside test scope.
     */
    static final class InMemoryTestCryptoService implements PlatformCryptographyService {
        private static final byte[] ENC_KEY = "test-enc-key-32-bytes-padding-ok".getBytes(StandardCharsets.UTF_8);
        private static final byte[] BLIND_KEY = "test-blind-key-32-bytes-padding-!".substring(0, 32)
                .getBytes(StandardCharsets.UTF_8);

        @Override
        public EncryptedValue encrypt(UUID tenantId, String purpose, String plaintext) {
            try {
                javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
                javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(ENC_KEY, "AES");
                byte[] nonce = new byte[12];
                new java.security.SecureRandom().nextBytes(nonce);
                javax.crypto.spec.GCMParameterSpec params = new javax.crypto.spec.GCMParameterSpec(128, nonce);
                cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, params);
                cipher.updateAAD((tenantId + "|" + purpose + "|v1").getBytes(StandardCharsets.UTF_8));
                byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
                byte[] combined = new byte[nonce.length + ct.length];
                System.arraycopy(nonce, 0, combined, 0, nonce.length);
                System.arraycopy(ct, 0, combined, nonce.length, ct.length);
                return new EncryptedValue(
                        "enc:v1:" + java.util.Base64.getEncoder().encodeToString(combined),
                        "v1",
                        "AES-256-GCM");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String decrypt(UUID tenantId, String purpose, EncryptedValue value) {
            try {
                String payload = value.ciphertext();
                if (!payload.startsWith("enc:v1:")) throw new IllegalStateException("bad prefix");
                byte[] combined = java.util.Base64.getDecoder().decode(payload.substring(7));
                byte[] nonce = new byte[12];
                byte[] ct = new byte[combined.length - 12];
                System.arraycopy(combined, 0, nonce, 0, 12);
                System.arraycopy(combined, 12, ct, 0, ct.length);
                javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
                javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(ENC_KEY, "AES");
                cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec,
                        new javax.crypto.spec.GCMParameterSpec(128, nonce));
                cipher.updateAAD((tenantId + "|" + purpose + "|v1").getBytes(StandardCharsets.UTF_8));
                return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new IllegalStateException("decrypt failed (AAD mismatch?)", e);
            }
        }

        @Override
        public BlindIndex blindIndex(UUID tenantId, String purpose, String normalizedValue) {
            try {
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(new javax.crypto.spec.SecretKeySpec(BLIND_KEY, "HmacSHA256"));
                mac.update((tenantId + "|" + purpose + "|").getBytes(StandardCharsets.UTF_8));
                byte[] hash = mac.doFinal(normalizedValue.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder(hash.length * 2);
                for (byte b : hash) {
                    hex.append(String.format("%02x", b));
                }
                return new BlindIndex(hex.toString(), "v1", "HMAC-SHA-256");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
