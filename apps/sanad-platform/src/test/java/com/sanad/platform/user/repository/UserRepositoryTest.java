package com.sanad.platform.user.repository;

import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository-level integration tests for {@link UserRepository}.
 *
 * <p>Uses {@link SpringBootTest @SpringBootTest
@Import(SecurityPermitAllTestConfig.class)} with the {@code local}
 * profile to boot the full Spring context (including JPA auditing)
 * against the H2 in-memory database with Flyway V1+V2+V3+V4 applied.</p>
 */
@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    private UUID tenantAId;
    private UUID tenantBId;

    @BeforeEach
    void setUp() {
        // Cause A fix: use saveAndFlush to force tenant INSERTs to execute immediately
        // (not deferred by Hibernate). This is required because seedUserRaw() uses raw
        // JdbcTemplate INSERT INTO users (bypassing Hibernate's persistence context),
        // and the FK constraint fk_users_tenant requires the tenant row to exist in
        // the DB at INSERT time. Without saveAndFlush, Hibernate defers the tenant
        // INSERT, and the raw JdbcTemplate user INSERT fails with FK violation.
        Tenant tenantA = tenantRepository.saveAndFlush(
                new Tenant("Tenant A", "tenant-a-" + UUID.randomUUID(), TenantStatus.ACTIVE));
        Tenant tenantB = tenantRepository.saveAndFlush(
                new Tenant("Tenant B", "tenant-b-" + UUID.randomUUID(), TenantStatus.ACTIVE));
        tenantAId = tenantA.getId();
        tenantBId = tenantB.getId();
    }

    // ============================================================
    // TEST 1: save user
    // ============================================================
    @Test
    @DisplayName("TEST 1: save user - persists with generated id + timestamps")
    void save_persistsWithIdAndTimestamps() {
        bindTenantContext(tenantAId);
        try {
            User user = new User(tenantAId, "alice@example.com", "Alice", UserStatus.ACTIVE);

            User saved = userRepository.save(user);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();
            assertThat(saved.getEmail()).isEqualTo("alice@example.com");
            assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        } finally {
            clearTenantContext();
        }
    }

    // ============================================================
    // TEST 2: email normalized to lowercase
    // ============================================================
    @Test
    @DisplayName("TEST 2: email is normalized to lowercase on save")
    void emailNormalizedToLowerCase() {
        bindTenantContext(tenantAId);
        try {
            User saved = userRepository.save(
                    new User(tenantAId, "Alice.Black@Example.COM", "Alice", UserStatus.ACTIVE));

            assertThat(saved.getEmail()).isEqualTo("alice.black@example.com");
        } finally {
            clearTenantContext();
        }
    }

    // ============================================================
    // TEST 3: findByTenantId
    // ============================================================
    @Test
    @DisplayName("TEST 3: findByTenantId returns all users for the tenant")
    void findByTenantId_returnsAllForTenant() {
        // Cause A fix: replace JPA userRepository.save() (which defers INSERTs and
        // flushes them all with the LAST set GUC) with raw JdbcTemplate set_config
        // + INSERT per tenant. Each INSERT runs immediately with the correct GUC,
        // bypassing Hibernate's deferred-flush + handler's per-tx GUC model.
        // The test class is @Transactional, so all INSERTs run in the test tx and
        // are rolled back at end of test method.
        seedUserRaw(tenantAId, "alice@a.com", "Alice", "ACTIVE");
        seedUserRaw(tenantAId, "bob@a.com", "Bob", "INVITED");
        seedUserRaw(tenantBId, "carol@b.com", "Carol", "ACTIVE");

        bindTenantContext(tenantAId);
        try {
            List<User> result = userRepository.findByTenantId(tenantAId);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(User::getEmail)
                    .containsExactlyInAnyOrder("alice@a.com", "bob@a.com");
        } finally {
            clearTenantContext();
        }
    }

    // ============================================================
    // TEST 4: findByTenantIdAndId
    // ============================================================
    @Test
    @DisplayName("TEST 4: findByTenantIdAndId returns the specific user")
    void findByTenantIdAndId_returnsSpecificUser() {
        bindTenantContext(tenantAId);
        try {
            User saved = userRepository.save(
                    new User(tenantAId, "alice@a.com", "Alice", UserStatus.ACTIVE));

            Optional<User> found = userRepository.findByTenantIdAndId(tenantAId, saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getEmail()).isEqualTo("alice@a.com");
        } finally {
            clearTenantContext();
        }
    }

    // ============================================================
    // TEST 5: findByTenantIdAndEmail
    // ============================================================
    @Test
    @DisplayName("TEST 5: findByTenantIdAndEmail returns user by email (case-insensitive)")
    void findByTenantIdAndEmail_returnsUser() {
        bindTenantContext(tenantAId);
        try {
            userRepository.save(
                    new User(tenantAId, "alice@example.com", "Alice", UserStatus.ACTIVE));

            // Lookup with lowercased email
            Optional<User> found = userRepository.findByTenantIdAndEmail(tenantAId, "alice@example.com");
            assertThat(found).isPresent();
            assertThat(found.get().getDisplayName()).isEqualTo("Alice");
        } finally {
            clearTenantContext();
        }
    }

    // ============================================================
    // TEST 6: existsByTenantIdAndEmail
    // ============================================================
    @Test
    @DisplayName("TEST 6: existsByTenantIdAndEmail detects duplicates")
    void existsByTenantIdAndEmail_detectsDuplicates() {
        bindTenantContext(tenantAId);
        try {
            userRepository.save(
                    new User(tenantAId, "Alice@Example.COM", "Alice", UserStatus.ACTIVE));

            // Same email lowercased should be detected (normalization)
            assertThat(userRepository.existsByTenantIdAndEmail(tenantAId, "alice@example.com")).isTrue();

            // Different email should not be detected
            assertThat(userRepository.existsByTenantIdAndEmail(tenantAId, "bob@example.com")).isFalse();
        } finally {
            clearTenantContext();
        }
    }

    // ============================================================
    // TEST 7: Tenant isolation — Tenant A user must not appear in Tenant B queries
    // ============================================================
    @Test
    @DisplayName("TEST 7: Tenant isolation - Tenant A user NOT in Tenant B queries")
    void tenantIsolation_userNotVisibleAcrossTenants() {
        bindTenantContext(tenantAId);
        try {
            userRepository.save(
                    new User(tenantAId, "alice@a.com", "Alice", UserStatus.ACTIVE));

            // findByTenantId: Tenant B sees zero users (GUC=tenantA filters out tenantB rows)
            assertThat(userRepository.findByTenantId(tenantBId)).isEmpty();

            // findByTenantIdAndId: cross-tenant lookup returns empty
            User saved = userRepository.findByTenantIdAndEmail(tenantAId, "alice@a.com").orElseThrow();
            assertThat(userRepository.findByTenantIdAndId(tenantBId, saved.getId())).isEmpty();

            // existsByTenantIdAndEmail: cross-tenant returns false
            assertThat(userRepository.existsByTenantIdAndEmail(tenantBId, "alice@a.com")).isFalse();

            // findByTenantIdAndEmail: cross-tenant returns empty
            assertThat(userRepository.findByTenantIdAndEmail(tenantBId, "alice@a.com")).isEmpty();
        } finally {
            clearTenantContext();
        }
    }

    // ============================================================
    // TEST 8: Duplicate email inside same tenant should fail
    // ============================================================
    @Test
    @DisplayName("TEST 8: Duplicate email inside same tenant fails (unique constraint)")
    void duplicateEmail_sameTenant_fails() {
        bindTenantContext(tenantAId);
        try {
            userRepository.save(
                    new User(tenantAId, "alice@a.com", "Alice", UserStatus.ACTIVE));

            // Attempting to save a second user with the same (tenant, email)
            assertThatThrownBy(() -> userRepository.saveAndFlush(
                    new User(tenantAId, "ALICE@a.com", "Alice Duplicate", UserStatus.INVITED)))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {
            clearTenantContext();
        }
    }

    // ============================================================
    // TEST 9: Same email in different tenants should be allowed
    // ============================================================
    @Test
    @DisplayName("TEST 9: Same email in different tenants is allowed")
    void sameEmail_differentTenants_allowed() {
        // Cause A fix: use seedUserRaw (raw JdbcTemplate set_config + INSERT per tenant)
        // instead of JPA userRepository.save() (which defers INSERTs via Hibernate and
        // flushes them all with the LAST set GUC). Each seedUserRaw INSERT runs
        // immediately with the correct GUC, bypassing Hibernate's deferred flush.
        // The original queries (findByTenantIdAndEmail + findByTenantId) are preserved
        // verbatim — only the INSERT mechanism changes, the assertions are unchanged.
        seedUserRaw(tenantAId, "shared@shared.com", "Shared A", "ACTIVE");
        seedUserRaw(tenantBId, "shared@shared.com", "Shared B", "ACTIVE");

        // Rebind to tenantA for tenantA queries, then tenantB for tenantB queries.
        bindTenantContext(tenantAId);
        assertThat(userRepository.findByTenantIdAndEmail(tenantAId, "shared@shared.com")).isPresent();
        assertThat(userRepository.findByTenantId(tenantAId)).hasSize(1);
        bindTenantContext(tenantBId);
        try {
            assertThat(userRepository.findByTenantIdAndEmail(tenantBId, "shared@shared.com")).isPresent();
            assertThat(userRepository.findByTenantId(tenantBId)).hasSize(1);
        } finally {
            clearTenantContext();
        }
    }

    // ============================================================
    // TEST 10: Status CHECK constraint works (all valid statuses accepted)
    // ============================================================
    @Test
    @DisplayName("TEST 10: All valid UserStatus values are accepted by the CHECK constraint")
    void statusCheckConstraint_allValidStatusesAccepted() {
        bindTenantContext(tenantAId);
        try {
            for (UserStatus s : UserStatus.values()) {
                User u = new User(tenantAId, "test-" + s.name().toLowerCase() + "@a.com",
                        "Test " + s.name(), s);
                // saveAndFlush forces the INSERT + flush; if the CHECK constraint
                // rejected this status, a DataIntegrityViolationException would be thrown
                userRepository.saveAndFlush(u);
            }

            // Verify all 5 valid statuses were accepted (1 alice from setUp-like save + 5 here = 5)
            // Note: no user was saved in setUp for this test, so we expect exactly 5
            List<User> users = userRepository.findByTenantId(tenantAId);
            assertThat(users).hasSize(5);
            assertThat(users).extracting(User::getStatus)
                    .containsExactlyInAnyOrder(UserStatus.values());
        } finally {
            clearTenantContext();
        }
    }

    /**
     * Bind tenant_id into SecurityContextHolder so TenantRlsConnectionHandler
     * applies SET LOCAL app.tenant_id inside the @Transactional boundary.
     * user_id is not needed for RLS — pass a placeholder.
     */
    private void bindTenantContext(UUID tenantId) {
        var token = new UsernamePasswordAuthenticationToken(
                "test-" + tenantId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(Map.of("tenant_id", tenantId.toString(),
                "user_id", UUID.randomUUID().toString()));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private void clearTenantContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Cause A fix helper — raw JdbcTemplate set_config + INSERT INTO users
     * per tenant, so each INSERT runs immediately with the correct GUC.
     * Bypasses Hibernate's deferred-flush + handler's per-tx GUC model
     * (incompatible with multi-tenant JPA fixtures).
     *
     * <p>The INSERT runs inside a {@code tx.execute} block so set_config
     * (is_local=true, scoped to tx) and the INSERT share the same tx-bound
     * Connection. No SecurityContextHolder is bound (handler returns early
     * when {@code currentTenantId()==null}), so the GUC is managed entirely
     * by the explicit {@code set_config} call.
     */
    private void seedUserRaw(UUID tenantId, String email, String displayName, String status) {
        if (tx == null) {
            tx = new TransactionTemplate(transactionManager);
        }
        tx.executeWithoutResult(s -> {
            jdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)",
                    String.class, tenantId.toString());
            jdbc.update(
                    "INSERT INTO users (id, tenant_id, email, display_name, status, "
                            + "password_hash, must_change_password, platform_admin, session_version, "
                            + "created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, 'dummy', false, false, 0, NOW(), NOW())",
                    UUID.randomUUID(), tenantId, email, displayName, status);
        });
    }
}
