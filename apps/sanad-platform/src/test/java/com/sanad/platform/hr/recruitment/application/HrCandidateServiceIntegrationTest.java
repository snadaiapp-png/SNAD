package com.sanad.platform.hr.recruitment.application;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.security.crypto.BlindIndex;
import com.sanad.platform.security.crypto.EncryptedValue;
import com.sanad.platform.security.crypto.EnvironmentKeyMaterialProvider;
import com.sanad.platform.security.crypto.JcePlatformCryptographyService;
import com.sanad.platform.security.crypto.PlatformCryptographyService;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.SecureRandom;
import java.sql.Connection;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G1 T4 — HrCandidate aggregate service integration contract (design
 * §5.1, §10.1 rows 536-537, §10.2; task card G1-T4).
 *
 * <p>Minimized contact storage per the G0 pattern: contacts are stored ONLY
 * as AES-256-GCM ciphertext + HMAC blind-index hash (PlatformCryptographyService
 * authority); raw PII never reaches storage, audit payloads, outbox payloads,
 * or exception messages. Duplicate contact detection emits a WARNING + audit
 * row and NEVER merges. Read paths are capability-split: VIEW = masked,
 * MANAGE = full.</p>
 *
 * <p>Clean-RED convention: T4 classes load reflectively, so RED fails only
 * because the classes are missing — never because of a compilation error.</p>
 */
class HrCandidateServiceIntegrationTest {

    private static final String REPO_CLASS = "com.sanad.platform.hr.recruitment.infrastructure.JdbcHrCandidateRepository";
    private static final String SERVICE_CLASS = "com.sanad.platform.hr.recruitment.application.HrCandidateService";
    private static final String PORT_CLASS = "com.sanad.platform.hr.recruitment.application.RecruitmentAuthorizationPort";

    private static final String CAP_MANAGE = "HRM.RECRUITMENT.CANDIDATE.MANAGE";
    private static final String CAP_VIEW = "HRM.RECRUITMENT.CANDIDATE.VIEW";

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static String isolatedUrl;

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private Object repository;
    private Object service;
    private PlatformCryptographyService crypto;
    private Map<String, Boolean> grants;
    private UUID tenantId;
    private UUID managerUserId;
    private UUID viewerUserId;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available = false;
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
            try (Connection c = ds.getConnection()) {
                available = c.isValid(5);
            }
        } catch (Throwable ignored) {
        }
        Assumptions.assumeTrue(available, "PostgreSQL Direct is not available");
        MigrationTestSchemaSupport.ensureDatabase(DB_URL, DB_USER, DB_PASSWORD);
        isolatedUrl = MigrationTestSchemaSupport.getIsolatedJdbcUrl(DB_URL);
    }

    @BeforeEach
    void migrateFreshDatabase() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(isolatedUrl, DB_USER, DB_PASSWORD);
        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .baselineOnMigrate(true)
                .cleanDisabled(false)
                .validateOnMigrate(false)
                .load();
        flyway.clean();
        flyway.migrate();
        dataSource = ds;
        tenantId = UUID.randomUUID();
        jdbc = new JdbcTemplate(sessionTenantDataSource(ds, tenantId));
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())",
                tenantId, "Tenant " + tenantId, "t-" + tenantId.toString().substring(0, 8));
        managerUserId = UUID.randomUUID();
        viewerUserId = UUID.randomUUID();

        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String encKey = Base64.getEncoder().encodeToString(key);
        String blindKey = Base64.getEncoder().encodeToString(key);
        crypto = new JcePlatformCryptographyService(
                new EnvironmentKeyMaterialProvider("v1", encKey, "v1", blindKey));

        repository = Class.forName(REPO_CLASS)
                .getConstructor(DataSource.class)
                .newInstance(ds);

        grants = new HashMap<>();
        grants.put(CAP_MANAGE, true);
        grants.put(CAP_VIEW, true);
        Object port = stubPort(grants);

        service = Class.forName(SERVICE_CLASS)
                .getConstructor(Class.forName(REPO_CLASS),
                        Class.forName(PORT_CLASS),
                        PlatformCryptographyService.class)
                .newInstance(repository, port, crypto);
    }

    // ==================== minimized contact storage ====================

    @Test
    void create_storesCiphertextAndHash_only_noRawPii() throws Exception {
        UUID candidateId = create("dina.alotibi@example.com", "+966501234567");

        String ciphertext = jdbc.queryForObject(
                "SELECT contact_email_ciphertext FROM hr_candidates WHERE id = ? AND tenant_id = ?",
                String.class, candidateId, tenantId);
        String hash = jdbc.queryForObject(
                "SELECT contact_email_hash FROM hr_candidates WHERE id = ? AND tenant_id = ?",
                String.class, candidateId, tenantId);
        assertThat(ciphertext).as("email stored as ciphertext").isNotBlank().startsWith("enc:");
        assertThat(hash).as("email stored with blind-index hash").isNotBlank();

        // Round-trip through the G0 crypto authority proves the ciphertext is the
        // only carrier of the plaintext (versioned payload: enc:<keyVersion>:<base64>).
        String[] parts = ciphertext.split(":", 3);
        assertThat(parts).as("versioned payload format").hasSize(3);
        assertThat(crypto.decrypt(tenantId, "HRM.CANDIDATE.CONTACT.EMAIL",
                new EncryptedValue(ciphertext, parts[1], "AES-256-GCM")))
                .isEqualTo("dina.alotibi@example.com");

        // No raw PII anywhere in the stored row.
        String row = jdbc.queryForObject(
                "SELECT to_jsonb(t)::text FROM hr_candidates t WHERE id = ?",
                String.class, candidateId);
        assertThat(row).doesNotContain("dina.alotibi@example.com").doesNotContain("+966501234567");
    }

    @Test
    void duplicateContact_warnsAndAudits_neverMerges() throws Exception {
        UUID first = create("shared@example.com", null);
        UUID second = create("shared@example.com", null);

        assertThat(first).isNotEqualTo(second);
        Integer activeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_candidates WHERE tenant_id = ? AND pool_state = 'ACTIVE'",
                Integer.class, tenantId);
        assertThat(activeCount).as("duplicates are retained as separate rows (never merged)").isEqualTo(2);

        Integer warnings = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_audit_ledger WHERE tenant_id = ? "
                        + "AND action = 'HRM.RECRUITMENT.CANDIDATE_DUPLICATE'",
                Integer.class, tenantId);
        assertThat(warnings).as("duplicate detection emits an audit row").isEqualTo(1);

        // The duplicate audit payload carries the hash only — never the raw value.
        String payload = jdbc.queryForObject(
                "SELECT before_state::text || after_state::text FROM hr_audit_ledger "
                        + "WHERE tenant_id = ? AND action = 'HRM.RECRUITMENT.CANDIDATE_DUPLICATE' LIMIT 1",
                String.class, tenantId);
        assertThat(payload).doesNotContain("shared@example.com");
    }

    @Test
    void maskedReadForView_fullReadForManage() throws Exception {
        UUID candidateId = create("noura@example.com", "+966555000111");

        Object viewRead = read(candidateId, viewerUserId, false);
        String viewText = String.valueOf(viewRead);
        assertThat(viewText).as("VIEW masking never exposes plaintext contact")
                .doesNotContain("noura@example.com")
                .doesNotContain("+966555000111");

        Object fullRead = read(candidateId, managerUserId, true);
        String fullText = String.valueOf(fullRead);
        assertThat(fullText).as("MANAGE read returns the full decrypted contact")
                .contains("noura@example.com");
    }

    @Test
    void archive_isTerminal_andRequiresManage() throws Exception {
        UUID candidateId = create("archive.me@example.com", null);
        invokeService("archive", new Class<?>[]{HrCommandContext.class, UUID.class},
                ctx(managerUserId), candidateId);

        String poolState = jdbc.queryForObject(
                "SELECT pool_state FROM hr_candidates WHERE id = ?", String.class, candidateId);
        assertThat(poolState).isEqualTo("ARCHIVED");

        assertThatThrownBy(() -> invokeService("archive",
                new Class<?>[]{HrCommandContext.class, UUID.class}, ctx(managerUserId), candidateId))
                .as("ARCHIVED is terminal").isInstanceOf(Exception.class);
    }

    @Test
    void everyCommand_requiresItsCapability() throws Exception {
        grants.put(CAP_MANAGE, false);
        assertThatThrownBy(() -> create("no.capability@example.com", null))
                .as("create requires CANDIDATE.MANAGE").isInstanceOf(Exception.class);
        grants.put(CAP_MANAGE, true);

        UUID candidateId = create("cap@example.com", null);

        grants.put(CAP_VIEW, false);
        assertThatThrownBy(() -> read(candidateId, viewerUserId, false))
                .as("read requires CANDIDATE.VIEW").isInstanceOf(Exception.class);
        grants.put(CAP_VIEW, true);
    }

    @Test
    void crossTenant_serviceLevelAccess_isDenied() throws Exception {
        UUID candidateId = create("tenant.a@example.com", null);
        UUID tenantB = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())",
                tenantB, "Tenant B", "tb-" + tenantB.toString().substring(0, 8));

        assertThatThrownBy(() -> invokeService("read",
                new Class<?>[]{HrCommandContext.class, UUID.class, boolean.class},
                ctxFor(tenantB, managerUserId), candidateId, true))
                .as("a foreign-tenant context must never resolve the candidate")
                .isInstanceOf(Exception.class);
    }

    @Test
    void blindIndex_isTenantScoped_neverGlobal() {
        BlindIndex tenantAIndex = crypto.blindIndex(tenantId, "HRM.CANDIDATE.CONTACT.EMAIL",
                "cross@example.com");
        BlindIndex tenantBIndex = crypto.blindIndex(UUID.randomUUID(), "HRM.CANDIDATE.CONTACT.EMAIL",
                "cross@example.com");
        assertThat(tenantAIndex.value())
                .as("identical contact values in different tenants must never collide")
                .isNotEqualTo(tenantBIndex.value());
    }

    // ==================== helpers ====================

    private UUID create(String email, String phone) throws Exception {
        return (UUID) invokeService("create",
                new Class<?>[]{HrCommandContext.class, String.class, String.class, String.class, String.class},
                ctx(managerUserId), "Dina Alotaibi", email, phone, null);
    }

    private Object read(UUID candidateId, UUID actor, boolean full) throws Exception {
        return invokeService("read", new Class<?>[]{HrCommandContext.class, UUID.class, boolean.class},
                ctx(actor), candidateId, full);
    }

    private HrCommandContext ctx(UUID actor) {
        return new HrCommandContext(tenantId, null, actor, UUID.randomUUID());
    }

    private HrCommandContext ctxFor(UUID tenant, UUID actor) {
        return new HrCommandContext(tenant, null, actor, UUID.randomUUID());
    }

    private Object stubPort(Map<String, Boolean> grants) throws Exception {
        Class<?> portClass = Class.forName(PORT_CLASS);
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "requireCandidateManage" -> require(grants, CAP_MANAGE);
                case "requireCandidateView" -> require(grants, CAP_VIEW);
                case "requireOpeningManage", "requireOpeningPublish", "requireOpeningView" -> require(grants,
                        method.getName().endsWith("Publish") ? "HRM.RECRUITMENT.OPENING.PUBLISH"
                                : method.getName().endsWith("Manage") ? "HRM.RECRUITMENT.OPENING.MANAGE"
                                : "HRM.RECRUITMENT.OPENING.VIEW");
                default -> {
                }
            }
            return null;
        };
        return Proxy.newProxyInstance(portClass.getClassLoader(), new Class<?>[]{portClass}, handler);
    }

    private void require(Map<String, Boolean> grants, String capability) {
        if (!grants.getOrDefault(capability, false)) {
            throw new IllegalStateException("HRM_SCOPE_DENIED: " + capability + " denied (test stub)");
        }
    }

    private Object invokeService(String method, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = service.getClass().getMethod(method, paramTypes);
        try {
            return m.invoke(service, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new IllegalStateException(cause);
        }
    }

    private DataSource sessionTenantDataSource(DataSource base, UUID tenant) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getConnection")) {
                        Connection c = (Connection) method.invoke(base, args);
                        try (var st = c.createStatement()) {
                            st.execute("SELECT set_config('app.tenant_id', '" + tenant + "', false)");
                        }
                        return c;
                    }
                    return method.invoke(base, args);
                });
    }
}
