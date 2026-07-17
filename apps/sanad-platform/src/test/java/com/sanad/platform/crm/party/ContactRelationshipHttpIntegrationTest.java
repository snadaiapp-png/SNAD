package com.sanad.platform.crm.party;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.query.domain.Customer360QueryPort;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class ContactRelationshipHttpIntegrationTest {

    private static final List<String> CAPABILITIES = List.of(
            "CRM.ACCOUNT.READ",
            "CRM.CONTACT.READ",
            "CRM.CONTACT.WRITE",
            "CRM.CONTACT.ARCHIVE",
            "CRM.CONTACT.SENSITIVE.READ",
            "CRM.RELATIONSHIP.READ",
            "CRM.RELATIONSHIP.WRITE",
            "CRM.RELATIONSHIP.ADMIN");

    @Autowired MockMvc mockMvc;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired Customer360QueryPort customer360;

    @Test
    void supportsZeroOneAndMultipleAccountsWithOnePrimary() throws Exception {
        Fixture fixture = fixture("multi-account", true);
        UUID contactId = contact(fixture, "عبدالرحمن", "سنان", "person@example.test");
        UUID firstAccount = account(fixture, "الحساب الأول");
        UUID secondAccount = account(fixture, "الحساب الثاني");

        mockMvc.perform(get("/api/v2/crm/contacts/{id}/relationships", contactId)
                        .with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        JsonNode first = createRelationship(fixture, contactId, firstAccount,
                "DECISION_MAKER", true, "2026-01-01", null);
        JsonNode second = createRelationship(fixture, contactId, secondAccount,
                "TECHNICAL", true, "2026-02-01", "2027-02-01");

        assertThat(first.path("id").asText()).isNotBlank();
        assertThat(second.path("primaryRelationship").asBoolean()).isTrue();

        MvcResult result = mockMvc.perform(get("/api/v2/crm/contacts/{id}/relationships", contactId)
                        .with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn();
        JsonNode relationships = mapper.readTree(result.getResponse().getContentAsString()).path("data");
        long primaryCount = 0;
        for (JsonNode relationship : relationships) {
            if (relationship.path("primaryRelationship").asBoolean()) primaryCount++;
        }
        assertThat(primaryCount).isEqualTo(1);
        assertThat(relationships.toString()).contains(secondAccount.toString());

        mockMvc.perform(get("/api/v2/crm/accounts/{id}/contact-relationships", firstAccount)
                        .with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].contactId").value(contactId.toString()))
                .andExpect(jsonPath("$.data[0].roleCode").value("DECISION_MAKER"));

        Customer360QueryPort.Customer360View view = customer360.getCustomer360(fixture.tenantId(), secondAccount);
        assertThat(view.contacts()).hasSize(1);
        assertThat(view.contacts().get(0).relationshipRole()).isEqualTo("TECHNICAL");
        assertThat(view.contacts().get(0).primaryRelationship()).isTrue();
    }

    @Test
    void enforcesDatesDuplicatesTenantIsolationAndOptimisticConcurrency() throws Exception {
        Fixture tenantA = fixture("tenant-a", true);
        Fixture tenantB = fixture("tenant-b", true);
        UUID contactId = contact(tenantA, "Mona", "Saleh", "mona@example.test");
        UUID accountId = account(tenantA, "Acme A");
        UUID foreignAccount = account(tenantB, "Acme B");

        mockMvc.perform(post("/api/v2/crm/contacts/{id}/relationships", contactId)
                        .with(authentication(auth(tenantA)))
                        .contentType("application/json")
                        .content("""
                                {"accountId":"%s","roleCode":"BILLING","primaryRelationship":false,
                                 "validFrom":"2026-12-31","validTo":"2026-01-01","decisionAuthority":"NONE"}
                                """.formatted(accountId)))
                .andExpect(status().isBadRequest());

        JsonNode created = createRelationship(tenantA, contactId, accountId,
                "BILLING", false, "2026-01-01", null);
        UUID relationshipId = UUID.fromString(created.path("id").asText());

        mockMvc.perform(post("/api/v2/crm/contacts/{id}/relationships", contactId)
                        .with(authentication(auth(tenantA)))
                        .contentType("application/json")
                        .content("""
                                {"accountId":"%s","roleCode":"BILLING","primaryRelationship":false,
                                 "decisionAuthority":"NONE"}
                                """.formatted(accountId)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v2/crm/contacts/{id}/relationships", contactId)
                        .with(authentication(auth(tenantA)))
                        .contentType("application/json")
                        .content("""
                                {"accountId":"%s","roleCode":"TECHNICAL","primaryRelationship":false,
                                 "decisionAuthority":"NONE"}
                                """.formatted(foreignAccount)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v2/crm/contact-relationships/{id}", relationshipId)
                        .with(authentication(auth(tenantB))))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v2/crm/contact-relationships/{id}", relationshipId)
                        .with(authentication(auth(tenantA)))
                        .header("If-Match", "\"stale\"")
                        .contentType("application/json")
                        .content("{\"department\":\"Finance\"}"))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void persistsProfileOwnershipAuditTimelineAndRelationshipHistory() throws Exception {
        Fixture fixture = fixture("audit-history", true);
        UUID contactId = contact(fixture, "Layla", "Hassan", "layla@example.test");
        UUID accountId = account(fixture, "History Account");
        UUID relationshipId = UUID.fromString(createRelationship(
                fixture, contactId, accountId, "INFLUENCER", false, null, null).path("id").asText());

        String profileEtag = mockMvc.perform(get("/api/v2/crm/contacts/{id}/profile", contactId)
                        .with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Layla Hassan"))
                .andReturn().getResponse().getHeader("ETag");

        UUID newOwner = user(fixture.tenantId(), "new-owner");
        mockMvc.perform(patch("/api/v2/crm/contacts/{id}/profile", contactId)
                        .with(authentication(auth(fixture)))
                        .header("If-Match", profileEtag)
                        .contentType("application/json")
                        .content("""
                                {"legalName":"ليلى حسن","preferredName":"ليلى","middleName":"محمد",
                                 "pronouns":"هي","preferredLocale":"ar-SA","timeZone":"Asia/Riyadh",
                                 "ownerUserId":"%s","ownerChangeReason":"Territory reassignment"}
                                """.formatted(newOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legalName").value("ليلى حسن"))
                .andExpect(jsonPath("$.data.ownerUserId").value(newOwner.toString()));

        Integer ownership = count("crm_contact_ownership_history", fixture.tenantId(), "contact_id", contactId);
        Integer audit = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform_audit_logs WHERE target_tenant_id=:tenantId " +
                        "AND resource_id=:resourceId AND action IN ('UPDATE','OWNER_CHANGE')",
                p().addValue("tenantId", fixture.tenantId()).addValue("resourceId", contactId.toString()),
                Integer.class);
        Integer timeline = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_timeline_events WHERE tenant_id=:tenantId AND subject_id=:contactId " +
                        "AND event_type IN ('crm.contact.profile.updated','crm.contact.owner.changed')",
                p().addValue("tenantId", fixture.tenantId()).addValue("contactId", contactId), Integer.class);
        assertThat(ownership).isEqualTo(1);
        assertThat(audit).isEqualTo(2);
        assertThat(timeline).isEqualTo(2);

        mockMvc.perform(get("/api/v2/crm/contact-relationships/{id}/history", relationshipId)
                        .with(authentication(auth(fixture))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventType").value("CREATED"));
    }

    @Test
    void controlsArchiveReactivationAndCustomRoles() throws Exception {
        Fixture fixture = fixture("lifecycle", true);
        UUID contactId = contact(fixture, "Noura", "Ali", "noura@example.test");
        UUID accountId = account(fixture, "Partner Account");

        MvcResult roleResult = mockMvc.perform(post("/api/v2/crm/relationship-roles")
                        .with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"code\":\"BOARD_ADVISOR\",\"nameAr\":\"مستشار مجلس\",\"nameEn\":\"Board advisor\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID customRoleId = UUID.fromString(mapper.readTree(roleResult.getResponse().getContentAsString())
                .path("data").path("id").asText());

        MvcResult create = mockMvc.perform(post("/api/v2/crm/contacts/{id}/relationships", contactId)
                        .with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("""
                                {"accountId":"%s","roleCode":"OTHER","customRoleId":"%s",
                                 "primaryRelationship":false,"decisionAuthority":"RECOMMENDER"}
                                """.formatted(accountId, customRoleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.customRoleNameAr").value("مستشار مجلس"))
                .andReturn();
        JsonNode response = mapper.readTree(create.getResponse().getContentAsString()).path("data");
        UUID relationshipId = UUID.fromString(response.path("id").asText());
        String etag = create.getResponse().getHeader("ETag");

        MvcResult archived = mockMvc.perform(patch("/api/v2/crm/contact-relationships/{id}/archive", relationshipId)
                        .with(authentication(auth(fixture))).header("If-Match", etag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                .andReturn();

        mockMvc.perform(patch("/api/v2/crm/contact-relationships/{id}", relationshipId)
                        .with(authentication(auth(fixture)))
                        .header("If-Match", archived.getResponse().getHeader("ETag"))
                        .contentType("application/json").content("{\"department\":\"Board\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/v2/crm/contact-relationships/{id}/reactivate", relationshipId)
                        .with(authentication(auth(fixture)))
                        .header("If-Match", archived.getResponse().getHeader("ETag")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void deniesUnauthenticatedAndMissingCapabilitiesAndDoesNotMergeDuplicateEmail() throws Exception {
        Fixture allowed = fixture("allowed", true);
        Fixture denied = fixture("denied", false);
        UUID contactOne = contact(allowed, "One", "Person", "duplicate@example.test");
        UUID contactTwo = contact(allowed, "Two", "Person", "duplicate@example.test");
        assertThat(contactOne).isNotEqualTo(contactTwo);
        Integer duplicates = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contacts WHERE tenant_id=:tenantId AND normalized_email=:email",
                p().addValue("tenantId", allowed.tenantId()).addValue("email", "duplicate@example.test"),
                Integer.class);
        assertThat(duplicates).isEqualTo(2);

        mockMvc.perform(get("/api/v2/crm/contacts/{id}/relationships", contactOne))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v2/crm/contacts/{id}/relationships", contactOne)
                        .with(authentication(auth(denied))))
                .andExpect(status().isForbidden());
    }

    private JsonNode createRelationship(
            Fixture fixture, UUID contactId, UUID accountId, String role,
            boolean primary, String validFrom, String validTo) throws Exception {
        String dates = (validFrom == null ? "" : ",\"validFrom\":\"" + validFrom + "\"")
                + (validTo == null ? "" : ",\"validTo\":\"" + validTo + "\"");
        MvcResult result = mockMvc.perform(post("/api/v2/crm/contacts/{id}/relationships", contactId)
                        .with(authentication(auth(fixture)))
                        .contentType("application/json")
                        .content("{\"accountId\":\"" + accountId + "\",\"roleCode\":\"" + role +
                                "\",\"primaryRelationship\":" + primary +
                                ",\"decisionAuthority\":\"NONE\"" + dates + "}"))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private Fixture fixture(String key, boolean grantCapabilities) {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) " +
                        "VALUES (:id,:name,:subdomain,'ACTIVE',:now,:now)",
                p().addValue("id", tenantId).addValue("name", key)
                        .addValue("subdomain", key + "-" + tenantId.toString().substring(0, 8)).addValue("now", now));
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:email,'CRM 006 User','ACTIVE','dummy',:now,:now)",
                p().addValue("id", userId).addValue("tenantId", tenantId)
                        .addValue("email", key + "-" + userId.toString().substring(0, 8) + "@example.test")
                        .addValue("now", now));
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:code,'CRM 006 Role','CRM-006 tests','ACTIVE',:now,:now)",
                p().addValue("id", roleId).addValue("tenantId", tenantId)
                        .addValue("code", "CRM006_" + key.toUpperCase().replace('-', '_')).addValue("now", now));
        if (grantCapabilities) {
            List<UUID> capabilityIds = jdbc.query(
                    "SELECT id FROM access_capabilities WHERE code IN (:codes)",
                    p().addValue("codes", CAPABILITIES),
                    (resultSet, row) -> resultSet.getObject("id", UUID.class));
            assertThat(capabilityIds).hasSize(CAPABILITIES.size());
            for (UUID capabilityId : capabilityIds) {
                jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) " +
                                "VALUES (:id,:tenantId,:roleId,:capabilityId,:now)",
                        p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                                .addValue("roleId", roleId).addValue("capabilityId", capabilityId).addValue("now", now));
            }
        }
        jdbc.update("INSERT INTO user_role_assignments " +
                        "(id,tenant_id,user_id,role_id,organization_id,status,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:userId,:roleId,NULL,'ACTIVE',:now,:now)",
                p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                        .addValue("userId", userId).addValue("roleId", roleId).addValue("now", now));
        return new Fixture(tenantId, userId);
    }

    private UUID user(UUID tenantId, String key) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:email,:name,'ACTIVE','dummy',:now,:now)",
                p().addValue("id", id).addValue("tenantId", tenantId)
                        .addValue("email", key + "-" + id.toString().substring(0, 8) + "@example.test")
                        .addValue("name", key).addValue("now", now));
        return id;
    }

    private UUID account(Fixture fixture, String name) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO crm_accounts (id,tenant_id,version,display_name,normalized_name,account_type," +
                        "lifecycle_status,primary_currency_code,preferred_locale,time_zone,source,owner_user_id," +
                        "created_by,updated_by,created_at,updated_at) VALUES (:id,:tenantId,0,:name,:normalized," +
                        "'BUSINESS','ACTIVE','SAR','ar-SA','Asia/Riyadh','CRM006_TEST',:owner,:owner,:owner,:now,:now)",
                p().addValue("id", id).addValue("tenantId", fixture.tenantId()).addValue("name", name)
                        .addValue("normalized", name.toLowerCase()).addValue("owner", fixture.userId()).addValue("now", now));
        return id;
    }

    private UUID contact(Fixture fixture, String givenName, String familyName, String email) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String displayName = givenName + " " + familyName;
        jdbc.update("INSERT INTO crm_contacts (id,tenant_id,version,account_id,legal_name,preferred_name," +
                        "given_name,middle_name,family_name,display_name,normalized_name,primary_email,normalized_email," +
                        "preferred_locale,time_zone,lifecycle_status,owner_user_id,source,created_by,updated_by,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,0,NULL,:legalName,:preferredName,:givenName,NULL,:familyName,:displayName," +
                        ":normalizedName,:email,:normalizedEmail,'ar-SA','Asia/Riyadh','ACTIVE',:owner,'CRM006_TEST'," +
                        ":owner,:owner,:now,:now)",
                p().addValue("id", id).addValue("tenantId", fixture.tenantId())
                        .addValue("legalName", displayName).addValue("preferredName", givenName)
                        .addValue("givenName", givenName).addValue("familyName", familyName)
                        .addValue("displayName", displayName).addValue("normalizedName", displayName.toLowerCase())
                        .addValue("email", email).addValue("normalizedEmail", email.toLowerCase())
                        .addValue("owner", fixture.userId()).addValue("now", now));
        return id;
    }

    private Integer count(String table, UUID tenantId, String idColumn, UUID id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table +
                        " WHERE tenant_id=:tenantId AND " + idColumn + "=:id",
                p().addValue("tenantId", tenantId).addValue("id", id), Integer.class);
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

    private static MapSqlParameterSource p() {
        return new MapSqlParameterSource();
    }

    private record Fixture(UUID tenantId, UUID userId) {}
}
