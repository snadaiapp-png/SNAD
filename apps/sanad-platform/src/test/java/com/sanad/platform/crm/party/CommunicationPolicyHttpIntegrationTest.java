package com.sanad.platform.crm.party;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class CommunicationPolicyHttpIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired NamedParameterJdbcTemplate jdbc;

    @Test
    void readsDefaultsAndUpdatesOnlyAuthenticatedTenantWithAudit() throws Exception {
        Fixture first = fixture("policy-a");
        Fixture second = fixture("policy-b");

        mockMvc.perform(get("/api/v2/crm/communication-policy")
                        .with(authentication(auth(first))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailUniqueWithinOwner").value(true))
                .andExpect(jsonPath("$.data.phoneUniqueWithinOwner").value(true))
                .andExpect(jsonPath("$.data.singlePreferredPerType").value(true));

        mockMvc.perform(patch("/api/v2/crm/communication-policy")
                        .with(authentication(auth(first)))
                        .contentType("application/json")
                        .content("""
                                {"emailUniqueWithinOwner":false,"phoneUniqueWithinOwner":true,
                                 "singlePreferredPerType":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailUniqueWithinOwner").value(false));

        Boolean firstValue = jdbc.queryForObject(
                "SELECT email_unique_within_owner FROM crm_communication_policies WHERE tenant_id=:tenantId",
                p().addValue("tenantId", first.tenantId()), Boolean.class);
        Long secondRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_communication_policies WHERE tenant_id=:tenantId",
                p().addValue("tenantId", second.tenantId()), Long.class);
        Long auditRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform_audit_logs WHERE target_tenant_id=:tenantId " +
                        "AND action='UPDATE_COMMUNICATION_POLICY'",
                p().addValue("tenantId", first.tenantId()), Long.class);
        assertThat(firstValue).isFalse();
        assertThat(secondRows).isZero();
        assertThat(auditRows).isOne();
    }

    private Fixture fixture(String key) {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) " +
                        "VALUES (:id,:name,:subdomain,'ACTIVE',:now,:now)",
                p().addValue("id", tenantId).addValue("name", key)
                        .addValue("subdomain", key + "-" + tenantId.toString().substring(0, 8)).addValue("now", now));
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:email,'Policy User','ACTIVE','dummy',:now,:now)",
                p().addValue("id", userId).addValue("tenantId", tenantId)
                        .addValue("email", key + "-" + userId.toString().substring(0, 8) + "@example.test").addValue("now", now));
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:code,'Policy Role','CRM-007 policy test','ACTIVE',:now,:now)",
                p().addValue("id", roleId).addValue("tenantId", tenantId)
                        .addValue("code", "CRM007_POLICY_" + key.toUpperCase().replace('-', '_')).addValue("now", now));
        UUID capabilityId = jdbc.queryForObject(
                "SELECT id FROM access_capabilities WHERE code='CRM.COMMUNICATION.ADMIN'",
                p(), UUID.class);
        jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) " +
                        "VALUES (:id,:tenantId,:roleId,:capabilityId,:now)",
                p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                        .addValue("roleId", roleId).addValue("capabilityId", capabilityId).addValue("now", now));
        jdbc.update("INSERT INTO user_role_assignments (id,tenant_id,user_id,role_id,organization_id,status,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:userId,:roleId,NULL,'ACTIVE',:now,:now)",
                p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                        .addValue("userId", userId).addValue("roleId", roleId).addValue("now", now));
        return new Fixture(tenantId, userId);
    }

    private Authentication auth(Fixture fixture) {
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", fixture.tenantId().toString());
        details.put("user_id", fixture.userId().toString());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                fixture.userId().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        authentication.setDetails(details);
        return authentication;
    }

    private static MapSqlParameterSource p() { return new MapSqlParameterSource(); }
    private record Fixture(UUID tenantId, UUID userId) {}
}
