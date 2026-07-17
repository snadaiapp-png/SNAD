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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class AddressCommunicationLifecycleIntegrationTest {
    private static final List<String> CAPABILITIES = List.of(
            "CRM.ADDRESS.READ", "CRM.ADDRESS.WRITE", "CRM.ADDRESS.ADMIN",
            "CRM.COMMUNICATION.READ", "CRM.COMMUNICATION.WRITE", "CRM.COMMUNICATION.ADMIN");

    @Autowired MockMvc mockMvc;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void replacingPrimaryAddressRecordsOldHistoryAndUpdatesLegacyProjection() throws Exception {
        Fixture fixture = fixture("lifecycle-address");
        UUID accountId = account(fixture, "Lifecycle Account");
        UUID first = address(fixture, accountId, "First Street", true);
        UUID second = address(fixture, accountId, "Second Street", true);

        Map<String, Object> canonical = jdbc.queryForMap(
                "SELECT primary_address,version FROM crm_party_addresses WHERE tenant_id=:tenantId AND id=:id",
                p().addValue("tenantId", fixture.tenantId()).addValue("id", first));
        Map<String, Object> legacy = jdbc.queryForMap(
                "SELECT primary_address,version FROM crm_account_addresses WHERE tenant_id=:tenantId AND id=:id",
                p().addValue("tenantId", fixture.tenantId()).addValue("id", first));
        Long history = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_party_address_history WHERE tenant_id=:tenantId AND address_id=:id " +
                        "AND event_type='PRIMARY_REPLACED'",
                p().addValue("tenantId", fixture.tenantId()).addValue("id", first), Long.class);
        Boolean secondPrimary = jdbc.queryForObject(
                "SELECT primary_address FROM crm_account_addresses WHERE tenant_id=:tenantId AND id=:id",
                p().addValue("tenantId", fixture.tenantId()).addValue("id", second), Boolean.class);

        assertThat(canonical.get("primary_address")).isEqualTo(false);
        assertThat(((Number) canonical.get("version")).longValue()).isGreaterThan(0);
        assertThat(legacy.get("primary_address")).isEqualTo(false);
        assertThat(secondPrimary).isTrue();
        assertThat(history).isOne();
    }

    @Test
    void replacingAndArchivingPreferredCommunicationMaintainsHistoryAndLegacyPrimary() throws Exception {
        Fixture fixture = fixture("lifecycle-communication");
        UUID contactId = contact(fixture, "Preferred", "Contact");
        UUID first = communication(fixture, contactId, "first@example.test", true);
        UUID second = communication(fixture, contactId, "second@example.test", true);

        Boolean firstPreferred = jdbc.queryForObject(
                "SELECT preferred FROM crm_communication_methods WHERE tenant_id=:tenantId AND id=:id",
                p().addValue("tenantId", fixture.tenantId()).addValue("id", first), Boolean.class);
        Long replacedHistory = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_communication_method_history WHERE tenant_id=:tenantId " +
                        "AND communication_method_id=:id AND event_type='PREFERRED_REPLACED'",
                p().addValue("tenantId", fixture.tenantId()).addValue("id", first), Long.class);
        String projected = jdbc.queryForObject(
                "SELECT primary_email FROM crm_contacts WHERE tenant_id=:tenantId AND id=:id",
                p().addValue("tenantId", fixture.tenantId()).addValue("id", contactId), String.class);
        assertThat(firstPreferred).isFalse();
        assertThat(replacedHistory).isOne();
        assertThat(projected).isEqualTo("second@example.test");

        long version = jdbc.queryForObject(
                "SELECT version FROM crm_communication_methods WHERE tenant_id=:tenantId AND id=:id",
                p().addValue("tenantId", fixture.tenantId()).addValue("id", second), Long.class);
        mockMvc.perform(patch("/api/v2/crm/communication-methods/{id}/archive", second)
                        .with(authentication(auth(fixture)))
                        .header("If-Match", etag("communication-method", second, version))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk());

        String afterArchive = jdbc.queryForObject(
                "SELECT primary_email FROM crm_contacts WHERE tenant_id=:tenantId AND id=:id",
                p().addValue("tenantId", fixture.tenantId()).addValue("id", contactId), String.class);
        assertThat(afterArchive).isNull();
    }

    private UUID address(Fixture fixture, UUID accountId, String line1, boolean primary) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v2/crm/accounts/{id}/addresses", accountId)
                        .with(authentication(auth(fixture)))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("""
                                {"addressType":"OFFICE","line1":"%s","city":"Riyadh","countryCode":"SA",
                                 "primaryAddress":%s,"verified":false}
                                """.formatted(line1, primary)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode data = mapper.readTree(result.getResponse().getContentAsString()).path("data");
        return UUID.fromString(data.path("id").asText());
    }

    private UUID communication(Fixture fixture, UUID contactId, String value, boolean preferred) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v2/crm/contacts/{id}/communication-methods", contactId)
                        .with(authentication(auth(fixture)))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("""
                                {"methodType":"EMAIL","rawValue":"%s","preferred":%s,
                                 "privacyClassification":"INTERNAL","usagePurpose":"GENERAL"}
                                """.formatted(value, preferred)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText());
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
                        "VALUES (:id,:tenantId,:email,'Lifecycle User','ACTIVE','dummy',:now,:now)",
                p().addValue("id", userId).addValue("tenantId", tenantId)
                        .addValue("email", key + "@example.test").addValue("now", now));
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:code,'Lifecycle Role','CRM-007 lifecycle test','ACTIVE',:now,:now)",
                p().addValue("id", roleId).addValue("tenantId", tenantId)
                        .addValue("code", "CRM007_" + key.toUpperCase().replace('-', '_')).addValue("now", now));
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
                        "'BUSINESS','ACTIVE','SAR','ar-SA','Asia/Riyadh','CRM007_LIFECYCLE',:owner,:owner,:owner,:now,:now)",
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
                        ":displayName,:normalized,'en','UTC','ACTIVE',:owner,'GRANTED',:owner,:owner,:now,:now)",
                p().addValue("id", id).addValue("tenantId", fixture.tenantId())
                        .addValue("givenName", givenName).addValue("familyName", familyName)
                        .addValue("displayName", displayName).addValue("normalized", displayName.toLowerCase())
                        .addValue("owner", fixture.userId()).addValue("now", now));
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

    private static String etag(String type, UUID id, long version) {
        try {
            String material = type.toLowerCase() + ":" + id + ":" + version;
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "\"" + type.toLowerCase() + "-" + id + "-v" + version + "-" +
                    java.util.HexFormat.of().formatHex(digest, 0, 8) + "\"";
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static MapSqlParameterSource p() { return new MapSqlParameterSource(); }
    private record Fixture(UUID tenantId, UUID userId) {}
}
