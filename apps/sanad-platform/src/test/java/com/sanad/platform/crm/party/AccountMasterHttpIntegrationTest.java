package com.sanad.platform.crm.party;

import com.sanad.platform.crm.party.application.AccountUseCases;
import com.sanad.platform.crm.party.domain.AccountRepository.AccountRecord;
import com.sanad.platform.crm.party.domain.AccountRepository.CreateAccountCommand;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class AccountMasterHttpIntegrationTest {
    private static final List<String> MASTER_CAPABILITIES = List.of(
            "CRM.ACCOUNT.MASTER.READ",
            "CRM.ACCOUNT.MASTER.WRITE",
            "CRM.ACCOUNT.RELATIONSHIP.READ",
            "CRM.ACCOUNT.RELATIONSHIP.WRITE",
            "CRM.ACCOUNT.IDENTIFIER.READ",
            "CRM.ACCOUNT.IDENTIFIER.WRITE",
            "CRM.ACCOUNT.HISTORY.READ",
            "CRM.ACCOUNT.RISK.READ",
            "CRM.ACCOUNT.RISK.WRITE");

    @Autowired MockMvc mockMvc;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired AccountUseCases accounts;

    @Test
    void returnsTypedEnterpriseMasterAndUnavailableProjections() throws Exception {
        TenantUser context = seedTenantUser(true);
        AccountRecord account = createAccount(context, "HTTP Master");

        mockMvc.perform(get("/api/v2/crm/accounts/{id}/master", account.id())
                        .with(authentication(auth(context))))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.profile.legalName").value("HTTP Master"))
                .andExpect(jsonPath("$.projections.length()").value(3))
                .andExpect(jsonPath("$.projections[0].connectionStatus").value("NOT_CONNECTED"));
    }

    @Test
    void updatesProfileWithRealIfMatch() throws Exception {
        TenantUser context = seedTenantUser(true);
        AccountRecord account = createAccount(context, "Before Master Update");

        String etag = mockMvc.perform(get("/api/v2/crm/accounts/{id}/master", account.id())
                        .with(authentication(auth(context))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");

        mockMvc.perform(put("/api/v2/crm/accounts/{id}/master/profile", account.id())
                        .with(authentication(auth(context)))
                        .header("If-Match", etag)
                        .contentType("application/json")
                        .content("""
                                {
                                  "legalName":"Acme Legal LLC",
                                  "tradeName":"Acme",
                                  "registrationNumber":"CR-500",
                                  "industry":"TECHNOLOGY",
                                  "organizationSize":"ENTERPRISE",
                                  "websiteUrl":"https://example.test",
                                  "customerTier":"PLATINUM"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.legalName").value("Acme Legal LLC"))
                .andExpect(jsonPath("$.registrationNumber").value("CR-500"));
    }

    @Test
    void rejectsProfileUpdateWithoutIfMatch() throws Exception {
        TenantUser context = seedTenantUser(true);
        AccountRecord account = createAccount(context, "Missing ETag");

        mockMvc.perform(put("/api/v2/crm/accounts/{id}/master/profile", account.id())
                        .with(authentication(auth(context)))
                        .contentType("application/json")
                        .content("{\"legalName\":\"Changed\"}"))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void enforcesGranularMasterCapability() throws Exception {
        TenantUser context = seedTenantUser(false);
        AccountRecord account = createAccount(context, "No Master Permission");

        mockMvc.perform(get("/api/v2/crm/accounts/{id}/master", account.id())
                        .with(authentication(auth(context))))
                .andExpect(status().isForbidden());
    }

    @Test
    void hidesCrossTenantAccountAsNotFound() throws Exception {
        TenantUser tenantA = seedTenantUser(true);
        AccountRecord accountA = createAccount(tenantA, "Tenant A Secret");
        TenantUser tenantB = seedTenantUser(true);

        mockMvc.perform(get("/api/v2/crm/accounts/{id}/master", accountA.id())
                        .with(authentication(auth(tenantB))))
                .andExpect(status().isNotFound());
    }

    @Test
    void createsRelationshipAndExternalIdentifierThroughGovernedEndpoints() throws Exception {
        TenantUser context = seedTenantUser(true);
        AccountRecord source = createAccount(context, "Source");
        AccountRecord target = createAccount(context, "Target");

        mockMvc.perform(post("/api/v2/crm/accounts/{id}/relationships", source.id())
                        .with(authentication(auth(context)))
                        .contentType("application/json")
                        .content("{\"targetAccountId\":\"" + target.id() + "\",\"relationshipType\":\"PARTNER\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v2/crm/accounts/{id}/external-identifiers", source.id())
                        .with(authentication(auth(context)))
                        .contentType("application/json")
                        .content("{\"provider\":\"SAP\",\"systemScope\":\"CUSTOMER\",\"externalId\":\"C-900\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("SAP"))
                .andExpect(jsonPath("$.externalId").value("C-900"));
    }

    private AccountRecord createAccount(TenantUser context, String name) {
        return accounts.create(
                context.tenantId(), context.userId(),
                new CreateAccountCommand(
                        name, "BUSINESS", context.userId(), null,
                        "SAR", "ar-SA", "Asia/Riyadh", "CRM-005-HTTP"));
    }

    private TenantUser seedTenantUser(boolean grantMasterCapabilities) {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) " +
                        "VALUES (:id,'CRM-005 HTTP',:subdomain,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                new MapSqlParameterSource("id", tenantId)
                        .addValue("subdomain", "crm005-http-" + tenantId.toString().substring(0, 8)));
        jdbc.update(
                "INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:email,'CRM-005 User','ACTIVE','dummy',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                new MapSqlParameterSource("id", userId).addValue("tenantId", tenantId)
                        .addValue("email", "crm005-http-" + userId.toString().substring(0, 8) + "@example.test"));
        jdbc.update(
                "INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:code,'CRM-005 Role','Test role','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                new MapSqlParameterSource("id", roleId).addValue("tenantId", tenantId)
                        .addValue("code", "CRM005_" + roleId.toString().substring(0, 8)));
        jdbc.update(
                "INSERT INTO user_role_assignments " +
                        "(id,tenant_id,user_id,role_id,organization_id,status,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:userId,:roleId,NULL,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                        .addValue("userId", userId).addValue("roleId", roleId));
        if (grantMasterCapabilities) {
            List<UUID> capabilityIds = jdbc.query(
                    "SELECT id FROM access_capabilities WHERE code IN (:codes)",
                    new MapSqlParameterSource("codes", MASTER_CAPABILITIES),
                    (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class));
            if (capabilityIds.size() != MASTER_CAPABILITIES.size()) {
                throw new IllegalStateException("CRM-005 capability catalog is incomplete");
            }
            for (UUID capabilityId : capabilityIds) {
                jdbc.update(
                        "INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) " +
                                "VALUES (:id,:tenantId,:roleId,:capabilityId,CURRENT_TIMESTAMP)",
                        new MapSqlParameterSource("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                                .addValue("roleId", roleId).addValue("capabilityId", capabilityId));
            }
        }
        return new TenantUser(tenantId, userId);
    }

    private Authentication auth(TenantUser context) {
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", context.tenantId().toString());
        details.put("user_id", context.userId().toString());
        var authentication = new UsernamePasswordAuthenticationToken(
                context.userId().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authentication.setDetails(details);
        return authentication;
    }

    private record TenantUser(UUID tenantId, UUID userId) { }
}
