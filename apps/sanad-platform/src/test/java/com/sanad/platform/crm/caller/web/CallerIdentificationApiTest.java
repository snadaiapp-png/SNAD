package com.sanad.platform.crm.caller.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G8 caller-identification endpoint contract (G8-02 §41–§42).
 *
 * <p>Runs on the local H2 profile with the real capability evaluator: each
 * fixture seeds a tenant/user/role and grants exactly the capabilities the
 * scenario needs, then authenticates via the standard details-map pattern.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class CallerIdentificationApiTest {

    private static final String LOOKUP = "/api/v2/crm/caller-identification/lookup";
    private static final String SA_PHONE = "+966541234567";

    @Autowired MockMvc mockMvc;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired MeterRegistry meterRegistry;

    // ===== EXACT / card contract =====

    @Test
    void lookupReturnsExactContactCard() throws Exception {
        Fixture fixture = fixture("g8-exact");
        UUID contactId = contact(fixture, "محمد أحمد", "م", "أ", SA_PHONE, "ACTIVE", "INTERNAL",
                true, "VERIFIED", true);
        UUID accountId = account(fixture, "شركة سند");
        jdbc.update("UPDATE crm_contacts SET account_id=:accountId WHERE id=:id",
                p().addValue("accountId", accountId).addValue("id", contactId));

        mockMvc.perform(post(LOOKUP).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"phone\":\"0541234567\",\"countryHint\":\"SA\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchStatus").value("EXACT"))
                .andExpect(jsonPath("$.entityType").value("CONTACT"))
                .andExpect(jsonPath("$.entityId").value(contactId.toString()))
                .andExpect(jsonPath("$.displayName").value("محمد أحمد"))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.accountName").value("شركة سند"))
                .andExpect(jsonPath("$.phoneLabel").value("Mobile"))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.preferred").value(true))
                .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.privacyLevel").value("INTERNAL"))
                .andExpect(jsonPath("$.matchSource").value("CANONICAL_COMMUNICATION_METHOD"));
    }

    @Test
    void lookupAcceptsAllSaudiInputForms() throws Exception {
        Fixture fixture = fixture("g8-forms");
        contact(fixture, "شخص", "ش", "خ", SA_PHONE, "ACTIVE", "INTERNAL", false, "UNVERIFIED", false);

        for (String raw : List.of("0541234567", "541234567", "966541234567", "+966541234567",
                "00966541234567", "05-4123-4567", " 054 123 4567 ")) {
            mockMvc.perform(post(LOOKUP).with(authentication(auth(fixture)))
                            .contentType("application/json")
                            .content("{\"phone\":\"" + raw + "\",\"countryHint\":\"SA\",\"source\":\"MANUAL\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.matchStatus").value("EXACT"));
        }
    }

    // ===== UNKNOWN / no auto-create =====

    @Test
    void lookupReturnsUnknownWhenNothingMatches() throws Exception {
        Fixture fixture = fixture("g8-unknown");

        mockMvc.perform(post(LOOKUP).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"phone\":\"+966599999999\",\"countryHint\":\"SA\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.entityId").doesNotExist());
    }

    // ===== AMBIGUOUS =====

    @Test
    void duplicateSameTenantReturnsAmbiguousCountOnly() throws Exception {
        Fixture fixture = fixture("g8-ambiguous");
        contact(fixture, "أحمد الأول", "أ", "و", SA_PHONE, "ACTIVE", "INTERNAL", true, "VERIFIED", false);
        contact(fixture, "أحمد الثاني", "أ", "و", SA_PHONE, "ACTIVE", "INTERNAL", true, "VERIFIED", false);

        mockMvc.perform(post(LOOKUP).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"phone\":\"0541234567\",\"countryHint\":\"SA\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchStatus").value("AMBIGUOUS"))
                .andExpect(jsonPath("$.candidateCount").value(2))
                .andExpect(jsonPath("$.entityId").doesNotExist())
                .andExpect(jsonPath("$.displayName").doesNotExist())
                .andExpect(jsonPath("$.candidateCount").isNumber());
    }

    // ===== Lead fallback (G8-ADR-002) =====

    @Test
    void leadFallbackReturnsExactLeadWithLegacySource() throws Exception {
        Fixture fixture = fixture("g8-lead");
        lead(fixture, "عميل محتمل", "شركة ناشئة", "0541234567", "NEW");

        mockMvc.perform(post(LOOKUP).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"phone\":\"0541234567\",\"countryHint\":\"SA\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchStatus").value("EXACT"))
                .andExpect(jsonPath("$.entityType").value("LEAD"))
                .andExpect(jsonPath("$.matchSource").value("LEGACY_LEAD_PHONE"));
    }

    // ===== PRIVATE / INVALID =====

    @Test
    void privateNumberReturnsPrivateNumberWithoutLookup() throws Exception {
        Fixture fixture = fixture("g8-private");

        mockMvc.perform(post(LOOKUP).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"phone\":\"PRIVATE\",\"countryHint\":\"SA\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchStatus").value("PRIVATE_NUMBER"));
    }

    @Test
    void invalidPhoneIsA422StructuredError() throws Exception {
        Fixture fixture = fixture("g8-invalid");

        mockMvc.perform(post(LOOKUP).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"phone\":\"not-a-number\",\"countryHint\":\"SA\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CALLER_PHONE_INVALID"));
    }

    // ===== Privacy: CONFIDENTIAL / RESTRICTED =====

    @Test
    void confidentialCardIsMaskedServerSideWithoutRestrictedCapability() throws Exception {
        Fixture fixture = fixture("g8-conf");
        contact(fixture, "سرية محمد", "س", "م", SA_PHONE, "ACTIVE", "CONFIDENTIAL",
                true, "VERIFIED", false);

        MvcResult masked = mockMvc.perform(post(LOOKUP).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"phone\":\"0541234567\",\"countryHint\":\"SA\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchStatus").value("EXACT"))
                .andExpect(jsonPath("$.privacyLevel").value("CONFIDENTIAL"))
                .andReturn();
        JsonNode body = mapper.readTree(masked.getResponse().getContentAsString());
        assertThat(body.path("displayName").asText()).isNotEqualTo("سرية محمد");

        Fixture privileged = fixture("g8-conf-priv");
        contact(privileged, "سرية محمد", "س", "م", SA_PHONE, "ACTIVE", "CONFIDENTIAL",
                true, "VERIFIED", false);
        mockMvc.perform(post(LOOKUP).with(authentication(auth(privileged, CAP_RESTRICTED)))
                        .contentType("application/json")
                        .content("{\"phone\":\"0541234567\",\"countryHint\":\"SA\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchStatus").value("EXACT"))
                .andExpect(jsonPath("$.displayName").value("سرية محمد"));
    }

    @Test
    void restrictedRecordIsRestrictedWithoutCapabilityAndExactWithIt() throws Exception {
        Fixture fixture = fixture("g8-restricted");
        contact(fixture, "مقيد جدا", "م", "ق", SA_PHONE, "ACTIVE", "RESTRICTED",
                true, "VERIFIED", false);

        mockMvc.perform(post(LOOKUP).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"phone\":\"0541234567\",\"countryHint\":\"SA\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchStatus").value("RESTRICTED"))
                .andExpect(jsonPath("$.entityId").doesNotExist());

        Fixture privileged = fixture("g8-restricted-priv");
        contact(privileged, "مقيد جدا", "م", "ق", SA_PHONE, "ACTIVE", "RESTRICTED",
                true, "VERIFIED", false);
        mockMvc.perform(post(LOOKUP).with(authentication(auth(privileged, CAP_RESTRICTED)))
                        .contentType("application/json")
                        .content("{\"phone\":\"0541234567\",\"countryHint\":\"SA\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchStatus").value("EXACT"))
                .andExpect(jsonPath("$.displayName").value("مقيد جدا"));
    }

    // ===== Contract validation =====

    @Test
    void tenantIdInBodyIsRejected() throws Exception {
        Fixture fixture = fixture("g8-tenantbody");

        mockMvc.perform(post(LOOKUP).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"phone\":\"0541234567\",\"tenantId\":\"" + fixture.tenantId
                                + "\",\"countryHint\":\"SA\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("tenantId")));
    }

    @Test
    void missingPhoneSourceOrInvalidSourceAreRejected() throws Exception {
        Fixture fixture = fixture("g8-validate");

        mockMvc.perform(post(LOOKUP).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"countryHint\":\"SA\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(LOOKUP).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"phone\":\"0541234567\",\"countryHint\":\"SA\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(LOOKUP).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"phone\":\"0541234567\",\"source\":\"SMOKE_SIGNALS\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonIsA400ContractError() throws Exception {
        Fixture fixture = fixture("g8-malformed");

        mockMvc.perform(post(LOOKUP).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void headersNeverEchoThePhoneInUrlOrLogs() throws Exception {
        Fixture fixture = fixture("g8-nolog");
        contact(fixture, "بلا رقم", "ب", "ل", SA_PHONE, "ACTIVE", "INTERNAL", false, "UNVERIFIED", false);

        MvcResult result = mockMvc.perform(post(LOOKUP).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"phone\":\"0541234567\",\"countryHint\":\"SA\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isOk())
                .andReturn();
        // The minimal card must not carry the phone back to the client.
        assertThat(result.getResponse().getContentAsString()).doesNotContain("541234567");
    }

    // ===== AuthN / AuthZ =====

    @Test
    void unauthenticatedLookupIsRejected() throws Exception {
        mockMvc.perform(post(LOOKUP)
                        .contentType("application/json")
                        .content("{\"phone\":\"0541234567\",\"countryHint\":\"SA\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingCallerCapabilityIsForbidden() throws Exception {
        Fixture fixture = fixtureWithoutCallerCaps("g8-nocap");
        contact(fixture, "شخص محمي", "ش", "خ", SA_PHONE, "ACTIVE", "INTERNAL", false, "UNVERIFIED", false);

        mockMvc.perform(post(LOOKUP).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"phone\":\"0541234567\",\"countryHint\":\"SA\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isForbidden());
    }

    // ===== Anti-enumeration =====

    @Test
    void burstBeyondLimitIsRateLimited() throws Exception {
        Fixture fixture = fixture("g8-ratelimit");
        contact(fixture, "شخص متكرر", "ش", "ت", SA_PHONE, "ACTIVE", "INTERNAL", false, "UNVERIFIED", false);

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post(LOOKUP).with(authentication(auth(fixture)))
                            .contentType("application/json")
                            .content("{\"phone\":\"0541234567\",\"countryHint\":\"SA\",\"source\":\"MANUAL\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post(LOOKUP).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"phone\":\"0541234567\",\"countryHint\":\"SA\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void lookupMetricsAreRecorded() throws Exception {
        Fixture fixture = fixture("g8-metrics");
        contact(fixture, "قياس", "ق", "ي", SA_PHONE, "ACTIVE", "INTERNAL", true, "VERIFIED", false);
        // Tagged lookup keeps the assertion deterministic (result/source are the
        // only metric labels — never phone/tenant/customer ids, G8-02 §32).
        double before = meterRegistry.get("caller_lookup_total")
                .tag("result", "EXACT").tag("source", "MANUAL").counter().count();

        mockMvc.perform(post(LOOKUP).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"phone\":\"0541234567\",\"countryHint\":\"SA\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isOk());

        assertThat(meterRegistry.get("caller_lookup_total")
                .tag("result", "EXACT").tag("source", "MANUAL").counter().count())
                .isGreaterThan(before);
    }

    // ===== Fixtures =====

    private static final String CAP = "CRM.CALLER_ID.READ";
    private static final String CAP_RESTRICTED = "CRM.CALLER_ID.READ_RESTRICTED";

    private Fixture fixture(String key) {
        return fixture(key, List.of(CAP, CAP_RESTRICTED));
    }

    private Fixture fixtureWithoutCallerCaps(String key) {
        return fixture(key, List.of("CRM.COMMUNICATION.READ"));
    }

    private Fixture fixture(String key, List<String> capabilities) {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) " +
                        "VALUES (:id,:name,:subdomain,'ACTIVE',:now,:now)",
                p().addValue("id", tenantId).addValue("name", key)
                        .addValue("subdomain", key + "-" + tenantId.toString().substring(0, 8))
                        .addValue("now", java.sql.Timestamp.from(now)));
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:email,'G8 Caller User','ACTIVE','dummy',:now,:now)",
                p().addValue("id", userId).addValue("tenantId", tenantId)
                        .addValue("email", key + "-" + userId.toString().substring(0, 8) + "@example.test")
                        .addValue("now", java.sql.Timestamp.from(now)));
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:code,'G8 Caller Role','G8 caller tests','ACTIVE',:now,:now)",
                p().addValue("id", roleId).addValue("tenantId", tenantId)
                        .addValue("code", "G8_" + key.toUpperCase().replace('-', '_'))
                        .addValue("now", java.sql.Timestamp.from(now)));
        List<UUID> capabilityIds = jdbc.query("SELECT id FROM access_capabilities WHERE code IN (:codes)",
                p().addValue("codes", capabilities), (rs, row) -> rs.getObject("id", UUID.class));
        assertThat(capabilityIds).as("capabilities %s must be seeded by migrations", capabilities)
                .hasSize(capabilities.size());
        for (UUID capabilityId : capabilityIds) {
            jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) " +
                            "VALUES (:id,:tenantId,:roleId,:capabilityId,:now)",
                    p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                            .addValue("roleId", roleId).addValue("capabilityId", capabilityId)
                            .addValue("now", java.sql.Timestamp.from(now)));
        }
        jdbc.update("INSERT INTO user_role_assignments (id,tenant_id,user_id,role_id,organization_id,status,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:userId,:roleId,NULL,'ACTIVE',:now,:now)",
                p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                        .addValue("userId", userId).addValue("roleId", roleId)
                        .addValue("now", java.sql.Timestamp.from(now)));
        return new Fixture(tenantId, userId);
    }

    private UUID account(Fixture fixture, String displayName) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO crm_accounts (id,tenant_id,version,display_name,normalized_name,account_type," +
                        "lifecycle_status,owner_user_id,created_by,updated_by,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,0,:displayName,:normalized,'BUSINESS','ACTIVE',:owner,:owner,:owner,:now,:now)",
                p().addValue("id", id).addValue("tenantId", fixture.tenantId)
                        .addValue("displayName", displayName)
                        .addValue("normalized", displayName.toLowerCase())
                        .addValue("owner", fixture.userId)
                        .addValue("now", java.sql.Timestamp.from(now)));
        return id;
    }

    private UUID contact(Fixture fixture, String displayName, String given, String family,
                         String phone, String lifecycle, String privacy,
                         boolean verified, String verification, boolean preferred) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO crm_contacts (id,tenant_id,version,account_id,given_name,family_name,display_name," +
                        "normalized_name,preferred_locale,time_zone,lifecycle_status,owner_user_id,consent_summary," +
                        "created_by,updated_by,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,0,NULL,:given,:family,:displayName,:normalized,'ar-SA','Asia/Riyadh'," +
                        ":lifecycle,:owner,'GRANTED',:owner,:owner,:now,:now)",
                p().addValue("id", id).addValue("tenantId", fixture.tenantId)
                        .addValue("given", given).addValue("family", family)
                        .addValue("displayName", displayName)
                        .addValue("normalized", displayName.toLowerCase())
                        .addValue("lifecycle", lifecycle).addValue("owner", fixture.userId)
                        .addValue("now", java.sql.Timestamp.from(now)));
        communicationMethod(fixture, id, phone, verified, verification, preferred, privacy);
        return id;
    }

    private void communicationMethod(Fixture fixture, UUID ownerId, String phone,
                                     boolean verified, String verification, boolean preferred, String privacy) {
        Instant now = Instant.now();
        jdbc.update("INSERT INTO crm_communication_methods (id,tenant_id,version,owner_type,owner_id,account_id," +
                        "contact_id,method_type,raw_value,normalized_value,display_value,label,preferred,preferred_slot," +
                        "verified,verification_status,privacy_classification,consent_state_reference,usage_purpose,status," +
                        "created_by,updated_by,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,0,'PERSON',:ownerId,NULL,:ownerId,'MOBILE',:raw,:normalized,:display," +
                        ":label,:preferred,:slot,:verified,:verification,:privacy,:consent,'BUSINESS','ACTIVE'," +
                        ":actor,:actor,:now,:now)",
                p().addValue("id", UUID.randomUUID()).addValue("tenantId", fixture.tenantId)
                        .addValue("ownerId", ownerId).addValue("raw", phone)
                        .addValue("normalized", phone).addValue("display", phone)
                        .addValue("label", "Mobile")
                        .addValue("preferred", preferred).addValue("slot", preferred ? 1 : null)
                        .addValue("verified", verified).addValue("verification", verification)
                        .addValue("privacy", privacy).addValue("consent", "C-REF-G8")
                        .addValue("actor", fixture.userId).addValue("now", java.sql.Timestamp.from(now)));
    }

    private UUID lead(Fixture fixture, String displayName, String company, String phone, String status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO crm_leads (id,tenant_id,version,display_name,normalized_name,company_name,phone," +
                        "source,status,owner_user_id,created_by,updated_by,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,0,:displayName,:normalized,:company,:phone,'PHONE_CALL',:status," +
                        ":owner,:owner,:owner,:now,:now)",
                p().addValue("id", id).addValue("tenantId", fixture.tenantId)
                        .addValue("displayName", displayName)
                        .addValue("normalized", displayName.toLowerCase())
                        .addValue("company", company).addValue("phone", phone)
                        .addValue("status", status).addValue("owner", fixture.userId)
                        .addValue("now", java.sql.Timestamp.from(now)));
        return id;
    }

    private Authentication auth(Fixture fixture, String... extraAuthorities) {
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", fixture.tenantId.toString());
        details.put("user_id", fixture.userId.toString());
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        for (String authority : extraAuthorities) {
            authorities.add(new SimpleGrantedAuthority("CAPABILITY_" + authority));
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                fixture.userId.toString(), null, authorities);
        authentication.setDetails(details);
        return authentication;
    }

    private static MapSqlParameterSource p() { return new MapSqlParameterSource(); }

    private record Fixture(UUID tenantId, UUID userId) {}
}
