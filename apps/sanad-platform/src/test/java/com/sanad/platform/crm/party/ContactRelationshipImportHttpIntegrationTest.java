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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class ContactRelationshipImportHttpIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void importsOnePersonWithMultipleRelationshipsAndKeepsValidRows() throws Exception {
        Fixture tenant = fixture("import-main", true);
        Fixture foreignTenant = fixture("import-foreign", true);
        UUID firstAccount = account(tenant, "First Account");
        UUID secondAccount = account(tenant, "Second Account");
        UUID foreignAccount = account(foreignTenant, "Foreign Account");

        MvcResult result = mockMvc.perform(post("/api/v2/crm/contact-relationship-imports")
                        .with(authentication(authentication(tenant)))
                        .contentType("application/json")
                        .content("""
                                {
                                  "rows": [
                                    {
                                      "personKey": "person-ar-1",
                                      "legalName": "عبدالرحمن سنان",
                                      "preferredName": "عبدالرحمن",
                                      "givenName": "عبدالرحمن",
                                      "familyName": "سنان",
                                      "primaryEmail": "multi@example.test",
                                      "preferredLocale": "ar-SA",
                                      "timeZone": "Asia/Riyadh",
                                      "accountId": "%s",
                                      "roleCode": "EMPLOYEE",
                                      "primaryRelationship": true,
                                      "decisionAuthority": "INFLUENCER"
                                    },
                                    {
                                      "personKey": "person-ar-1",
                                      "accountId": "%s",
                                      "roleCode": "BILLING",
                                      "primaryRelationship": false,
                                      "decisionAuthority": "NONE"
                                    },
                                    {
                                      "personKey": "person-ar-1",
                                      "accountId": "%s",
                                      "roleCode": "TECHNICAL",
                                      "primaryRelationship": false,
                                      "decisionAuthority": "NONE"
                                    }
                                  ]
                                }
                                """.formatted(firstAccount, secondAccount, foreignAccount)))
                .andExpect(status().isMultiStatus())
                .andExpect(jsonPath("$.data.totalRows").value(3))
                .andExpect(jsonPath("$.data.succeededRows").value(2))
                .andExpect(jsonPath("$.data.failedRows").value(1))
                .andExpect(jsonPath("$.data.rows[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.rows[1].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.rows[2].status").value("FAILED"))
                .andReturn();

        JsonNode response = mapper.readTree(result.getResponse().getContentAsString()).path("data");
        UUID contactId = UUID.fromString(response.path("rows").get(0).path("contactId").asText());
        assertThat(response.path("rows").get(1).path("contactId").asText())
                .isEqualTo(contactId.toString());

        assertThat(count("crm_contacts", tenant.tenantId(), "id", contactId)).isEqualTo(1);
        assertThat(count("crm_contact_account_relationships", tenant.tenantId(), "contact_id", contactId))
                .isEqualTo(2);
        assertThat(count("crm_contact_relationship_history", tenant.tenantId(), "contact_id", contactId))
                .isEqualTo(2);

        Integer importedAudits = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM platform_audit_logs
                WHERE target_tenant_id=:tenantId
                  AND action IN ('IMPORT_PERSON_CREATE','IMPORT_RELATIONSHIP_CREATE')
                """,
                parameters().addValue("tenantId", tenant.tenantId()),
                Integer.class);
        assertThat(importedAudits).isEqualTo(3);
    }

    @Test
    void duplicateEmailNeverMergesWithoutExplicitPersonKey() throws Exception {
        Fixture tenant = fixture("import-duplicate", true);
        UUID accountId = account(tenant, "Duplicate Email Account");

        mockMvc.perform(post("/api/v2/crm/contact-relationship-imports")
                        .with(authentication(authentication(tenant)))
                        .contentType("application/json")
                        .content("""
                                {
                                  "rows": [
                                    {
                                      "personKey": "person-a",
                                      "givenName": "Person A",
                                      "primaryEmail": "same@example.test",
                                      "accountId": "%s",
                                      "roleCode": "EMPLOYEE",
                                      "decisionAuthority": "NONE"
                                    },
                                    {
                                      "personKey": "person-b",
                                      "givenName": "Person B",
                                      "primaryEmail": "same@example.test",
                                      "accountId": "%s",
                                      "roleCode": "PARTNER",
                                      "decisionAuthority": "NONE"
                                    }
                                  ]
                                }
                                """.formatted(accountId, accountId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.succeededRows").value(2))
                .andExpect(jsonPath("$.data.failedRows").value(0));

        Integer contacts = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM crm_contacts
                WHERE tenant_id=:tenantId AND normalized_email='same@example.test'
                """,
                parameters().addValue("tenantId", tenant.tenantId()),
                Integer.class);
        assertThat(contacts).isEqualTo(2);
    }

    @Test
    void importRequiresAuthenticationAndCapability() throws Exception {
        Fixture denied = fixture("import-denied", false);

        mockMvc.perform(post("/api/v2/crm/contact-relationship-imports")
                        .contentType("application/json")
                        .content("{\"rows\":[{}]}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v2/crm/contact-relationship-imports")
                        .with(authentication(authentication(denied)))
                        .contentType("application/json")
                        .content("{\"rows\":[{}]}"))
                .andExpect(status().isForbidden());
    }

    private Fixture fixture(String key, boolean grantImport) {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                """
                INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at)
                VALUES (:id,:name,:subdomain,'ACTIVE',:now,:now)
                """,
                parameters().addValue("id", tenantId).addValue("name", key)
                        .addValue("subdomain", key + "-" + tenantId.toString().substring(0, 8))
                        .addValue("now", now));
        jdbc.update(
                """
                INSERT INTO users
                    (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at)
                VALUES (:id,:tenantId,:email,'Import User','ACTIVE','dummy',:now,:now)
                """,
                parameters().addValue("id", userId).addValue("tenantId", tenantId)
                        .addValue("email", key + "-" + userId.toString().substring(0, 8) + "@example.test")
                        .addValue("now", now));
        jdbc.update(
                """
                INSERT INTO roles
                    (id,tenant_id,code,name,description,status,created_at,updated_at)
                VALUES (:id,:tenantId,:code,'Import Role','CRM-006 import test','ACTIVE',:now,:now)
                """,
                parameters().addValue("id", roleId).addValue("tenantId", tenantId)
                        .addValue("code", "CRM006_IMPORT_" + key.toUpperCase().replace('-', '_'))
                        .addValue("now", now));
        if (grantImport) {
            UUID capabilityId = jdbc.queryForObject(
                    "SELECT id FROM access_capabilities WHERE code='CRM.CONTACT.IMPORT'",
                    parameters(), UUID.class);
            jdbc.update(
                    """
                    INSERT INTO role_capabilities
                        (id,tenant_id,role_id,capability_id,created_at)
                    VALUES (:id,:tenantId,:roleId,:capabilityId,:now)
                    """,
                    parameters().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                            .addValue("roleId", roleId).addValue("capabilityId", capabilityId)
                            .addValue("now", now));
        }
        jdbc.update(
                """
                INSERT INTO user_role_assignments
                    (id,tenant_id,user_id,role_id,organization_id,status,created_at,updated_at)
                VALUES (:id,:tenantId,:userId,:roleId,NULL,'ACTIVE',:now,:now)
                """,
                parameters().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                        .addValue("userId", userId).addValue("roleId", roleId).addValue("now", now));
        return new Fixture(tenantId, userId);
    }

    private UUID account(Fixture fixture, String name) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                """
                INSERT INTO crm_accounts
                    (id,tenant_id,version,display_name,normalized_name,account_type,
                     lifecycle_status,primary_currency_code,preferred_locale,time_zone,source,
                     owner_user_id,created_by,updated_by,created_at,updated_at)
                VALUES (:id,:tenantId,0,:name,:normalized,'BUSINESS','ACTIVE','SAR',
                        'ar-SA','Asia/Riyadh','CRM006_IMPORT_TEST',:owner,:owner,:owner,:now,:now)
                """,
                parameters().addValue("id", id).addValue("tenantId", fixture.tenantId())
                        .addValue("name", name).addValue("normalized", name.toLowerCase())
                        .addValue("owner", fixture.userId()).addValue("now", now));
        return id;
    }

    private Integer count(String table, UUID tenantId, String column, UUID id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE tenant_id=:tenantId AND " + column + "=:id",
                parameters().addValue("tenantId", tenantId).addValue("id", id),
                Integer.class);
    }

    private Authentication authentication(Fixture fixture) {
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", fixture.tenantId().toString());
        details.put("user_id", fixture.userId().toString());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                fixture.userId().toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authentication.setDetails(details);
        return authentication;
    }

    private static MapSqlParameterSource parameters() {
        return new MapSqlParameterSource();
    }

    private record Fixture(UUID tenantId, UUID userId) {}
}
