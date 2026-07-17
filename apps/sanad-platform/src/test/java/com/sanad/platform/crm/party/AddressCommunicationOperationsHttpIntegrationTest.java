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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class AddressCommunicationOperationsHttpIntegrationTest {
    private static final List<String> CAPABILITIES = List.of(
            "CRM.ADDRESS.READ", "CRM.ADDRESS.WRITE", "CRM.ADDRESS.EXPORT",
            "CRM.COMMUNICATION.READ", "CRM.COMMUNICATION.WRITE", "CRM.COMMUNICATION.EXPORT",
            "CRM.IMPORT.WRITE");

    @Autowired MockMvc mockMvc;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void searchesAndExportsArabicAddressesWithinTenant() throws Exception {
        Fixture fixture = fixture("crm007-search");
        UUID accountId = account(fixture, "شركة البحث");
        createAddress(fixture, accountId, "مقر طريق الملك فهد", "الرياض");

        mockMvc.perform(get("/api/v2/crm/addresses/search")
                        .with(authentication(auth(fixture)))
                        .queryParam("q", "الرياض")
                        .queryParam("countryCode", "SA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].line1").value("مقر طريق الملك فهد"));

        MvcResult export = mockMvc.perform(get("/api/v2/crm/addresses/export")
                        .with(authentication(auth(fixture)))
                        .queryParam("q", "الرياض"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"crm-addresses.csv\""))
                .andReturn();
        String csv = new String(export.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF").contains("مقر طريق الملك فهد").contains("الرياض");
    }

    @Test
    void masksConfidentialCommunicationDuringExportUnlessSensitiveReadIsPresent() throws Exception {
        Fixture fixture = fixture("crm007-export-mask");
        UUID contactId = contact(fixture, "سارة", "الخصوصية");
        mockMvc.perform(post("/api/v2/crm/contacts/{id}/communication-methods", contactId)
                        .with(authentication(auth(fixture)))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("""
                                {"methodType":"EMAIL","rawValue":"private@example.test","preferred":true,
                                 "privacyClassification":"CONFIDENTIAL","usagePurpose":"SUPPORT"}
                                """))
                .andExpect(status().isCreated());

        String masked = new String(mockMvc.perform(get("/api/v2/crm/communication-methods/export")
                        .with(authentication(auth(fixture))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(masked).contains("p***@example.test").doesNotContain("private@example.test");

        String visible = new String(mockMvc.perform(get("/api/v2/crm/communication-methods/export")
                        .with(authentication(auth(fixture, "CRM.COMMUNICATION.SENSITIVE.READ"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(visible).contains("private@example.test");
    }

    @Test
    void importIsRowIsolatedAndPersistsFrameworkJobAndErrors() throws Exception {
        Fixture fixture = fixture("crm007-import");
        UUID accountId = account(fixture, "Import Customer");
        String payload = """
                {"rows":[
                  {"ownerType":"ACCOUNT","ownerId":"%s","address":{
                    "addressType":"SHIPPING","line1":"طريق مكة","city":"جدة","countryCode":"SA",
                    "primaryAddress":false,"verified":false}},
                  {"ownerType":"ACCOUNT","ownerId":"%s","address":{
                    "addressType":"BILLING","line1":"Invalid Extension","city":"Riyadh","countryCode":"SA",
                    "countryExtension":{"unsupportedNested":{"value":true}},
                    "primaryAddress":false,"verified":false}}
                ]}
                """.formatted(accountId, accountId);

        MvcResult response = mockMvc.perform(post("/api/v2/crm/addresses/import")
                        .with(authentication(auth(fixture)))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.succeededRows").value(1))
                .andExpect(jsonPath("$.data.failedRows").value(1))
                .andExpect(jsonPath("$.data.errors.length()").value(1))
                .andReturn();

        JsonNode data = mapper.readTree(response.getResponse().getContentAsString()).path("data");
        UUID jobId = UUID.fromString(data.path("jobId").asText());
        Map<String, Object> job = jdbc.queryForMap(
                "SELECT status,total_rows,processed_rows,succeeded_rows,failed_rows FROM crm_import_jobs " +
                        "WHERE tenant_id=:tenantId AND id=:jobId",
                p().addValue("tenantId", fixture.tenantId()).addValue("jobId", jobId));
        assertThat(job.get("status")).isEqualTo("COMPLETED");
        assertThat(((Number) job.get("processed_rows")).intValue()).isEqualTo(2);
        assertThat(((Number) job.get("succeeded_rows")).intValue()).isEqualTo(1);
        assertThat(((Number) job.get("failed_rows")).intValue()).isEqualTo(1);
        Long errors = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_import_errors WHERE tenant_id=:tenantId AND import_job_id=:jobId",
                p().addValue("tenantId", fixture.tenantId()).addValue("jobId", jobId), Long.class);
        Long rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_party_addresses WHERE tenant_id=:tenantId AND account_id=:accountId",
                p().addValue("tenantId", fixture.tenantId()).addValue("accountId", accountId), Long.class);
        assertThat(errors).isOne();
        assertThat(rows).isOne();
    }

    private void createAddress(Fixture fixture, UUID accountId, String line1, String city) throws Exception {
        mockMvc.perform(post("/api/v2/crm/accounts/{id}/addresses", accountId)
                        .with(authentication(auth(fixture)))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("""
                                {"addressType":"OFFICE","line1":"%s","city":"%s","countryCode":"SA",
                                 "primaryAddress":true,"verified":false}
                                """.formatted(line1, city)))
                .andExpect(status().isCreated());
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
                        "VALUES (:id,:tenantId,:email,'CRM-007 Ops User','ACTIVE','dummy',:now,:now)",
                p().addValue("id", userId).addValue("tenantId", tenantId)
                        .addValue("email", key + "-" + userId.toString().substring(0, 8) + "@example.test").addValue("now", now));
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:code,'CRM-007 Ops','CRM-007 ops tests','ACTIVE',:now,:now)",
                p().addValue("id", roleId).addValue("tenantId", tenantId)
                        .addValue("code", "CRM007_OPS_" + key.toUpperCase().replace('-', '_')).addValue("now", now));
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
                        "'BUSINESS','ACTIVE','SAR','ar-SA','Asia/Riyadh','CRM007_OPS',:owner,:owner,:owner,:now,:now)",
                p().addValue("id", id).addValue("tenantId", fixture.tenantId()).addValue("name", name)
                        .addValue("normalized", name.toLowerCase()).addValue("owner", fixture.userId()).addValue("now", now));
        return id;
    }

    private UUID contact(Fixture fixture, String givenName, String familyName) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String displayName = givenName + " " + familyName;
        jdbc.update("INSERT INTO crm_contacts (id,tenant_id,version,account_id,given_name,family_name,display_name," +
                        "normalized_name,preferred_locale,time_zone,lifecycle_status,owner_user_id,consent_summary," +
                        "created_by,updated_by,created_at,updated_at) VALUES (:id,:tenantId,0,NULL,:givenName,:familyName," +
                        ":displayName,:normalized,'ar-SA','Asia/Riyadh','ACTIVE',:owner,'GRANTED',:owner,:owner,:now,:now)",
                p().addValue("id", id).addValue("tenantId", fixture.tenantId())
                        .addValue("givenName", givenName).addValue("familyName", familyName)
                        .addValue("displayName", displayName).addValue("normalized", displayName.toLowerCase())
                        .addValue("owner", fixture.userId()).addValue("now", now));
        return id;
    }

    private Authentication auth(Fixture fixture, String... extraAuthorities) {
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", fixture.tenantId().toString());
        details.put("user_id", fixture.userId().toString());
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        for (String authority : extraAuthorities) authorities.add(new SimpleGrantedAuthority(authority));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                fixture.userId().toString(), null, authorities);
        authentication.setDetails(details);
        return authentication;
    }

    private static MapSqlParameterSource p() { return new MapSqlParameterSource(); }
    private record Fixture(UUID tenantId, UUID userId) {}
}
