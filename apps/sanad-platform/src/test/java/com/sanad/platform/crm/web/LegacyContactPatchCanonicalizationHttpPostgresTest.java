package com.sanad.platform.crm.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.crm.legacy.infrastructure.LegacyContactService;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task C6-B-R2 — Real V1/V2 Contact PATCH HTTP certification.
 *
 * <p>Proves that the C6-B legacy adapter delegation actually works end-to-end
 * through real HTTP, real Spring beans, real PostgreSQL Direct, and real
 * TenantRlsDataSource / SecurityContext — WITHOUT @Transactional on the
 * test class and WITHOUT outer TransactionTemplate around MockMvc calls.</p>
 *
 * <h3>Test matrix</h3>
 * <ul>
 *   <li>V1 ordinary-only, owner-only, mixed</li>
 *   <li>V2 ordinary-only, owner-only, mixed</li>
 *   <li>V2 If-Match / ETag</li>
 *   <li>V2 stale If-Match</li>
 *   <li>V1 account reference compatibility</li>
 *   <li>LegacyContactService account compatibility</li>
 *   <li>Canonical invalid-owner rejection</li>
 *   <li>No duplicate timeline</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityPermitAllTestConfig.class)
@ActiveProfiles("local")
@DisplayName("Task C6-B-R2 — Real V1/V2 Contact PATCH HTTP certification")
class LegacyContactPatchCanonicalizationHttpPostgresTest {

    private static final UUID TENANT_A = UUID.fromString("c6b20000-0000-4000-8000-000000000001");
    private static final UUID USER_A = UUID.fromString("c6b20000-0000-4000-8000-00000000a001");
    private static final UUID USER_B = UUID.fromString("c6b20000-0000-4000-8000-00000000b001");
    private static final UUID ACTOR_ID = UUID.fromString("c6b20000-0000-4000-8000-00000000d001");
    private static final UUID ROLE_ID = UUID.fromString("c6b20000-0000-4000-8000-00000000e001");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private NamedParameterJdbcTemplate jdbc;
    @Autowired private org.springframework.transaction.PlatformTransactionManager txm;
    @Autowired private LegacyContactService legacyContactService;
    @Autowired private com.sanad.platform.crm.concurrency.ETagService etagService;

    private TransactionTemplate rawTxn;

    @BeforeAll
    static void requirePostgres() {
        boolean ok;
        try {
            ok = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("LegacyContactPatchCanonicalizationHttpPostgresTest");
        } catch (Throwable ignored) {
            ok = false;
        }
        Assumptions.assumeTrue(ok, "PostgreSQL Direct required for C6-B-R2 certification");
    }

    @BeforeEach
    void setUp() {
        rawTxn = new TransactionTemplate(txm);
        seedIdentityAndCapabilities();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void seedIdentityAndCapabilities() {
        rawTxn.executeWithoutResult(s -> {
            // Set GUC for RLS on shared sanad DB
            jdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)",
                    new MapSqlParameterSource().addValue("t", TENANT_A.toString()), String.class);
            // Clean up previous test data
            jdbc.update("DELETE FROM crm_entity_participants WHERE tenant_id = :t",
                    new MapSqlParameterSource().addValue("t", TENANT_A));
            jdbc.update("DELETE FROM crm_contacts WHERE tenant_id = :t",
                    new MapSqlParameterSource().addValue("t", TENANT_A));
            jdbc.update("DELETE FROM crm_timeline_events WHERE tenant_id = :t",
                    new MapSqlParameterSource().addValue("t", TENANT_A));
            jdbc.update("DELETE FROM crm_accounts WHERE tenant_id = :t",
                    new MapSqlParameterSource().addValue("t", TENANT_A));
            jdbc.update("DELETE FROM user_role_assignments WHERE tenant_id = :t",
                    new MapSqlParameterSource().addValue("t", TENANT_A));
            jdbc.update("DELETE FROM role_capabilities WHERE tenant_id = :t",
                    new MapSqlParameterSource().addValue("t", TENANT_A));
            jdbc.update("DELETE FROM roles WHERE tenant_id = :t",
                    new MapSqlParameterSource().addValue("t", TENANT_A));
            jdbc.update("DELETE FROM users WHERE tenant_id = :t",
                    new MapSqlParameterSource().addValue("t", TENANT_A));
            jdbc.update("DELETE FROM tenants WHERE id = :t",
                    new MapSqlParameterSource().addValue("t", TENANT_A));

            // Seed tenant
            jdbc.update("""
                    INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                    VALUES (:id, 'C6BR2 Tenant', 'c6br2-tenant', 'ACTIVE', NOW(), NOW())
                    """, new MapSqlParameterSource().addValue("id", TENANT_A));

            // Seed users
            for (UUID userId : new UUID[]{USER_A, USER_B, ACTOR_ID}) {
                jdbc.update("""
                        INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
                        VALUES (:id, :t, :email, :name, 'ACTIVE', 'dummy', NOW(), NOW())
                        """, new MapSqlParameterSource()
                        .addValue("id", userId)
                        .addValue("t", TENANT_A)
                        .addValue("email", "c6br2-" + userId + "@snad.test")
                        .addValue("name", "C6BR2 User " + userId.toString().substring(0, 8)));
            }

            // Seed role
            jdbc.update("""
                    INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at)
                    VALUES (:id, :t, 'ADMIN', 'Admin', 'ACTIVE', NOW(), NOW())
                    """, new MapSqlParameterSource()
                    .addValue("id", ROLE_ID)
                    .addValue("t", TENANT_A));

            // Assign role to ACTOR
            jdbc.update("""
                    INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at)
                    VALUES (gen_random_uuid(), :t, :u, :r, 'ACTIVE', NOW(), NOW())
                    """, new MapSqlParameterSource()
                    .addValue("t", TENANT_A)
                    .addValue("u", ACTOR_ID)
                    .addValue("r", ROLE_ID));

            // Grant all CRM.% capabilities
            List<String> caps = List.of(
                    "CRM.CONTACT.READ", "CRM.CONTACT.WRITE", "CRM.CONTACT.ARCHIVE",
                    "CRM.ACCOUNT.READ", "CRM.ACCOUNT.WRITE", "CRM.ACCOUNT.ARCHIVE",
                    "CRM.LEAD.READ", "CRM.LEAD.WRITE", "CRM.LEAD.CONVERT",
                    "CRM.OPPORTUNITY.READ", "CRM.OPPORTUNITY.WRITE",
                    "CRM.ACTIVITY.READ", "CRM.ACTIVITY.WRITE",
                    "CRM.ADMIN");
            for (String cap : caps) {
                // Insert capability if not exists
                jdbc.update("""
                        INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
                        SELECT gen_random_uuid(), :code, :code, 'Test capability', 'ACTIVE', NOW(), NOW()
                        WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = :code)
                        """, new MapSqlParameterSource().addValue("code", cap));
                // Grant to role
                jdbc.update("""
                        INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                        SELECT gen_random_uuid(), :t, :r, ac.id, NOW()
                        FROM access_capabilities ac
                        WHERE ac.code = :code
                        AND NOT EXISTS (
                            SELECT 1 FROM role_capabilities rc
                            WHERE rc.tenant_id = :t AND rc.role_id = :r AND rc.capability_id = ac.id
                        )
                        """, new MapSqlParameterSource()
                        .addValue("t", TENANT_A)
                        .addValue("r", ROLE_ID)
                        .addValue("code", cap));
            }
        });
    }

    private UUID seedContact(UUID ownerUserId, String givenName) {
        return rawTxn.execute(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)",
                    new MapSqlParameterSource().addValue("t", TENANT_A.toString()), String.class);
            UUID contactId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO crm_contacts (id, tenant_id, version, given_name, family_name, display_name,
                        normalized_name, lifecycle_status, owner_user_id, consent_summary,
                        created_by, updated_by, created_at, updated_at)
                    VALUES (:id, :t, 0, :given, 'Tester', :display, :norm,
                        'ACTIVE', :owner, 'UNKNOWN', :actor, :actor, NOW(), NOW())
                    """, new MapSqlParameterSource()
                    .addValue("id", contactId)
                    .addValue("t", TENANT_A)
                    .addValue("given", givenName)
                    .addValue("display", givenName + " Tester")
                    .addValue("norm", (givenName + " Tester").toLowerCase())
                    .addValue("owner", ownerUserId)
                    .addValue("actor", ACTOR_ID));
            return contactId;
        });
    }

    private long contactVersion(UUID contactId) {
        return rawTxn.execute(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)",
                    new MapSqlParameterSource().addValue("t", TENANT_A.toString()), String.class);
            Long v = jdbc.queryForObject("SELECT version FROM crm_contacts WHERE id = :id",
                    new MapSqlParameterSource().addValue("id", contactId), Long.class);
            return v != null ? v : 0L;
        });
    }

    private String contactOwner(UUID contactId) {
        return rawTxn.execute(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)",
                    new MapSqlParameterSource().addValue("t", TENANT_A.toString()), String.class);
            return jdbc.queryForObject("SELECT owner_user_id::text FROM crm_contacts WHERE id = :id",
                    new MapSqlParameterSource().addValue("id", contactId), String.class);
        });
    }

    private String contactGivenName(UUID contactId) {
        return rawTxn.execute(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)",
                    new MapSqlParameterSource().addValue("t", TENANT_A.toString()), String.class);
            return jdbc.queryForObject("SELECT given_name FROM crm_contacts WHERE id = :id",
                    new MapSqlParameterSource().addValue("id", contactId), String.class);
        });
    }

    private long activeParticipantCount(UUID contactId, UUID userId) {
        return rawTxn.execute(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)",
                    new MapSqlParameterSource().addValue("t", TENANT_A.toString()), String.class);
            Long c = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM crm_entity_participants
                    WHERE tenant_id = :t AND entity_type = 'CONTACT' AND entity_id = :c
                    AND user_id = :u AND removed_at IS NULL
                    """, new MapSqlParameterSource()
                    .addValue("t", TENANT_A)
                    .addValue("c", contactId)
                    .addValue("u", userId), Long.class);
            return c != null ? c : 0L;
        });
    }

    private long timelineCount(UUID contactId, String eventType) {
        return rawTxn.execute(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)",
                    new MapSqlParameterSource().addValue("t", TENANT_A.toString()), String.class);
            Long c = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM crm_timeline_events
                    WHERE tenant_id = :t AND subject_type = 'CONTACT' AND subject_id = :c
                    AND event_type = :et
                    """, new MapSqlParameterSource()
                    .addValue("t", TENANT_A)
                    .addValue("c", contactId)
                    .addValue("et", eventType), Long.class);
            return c != null ? c : 0L;
        });
    }

    private String activeParticipantRole(UUID contactId, UUID userId) {
        return rawTxn.execute(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)",
                    new MapSqlParameterSource().addValue("t", TENANT_A.toString()), String.class);
            return jdbc.queryForObject("""
                    SELECT role FROM crm_entity_participants
                    WHERE tenant_id = :t AND entity_type = 'CONTACT' AND entity_id = :c
                    AND user_id = :u AND removed_at IS NULL
                    """, new MapSqlParameterSource()
                    .addValue("t", TENANT_A)
                    .addValue("c", contactId)
                    .addValue("u", userId), String.class);
        });
    }

    private Authentication auth(UUID tenantId, UUID userId) {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        userId.toString(), "n/a", List.of());
        authentication.setDetails(Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", userId.toString()));
        return authentication;
    }

    private String patchV1(UUID contactId, Map<String, Object> body) throws Exception {
        MockHttpServletRequestBuilder req = patch("/api/v1/crm/contacts/" + contactId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .authentication(auth(TENANT_A, ACTOR_ID)));
        return mockMvc.perform(req)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private record V2PatchResult(JsonNode body, String etag) {}

    private V2PatchResult patchV2(UUID contactId, Map<String, Object> body, long expectedVersion) throws Exception {
        String ifMatch = etagService.etag("contact", contactId, expectedVersion);
        MockHttpServletRequestBuilder req = patch("/api/v2/crm/contacts/" + contactId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", ifMatch)
                .content(objectMapper.writeValueAsString(body))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .authentication(auth(TENANT_A, ACTOR_ID)));
        MockHttpServletResponse resp = mockMvc.perform(req)
                .andExpect(status().isOk())
                .andReturn().getResponse();
        String etagHeader = resp.getHeader("ETag");
        assertThat(etagHeader).as("V2 response must have ETag header").isNotNull();
        return new V2PatchResult(objectMapper.readTree(resp.getContentAsString()), etagHeader);
    }

    // ── V1 Tests ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("V1 ordinary-only PATCH → version N+1, owner unchanged")
    void v1OrdinaryOnly() throws Exception {
        UUID contactId = seedContact(USER_A, "Before");
        long n = contactVersion(contactId);
        Map<String, Object> body = new HashMap<>();
        body.put("givenName", "After");
        patchV1(contactId, body);
        assertThat(contactGivenName(contactId)).isEqualTo("After");
        assertThat(contactOwner(contactId)).isEqualTo(USER_A.toString());
        assertThat(contactVersion(contactId)).isEqualTo(n + 1);
    }

    @Test
    @DisplayName("V1 owner-only PATCH → version N+1, previous owner WATCHER")
    void v1OwnerOnly() throws Exception {
        UUID contactId = seedContact(USER_A, "Jane");
        long n = contactVersion(contactId);
        Map<String, Object> body = new HashMap<>();
        body.put("ownerUserId", USER_B.toString());
        patchV1(contactId, body);
        assertThat(contactOwner(contactId)).isEqualTo(USER_B.toString());
        assertThat(contactVersion(contactId)).isEqualTo(n + 1);
        assertThat(activeParticipantCount(contactId, USER_A)).as("USER_A should be WATCHER").isEqualTo(1);
        assertThat(activeParticipantCount(contactId, USER_B)).as("USER_B should have no active participant").isEqualTo(0);
    }

    @Test
    @DisplayName("V1 mixed PATCH → version N+2, owner + givenName changed")
    void v1Mixed() throws Exception {
        UUID contactId = seedContact(USER_A, "Before");
        long n = contactVersion(contactId);
        Map<String, Object> body = new HashMap<>();
        body.put("givenName", "After");
        body.put("ownerUserId", USER_B.toString());
        patchV1(contactId, body);
        assertThat(contactOwner(contactId)).isEqualTo(USER_B.toString());
        assertThat(contactGivenName(contactId)).isEqualTo("After");
        assertThat(contactVersion(contactId)).isEqualTo(n + 2);
        assertThat(activeParticipantCount(contactId, USER_A)).as("USER_A should be WATCHER").isEqualTo(1);
        assertThat(activeParticipantCount(contactId, USER_B)).as("USER_B should have no active participant").isEqualTo(0);
    }

    // ── V2 Tests ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("V2 ordinary-only PATCH → version N+1, actual ETag=N+1")
    void v2OrdinaryOnly() throws Exception {
        UUID contactId = seedContact(USER_A, "Before");
        long n = contactVersion(contactId);
        Map<String, Object> body = new HashMap<>();
        body.put("givenName", "After");
        V2PatchResult result = patchV2(contactId, body, n);
        assertThat(result.body().path("data").path("version").asLong()).isEqualTo(n + 1);
        // Actual ETag must match N+1
        String expectedEtagN1 = etagService.etag("contact", contactId, n + 1);
        assertThat(result.etag()).as("V2 ordinary ETag must match N+1").isEqualTo(expectedEtagN1);
        // And must NOT match N (negative guard)
        String etagN = etagService.etag("contact", contactId, n);
        assertThat(result.etag()).as("V2 ordinary ETag must NOT match N").isNotEqualTo(etagN);
        assertThat(contactGivenName(contactId)).isEqualTo("After");
        assertThat(contactOwner(contactId)).isEqualTo(USER_A.toString());
        assertThat(contactVersion(contactId)).isEqualTo(n + 1);
    }

    @Test
    @DisplayName("V2 owner-only PATCH → version N+1, actual ETag=N+1")
    void v2OwnerOnly() throws Exception {
        UUID contactId = seedContact(USER_A, "Jane");
        long n = contactVersion(contactId);
        Map<String, Object> body = new HashMap<>();
        body.put("ownerUserId", USER_B.toString());
        V2PatchResult result = patchV2(contactId, body, n);
        assertThat(result.body().path("data").path("version").asLong()).isEqualTo(n + 1);
        String expectedEtag = etagService.etag("contact", contactId, n + 1);
        assertThat(result.etag()).as("V2 owner ETag must match N+1").isEqualTo(expectedEtag);
        assertThat(contactOwner(contactId)).isEqualTo(USER_B.toString());
        assertThat(contactVersion(contactId)).isEqualTo(n + 1);
        assertThat(activeParticipantCount(contactId, USER_A)).isEqualTo(1);
        assertThat(activeParticipantCount(contactId, USER_B)).isEqualTo(0);
    }

    @Test
    @DisplayName("V2 mixed PATCH → version N+2, actual ETag=N+2 (negative guard: not N+1)")
    void v2Mixed() throws Exception {
        UUID contactId = seedContact(USER_A, "Before");
        long n = contactVersion(contactId);
        Map<String, Object> body = new HashMap<>();
        body.put("givenName", "After");
        body.put("ownerUserId", USER_B.toString());
        V2PatchResult result = patchV2(contactId, body, n);
        long respVersion = result.body().path("data").path("version").asLong();
        assertThat(respVersion).as("V2 mixed must return version N+2").isEqualTo(n + 2);
        assertThat(result.body().path("data").path("ownerUserId").asText()).isEqualTo(USER_B.toString());
        assertThat(result.body().path("data").path("givenName").asText()).isEqualTo("After");
        // Actual ETag must match N+2
        String expectedFinalEtag = etagService.etag("contact", contactId, n + 2);
        assertThat(result.etag()).as("V2 mixed ETag must match N+2").isEqualTo(expectedFinalEtag);
        // Negative guard: must NOT match N+1 (proves ETag from final version, not expectedVersion+1)
        String incorrectNPlusOneEtag = etagService.etag("contact", contactId, n + 1);
        assertThat(result.etag()).as("V2 mixed ETag must NOT match N+1 (proves final version derivation)")
                .isNotEqualTo(incorrectNPlusOneEtag);
        assertThat(contactVersion(contactId)).isEqualTo(n + 2);
        assertThat(activeParticipantCount(contactId, USER_A)).isEqualTo(1);
        assertThat(activeParticipantCount(contactId, USER_B)).isEqualTo(0);
    }

    @Test
    @DisplayName("V2 full response mapping — all ContactResponse fields present")
    void v2FullResponseMapping() throws Exception {
        UUID contactId = seedContact(USER_A, "TestName");
        long n = contactVersion(contactId);
        Map<String, Object> body = new HashMap<>();
        body.put("givenName", "AfterName");
        V2PatchResult result = patchV2(contactId, body, n);
        JsonNode data = result.body().path("data");
        assertThat(data.path("id").asText()).as("id must be present").isEqualTo(contactId.toString());
        assertThat(data.path("version").asLong()).as("version must be present").isEqualTo(n + 1);
        assertThat(data.has("accountId")).as("accountId key must exist").isTrue();
        assertThat(data.path("givenName").asText()).as("givenName must be present").isEqualTo("AfterName");
        assertThat(data.has("familyName")).as("familyName key must exist").isTrue();
        assertThat(data.has("displayName")).as("displayName key must exist").isTrue();
        assertThat(data.has("primaryEmail")).as("primaryEmail key must exist").isTrue();
        assertThat(data.has("normalizedEmail")).as("normalizedEmail key must exist").isTrue();
        assertThat(data.has("primaryPhone")).as("primaryPhone key must exist").isTrue();
        assertThat(data.has("preferredLocale")).as("preferredLocale key must exist").isTrue();
        assertThat(data.has("timeZone")).as("timeZone key must exist").isTrue();
        assertThat(data.path("lifecycleStatus").asText()).as("lifecycleStatus must be present").isEqualTo("ACTIVE");
        assertThat(data.path("ownerUserId").asText()).as("ownerUserId must be present").isEqualTo(USER_A.toString());
        assertThat(data.has("consentSummary")).as("consentSummary key must exist").isTrue();
        assertThat(data.has("createdAt")).as("createdAt key must exist").isTrue();
        assertThat(data.has("updatedAt")).as("updatedAt key must exist").isTrue();
    }

    @Test
    @DisplayName("Canonical invalid-owner rejection via V2 HTTP → HTTP 400 + VALIDATION_ERROR")
    void canonicalInvalidOwnerRejection() throws Exception {
        UUID contactId = seedContact(USER_A, "Jane");
        long n = contactVersion(contactId);
        long baselineTimeline = timelineCount(contactId, "crm.contact.updated");
        UUID invalidOwnerId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        Map<String, Object> body = new HashMap<>();
        body.put("ownerUserId", invalidOwnerId.toString());
        String ifMatch = etagService.etag("contact", contactId, n);
        MockHttpServletRequestBuilder req = patch("/api/v2/crm/contacts/" + contactId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", ifMatch)
                .content(objectMapper.writeValueAsString(body))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .authentication(auth(TENANT_A, ACTOR_ID)));
        MockHttpServletResponse response = mockMvc.perform(req)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andReturn().getResponse();
        // Explicit 500 guard
        assertThat(response.getStatus())
                .as("Invalid owner must be a structured client rejection, never HTTP 500")
                .isNotEqualTo(500);
        // Verify no mutation
        assertThat(contactOwner(contactId)).as("Owner must be unchanged").isEqualTo(USER_A.toString());
        assertThat(contactVersion(contactId)).as("Version must be unchanged").isEqualTo(n);
        assertThat(contactGivenName(contactId)).as("givenName must be unchanged").isEqualTo("Jane");
        assertThat(activeParticipantCount(contactId, USER_A)).as("USER_A must have no new participant").isEqualTo(0);
        assertThat(activeParticipantCount(contactId, invalidOwnerId)).as("Invalid owner must have no participant").isEqualTo(0);
        // No timeline side effect
        assertThat(timelineCount(contactId, "crm.contact.updated") - baselineTimeline)
                .as("No timeline event for rejected mutation").isZero();
    }

    @Test
    @DisplayName("V1 custom_fields response compatibility")
    void v1CustomFieldsResponseCompatibility() throws Exception {
        UUID contactId = seedContact(USER_A, "Jane");
        Map<String, Object> body = new HashMap<>();
        body.put("givenName", "After");
        String response = patchV1(contactId, body);
        JsonNode json = objectMapper.readTree(response);
        assertThat(json.has("custom_fields")).as("V1 response must contain custom_fields key").isTrue();
        assertThat(json.get("custom_fields")).as("custom_fields must not be null").isNotNull();
    }

    @Test
    @DisplayName("V2 mixed — exact WATCHER role for USER_A (not just count)")
    void v2MixedExactWatcherRole() throws Exception {
        UUID contactId = seedContact(USER_A, "Before");
        long n = contactVersion(contactId);
        Map<String, Object> body = new HashMap<>();
        body.put("givenName", "After");
        body.put("ownerUserId", USER_B.toString());
        patchV2(contactId, body, n);
        // Query exact role
        String role = activeParticipantRole(contactId, USER_A);
        assertThat(role).as("USER_A active participant role must be WATCHER").isEqualTo("WATCHER");
        assertThat(activeParticipantCount(contactId, USER_B)).as("USER_B must have 0 active participants").isEqualTo(0);
    }

    @Test
    @DisplayName("V1 mixed — exact WATCHER role for USER_A (not just count)")
    void v1MixedExactWatcherRole() throws Exception {
        UUID contactId = seedContact(USER_A, "Before");
        Map<String, Object> body = new HashMap<>();
        body.put("givenName", "After");
        body.put("ownerUserId", USER_B.toString());
        patchV1(contactId, body);
        String role = activeParticipantRole(contactId, USER_A);
        assertThat(role).as("USER_A active participant role must be WATCHER").isEqualTo("WATCHER");
        assertThat(activeParticipantCount(contactId, USER_B)).as("USER_B must have 0 active participants").isEqualTo(0);
    }

    @Test
    @DisplayName("V2 stale If-Match → HTTP 412, no mutation")
    void v2StaleIfMatch() throws Exception {
        UUID contactId = seedContact(USER_A, "Before");
        long n = contactVersion(contactId);
        // Use stale version (n - 1) for If-Match
        String staleEtag = etagService.etag("contact", contactId, n - 1);
        Map<String, Object> body = new HashMap<>();
        body.put("givenName", "ShouldNotPersist");
        body.put("ownerUserId", USER_B.toString());
        MockHttpServletRequestBuilder req = patch("/api/v2/crm/contacts/" + contactId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", staleEtag)
                .content(objectMapper.writeValueAsString(body))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .authentication(auth(TENANT_A, ACTOR_ID)));
        mockMvc.perform(req).andExpect(status().isPreconditionFailed());
        // Verify no mutation
        assertThat(contactOwner(contactId)).isEqualTo(USER_A.toString());
        assertThat(contactGivenName(contactId)).isEqualTo("Before");
        assertThat(contactVersion(contactId)).isEqualTo(n);
        assertThat(activeParticipantCount(contactId, USER_A)).isEqualTo(0);
        assertThat(activeParticipantCount(contactId, USER_B)).isEqualTo(0);
    }

    // ── Account Compatibility ─────────────────────────────────────────────

    @Test
    @DisplayName("V1 account reference compatibility → nonexistent account rejected")
    void v1AccountReferenceCompatibility() throws Exception {
        UUID contactId = seedContact(USER_A, "Jane");
        long n = contactVersion(contactId);
        Map<String, Object> body = new HashMap<>();
        body.put("accountId", UUID.randomUUID().toString());
        body.put("givenName", "After");
        MockHttpServletRequestBuilder req = patch("/api/v1/crm/contacts/" + contactId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .authentication(auth(TENANT_A, ACTOR_ID)));
        mockMvc.perform(req).andExpect(status().isNotFound());
        // Verify no mutation
        assertThat(contactGivenName(contactId)).isEqualTo("Jane");
        assertThat(contactVersion(contactId)).isEqualTo(n);
    }

    // ── LegacyContactService Account Compatibility ────────────────────────

    @Test
    @DisplayName("LegacyContactService account reference compatibility → nonexistent account rejected")
    void legacyContactServiceAccountCompatibility() {
        UUID contactId = seedContact(USER_A, "Jane");
        long n = contactVersion(contactId);
        UpdateContactRequest request = new UpdateContactRequest(
                UUID.randomUUID(), "After", null, null, null, null, null, null, null);
        Authentication authentication = auth(TENANT_A, ACTOR_ID);
        // LegacyContactService.updateContact reads the Contact via contactUseCases.getById
        // which requires SecurityContext for RLS. The shared DB uses FORCE RLS so
        // the GUC must be set in the SecurityContext (production TenantRlsConnectionHandler
        // pattern). Since this test calls the service directly (not through MockMvc),
        // we set SecurityContext for the service call.
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            Throwable ex = org.assertj.core.api.Assertions.catchThrowable(
                    () -> legacyContactService.updateContact(authentication, contactId, request));
            assertThat(ex).as("LegacyContactService must reject nonexistent account").isNotNull();
            // The error could be ResponseStatusException (from LegacySupport.one())
            // or wrapped by Spring's DataIntegrityViolationException.
            // What matters is that the Contact was NOT mutated.
            assertThat(contactGivenName(contactId)).isEqualTo("Jane");
            assertThat(contactVersion(contactId)).isEqualTo(n);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ── Duplicate Timeline ────────────────────────────────────────────────

    @Test
    @DisplayName("V1 ordinary-only PATCH → exactly one crm.contact.updated timeline event")
    void v1NoDuplicateTimeline() throws Exception {
        UUID contactId = seedContact(USER_A, "Before");
        long baseline = timelineCount(contactId, "crm.contact.updated");
        Map<String, Object> body = new HashMap<>();
        body.put("givenName", "After");
        patchV1(contactId, body);
        long after = timelineCount(contactId, "crm.contact.updated");
        assertThat(after - baseline).as("V1 ordinary update must emit exactly one timeline event").isEqualTo(1);
    }

    @Test
    @DisplayName("V2 ordinary-only PATCH → exactly one crm.contact.updated timeline event")
    void v2NoDuplicateTimeline() throws Exception {
        UUID contactId = seedContact(USER_A, "Before");
        long n = contactVersion(contactId);
        long baseline = timelineCount(contactId, "crm.contact.updated");
        Map<String, Object> body = new HashMap<>();
        body.put("givenName", "After");
        patchV2(contactId, body, n);
        long after = timelineCount(contactId, "crm.contact.updated");
        assertThat(after - baseline).as("V2 ordinary update must emit exactly one timeline event").isEqualTo(1);
    }
}
