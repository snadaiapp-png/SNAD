package com.sanad.platform.hr.identity;

import com.sanad.platform.security.crypto.PlatformCryptographyService;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WS2 Task 1B — Behavioral Contract (RED baseline).
 *
 * <p>This test expresses the FINAL required behavior of the HR Person
 * service. With the current production skeletons (Cycle 2), every
 * behavioral test fails because the production methods throw
 * {@link UnsupportedOperationException}. When Cycle 4 GREEN implements
 * the production methods, the <strong>exact same</strong> assertions
 * must pass — the contract is FROZEN.</p>
 */
class HrPersonServiceBehaviorIntegrationTest {

    private Connection conn;
    private DriverManagerDataSource dataSource;
    private static String ISOLATED_URL;
    private static final String DB_URL = System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

    private HrPersonService hrPersonService;
    private IdentifierNormalizer identifierNormalizer;

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
                .javaMigrations(new com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities())
                .baselineOnMigrate(true).cleanDisabled(false).validateOnMigrate(false).load();
        flyway.clean();
        flyway.migrate();
        conn = dataSource.getConnection();
        conn.setAutoCommit(true);

        PlatformCryptographyService cryptoService = inMemoryTestCryptoService();
        JdbcHrPersonRepository repository = new JdbcHrPersonRepository(dataSource, cryptoService);
        identifierNormalizer = new IdentifierNormalizer();
        hrPersonService = new HrPersonService(repository, cryptoService, identifierNormalizer);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (conn != null && !conn.isClosed()) conn.close();
    }

    private PlatformCryptographyService inMemoryTestCryptoService() {
        return new InMemoryTestCryptoService();
    }

    private void seedTenant(UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) VALUES (?, 'Test Tenant', ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "t-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    private UUID seedUser(UUID tenantId, String email) throws Exception {
        UUID userId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (id, tenant_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, 'Test User', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, userId);
            ps.setObject(2, tenantId);
            ps.setString(3, email);
            ps.executeUpdate();
        }
        return userId;
    }

    private void setTenant(UUID tenantId) throws Exception {
        try (Statement s = conn.createStatement()) {
            s.execute("SET app.tenant_id = '" + tenantId + "'");
        }
    }

    private UUID seedPerson(UUID tenantId, UUID userId, String first, String last) throws Exception {
        UUID personId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_people (id, tenant_id, user_id, first_name, last_name, display_name, version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, 0, NOW(), NOW())")) {
            ps.setObject(1, personId);
            ps.setObject(2, tenantId);
            if (userId != null) ps.setObject(3, userId); else ps.setNull(3, java.sql.Types.OTHER);
            ps.setString(4, first);
            ps.setString(5, last);
            ps.setString(6, first + " " + last);
            ps.executeUpdate();
        }
        return personId;
    }

    @Test
    void normalizer_trimsAndUpperCasesIdentifierType() {
        assertThat(identifierNormalizer.normalizeIdentifierType("  national_id  "))
                .isEqualTo("NATIONAL_ID");
    }

    @Test
    void normalizer_uppercasesNonNormalizedCountryCode() {
        assertThat(identifierNormalizer.normalizeCountryCode(" sa "))
                .isEqualTo("SA");
    }

    @Test
    void normalizer_preservesNullCountryCode() {
        assertThat(identifierNormalizer.normalizeCountryCode(null))
                .isNull();
    }

    @Test
    void normalizer_trimsPlaintextValue() {
        assertThat(identifierNormalizer.normalizeValue(" 1234567890 "))
                .isEqualTo("1234567890");
    }

    @Test
    void createPerson_returnsPersistedPersonWithCorrectFields() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);

        HrPerson person = hrPersonService.createPerson(tenantId, "Alice", null, "Smith");

        assertThat(person.id()).isNotNull();
        assertThat(person.tenantId()).isEqualTo(tenantId);
        assertThat(person.firstName()).isEqualTo("Alice");
        assertThat(person.middleName()).isNull();
        assertThat(person.lastName()).isEqualTo("Smith");
        assertThat(person.displayName()).isEqualTo("Alice Smith");
        assertThat(person.userId()).isNull();
        assertThat(person.version()).isZero();

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT tenant_id, user_id, first_name, middle_name, last_name, display_name, version " +
                "FROM hr_people WHERE id = ?")) {
            ps.setObject(1, person.id());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getObject("tenant_id")).isEqualTo(tenantId);
                assertThat(rs.getObject("user_id")).isNull();
                assertThat(rs.getString("first_name")).isEqualTo("Alice");
                assertThat(rs.getString("middle_name")).isNull();
                assertThat(rs.getString("last_name")).isEqualTo("Smith");
                assertThat(rs.getString("display_name")).isEqualTo("Alice Smith");
                assertThat(rs.getLong("version")).isZero();
            }
        }
    }

    @Test
    void linkUser_setsUserIdOnExistingPerson() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID userId = seedUser(tenantId, "link@snad.test");
        UUID personId = seedPerson(tenantId, null, "Link", "Test");

        hrPersonService.linkUser(tenantId, personId, userId);

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT user_id FROM hr_people WHERE id = ? AND tenant_id = ?")) {
            ps.setObject(1, personId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getObject("user_id")).isEqualTo(userId);
            }
        }
    }

    @Test
    void addIdentifier_persistsEncryptedIdentifierWithCanonicalColumns() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID personId = seedPerson(tenantId, null, "Add", "Ident");

        PersonIdentifier identifier = hrPersonService.addIdentifier(
                tenantId, personId, "NATIONAL_ID", "SA", "1234567890");

        assertThat(identifier.id()).isNotNull();
        assertThat(identifier.tenantId()).isEqualTo(tenantId);
        assertThat(identifier.personId()).isEqualTo(personId);
        assertThat(identifier.identifierType()).isEqualTo("NATIONAL_ID");
        assertThat(identifier.issuingCountryCode()).isEqualTo("SA");
        assertThat(identifier.status()).isEqualTo("ACTIVE");

        assertThat(identifier.identifierCiphertext()).isNotNull().isNotEmpty();
        assertThat(identifier.blindIndex()).isNotNull().isNotEmpty();
        assertThat(identifier.encryptionKeyVersion()).isNotNull().isNotEmpty();
        assertThat(identifier.blindIndexKeyVersion()).isNotNull().isNotEmpty();

        assertThat(identifier.identifierCiphertext())
                .as("ciphertext must NOT equal plaintext (encryption applied)")
                .isNotEqualTo("1234567890");
        assertThat(identifier.blindIndex())
                .as("blind_index must NOT equal plaintext (HMAC applied)")
                .isNotEqualTo("1234567890");

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT identifier_ciphertext, blind_index, encryption_key_version, " +
                "blind_index_key_version, status, identifier_type, issuing_country_code " +
                "FROM hr_person_identifiers WHERE id = ?")) {
            ps.setObject(1, identifier.id());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("identifier_ciphertext"))
                        .isEqualTo(identifier.identifierCiphertext());
                assertThat(rs.getString("blind_index"))
                        .isEqualTo(identifier.blindIndex());
                assertThat(rs.getString("encryption_key_version"))
                        .isEqualTo(identifier.encryptionKeyVersion());
                assertThat(rs.getString("blind_index_key_version"))
                        .isEqualTo(identifier.blindIndexKeyVersion());
                assertThat(rs.getString("status")).isEqualTo("ACTIVE");
                assertThat(rs.getString("identifier_type")).isEqualTo("NATIONAL_ID");
                assertThat(rs.getString("issuing_country_code")).isEqualTo("SA");

                assertThat(rs.getString("identifier_ciphertext"))
                        .as("DB-stored ciphertext must NOT equal plaintext")
                        .isNotEqualTo("1234567890");
                assertThat(rs.getString("blind_index"))
                        .as("DB-stored blind_index must NOT equal plaintext")
                        .isNotEqualTo("1234567890");
            }
        }
    }

    @Test
    void addIdentifier_rejectsDuplicateActive() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID personA = seedPerson(tenantId, null, "Dup", "A");
        UUID personB = seedPerson(tenantId, null, "Dup", "B");

        hrPersonService.addIdentifier(tenantId, personA, "NATIONAL_ID", "SA", "1234567890");

        assertThatThrownBy(() ->
                hrPersonService.addIdentifier(tenantId, personB, "NATIONAL_ID", "SA", "1234567890"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void addIdentifier_normalizesInputBeforeUniquenessCheck() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID personA = seedPerson(tenantId, null, "Norm", "A");
        UUID personB = seedPerson(tenantId, null, "Norm", "B");

        hrPersonService.addIdentifier(tenantId, personA,
                "  national_id  ", "  sa  ", "  1234567890  ");

        assertThatThrownBy(() ->
                hrPersonService.addIdentifier(tenantId, personB,
                        "NATIONAL_ID", "SA", "1234567890"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void findExactIdentifierMatch_returnsActiveMatch() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID personId = seedPerson(tenantId, null, "Find", "Match");

        hrPersonService.addIdentifier(tenantId, personId, "NATIONAL_ID", "SA", "1234567890");

        Optional<PersonIdentifier> found = hrPersonService.findExactIdentifierMatch(
                tenantId, "NATIONAL_ID", "SA", "1234567890");

        assertThat(found).isPresent();
        assertThat(found.get().personId()).isEqualTo(personId);
        assertThat(found.get().identifierType()).isEqualTo("NATIONAL_ID");
        assertThat(found.get().issuingCountryCode()).isEqualTo("SA");
        assertThat(found.get().status()).isEqualTo("ACTIVE");
    }

    @Test
    void findExactIdentifierMatch_returnsEmptyForNonExistentValue() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID personId = seedPerson(tenantId, null, "Find", "Empty");

        hrPersonService.addIdentifier(tenantId, personId, "NATIONAL_ID", "SA", "1234567890");

        Optional<PersonIdentifier> found = hrPersonService.findExactIdentifierMatch(
                tenantId, "NATIONAL_ID", "SA", "9999999999");

        assertThat(found).isEmpty();
    }

    @Test
    void findExactIdentifierMatch_returnsEmptyForWrongTenant() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantA);
        seedTenant(tenantB);
        setTenant(tenantA);
        UUID personInA = seedPerson(tenantA, null, "Find", "TenantA");

        hrPersonService.addIdentifier(tenantA, personInA, "NATIONAL_ID", "SA", "1234567890");

        Optional<PersonIdentifier> found = hrPersonService.findExactIdentifierMatch(
                tenantB, "NATIONAL_ID", "SA", "1234567890");

        assertThat(found).isEmpty();
    }

    @Test
    void findExactIdentifierMatch_returnsEmptyForWrongType() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID personId = seedPerson(tenantId, null, "Find", "Type");

        hrPersonService.addIdentifier(tenantId, personId, "NATIONAL_ID", "SA", "1234567890");

        Optional<PersonIdentifier> found = hrPersonService.findExactIdentifierMatch(
                tenantId, "PASSPORT", "SA", "1234567890");

        assertThat(found).isEmpty();
    }

    @Test
    void findExactIdentifierMatch_returnsEmptyForWrongIssuer() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID personId = seedPerson(tenantId, null, "Find", "Issuer");

        hrPersonService.addIdentifier(tenantId, personId, "NATIONAL_ID", "SA", "1234567890");

        Optional<PersonIdentifier> found = hrPersonService.findExactIdentifierMatch(
                tenantId, "NATIONAL_ID", "AE", "1234567890");

        assertThat(found).isEmpty();
    }

    @Test
    void findExactIdentifierMatch_excludesExpiredIdentifier() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        setTenant(tenantId);
        UUID personId = seedPerson(tenantId, null, "Find", "Expired");

        PersonIdentifier added = hrPersonService.addIdentifier(
                tenantId, personId, "NATIONAL_ID", "SA", "1234567890");

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE hr_person_identifiers SET status = 'EXPIRED' WHERE id = ?")) {
            ps.setObject(1, added.id());
            ps.executeUpdate();
        }

        Optional<PersonIdentifier> found = hrPersonService.findExactIdentifierMatch(
                tenantId, "NATIONAL_ID", "SA", "1234567890");

        assertThat(found).isEmpty();
    }

    @Test
    void crypto_samePlaintextProducesSameBlindIndex() {
        UUID tenantA = UUID.randomUUID();
        PlatformCryptographyService crypto = inMemoryTestCryptoService();

        var idx1 = crypto.blindIndex(tenantA, "HR_PERSON_IDENTIFIER:NATIONAL_ID:SA", "1234567890");
        var idx2 = crypto.blindIndex(tenantA, "HR_PERSON_IDENTIFIER:NATIONAL_ID:SA", "1234567890");

        assertThat(idx1.value())
                .as("same tenant + same plaintext -> same blind_index (deterministic)")
                .isEqualTo(idx2.value());
    }

    @Test
    void crypto_differentTenantProducesDifferentBlindIndex() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        PlatformCryptographyService crypto = inMemoryTestCryptoService();

        var idxA = crypto.blindIndex(tenantA, "HR_PERSON_IDENTIFIER:NATIONAL_ID:SA", "1234567890");
        var idxB = crypto.blindIndex(tenantB, "HR_PERSON_IDENTIFIER:NATIONAL_ID:SA", "1234567890");

        assertThat(idxB.value())
                .as("different tenant -> different blind_index (tenant-scoped HMAC)")
                .isNotEqualTo(idxA.value());
    }

    @Test
    void crypto_differentPurposeProducesDifferentBlindIndex() {
        UUID tenantId = UUID.randomUUID();
        PlatformCryptographyService crypto = inMemoryTestCryptoService();

        var idx1 = crypto.blindIndex(tenantId, "HR_PERSON_IDENTIFIER:NATIONAL_ID:SA", "1234567890");
        var idx2 = crypto.blindIndex(tenantId, "HR_PERSON_IDENTIFIER:PASSPORT:SA", "1234567890");

        assertThat(idx2.value())
                .as("different purpose -> different blind_index (purpose-bound HMAC)")
                .isNotEqualTo(idx1.value());
    }

    @Test
    void crypto_samePlaintextProducesDifferentCiphertext() {
        UUID tenantId = UUID.randomUUID();
        PlatformCryptographyService crypto = inMemoryTestCryptoService();

        var ct1 = crypto.encrypt(tenantId, "HR_PERSON_IDENTIFIER:NATIONAL_ID:SA", "1234567890");
        var ct2 = crypto.encrypt(tenantId, "HR_PERSON_IDENTIFIER:NATIONAL_ID:SA", "1234567890");

        assertThat(ct1.ciphertext())
                .as("same plaintext -> different ciphertext (randomized GCM nonce)")
                .isNotEqualTo(ct2.ciphertext());

        assertThat(crypto.decrypt(tenantId, "HR_PERSON_IDENTIFIER:NATIONAL_ID:SA", ct1))
                .isEqualTo("1234567890");
        assertThat(crypto.decrypt(tenantId, "HR_PERSON_IDENTIFIER:NATIONAL_ID:SA", ct2))
                .isEqualTo("1234567890");
    }

    @Test
    void crypto_wrongTenantDecryptionRejected() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        PlatformCryptographyService crypto = inMemoryTestCryptoService();

        var ct = crypto.encrypt(tenantA, "HR_PERSON_IDENTIFIER:NATIONAL_ID:SA", "1234567890");

        assertThatThrownBy(() -> crypto.decrypt(tenantB, "HR_PERSON_IDENTIFIER:NATIONAL_ID:SA", ct))
                .as("wrong-tenant decryption must fail (AAD mismatch)")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void crypto_wrongPurposeDecryptionRejected() {
        UUID tenantId = UUID.randomUUID();
        PlatformCryptographyService crypto = inMemoryTestCryptoService();

        var ct = crypto.encrypt(tenantId, "HR_PERSON_IDENTIFIER:NATIONAL_ID:SA", "1234567890");

        assertThatThrownBy(() -> crypto.decrypt(tenantId, "HR_PERSON_IDENTIFIER:PASSPORT:SA", ct))
                .as("wrong-purpose decryption must fail (AAD mismatch)")
                .isInstanceOf(RuntimeException.class);
    }

    static final class InMemoryTestCryptoService implements PlatformCryptographyService {
        private static final byte[] ENC_KEY = "test-enc-key-32-bytes-padding-ok".getBytes();
        private static final byte[] BLIND_KEY = "test-blind-key-32-bytes-padding-!".substring(0, 32).getBytes();

        @Override
        public com.sanad.platform.security.crypto.EncryptedValue encrypt(
                UUID tenantId, String purpose, String plaintext) {
            try {
                var cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
                var keySpec = new javax.crypto.spec.SecretKeySpec(ENC_KEY, "AES");
                byte[] nonce = new byte[12];
                new java.security.SecureRandom().nextBytes(nonce);
                var params = new javax.crypto.spec.GCMParameterSpec(128, nonce);
                cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, params);
                cipher.updateAAD((tenantId + "|" + purpose + "|v1").getBytes());
                byte[] ct = cipher.doFinal(plaintext.getBytes());
                byte[] combined = new byte[nonce.length + ct.length];
                System.arraycopy(nonce, 0, combined, 0, nonce.length);
                System.arraycopy(ct, 0, combined, nonce.length, ct.length);
                return new com.sanad.platform.security.crypto.EncryptedValue(
                        "enc:v1:" + java.util.Base64.getEncoder().encodeToString(combined),
                        "v1",
                        "AES-256-GCM");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String decrypt(UUID tenantId, String purpose,
                              com.sanad.platform.security.crypto.EncryptedValue value) {
            try {
                String payload = value.ciphertext();
                if (!payload.startsWith("enc:v1:")) throw new RuntimeException("bad prefix");
                byte[] combined = java.util.Base64.getDecoder().decode(payload.substring(7));
                byte[] nonce = new byte[12];
                byte[] ct = new byte[combined.length - 12];
                System.arraycopy(combined, 0, nonce, 0, 12);
                System.arraycopy(combined, 12, ct, 0, ct.length);
                var cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
                var keySpec = new javax.crypto.spec.SecretKeySpec(ENC_KEY, "AES");
                cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec,
                        new javax.crypto.spec.GCMParameterSpec(128, nonce));
                cipher.updateAAD((tenantId + "|" + purpose + "|v1").getBytes());
                return new String(cipher.doFinal(ct));
            } catch (Exception e) {
                throw new RuntimeException("decrypt failed (AAD mismatch?)", e);
            }
        }

        @Override
        public com.sanad.platform.security.crypto.BlindIndex blindIndex(
                UUID tenantId, String purpose, String normalizedValue) {
            try {
                var mac = javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(new javax.crypto.spec.SecretKeySpec(BLIND_KEY, "HmacSHA256"));
                mac.update((tenantId + "|" + purpose + "|").getBytes());
                byte[] hash = mac.doFinal(normalizedValue.getBytes());
                StringBuilder hex = new StringBuilder(hash.length * 2);
                for (byte b : hash) {
                    hex.append(String.format("%02x", b));
                }
                return new com.sanad.platform.security.crypto.BlindIndex(
                        hex.toString(),
                        "v1",
                        "HMAC-SHA-256");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
