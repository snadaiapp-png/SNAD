package com.sanad.platform.crm.caller.web;

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
import org.springframework.test.context.TestPropertySource;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G8 offline caller dataset delta API (G8-03 §37–§38, §68): auth, key
 * issuance, entry shape (no plaintext phones), tenant isolation, cursor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@TestPropertySource(properties = "sanad.caller-dataset.master-key=g8-test-master-key")
class CallerDatasetApiTest {

    private static final String DELTA = "/api/v2/crm/caller-identification/delta";
    private static final String MASTER_KEY = "g8-test-master-key";
    private static final List<String> CAPABILITIES = List.of("CRM.CALLER_ID.READ");

    @Autowired MockMvc mockMvc;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void firstSyncIssuesTenantKeyAndEntriesCarryOnlyTokens() throws Exception {
        Fixture fixture = fixture("ds-api-1");
        contact(fixture, "محمد أحمد", "+966541234567");

        MvcResult result = mockMvc.perform(get(DELTA + "?keyMissing=true").with(authentication(auth(fixture)))
                        .header("X-Device-Id", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetVersion").value(1))
                .andExpect(jsonPath("$.entries").isArray())
                .andExpect(jsonPath("$.entries[0].entityType").value("CONTACT"))
                .andExpect(jsonPath("$.entries[0].displayName").value("محمد أحمد"))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertThat(json).doesNotContain("+966").doesNotContain("541234567");
        assertThat(mapper.readTree(json).path("datasetKey").asText()).isNotBlank();
    }

    @Test
    void unauthenticatedAndUnauthorizedAreRejected() throws Exception {
        mockMvc.perform(get(DELTA)).andExpect(status().isUnauthorized());

        Fixture noCaps = fixtureWithoutCaps("ds-api-403");
        mockMvc.perform(get(DELTA).with(authentication(auth(noCaps))))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidCursorIs400() throws Exception {
        Fixture fixture = fixture("ds-api-2");

        mockMvc.perform(get(DELTA + "?cursor=!!!").with(authentication(auth(fixture))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void datasetIsTenantIsolatedAcrossSameNumber() throws Exception {
        Fixture tenantA = fixture("ds-api-a");
        Fixture tenantB = fixture("ds-api-b");
        UUID contactA = contact(tenantA, "عميل أ", "+966541234567");
        UUID contactB = contact(tenantB, "عميل ب", "+966541234567");

        // TenantRlsConnectionHandler caches `tenantApplied=true` on the
        // transaction-bound Connection proxy after the first SET LOCAL. Once
        // cached, subsequent SecurityContext changes mid-transaction do NOT
        // re-issue SET LOCAL, so the GUC stays at whatever value was last
        // applied. The fixture's contact() helper sets the GUC to tenantB
        // (the last contact() call), which would leak into the first mockMvc
        // call for tenantA. Explicitly re-assert the GUC before each mockMvc
        // call so the correct tenant is in effect for the query.
        jdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)",
                p().addValue("t", tenantA.tenantId().toString()), String.class);
        MvcResult resultA = mockMvc.perform(get(DELTA).with(authentication(auth(tenantA))))
                .andExpect(status().isOk()).andReturn();
        String jsonA = resultA.getResponse().getContentAsString();
        assertThat(jsonA).contains(contactA.toString()).doesNotContain(contactB.toString());

        jdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)",
                p().addValue("t", tenantB.tenantId().toString()), String.class);
        MvcResult resultB = mockMvc.perform(get(DELTA).with(authentication(auth(tenantB))))
                .andExpect(status().isOk()).andReturn();
        String jsonB = resultB.getResponse().getContentAsString();
        assertThat(jsonB).contains(contactB.toString()).doesNotContain(contactA.toString());
        // Tenant-bound tokens differ.
        assertThat(mapper.readTree(jsonA).path("entries").get(0).path("lookupToken").asText())
                .isNotEqualTo(mapper.readTree(jsonB).path("entries").get(0).path("lookupToken").asText());
    }

    @Test
    void restrictedEntriesNeverCarryDisplayPii() throws Exception {
        Fixture fixture = fixture("ds-api-3");
        contact(fixture, "مقيد جدا", "+966599999999", "RESTRICTED");

        mockMvc.perform(get(DELTA).with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].privacyLevel").value("RESTRICTED"))
                .andExpect(jsonPath("$.entries[0].displayName").doesNotExist())
                .andExpect(jsonPath("$.entries[0].entityId").doesNotExist());
    }

    // ── fixtures ──────────────────────────────────────────────────────────

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
                        "VALUES (:id,:tenantId,:email,'G8 Dataset User','ACTIVE','dummy',:now,:now)",
                p().addValue("id", userId).addValue("tenantId", tenantId)
                        .addValue("email", key + "-" + userId.toString().substring(0, 8) + "@example.test")
                        .addValue("now", java.sql.Timestamp.from(now)));
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:code,'G8 Dataset Role','G8 dataset tests','ACTIVE',:now,:now)",
                p().addValue("id", roleId).addValue("tenantId", tenantId)
                        .addValue("code", "G8D_" + key.toUpperCase().replace('-', '_'))
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
        return contact(fixture, displayName, phone, "INTERNAL");
    }

    private UUID contact(Fixture fixture, String displayName, String phone, String privacy) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)", p().addValue("t", fixture.tenantId().toString()), String.class);
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
                        "'Mobile',TRUE,1,TRUE,'VERIFIED',:privacy,'C-REF','BUSINESS','ACTIVE',:owner,:owner,:now,:now)",
                p().addValue("id", UUID.randomUUID()).addValue("tenantId", fixture.tenantId)
                        .addValue("contactId", id).addValue("phone", phone).addValue("privacy", privacy)
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
