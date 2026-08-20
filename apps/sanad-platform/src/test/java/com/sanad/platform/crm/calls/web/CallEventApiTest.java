package com.sanad.platform.crm.calls.web;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G8 call event API contract (G8-03 §67): 401/403, valid write/read,
 * duplicate replay, invalid transition, cross-tenant read isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class CallEventApiTest {

    private static final String EVENTS = "/api/v2/crm/calls/events";
    private static final String CALLS = "/api/v2/crm/calls";
    private static final List<String> CAPABILITIES = List.of(
            "CRM.CALL_EVENT.READ", "CRM.CALL_EVENT.WRITE", "CRM.CALLER_ID.READ");

    @Autowired MockMvc mockMvc;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    private String body(String callId, String status) {
        return "{\"provider\":\"NATIVE\",\"providerCallId\":\"" + callId
                + "\",\"direction\":\"INBOUND\",\"source\":\"ANDROID_CALL\","
                + "\"phone\":\"0541234567\",\"status\":\"" + status
                + "\",\"occurredAt\":\"" + Instant.now() + "\"}";
    }

    @Test
    void validWriteCreatesAndReadsBack() throws Exception {
        Fixture fixture = fixture("ce-api-1");

        MvcResult created = mockMvc.perform(post(EVENTS).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content(body("api-1", "RINGING")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RINGING"))
                .andExpect(jsonPath("$.matchStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.fromNumberMasked").value("••••4567"))
                .andReturn();
        String callId = mapper.readTree(created.getResponse().getContentAsString()).path("id").asText();

        mockMvc.perform(get(CALLS + "/" + callId).with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(callId));
    }

    @Test
    void duplicateEventIsIdempotentReplayWith200() throws Exception {
        Fixture fixture = fixture("ce-api-2");

        mockMvc.perform(post(EVENTS).with(authentication(auth(fixture)))
                        .contentType("application/json").content(body("api-2", "RINGING")))
                .andExpect(status().isCreated());
        mockMvc.perform(post(EVENTS).with(authentication(auth(fixture)))
                        .contentType("application/json").content(body("api-2", "RINGING")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RINGING"));
    }

    @Test
    void lifecycleTransitionsToCompletedWithDuration() throws Exception {
        Fixture fixture = fixture("ce-api-3");
        Instant answeredAt = Instant.now().minusSeconds(30);

        mockMvc.perform(post(EVENTS).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"provider\":\"NATIVE\",\"providerCallId\":\"api-3\",\"direction\":\"INBOUND\"," +
                                "\"source\":\"ANDROID_CALL\",\"phone\":\"0541234567\",\"status\":\"ANSWERED\"," +
                                "\"occurredAt\":\"" + answeredAt + "\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post(EVENTS).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"provider\":\"NATIVE\",\"providerCallId\":\"api-3\",\"direction\":\"INBOUND\"," +
                                "\"source\":\"ANDROID_CALL\",\"phone\":\"0541234567\",\"status\":\"COMPLETED\"," +
                                "\"occurredAt\":\"" + Instant.now() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.durationSeconds").isNumber())
                .andExpect(jsonPath("$.disposition").value("CONNECTED"));
    }

    @Test
    void invalidTransitionIs422() throws Exception {
        Fixture fixture = fixture("ce-api-4");

        mockMvc.perform(post(EVENTS).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"provider\":\"NATIVE\",\"providerCallId\":\"api-4\",\"direction\":\"INBOUND\"," +
                                "\"source\":\"ANDROID_CALL\",\"phone\":\"0541234567\",\"status\":\"RINGING\"," +
                                "\"occurredAt\":\"" + Instant.now() + "\"}"))
                .andExpect(status().isCreated());
        // RINGING -> COMPLETED skips ANSWERED: non-regression illegal transition.
        mockMvc.perform(post(EVENTS).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"provider\":\"NATIVE\",\"providerCallId\":\"api-4\",\"direction\":\"INBOUND\"," +
                                "\"source\":\"ANDROID_CALL\",\"phone\":\"0541234567\",\"status\":\"COMPLETED\"," +
                                "\"occurredAt\":\"" + Instant.now() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CALL_EVENT_INVALID_TRANSITION"));
    }

    @Test
    void unauthenticatedAndUnauthorizedAreRejected() throws Exception {
        mockMvc.perform(post(EVENTS)
                        .contentType("application/json")
                        .content(body("api-401", "RINGING")))
                .andExpect(status().isUnauthorized());

        Fixture noCaps = fixtureWithoutCaps("ce-api-403");
        mockMvc.perform(post(EVENTS).with(authentication(auth(noCaps)))
                        .contentType("application/json")
                        .content(body("api-403", "RINGING")))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossTenantReadIsIsolated() throws Exception {
        Fixture tenantA = fixture("ce-api-a");
        Fixture tenantB = fixture("ce-api-b");

        MvcResult created = mockMvc.perform(post(EVENTS).with(authentication(auth(tenantA)))
                        .contentType("application/json")
                        .content(body("cross-1", "RINGING")))
                .andExpect(status().isCreated())
                .andReturn();
        String callId = mapper.readTree(created.getResponse().getContentAsString()).path("id").asText();

        // Tenant B must not see tenant A's call.
        mockMvc.perform(get(CALLS + "/" + callId).with(authentication(auth(tenantB))))
                .andExpect(status().isNotFound());
        // Tenant B's list stays empty.
        mockMvc.perform(get(CALLS).with(authentication(auth(tenantB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void malformedInputIs400ContractError() throws Exception {
        Fixture fixture = fixture("ce-api-5");

        mockMvc.perform(post(EVENTS).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void exactCallerIsBoundAndMaskedResponseNeverLeaksPhone() throws Exception {
        Fixture fixture = fixture("ce-api-6");
        UUID contactId = contact(fixture, "محمد أحمد", "+966541234567");

        MvcResult result = mockMvc.perform(post(EVENTS).with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content(body("api-6", "RINGING")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.matchStatus").value("EXACT"))
                .andExpect(jsonPath("$.matchedEntityType").value("CONTACT"))
                .andExpect(jsonPath("$.matchedEntityId").value(contactId.toString()))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertThat(json).doesNotContain("541234567").doesNotContain("+966");
    }

    // ── fixtures (mirror the CRM-007 integration pattern) ─────────────────

    private Fixture fixture(String key) {
        return fixture(key, CAPABILITIES);
    }

    private Fixture fixtureWithoutCaps(String key) {
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
                        "VALUES (:id,:tenantId,:email,'G8 Call User','ACTIVE','dummy',:now,:now)",
                p().addValue("id", userId).addValue("tenantId", tenantId)
                        .addValue("email", key + "-" + userId.toString().substring(0, 8) + "@example.test")
                        .addValue("now", java.sql.Timestamp.from(now)));
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:code,'G8 Call Role','G8 call tests','ACTIVE',:now,:now)",
                p().addValue("id", roleId).addValue("tenantId", tenantId)
                        .addValue("code", "G8C_" + key.toUpperCase().replace('-', '_'))
                        .addValue("now", java.sql.Timestamp.from(now)));
        List<UUID> capabilityIds = jdbc.query("SELECT id FROM access_capabilities WHERE code IN (:codes)",
                p().addValue("codes", capabilities), (rs, row) -> rs.getObject("id", UUID.class));
        assertThat(capabilityIds).hasSize(capabilities.size());
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

    private UUID contact(Fixture fixture, String displayName, String phone) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO crm_contacts (id,tenant_id,version,account_id,given_name,family_name,display_name," +
                        "normalized_name,preferred_locale,time_zone,lifecycle_status,owner_user_id,consent_summary," +
                        "created_by,updated_by,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,0,NULL,:given,:family,:displayName,:normalized,'ar-SA','Asia/Riyadh'," +
                        "'ACTIVE',:owner,'GRANTED',:owner,:owner,:now,:now)",
                p().addValue("id", id).addValue("tenantId", fixture.tenantId)
                        .addValue("given", displayName.substring(0, 1))
                        .addValue("family", displayName.substring(Math.min(1, displayName.length() - 1)))
                        .addValue("displayName", displayName)
                        .addValue("normalized", displayName.toLowerCase())
                        .addValue("owner", fixture.userId)
                        .addValue("now", java.sql.Timestamp.from(now)));
        jdbc.update("INSERT INTO crm_communication_methods (id,tenant_id,version,owner_type,owner_id,account_id," +
                        "contact_id,method_type,raw_value,normalized_value,display_value,label,preferred,preferred_slot," +
                        "verified,verification_status,privacy_classification,consent_state_reference,usage_purpose,status," +
                        "created_by,updated_by,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,0,'PERSON',:contactId,NULL,:contactId,'MOBILE',:phone,:phone,:phone," +
                        "'Mobile',TRUE,1,TRUE,'VERIFIED','INTERNAL','C-REF','BUSINESS','ACTIVE',:owner,:owner,:now,:now)",
                p().addValue("id", UUID.randomUUID()).addValue("tenantId", fixture.tenantId)
                        .addValue("contactId", id).addValue("phone", phone)
                        .addValue("owner", fixture.userId).addValue("now", java.sql.Timestamp.from(now)));
        return id;
    }

    private Authentication auth(Fixture fixture) {
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", fixture.tenantId.toString());
        details.put("user_id", fixture.userId.toString());
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                fixture.userId.toString(), null, authorities);
        authentication.setDetails(details);
        return authentication;
    }

    private static MapSqlParameterSource p() { return new MapSqlParameterSource(); }

    private record Fixture(UUID tenantId, UUID userId) {}
}
