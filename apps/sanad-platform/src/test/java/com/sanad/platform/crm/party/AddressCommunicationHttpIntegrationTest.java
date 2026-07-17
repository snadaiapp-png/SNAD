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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class AddressCommunicationHttpIntegrationTest {
    private static final List<String> CAPABILITIES = List.of(
            "CRM.ADDRESS.READ", "CRM.ADDRESS.WRITE", "CRM.ADDRESS.ADMIN",
            "CRM.COMMUNICATION.READ", "CRM.COMMUNICATION.WRITE", "CRM.COMMUNICATION.ADMIN",
            "CRM.COMMUNICATION.SENSITIVE.READ", "CRM.COMMUNICATION.EXPORT");

    @Autowired MockMvc mockMvc;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void preservesArabicAddressAndMaintainsLegacyProjectionAuditTimelineAndHistory() throws Exception {
        Fixture fixture = fixture("crm007-address-ar");
        UUID accountId = account(fixture, "شركة سند العربية");

        MvcResult result = mockMvc.perform(post("/api/v2/crm/accounts/{id}/addresses", accountId)
                        .with(authentication(auth(fixture)))
                        .header("Idempotency-Key", idem())
                        .contentType("application/json")
                        .content("""
                                {"addressType":"REGISTERED","label":"المقر الرئيسي",
                                 "rawFormattedAddress":"٢٥ طريق الملك فهد، حي العليا، الرياض",
                                 "line1":"٢٥ طريق الملك فهد","district":"حي العليا","city":"الرياض",
                                 "postalCode":"١٢٣٤٥","countryCode":"SA",
                                 "countryExtension":{"buildingNumber":"٢٥","shortAddress":"ABCD 1234"},
                                 "primaryAddress":true,"verified":true,"verificationSource":"NATIONAL_ADDRESS"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.data.city").value("الرياض"))
                .andExpect(jsonPath("$.data.primaryAddress").value(true))
                .andReturn();

        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        UUID addressId = UUID.fromString(body.path("data").path("id").asText());

        Map<String, Object> canonical = jdbc.queryForMap(
                "SELECT line1,city,country_extension_json FROM crm_party_addresses WHERE tenant_id=:tenantId AND id=:id",
                p().addValue("tenantId", fixture.tenantId()).addValue("id", addressId));
        Map<String, Object> legacy = jdbc.queryForMap(
                "SELECT line1,city,primary_address FROM crm_account_addresses WHERE tenant_id=:tenantId AND id=:id",
                p().addValue("tenantId", fixture.tenantId()).addValue("id", addressId));
        assertThat(canonical.get("line1")).isEqualTo("٢٥ طريق الملك فهد");
        assertThat(canonical.get("city")).isEqualTo("الرياض");
        assertThat(canonical.get("country_extension_json").toString()).contains("shortAddress");
        assertThat(legacy.get("line1")).isEqualTo("٢٥ طريق الملك فهد");
        assertThat(legacy.get("primary_address")).isEqualTo(true);

        Integer history = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_party_address_history WHERE tenant_id=:tenantId AND address_id=:id",
                p().addValue("tenantId", fixture.tenantId()).addValue("id", addressId), Integer.class);
        Integer audit = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform_audit_logs WHERE target_tenant_id=:tenantId AND resource_id=:id AND action='CREATE_ADDRESS'",
                p().addValue("tenantId", fixture.tenantId()).addValue("id", addressId.toString()), Integer.class);
        Integer timeline = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_timeline_events WHERE tenant_id=:tenantId AND subject_id=:ownerId " +
                        "AND event_type='crm.address.created'",
                p().addValue("tenantId", fixture.tenantId()).addValue("ownerId", accountId), Integer.class);
        assertThat(history).isEqualTo(1);
        assertThat(audit).isEqualTo(1);
        assertThat(timeline).isEqualTo(1);
    }

    @Test
    void normalizesSaudiPhoneAndMasksConfidentialValueWithoutSensitiveAuthority() throws Exception {
        Fixture fixture = fixture("crm007-phone");
        UUID contactId = contact(fixture, "عبدالرحمن", "سنان");

        MvcResult created = mockMvc.perform(post("/api/v2/crm/contacts/{id}/communication-methods", contactId)
                        .with(authentication(auth(fixture)))
                        .header("Idempotency-Key", idem())
                        .contentType("application/json")
                        .content("""
                                {"methodType":"MOBILE","rawValue":"055 123 4567","label":"الجوال",
                                 "preferred":true,"privacyClassification":"CONFIDENTIAL",
                                 "consentStateReference":"GRANTED","usagePurpose":"SUPPORT","countryHint":"SA"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.normalizedValue").value("+966551234567"))
                .andReturn();
        UUID methodId = UUID.fromString(mapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText());

        mockMvc.perform(get("/api/v2/crm/communication-methods/{id}", methodId)
                        .with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rawValue").doesNotExist())
                .andExpect(jsonPath("$.data.normalizedValue").doesNotExist())
                .andExpect(jsonPath("$.data.displayValue").value("••••4567"));

        mockMvc.perform(get("/api/v2/crm/communication-methods/{id}", methodId)
                        .with(authentication(auth(fixture, "CRM.COMMUNICATION.SENSITIVE.READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rawValue").value("055 123 4567"))
                .andExpect(jsonPath("$.data.normalizedValue").value("+966551234567"));

        String projected = jdbc.queryForObject(
                "SELECT primary_phone FROM crm_contacts WHERE tenant_id=:tenantId AND id=:id",
                p().addValue("tenantId", fixture.tenantId()).addValue("id", contactId), String.class);
        assertThat(projected).isEqualTo("+966551234567");
    }

    @Test
    void replaysSameIdempotencyKeyWithoutDuplicateRows() throws Exception {
        Fixture fixture = fixture("crm007-idempotency");
        UUID accountId = account(fixture, "Idempotent Customer");
        String key = idem();
        String payload = """
                {"addressType":"BILLING","line1":"King Road","city":"Riyadh",
                 "countryCode":"SA","primaryAddress":true,"verified":false}
                """;

        MvcResult first = mockMvc.perform(post("/api/v2/crm/accounts/{id}/addresses", accountId)
                        .with(authentication(auth(fixture))).header("Idempotency-Key", key)
                        .contentType("application/json").content(payload))
                .andExpect(status().isCreated()).andReturn();
        MvcResult replay = mockMvc.perform(post("/api/v2/crm/accounts/{id}/addresses", accountId)
                        .with(authentication(auth(fixture))).header("Idempotency-Key", key)
                        .contentType("application/json").content(payload))
                .andExpect(status().isCreated()).andReturn();

        String firstId = mapper.readTree(first.getResponse().getContentAsString()).path("data").path("id").asText();
        String replayId = mapper.readTree(replay.getResponse().getContentAsString()).path("data").path("id").asText();
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_party_addresses WHERE tenant_id=:tenantId AND account_id=:accountId",
                p().addValue("tenantId", fixture.tenantId()).addValue("accountId", accountId), Long.class);
        assertThat(replayId).isEqualTo(firstId);
        assertThat(count).isOne();
    }

    @Test
    void enforcesEtagAndTenantIsolation() throws Exception {
        Fixture owner = fixture("crm007-owner");
        Fixture outsider = fixture("crm007-outsider");
        UUID accountId = account(owner, "Versioned Address Customer");

        MvcResult created = mockMvc.perform(post("/api/v2/crm/accounts/{id}/addresses", accountId)
                        .with(authentication(auth(owner))).header("Idempotency-Key", idem())
                        .contentType("application/json")
                        .content("""
                                {"addressType":"OFFICE","line1":"Olaya Street","city":"Riyadh",
                                 "countryCode":"SA","primaryAddress":false,"verified":false}
                                """))
                .andExpect(status().isCreated()).andReturn();
        JsonNode body = mapper.readTree(created.getResponse().getContentAsString());
        UUID addressId = UUID.fromString(body.path("data").path("id").asText());
        String etag = created.getResponse().getHeader("ETag");

        mockMvc.perform(patch("/api/v2/crm/addresses/{id}", addressId)
                        .with(authentication(auth(owner))).header("If-Match", etag)
                        .contentType("application/json").content("{\"city\":\"Jeddah\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.city").value("Jeddah"));

        mockMvc.perform(patch("/api/v2/crm/addresses/{id}", addressId)
                        .with(authentication(auth(owner))).header("If-Match", etag)
                        .contentType("application/json").content("{\"city\":\"Dammam\"}"))
                .andExpect(status().isPreconditionFailed());

        mockMvc.perform(get("/api/v2/crm/addresses/{id}", addressId)
                        .with(authentication(auth(outsider))))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateNormalizedValuePerOwnerButAllowsAnotherTenant() throws Exception {
        Fixture first = fixture("crm007-dupe-a");
        Fixture second = fixture("crm007-dupe-b");
        UUID firstContact = contact(first, "أحمد", "العربي");
        UUID secondContact = contact(second, "Ahmed", "Other");
        String payload = """
                {"methodType":"EMAIL","rawValue":"Support@Example.Test","preferred":false,
                 "privacyClassification":"INTERNAL","usagePurpose":"GENERAL"}
                """;

        mockMvc.perform(post("/api/v2/crm/contacts/{id}/communication-methods", firstContact)
                        .with(authentication(auth(first))).header("Idempotency-Key", idem())
                        .contentType("application/json").content(payload))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v2/crm/contacts/{id}/communication-methods", firstContact)
                        .with(authentication(auth(first))).header("Idempotency-Key", idem())
                        .contentType("application/json").content(payload.replace("Support@", "support@")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v2/crm/contacts/{id}/communication-methods", secondContact)
                        .with(authentication(auth(second))).header("Idempotency-Key", idem())
                        .contentType("application/json").content(payload))
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
                        "VALUES (:id,:tenantId,:email,'CRM-007 User','ACTIVE','dummy',:now,:now)",
                p().addValue("id", userId).addValue("tenantId", tenantId)
                        .addValue("email", key + "-" + userId.toString().substring(0, 8) + "@example.test").addValue("now", now));
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:code,'CRM-007 Role','CRM-007 tests','ACTIVE',:now,:now)",
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
                        "'BUSINESS','ACTIVE','SAR','ar-SA','Asia/Riyadh','CRM007_TEST',:owner,:owner,:owner,:now,:now)",
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

    private static String idem() { return UUID.randomUUID().toString(); }
    private static MapSqlParameterSource p() { return new MapSqlParameterSource(); }
    private record Fixture(UUID tenantId, UUID userId) {}
}
