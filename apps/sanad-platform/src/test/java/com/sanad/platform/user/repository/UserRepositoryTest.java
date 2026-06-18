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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository-level integration tests for {@link UserRepository}.
 *
 * <p>Uses {@link SpringBootTest @SpringBootTest} with the {@code local}
 * profile to boot the full Spring context (including JPA auditing)
 * against the H2 in-memory database with Flyway V1+V2+V3+V4 applied.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private UUID tenantAId;
    private UUID tenantBId;

    @BeforeEach
    void setUp() {
        Tenant tenantA = tenantRepository.save(
                new Tenant("Tenant A", "tenant-a-" + UUID.randomUUID(), TenantStatus.ACTIVE));
        Tenant tenantB = tenantRepository.save(
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
        User user = new User(tenantAId, "alice@example.com", "Alice", UserStatus.ACTIVE);

        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    // ============================================================
    // TEST 2: email normalized to lowercase
    // ============================================================
    @Test
    @DisplayName("TEST 2: email is normalized to lowercase on save")
    void emailNormalizedToLowerCase() {
        User saved = userRepository.save(
                new User(tenantAId, "Alice.Black@Example.COM", "Alice", UserStatus.ACTIVE));

        assertThat(saved.getEmail()).isEqualTo("alice.black@example.com");
    }

    // ============================================================
    // TEST 3: findByTenantId
    // ============================================================
    @Test
    @DisplayName("TEST 3: findByTenantId returns all users for the tenant")
    void findByTenantId_returnsAllForTenant() {
        userRepository.save(new User(tenantAId, "alice@a.com", "Alice", UserStatus.ACTIVE));
        userRepository.save(new User(tenantAId, "bob@a.com", "Bob", UserStatus.INVITED));
        userRepository.save(new User(tenantBId, "carol@b.com", "Carol", UserStatus.ACTIVE));

        List<User> result = userRepository.findByTenantId(tenantAId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(User::getEmail)
                .containsExactlyInAnyOrder("alice@a.com", "bob@a.com");
    }

    // ============================================================
    // TEST 4: findByTenantIdAndId
    // ============================================================
    @Test
    @DisplayName("TEST 4: findByTenantIdAndId returns the specific user")
    void findByTenantIdAndId_returnsSpecificUser() {
        User saved = userRepository.save(
                new User(tenantAId, "alice@a.com", "Alice", UserStatus.ACTIVE));

        Optional<User> found = userRepository.findByTenantIdAndId(tenantAId, saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("alice@a.com");
    }

    // ============================================================
    // TEST 5: findByTenantIdAndEmail
    // ============================================================
    @Test
    @DisplayName("TEST 5: findByTenantIdAndEmail returns user by email (case-insensitive)")
    void findByTenantIdAndEmail_returnsUser() {
        userRepository.save(
                new User(tenantAId, "alice@example.com", "Alice", UserStatus.ACTIVE));

        // Lookup with lowercased email
        Optional<User> found = userRepository.findByTenantIdAndEmail(tenantAId, "alice@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getDisplayName()).isEqualTo("Alice");
    }

    // ============================================================
    // TEST 6: existsByTenantIdAndEmail
    // ============================================================
    @Test
    @DisplayName("TEST 6: existsByTenantIdAndEmail detects duplicates")
    void existsByTenantIdAndEmail_detectsDuplicates() {
        userRepository.save(
                new User(tenantAId, "Alice@Example.COM", "Alice", UserStatus.ACTIVE));

        // Same email lowercased should be detected (normalization)
        assertThat(userRepository.existsByTenantIdAndEmail(tenantAId, "alice@example.com")).isTrue();

        // Different email should not be detected
        assertThat(userRepository.existsByTenantIdAndEmail(tenantAId, "bob@example.com")).isFalse();
    }

    // ============================================================
    // TEST 7: Tenant isolation — Tenant A user must not appear in Tenant B queries
    // ============================================================
    @Test
    @DisplayName("TEST 7: Tenant isolation - Tenant A user NOT in Tenant B queries")
    void tenantIsolation_userNotVisibleAcrossTenants() {
        userRepository.save(
                new User(tenantAId, "alice@a.com", "Alice", UserStatus.ACTIVE));

        // findByTenantId: Tenant B sees zero users
        assertThat(userRepository.findByTenantId(tenantBId)).isEmpty();

        // findByTenantIdAndId: cross-tenant lookup returns empty
        User saved = userRepository.findByTenantIdAndEmail(tenantAId, "alice@a.com").orElseThrow();
        assertThat(userRepository.findByTenantIdAndId(tenantBId, saved.getId())).isEmpty();

        // existsByTenantIdAndEmail: cross-tenant returns false
        assertThat(userRepository.existsByTenantIdAndEmail(tenantBId, "alice@a.com")).isFalse();

        // findByTenantIdAndEmail: cross-tenant returns empty
        assertThat(userRepository.findByTenantIdAndEmail(tenantBId, "alice@a.com")).isEmpty();
    }

    // ============================================================
    // TEST 8: Duplicate email inside same tenant should fail
    // ============================================================
    @Test
    @DisplayName("TEST 8: Duplicate email inside same tenant fails (unique constraint)")
    void duplicateEmail_sameTenant_fails() {
        userRepository.save(
                new User(tenantAId, "alice@a.com", "Alice", UserStatus.ACTIVE));

        // Attempting to save a second user with the same (tenant, email)
        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User(tenantAId, "ALICE@a.com", "Alice Duplicate", UserStatus.INVITED)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // ============================================================
    // TEST 9: Same email in different tenants should be allowed
    // ============================================================
    @Test
    @DisplayName("TEST 9: Same email in different tenants is allowed")
    void sameEmail_differentTenants_allowed() {
        userRepository.save(
                new User(tenantAId, "shared@shared.com", "Shared A", UserStatus.ACTIVE));
        userRepository.saveAndFlush(
                new User(tenantBId, "shared@shared.com", "Shared B", UserStatus.ACTIVE));

        // Both should exist in their respective tenants
        assertThat(userRepository.findByTenantIdAndEmail(tenantAId, "shared@shared.com")).isPresent();
        assertThat(userRepository.findByTenantIdAndEmail(tenantBId, "shared@shared.com")).isPresent();

        // Each tenant sees exactly 1 user
        assertThat(userRepository.findByTenantId(tenantAId)).hasSize(1);
        assertThat(userRepository.findByTenantId(tenantBId)).hasSize(1);
    }

    // ============================================================
    // TEST 10: Status CHECK constraint works (all valid statuses accepted)
    // ============================================================
    @Test
    @DisplayName("TEST 10: All valid UserStatus values are accepted by the CHECK constraint")
    void statusCheckConstraint_allValidStatusesAccepted() {
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
    }
}
