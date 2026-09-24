package com.sanad.platform.crm.ownership.web;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared test helpers for CRM-008 V1 controller integration tests.
 *
 * <p>Provides tenant/user seeding, authentication construction, and entity seeding
 * for the 8 V1 ownership controllers. Used with {@code SecurityPermitAllTestConfig}
 * which bypasses RBAC but still requires a valid Authentication object.
 */
final class CrmOwnershipControllerTestSupport {

    private CrmOwnershipControllerTestSupport() {}

    record Fixture(UUID tenantId, UUID userId) {}

    static Fixture createTenantFixture(NamedParameterJdbcTemplate jdbc, String key) {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());

        jdbc.update("""
                INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at)
                VALUES (:id,:name,:subdomain,'ACTIVE',:now,:now)
                """, p()
                .addValue("id", tenantId)
                .addValue("name", key)
                .addValue("subdomain", key + "-" + tenantId.toString().substring(0, 8))
                .addValue("now", now));

        jdbc.update("""
                INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at)
                VALUES (:id,:tenantId,:email,'CRM Test User','ACTIVE','dummy',:now,:now)
                """, p()
                .addValue("id", userId)
                .addValue("tenantId", tenantId)
                .addValue("email", key + "-" + userId.toString().substring(0, 8) + "@test.local")
                .addValue("now", now));

        return new Fixture(tenantId, userId);
    }

    static Authentication auth(Fixture fixture) {
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", fixture.tenantId().toString());
        details.put("user_id", fixture.userId().toString());
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                fixture.userId().toString(), null, List.of());
        token.setDetails(details);
        return token;
    }

    static UUID seedTeam(NamedParameterJdbcTemplate jdbc, UUID tenantId, UUID actorId, String code) {
        UUID teamId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO crm_sales_teams (id,tenant_id,code,display_name,status,
                    created_by,updated_by,created_at,updated_at)
                VALUES (:id,:tenantId,:code,:code,'ACTIVE',
                    :actor,:actor,:now,:now)
                """, p()
                .addValue("id", teamId)
                .addValue("tenantId", tenantId)
                .addValue("code", code)
                .addValue("actor", actorId)
                .addValue("now", now));
        return teamId;
    }

    static UUID seedShiftTemplate(NamedParameterJdbcTemplate jdbc, UUID tenantId, UUID actorId, String name) {
        UUID templateId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO crm_shift_templates (id,tenant_id,name,start_time,end_time,days_of_week,status,
                    created_by,updated_by,created_at,updated_at,version)
                VALUES (:id,:tenantId,:name,'08:00:00','16:00:00','1,3,5','ACTIVE',
                    :actor,:actor,:now,:now,1)
                """, p()
                .addValue("id", templateId)
                .addValue("tenantId", tenantId)
                .addValue("name", name)
                .addValue("actor", actorId)
                .addValue("now", now));
        return templateId;
    }

    static MapSqlParameterSource p() {
        return new MapSqlParameterSource();
    }
}
