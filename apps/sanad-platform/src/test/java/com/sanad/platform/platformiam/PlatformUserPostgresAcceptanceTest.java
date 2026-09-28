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
import org.springframework.transaction.annotation.Transactional;

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
@Transactional
class PlatformUserPostgresAcceptanceTest {

    static final String CONTROL_TENANT_ID = "00000000-0000-0000-0000-000000000001";
    private static final UUID CONTROL_TENANT = UUID.fromString(CONTROL_TENANT_ID);
    private static final UUID OWNER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Autowired private PlatformUserService platformUsers;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.sanad.platform.platformiam.service.PlatformTemporaryAccessService temporaryAccess;
    @Autowired private com.sanad.platform.platformiam.service.PlatformAuthorizationService authorization;

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

    @Test
    void temporaryGrantUsesCanonicalStoreAndRevocationRemovesAuthorization() {
        var user = platformUsers.createPlatformUser(actor(),
                new CreatePlatformUserRequest("temporary+" + UUID.randomUUID() + "@example.test", "Temporary"));
        createdUsers.add(user.userId());
        platformUsers.activate(actor(), user.userId(), "acceptance fixture");
        UUID capabilityId = jdbc.queryForObject(
                "SELECT id FROM access_capabilities WHERE code = 'PLATFORM.USER.READ'", UUID.class);
        var target = new UsernamePasswordAuthenticationToken("target", "unused", List.of());
        target.setDetails(Map.of("tenant_id", CONTROL_TENANT_ID, "user_id", user.userId().toString()));
        assertThat(authorization.evaluate(target, "PLATFORM.USER.READ").allowed()).isFalse();

        var grant = temporaryAccess.grant(actor(), user.userId(),
                new com.sanad.platform.platformiam.dto.CreatePlatformTemporaryAccessRequest(
                        capabilityId, Instant.now().plusSeconds(300), "acceptance coverage"));
        assertThat(grant.grantedBy()).isEqualTo(OWNER_USER_ID);
        assertThat(temporaryAccess.list(actor(), user.userId())).extracting(item -> item.id()).contains(grant.id());
        assertThat(authorization.evaluate(target, "PLATFORM.USER.READ").allowed()).isTrue();
        temporaryAccess.revoke(actor(), user.userId(), grant.id(), "acceptance completed");
        assertThat(authorization.evaluate(target, "PLATFORM.USER.READ").allowed()).isFalse();
        assertThat(temporaryAccess.list(actor(), user.userId())).extracting(item -> item.status()).contains("REVOKED");
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
