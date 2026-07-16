package com.sanad.platform.crm.party;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class CustomerMasterHttpIntegrationTest {
    private static final List<String> CAPABILITIES = List.of("CRM.ACCOUNT.READ", "CRM.ACCOUNT.WRITE");

    @Autowired MockMvc mockMvc;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void readsGoldenRecordAndEnforcesTenantIsolation() throws Exception {
        Fixture owner = fixture("master-owner");
        Fixture outsider = fixture("master-outsider");
        UUID accountId = account(owner, "Acme Arabia");

        mockMvc.perform(get("/api/v1/crm/accounts/{id}/master", accountId).with(authentication(auth(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.legalName").value("Acme Arabia"))
                .andExpect(jsonPath("$.version").value(0));

        mockMvc.perform(get("/api/v1/crm/accounts/{id}/master", accountId).with(authentication(auth(outsider))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatesIdentityClassificationRiskCreditAuditAndTimeline() throws Exception {
        Fixture fixture = fixture("master-update");
        UUID accountId = account(fixture, "SNAD Customer");

        mockMvc.perform(patch("/api/v1/crm/accounts/{id}/master", accountId)
                        .with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("""
                                {"expectedVersion":0,"legalName":"SNAD Customer Company","tradingName":"SNAD Customer",
                                 "registrationNumber":"CR-101010","taxNumber":"VAT-3100000000","industryCode":"SOFTWARE",
                                 "customerSegment":"ENTERPRISE","customerTier":"STRATEGIC","website":"https://customer.example",
                                 "primaryEmail":"finance@customer.example","primaryPhone":"+966500000000","countryCode":"SA",
                                 "riskRating":"LOW","creditLimit":250000,"paymentTermsDays":45}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.customerTier").value("STRATEGIC"))
                .andExpect(jsonPath("$.riskRating").value("LOW"))
                .andExpect(jsonPath("$.dataQualityScore").value(100));

        Integer auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform_audit_logs WHERE target_tenant_id=:tenantId " +
                        "AND resource_id=:resourceId AND action='UPDATE_CUSTOMER_MASTER'",
                p().addValue("tenantId", fixture.tenantId()).addValue("resourceId", accountId.toString()), Integer.class);
        Integer timelineCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_timeline_events WHERE tenant_id=:tenantId AND subject_id=:accountId " +
                        "AND event_type='crm.account.master.updated'",
                p().addValue("tenantId", fixture.tenantId()).addValue("accountId", accountId), Integer.class);
        assertThat(auditCount).isEqualTo(1);
        assertThat(timelineCount).isEqualTo(1);
    }

    @Test
    void rejectsStaleMasterVersion() throws Exception {
        Fixture fixture = fixture("master-stale");
        UUID accountId = account(fixture, "Versioned Customer");
        jdbc.update("UPDATE crm_accounts SET version=2 WHERE tenant_id=:tenantId AND id=:id",
                p().addValue("tenantId", fixture.tenantId()).addValue("id", accountId));

        mockMvc.perform(patch("/api/v1/crm/accounts/{id}/master", accountId)
                        .with(authentication(auth(fixture))).contentType("application/json")
                        .content("{\"expectedVersion\":0,\"legalName\":\"Stale Update\"}"))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void managesAddressesAndIdentifiers() throws Exception {
        Fixture fixture = fixture("master-attributes");
        UUID accountId = account(fixture, "Attribute Customer");

        JsonNode address = perform(post("/api/v1/crm/accounts/{id}/addresses", accountId)
                .with(authentication(auth(fixture))).contentType("application/json")
                .content("""
                        {"addressType":"REGISTERED","label":"Head Office","line1":"King Fahd Road",
                         "city":"Riyadh","postalCode":"12345","countryCode":"SA","primaryAddress":true}
                        """), 201);
        UUID addressId = UUID.fromString(address.path("id").asText());

        mockMvc.perform(post("/api/v1/crm/accounts/{id}/identifiers", accountId)
                        .with(authentication(auth(fixture))).contentType("application/json")
                        .content("""
                                {"identifierType":"COMMERCIAL_REGISTRATION","identifierValue":"1010-2020",
                                 "issuerCountryCode":"SA","primaryIdentifier":true,"verified":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verified").value(true));

        mockMvc.perform(get("/api/v1/crm/accounts/{id}/addresses", accountId).with(authentication(auth(fixture))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].city").value("Riyadh"));
        mockMvc.perform(get("/api/v1/crm/accounts/{id}/identifiers", accountId).with(authentication(auth(fixture))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].identifierValue").value("1010-2020"));

        mockMvc.perform(delete("/api/v1/crm/accounts/{accountId}/addresses/{addressId}", accountId, addressId)
                        .with(authentication(auth(fixture))))
                .andExpect(status().isNoContent());
    }

    @Test
    void preventsDuplicateIdentifierWithinTenantButAllowsAnotherTenant() throws Exception {
        Fixture tenantA = fixture("identifier-a");
        Fixture tenantB = fixture("identifier-b");
        UUID first = account(tenantA, "First Customer");
        UUID second = account(tenantA, "Second Customer");
        UUID otherTenant = account(tenantB, "Other Tenant Customer");
        String body = "{\"identifierType\":\"COMMERCIAL_REGISTRATION\",\"identifierValue\":\"1010-2020\",\"issuerCountryCode\":\"SA\",\"primaryIdentifier\":true,\"verified\":true}";

        mockMvc.perform(post("/api/v1/crm/accounts/{id}/identifiers", first)
                        .with(authentication(auth(tenantA))).contentType("application/json").content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/crm/accounts/{id}/identifiers", second)
                        .with(authentication(auth(tenantA))).contentType("application/json").content(body))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/crm/accounts/{id}/identifiers", otherTenant)
                        .with(authentication(auth(tenantB))).contentType("application/json").content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void detectsEnterpriseDuplicateCandidates() throws Exception {
        Fixture fixture = fixture("master-dupe");
        UUID source = account(fixture, "Acme Holdings");
        UUID candidate = account(fixture, "Acme Holding Company");
        updateIdentity(fixture, source, "ACME HOLDINGS LLC", "CR-777", "VAT-777", "office@acme.example");
        updateIdentity(fixture, candidate, "ACME HOLDINGS LLC", "CR-777", "VAT-777", "office@acme.example");

        mockMvc.perform(get("/api/v1/crm/accounts/{id}/duplicates", source).with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value(candidate.toString()))
                .andExpect(jsonPath("$[0].confidenceScore").value(100));
    }

    @Test
    void mergesCustomerRecordsAndRecordsHistoryAtomically() throws Exception {
        Fixture fixture = fixture("master-merge");
        UUID source = account(fixture, "Duplicate Customer");
        UUID target = account(fixture, "Golden Customer");

        mockMvc.perform(post("/api/v1/crm/accounts/{source}/merge/{target}", source, target)
                        .with(authentication(auth(fixture))).contentType("application/json")
                        .content("{\"expectedSourceVersion\":0,\"expectedTargetVersion\":0,\"reason\":\"Verified duplicate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceAccountId").value(source.toString()))
                .andExpect(jsonPath("$.targetAccountId").value(target.toString()))
                .andExpect(jsonPath("$.sourceVersion").value(1))
                .andExpect(jsonPath("$.targetVersion").value(1));

        Map<String, Object> sourceRow = jdbc.queryForMap(
                "SELECT lifecycle_status,merged_into_account_id FROM crm_accounts WHERE tenant_id=:tenantId AND id=:id",
                p().addValue("tenantId", fixture.tenantId()).addValue("id", source));
        assertThat(sourceRow.get("lifecycle_status")).isEqualTo("ARCHIVED");
        assertThat(sourceRow.get("merged_into_account_id")).isEqualTo(target);
        Integer history = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_account_merge_history WHERE tenant_id=:tenantId AND source_account_id=:sourceId",
                p().addValue("tenantId", fixture.tenantId()).addValue("sourceId", source), Integer.class);
        assertThat(history).isEqualTo(1);
    }

    private void updateIdentity(Fixture fixture, UUID accountId, String legalName, String registration,
                                String tax, String email) throws Exception {
        mockMvc.perform(patch("/api/v1/crm/accounts/{id}/master", accountId)
                        .with(authentication(auth(fixture))).contentType("application/json")
                        .content("{\"expectedVersion\":0,\"legalName\":\"" + legalName +
                                "\",\"registrationNumber\":\"" + registration +
                                "\",\"taxNumber\":\"" + tax +
                                "\",\"primaryEmail\":\"" + email + "\",\"countryCode\":\"SA\"}"))
                .andExpect(status().isOk());
    }

    private JsonNode perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                             int expectedStatus) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private Fixture fixture(String key) {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (:id,:name,:subdomain,'ACTIVE',:now,:now)",
                p().addValue("id", tenantId).addValue("name", key)
                        .addValue("subdomain", key + "-" + tenantId.toString().substring(0, 8)).addValue("now", now));
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:email,'CRM Master User','ACTIVE','dummy',:now,:now)",
                p().addValue("id", userId).addValue("tenantId", tenantId)
                        .addValue("email", key + "-" + userId.toString().substring(0, 8) + "@example.test").addValue("now", now));
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:code,'CRM Master Role','CRM-005 tests','ACTIVE',:now,:now)",
                p().addValue("id", roleId).addValue("tenantId", tenantId)
                        .addValue("code", "CRM_MASTER_" + key.toUpperCase().replace('-', '_')).addValue("now", now));
        List<UUID> capabilityIds = jdbc.query("SELECT id FROM access_capabilities WHERE code IN (:codes)",
                p().addValue("codes", CAPABILITIES), (rs, row) -> rs.getObject("id", UUID.class));
        assertThat(capabilityIds).hasSize(CAPABILITIES.size());
        for (UUID capabilityId : capabilityIds) {
            jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) " +
                            "VALUES (:id,:tenantId,:roleId,:capabilityId,:now)",
                    p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                            .addValue("roleId", roleId).addValue("capabilityId", capabilityId).addValue("now", now));
        }
        jdbc.update("INSERT INTO user_role_assignments (id,tenant_id,user_id,role_id,organization_id,status,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:userId,:roleId,NULL,'ACTIVE',:now,:now)",
                p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                        .addValue("userId", userId).addValue("roleId", roleId).addValue("now", now));
        return new Fixture(tenantId, userId);
    }

    private UUID account(Fixture fixture, String name) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO crm_accounts (id,tenant_id,version,display_name,normalized_name,account_type," +
                        "lifecycle_status,primary_currency_code,preferred_locale,time_zone,source,owner_user_id," +
                        "created_by,updated_by,created_at,updated_at) VALUES (:id,:tenantId,0,:name,:normalized," +
                        "'BUSINESS','ACTIVE','SAR','ar-SA','Asia/Riyadh','CRM005_TEST',:owner,:owner,:owner,:now,:now)",
                p().addValue("id", id).addValue("tenantId", fixture.tenantId()).addValue("name", name)
                        .addValue("normalized", name.toLowerCase()).addValue("owner", fixture.userId()).addValue("now", now));
        return id;
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
