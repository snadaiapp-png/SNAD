package com.sanad.platform.crm.party;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.concurrency.ETagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class CustomerMasterHttpIntegrationTest {
    private static final List<String> CAPABILITIES = List.of("CRM.ACCOUNT.READ", "CRM.ACCOUNT.WRITE");
    private static final String MASTER_ETAG_TYPE = "customer-master";
    private static final String ADDRESS_ETAG_TYPE = "customer-master-address";

    @Autowired MockMvc mockMvc;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ETagService etags;

    @Test
    void readsGoldenRecordEnforcesTenantIsolationAndEmitsStrongEtag() throws Exception {
        Fixture owner = fixture("master-owner");
        Fixture outsider = fixture("master-outsider");
        UUID accountId = account(owner, "Acme Arabia");

        mockMvc.perform(get("/api/v1/crm/accounts/{id}/master", accountId).with(authentication(auth(owner))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, etag(MASTER_ETAG_TYPE, accountId, 0)))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.legalName").value("Acme Arabia"))
                .andExpect(jsonPath("$.version").value(0));

        mockMvc.perform(get("/api/v1/crm/accounts/{id}/master", accountId).with(authentication(auth(outsider))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatesIdentityClassificationRiskCreditAuditTimelineAndNormalizesValues() throws Exception {
        Fixture fixture = fixture("master-update");
        UUID accountId = account(fixture, "SNAD Customer");

        mockMvc.perform(patch("/api/v1/crm/accounts/{id}/master", accountId)
                        .with(authentication(auth(fixture)))
                        .header(HttpHeaders.IF_MATCH, etag(MASTER_ETAG_TYPE, accountId, 0))
                        .contentType("application/json")
                        .content("""
                                {"legalName":"  SNAD Customer Company  ","tradingName":"SNAD Customer",
                                 "registrationNumber":"CR-101010","taxNumber":"VAT-3100000000","industryCode":"SOFTWARE",
                                 "customerSegment":"ENTERPRISE","customerTier":"strategic","website":"https://customer.example",
                                 "primaryEmail":"finance@customer.example","primaryPhone":"+966500000000","countryCode":"sa",
                                 "riskRating":"low","creditLimit":250000.00,"paymentTermsDays":45}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, etag(MASTER_ETAG_TYPE, accountId, 1)))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.legalName").value("SNAD Customer Company"))
                .andExpect(jsonPath("$.countryCode").value("SA"))
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
    void rejectsMissingAndStaleMasterPreconditions() throws Exception {
        Fixture fixture = fixture("master-stale");
        UUID accountId = account(fixture, "Versioned Customer");

        mockMvc.perform(patch("/api/v1/crm/accounts/{id}/master", accountId)
                        .with(authentication(auth(fixture))).contentType("application/json")
                        .content("{\"legalName\":\"No precondition\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.error.code").value("CRM_PRECONDITION_REQUIRED"));

        jdbc.update("UPDATE crm_accounts SET version=2 WHERE tenant_id=:tenantId AND id=:id",
                p().addValue("tenantId", fixture.tenantId()).addValue("id", accountId));

        mockMvc.perform(patch("/api/v1/crm/accounts/{id}/master", accountId)
                        .with(authentication(auth(fixture)))
                        .header(HttpHeaders.IF_MATCH, etag(MASTER_ETAG_TYPE, accountId, 0))
                        .contentType("application/json")
                        .content("{\"expectedVersion\":2,\"legalName\":\"Stale Update\"}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.error.code").value("CRM_CONCURRENCY_CONFLICT"));
    }

    @Test
    void rejectsCreditLimitOutsideDatabasePrecision() throws Exception {
        Fixture fixture = fixture("master-credit");
        UUID accountId = account(fixture, "Credit Customer");

        mockMvc.perform(patch("/api/v1/crm/accounts/{id}/master", accountId)
                        .with(authentication(auth(fixture)))
                        .header(HttpHeaders.IF_MATCH, etag(MASTER_ETAG_TYPE, accountId, 0))
                        .contentType("application/json")
                        .content("{\"creditLimit\":1.001}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void managesAddressesAndIdentifiersWithGovernedHeaders() throws Exception {
        Fixture fixture = fixture("master-attributes");
        UUID accountId = account(fixture, "Attribute Customer");

        JsonNode address = perform(post("/api/v1/crm/accounts/{id}/addresses", accountId)
                .with(authentication(auth(fixture)))
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json")
                .content("""
                        {"addressType":"REGISTERED","label":"Head Office","line1":"  King Fahd Road  ",
                         "city":"  Riyadh  ","postalCode":"12345","countryCode":"sa","primaryAddress":true}
                        """), 201);
        UUID addressId = UUID.fromString(address.path("id").asText());
        long addressVersion = address.path("version").asLong();
        assertThat(address.path("line1").asText()).isEqualTo("King Fahd Road");
        assertThat(address.path("city").asText()).isEqualTo("Riyadh");
        assertThat(address.path("countryCode").asText()).isEqualTo("SA");

        mockMvc.perform(post("/api/v1/crm/accounts/{id}/identifiers", accountId)
                        .with(authentication(auth(fixture)))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {"identifierType":"commercial_registration","identifierValue":"  1010-2020  ",
                                 "issuerCountryCode":"sa","primaryIdentifier":true,"verified":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.identifierValue").value("1010-2020"))
                .andExpect(jsonPath("$.issuerCountryCode").value("SA"))
                .andExpect(jsonPath("$.verified").value(true));

        mockMvc.perform(get("/api/v1/crm/accounts/{id}/addresses", accountId).with(authentication(auth(fixture))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].city").value("Riyadh"));
        mockMvc.perform(get("/api/v1/crm/accounts/{id}/identifiers", accountId).with(authentication(auth(fixture))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].identifierValue").value("1010-2020"));

        mockMvc.perform(delete("/api/v1/crm/accounts/{accountId}/addresses/{addressId}", accountId, addressId)
                        .with(authentication(auth(fixture)))
                        .header(HttpHeaders.IF_MATCH, etag(ADDRESS_ETAG_TYPE, addressId, addressVersion)))
                .andExpect(status().isNoContent());
    }

    @Test
    void replaysAddressCreationWithoutDuplicateWrite() throws Exception {
        Fixture fixture = fixture("master-idempotency");
        UUID accountId = account(fixture, "Idempotent Customer");
        String key = UUID.randomUUID().toString();
        String body = """
                {"addressType":"OFFICE","line1":"First Street","city":"Riyadh",
                 "countryCode":"SA","primaryAddress":false}
                """;

        JsonNode first = perform(post("/api/v1/crm/accounts/{id}/addresses", accountId)
                .with(authentication(auth(fixture))).header("Idempotency-Key", key)
                .contentType("application/json").content(body), 201);
        JsonNode replay = perform(post("/api/v1/crm/accounts/{id}/addresses", accountId)
                .with(authentication(auth(fixture))).header("Idempotency-Key", key)
                .contentType("application/json").content(body), 201);

        assertThat(replay).isEqualTo(first);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_account_addresses WHERE tenant_id=:tenantId AND account_id=:accountId",
                p().addValue("tenantId", fixture.tenantId()).addValue("accountId", accountId), Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void rejectsMissingIdempotencyKeyForCreate() throws Exception {
        Fixture fixture = fixture("master-idempotency-required");
        UUID accountId = account(fixture, "Key Required Customer");

        mockMvc.perform(post("/api/v1/crm/accounts/{id}/addresses", accountId)
                        .with(authentication(auth(fixture))).contentType("application/json")
                        .content("{\"addressType\":\"OFFICE\",\"line1\":\"Street\",\"city\":\"Riyadh\",\"countryCode\":\"SA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CRM_IDEMPOTENCY_KEY_REQUIRED"));
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
                        .with(authentication(auth(tenantA))).header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/crm/accounts/{id}/identifiers", second)
                        .with(authentication(auth(tenantA))).header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/crm/accounts/{id}/identifiers", otherTenant)
                        .with(authentication(auth(tenantB))).header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json").content(body))
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
    void mergesCustomerRecordsWithDualPreconditionsIdempotencyAndHistory() throws Exception {
        Fixture fixture = fixture("master-merge");
        UUID source = account(fixture, "Duplicate Customer");
        UUID target = account(fixture, "Golden Customer");
        String key = UUID.randomUUID().toString();
        String body = "{\"reason\":\"Verified duplicate\"}";

        JsonNode result = perform(post("/api/v1/crm/accounts/{source}/merge/{target}", source, target)
                .with(authentication(auth(fixture)))
                .header("Idempotency-Key", key)
                .header(HttpHeaders.IF_MATCH, etag(MASTER_ETAG_TYPE, source, 0))
                .header("X-Target-If-Match", etag(MASTER_ETAG_TYPE, target, 0))
                .contentType("application/json").content(body), 200);

        assertThat(result.path("sourceAccountId").asText()).isEqualTo(source.toString());
        assertThat(result.path("targetAccountId").asText()).isEqualTo(target.toString());
        assertThat(result.path("sourceVersion").asLong()).isEqualTo(1);
        assertThat(result.path("targetVersion").asLong()).isEqualTo(1);

        JsonNode replay = perform(post("/api/v1/crm/accounts/{source}/merge/{target}", source, target)
                .with(authentication(auth(fixture)))
                .header("Idempotency-Key", key)
                .header(HttpHeaders.IF_MATCH, etag(MASTER_ETAG_TYPE, source, 0))
                .header("X-Target-If-Match", etag(MASTER_ETAG_TYPE, target, 0))
                .contentType("application/json").content(body), 200);
        assertThat(replay).isEqualTo(result);

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
                        .with(authentication(auth(fixture)))
                        .header(HttpHeaders.IF_MATCH, etag(MASTER_ETAG_TYPE, accountId, 0))
                        .contentType("application/json")
                        .content("{\"legalName\":\"" + legalName +
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

    private String etag(String entityType, UUID id, long version) {
        return etags.etag(entityType, id, version);
    }

    private Fixture fixture(String key) {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (:id,:name,:subdomain,'ACTIVE',:now,:now)",
                p().addValue("id", tenantId).addValue("name", key)
                        .addValue("subdomain", key + "-" + tenantId.toString().substring(0, 8)).addValue("now", java.sql.Timestamp.from(now)));
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:email,'CRM Master User','ACTIVE','dummy',:now,:now)",
                p().addValue("id", userId).addValue("tenantId", tenantId)
                        .addValue("email", key + "-" + userId.toString().substring(0, 8) + "@example.test").addValue("now", java.sql.Timestamp.from(now)));
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:code,'CRM Master Role','CRM-005 tests','ACTIVE',:now,:now)",
                p().addValue("id", roleId).addValue("tenantId", tenantId)
                        .addValue("code", "CRM_MASTER_" + key.toUpperCase().replace('-', '_')).addValue("now", java.sql.Timestamp.from(now)));
        List<UUID> capabilityIds = jdbc.query("SELECT id FROM access_capabilities WHERE code IN (:codes)",
                p().addValue("codes", CAPABILITIES), (rs, row) -> rs.getObject("id", UUID.class));
        assertThat(capabilityIds).hasSize(CAPABILITIES.size());
        for (UUID capabilityId : capabilityIds) {
            jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) " +
                            "VALUES (:id,:tenantId,:roleId,:capabilityId,:now)",
                    p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                            .addValue("roleId", roleId).addValue("capabilityId", capabilityId).addValue("now", java.sql.Timestamp.from(now)));
        }
        jdbc.update("INSERT INTO user_role_assignments (id,tenant_id,user_id,role_id,organization_id,status,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:userId,:roleId,NULL,'ACTIVE',:now,:now)",
                p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                        .addValue("userId", userId).addValue("roleId", roleId).addValue("now", java.sql.Timestamp.from(now)));
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
                        .addValue("normalized", name.toLowerCase()).addValue("owner", fixture.userId()).addValue("now", java.sql.Timestamp.from(now)));
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
