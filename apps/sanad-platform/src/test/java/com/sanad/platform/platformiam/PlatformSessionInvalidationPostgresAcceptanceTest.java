package com.sanad.platform.platformiam;

import com.sanad.platform.commerce.PgAcceptanceWiringConfig;
import com.sanad.platform.platformiam.domain.PlatformMembershipStatus;
import com.sanad.platform.platformiam.service.PlatformUserService;
import com.sanad.platform.security.filter.SessionVersionCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Task 6B PostgreSQL Direct acceptance for security-sensitive Platform session invalidation. */
@SpringBootTest(properties = {
        "sanad.control-plane.tenant-id=" + PlatformSessionInvalidationPostgresAcceptanceTest.CONTROL_TENANT_ID
})
@ActiveProfiles("pg-acceptance")
@Import(PgAcceptanceWiringConfig.class)
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = "pg-acceptance")
@Transactional
class PlatformSessionInvalidationPostgresAcceptanceTest {

    static final String CONTROL_TENANT_ID = "00000000-0000-0000-0000-000000000001";
    private static final UUID CONTROL_TENANT = UUID.fromString(CONTROL_TENANT_ID);
    private static final UUID OWNER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Autowired private PlatformUserService platformUsers;
    @Autowired private SessionVersionCache sessionVersions;
    @Autowired private JdbcTemplate jdbc;

    private final List<UUID> createdUsers = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (UUID userId : createdUsers) {
            jdbc.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM user_role_assignments WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM platform_memberships WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        createdUsers.clear();
    }

    @Test
    void suspendRevokesRefreshTokensAndInvalidatesAlreadyIssuedSessionVersion() {
        assertSecurityTransitionInvalidatesSessions(PlatformMembershipStatus.SUSPENDED);
    }

    @Test
    void lockRevokesRefreshTokensAndInvalidatesAlreadyIssuedSessionVersion() {
        assertSecurityTransitionInvalidatesSessions(PlatformMembershipStatus.LOCKED);
    }

    @Test
    void disableRevokesRefreshTokensAndInvalidatesAlreadyIssuedSessionVersion() {
        assertSecurityTransitionInvalidatesSessions(PlatformMembershipStatus.DISABLED);
    }

    private void assertSecurityTransitionInvalidatesSessions(PlatformMembershipStatus target) {
        UUID userId = seedActivePlatformUser(target.name().toLowerCase());
        seedActiveRefreshToken(userId, target.name().toLowerCase());

        Long cachedBefore = sessionVersions.get(CONTROL_TENANT, userId);
        assertThat(cachedBefore).isZero();

        switch (target) {
            case SUSPENDED -> platformUsers.suspend(actor(), userId, "Task 6 security acceptance");
            case LOCKED -> platformUsers.lock(actor(), userId, "Task 6 security acceptance");
            case DISABLED -> platformUsers.disable(actor(), userId, "Task 6 security acceptance");
            default -> throw new IllegalArgumentException("Unsupported security transition: " + target);
        }

        assertThat(jdbc.queryForObject(
                "SELECT status FROM platform_memberships WHERE control_tenant_id=? AND user_id=?",
                String.class, CONTROL_TENANT, userId)).isEqualTo(target.name());
        assertThat(jdbc.queryForObject(
                "SELECT session_version FROM users WHERE tenant_id=? AND id=?",
                Long.class, CONTROL_TENANT, userId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE tenant_id=? AND user_id=? AND status='ACTIVE'",
                Integer.class, CONTROL_TENANT, userId)).isZero();

        // The cache was deliberately primed with version 0 before the transition.
        // Reading 1 immediately after proves AuthService.logout invalidated the stale cache entry.
        assertThat(sessionVersions.get(CONTROL_TENANT, userId)).isEqualTo(1L);
    }

    private UUID seedActivePlatformUser(String key) {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO users (
                    id, tenant_id, email, display_name, status,
                    password_hash, must_change_password, platform_admin,
                    session_version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'ACTIVE', NULL, false, false, 0, ?, ?)
                """,
                userId, CONTROL_TENANT,
                "task6-session-" + key + "+" + userId + "@example.test",
                "Task 6 Session " + key,
                Timestamp.from(now), Timestamp.from(now));

        // The fixture itself writes the FORCE-RLS table, so bind the already
        // trusted control tenant inside the surrounding test transaction.
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, CONTROL_TENANT_ID);
        jdbc.update("""
                INSERT INTO platform_memberships (
                    id, control_tenant_id, user_id, status,
                    invited_at, activated_at, suspended_at, locked_at, disabled_at,
                    created_by, updated_by, status_reason, created_at, updated_at
                ) VALUES (?, ?, ?, 'ACTIVE', ?, ?, NULL, NULL, NULL, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), CONTROL_TENANT, userId,
                Timestamp.from(now), Timestamp.from(now),
                OWNER_USER_ID, OWNER_USER_ID, "Task 6 session fixture",
                Timestamp.from(now), Timestamp.from(now));
        createdUsers.add(userId);
        return userId;
    }

    private void seedActiveRefreshToken(UUID userId, String key) {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO refresh_tokens (
                    id, tenant_id, user_id, token_hash, status,
                    expires_at, created_at, used_at, replaced_by_id
                ) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, NULL, NULL)
                """,
                UUID.randomUUID(), CONTROL_TENANT, userId,
                "task6-token-" + key + "-" + UUID.randomUUID(),
                Timestamp.from(now.plusSeconds(3600)), Timestamp.from(now));
    }

    private static Authentication actor() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("owner", "n/a", List.of());
        auth.setDetails(Map.of(
                "tenant_id", CONTROL_TENANT_ID,
                "user_id", OWNER_USER_ID.toString()));
        return auth;
    }
}
