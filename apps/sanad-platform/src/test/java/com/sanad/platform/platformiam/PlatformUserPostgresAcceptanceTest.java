package com.sanad.platform.platformiam;

import com.sanad.platform.commerce.PgAcceptanceWiringConfig;
import com.sanad.platform.platformiam.dto.CreatePlatformUserRequest;
import com.sanad.platform.platformiam.service.PlatformUserService;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Task 6A PostgreSQL Direct acceptance for cross-tenant Platform User identity isolation. */
@SpringBootTest(properties = {
        "sanad.control-plane.tenant-id=" + PlatformUserPostgresAcceptanceTest.CONTROL_TENANT_ID
})
@ActiveProfiles("pg-acceptance")
@Import(PgAcceptanceWiringConfig.class)
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = "pg-acceptance")
class PlatformUserPostgresAcceptanceTest {

    static final String CONTROL_TENANT_ID = "00000000-0000-0000-0000-000000000001";
    private static final UUID CONTROL_TENANT = UUID.fromString(CONTROL_TENANT_ID);
    private static final UUID OWNER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Autowired private PlatformUserService platformUsers;
    @Autowired private JdbcTemplate jdbc;

    private final List<UUID> createdUsers = new ArrayList<>();
    private final List<UUID> createdTenants = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (UUID userId : createdUsers) {
            jdbc.update("DELETE FROM platform_memberships WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        for (UUID tenantId : createdTenants) {
            jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
        }
        createdUsers.clear();
        createdTenants.clear();
    }

    @Test
    void sameEmailExistingOnlyOutsideControlTenantIsNeverReusedOrPromoted() {
        UUID tenantB = seedTenant("task6-cross-tenant");
        String email = "task6-cross-tenant+" + UUID.randomUUID() + "@example.test";
        UUID foreignUserId = seedUser(tenantB, email, "Tenant B Identity");

        var created = platformUsers.createPlatformUser(
                actor(), new CreatePlatformUserRequest(email, "Control Plane Identity"));
        createdUsers.add(created.userId());

        assertThat(created.userId()).isNotEqualTo(foreignUserId);
        assertThat(created.email()).isEqualTo(email.toLowerCase());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id=? AND tenant_id=?",
                Integer.class, created.userId(), CONTROL_TENANT)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform_memberships WHERE control_tenant_id=? AND user_id=?",
                Integer.class, CONTROL_TENANT, created.userId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform_memberships WHERE control_tenant_id=? AND user_id=?",
                Integer.class, CONTROL_TENANT, foreignUserId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id=? AND tenant_id=? AND email=?",
                Integer.class, foreignUserId, tenantB, email)).isEqualTo(1);
    }

    private UUID seedTenant(String key) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',?,?)",
                id, key, key + "-" + id.toString().substring(0, 8), Timestamp.from(now), Timestamp.from(now));
        createdTenants.add(id);
        return id;
    }

    private UUID seedUser(UUID tenantId, String email, String displayName) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO users (
                    id, tenant_id, email, display_name, status,
                    password_hash, must_change_password, platform_admin,
                    session_version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'ACTIVE', NULL, false, false, 0, ?, ?)
                """, id, tenantId, email, displayName, Timestamp.from(now), Timestamp.from(now));
        createdUsers.add(id);
        return id;
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
