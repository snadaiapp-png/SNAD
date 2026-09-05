package com.sanad.platform.hr.api.v2;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.security.authorization.CapabilityAuthorizationBypass;
import com.sanad.platform.security.service.JwtTokenProvider;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HRM-G0 / WS5 Task 3 slice 2 — People v2 contract (9 canonical operations).
 *
 * <p>Locks the People slice of the canonical 58-operation surface:
 *
 * <ul>
 *   <li>independent coarse capabilities per operation — directory reads and
 *       writes gate on HRM.EMPLOYEE.*, PII endpoints gate on HRM.PII.VIEW /
 *       HRM.PII.MANAGE, user-link gates on HRM.USER_LINK.MANAGE; denial is
 *       the canonical 403 HRM_SCOPE_DENIED envelope</li>
 *   <li>critical POSTs require an explicit {@code Idempotency-Key} header;
 *       replay of the same key+fingerprint returns the SAME response, the
 *       same key with a different fingerprint yields 409
 *       HRM_IDEMPOTENCY_CONFLICT</li>
 *   <li>profile and private mutations require an explicit
 *       {@code expectedVersion}; stale versions yield 409
 *       HRM_CONCURRENCY_CONFLICT; missing versions are 400 validation
 *       failures (no server-side version or date defaulting)</li>
 *   <li>PII reads (GET private) FAIL CLOSED through the sensitive-read
 *       audit — the restricted response is returned only after an audit
 *       ledger row survives in the same transaction</li>
 *   <li>identity document values are write-only: the API response and every
 *       log surface carry metadata only, the plaintext value is never
 *       echoed back</li>
 *   <li>cross-tenant reads fail closed as 404 HRM_PERSON_NOT_FOUND</li>
 * </ul>
 *
 * <p>PostgreSQL Direct only — tenant rows are seeded over real JDBC with the
 * tenant GUC (fail-closed FORCE RLS), never mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(HrApiV2PeopleContractTest.AuthProbeConfig.class)
class HrApiV2PeopleContractTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://127.0.0.1:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "sanad_pass");

    /** Capabilities the fake principal holds for the current test (default: none). */
    static final Set<String> GRANTED = new HashSet<>();

    static final UUID TENANT = UUID.fromString("55555555-5555-5555-5555-555555555555");
    static final UUID OTHER_TENANT = UUID.fromString("66666666-6666-6666-6666-666666666666");
    static final UUID USER = UUID.fromString("77777777-7777-7777-7777-777777777777");

    /** Ephemeral test key material (base64 of 32 bytes) — never a production secret. */
    private static final String TEST_ENC_KEY_B64 = java.util.Base64.getEncoder()
            .encodeToString("0123456789ABCDEF0123456789ABCDEF".getBytes());
    private static final String TEST_BLIND_KEY_B64 = java.util.Base64.getEncoder()
            .encodeToString("FEDCBA9876543210FEDCBA9876543210".getBytes());

    @org.springframework.test.context.DynamicPropertySource
    static void supplyCryptoKeyMaterial(DynamicPropertyRegistry registry) {
        registry.add("sanad.security.crypto.encryption-key", () -> TEST_ENC_KEY_B64);
        registry.add("sanad.security.crypto.blind-index-key", () -> TEST_BLIND_KEY_B64);
    }

    @Autowired private MockMvc mockMvc;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available = false;
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            available = c.isValid(5);
        } catch (Exception ignored) {
        }
        Assumptions.assumeTrue(available, "PostgreSQL Direct is not available");
    }

    @BeforeEach
    void resetGrants() {
        GRANTED.clear();
    }

    private UsernamePasswordAuthenticationToken principal() {
        return principal(TENANT);
    }

    private UsernamePasswordAuthenticationToken principal(UUID tenantId) {
        var token = new UsernamePasswordAuthenticationToken(
                "test-principal", "n/a", List.of(new SimpleGrantedAuthority("ROLE_TEST")));
        token.setDetails(Map.of("tenant_id", tenantId.toString(), "user_id", USER.toString()));
        return token;
    }

    // ==================== CAPABILITY BOUNDARIES ====================

    @Test
    void listPeople_withoutCapability_scopeDenied() throws Exception {
        mockMvc.perform(get("/api/v2/hr/people").with(authentication(principal())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    @Test
    void createPerson_withoutCapability_scopeDenied() throws Exception {
        mockMvc.perform(post("/api/v2/hr/people")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    @Test
    void getPersonPrivate_withoutPiiCapability_scopeDenied() throws Exception {
        // Directory viewer alone must NOT see private PII.
        GRANTED.add("HRM.EMPLOYEE.VIEW");
        UUID personId = seedPersonViaApi();
        mockMvc.perform(get("/api/v2/hr/people/{id}/private", personId).with(authentication(principal())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    @Test
    void patchPersonPrivate_piiViewAlone_scopeDenied() throws Exception {
        // PII read capability must NOT authorize PII mutation.
        GRANTED.add("HRM.PII.VIEW");
        UUID personId = seedPersonViaApi();
        mockMvc.perform(patch("/api/v2/hr/people/{id}/private", personId)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateOfBirth\":\"1990-01-01\",\"expectedVersion\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    @Test
    void addIdentifier_withoutPiiManageCapability_scopeDenied() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.UPDATE");
        UUID personId = seedPersonViaApi();
        mockMvc.perform(post("/api/v2/hr/people/{id}/identifiers", personId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validIdentifierBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    @Test
    void userLink_withoutUserLinkCapability_scopeDenied() throws Exception {
        // PII.MANAGE must NOT authorize identity linking — dedicated capability.
        GRANTED.add("HRM.PII.MANAGE");
        UUID personId = seedPersonViaApi();
        mockMvc.perform(post("/api/v2/hr/people/{id}/user-link", personId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    // ==================== CREATE / READ / PATCH PROFILE ====================

    @Test
    void createPerson_thenReadBack() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.CREATE");
        GRANTED.add("HRM.EMPLOYEE.VIEW");
        seedTenant(TENANT);

        String created = mockMvc.perform(post("/api/v2/hr/people")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.personId").isNotEmpty())
                .andExpect(jsonPath("$.displayName").value("Amina Test"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();

        String personId = com.jayway.jsonpath.JsonPath.read(created, "$.personId");
        mockMvc.perform(get("/api/v2/hr/people/{id}", personId).with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personId").value(personId))
                .andExpect(jsonPath("$.firstName").value("Amina"))
                .andExpect(jsonPath("$.lastName").value("Test"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void createPerson_duplicateKeyReplaysSamePersonId() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.CREATE");
        seedTenant(TENANT);
        String key = UUID.randomUUID().toString();

        String first = mockMvc.perform(post("/api/v2/hr/people")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(post("/api/v2/hr/people")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) com.jayway.jsonpath.JsonPath.read(second, "$.personId"))
                .isEqualTo(com.jayway.jsonpath.JsonPath.read(first, "$.personId"));
    }

    @Test
    void createPerson_sameKeyDifferentFingerprint_conflict409() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.CREATE");
        seedTenant(TENANT);
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v2/hr/people")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Amina\",\"lastName\":\"Test\"}"))
                .andExpect(status().isCreated());

        // Same key, different command fingerprint → canonical idempotency conflict.
        mockMvc.perform(post("/api/v2/hr/people")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Amina\",\"middleName\":\"Salem\",\"lastName\":\"Test\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void createPerson_missingIdempotencyKey_isRejected() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.CREATE");
        seedTenant(TENANT);
        mockMvc.perform(post("/api/v2/hr/people")
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPerson_missingLastName_isValidationError() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.CREATE");
        seedTenant(TENANT);
        mockMvc.perform(post("/api/v2/hr/people")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Amina\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HRM_VALIDATION_FAILED"));
    }

    @Test
    void listPeople_returnsDirectorySummaries() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.CREATE");
        GRANTED.add("HRM.EMPLOYEE.VIEW");
        seedTenant(TENANT);
        mockMvc.perform(post("/api/v2/hr/people")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(get("/api/v2/hr/people").with(authentication(principal())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("Amina");
        // Directory summaries never carry private PII fields.
        assertThat(body).doesNotContain("dateOfBirth");
        assertThat(body).doesNotContain("nationalityCountryCode");
    }

    @Test
    void getPerson_unknownId_notFound404() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.VIEW");
        seedTenant(TENANT);
        mockMvc.perform(get("/api/v2/hr/people/{id}", UUID.randomUUID()).with(authentication(principal())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HRM_PERSON_NOT_FOUND"));
    }

    @Test
    void crossTenant_personRead_failsClosedAs404() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.VIEW");
        seedTenant(OTHER_TENANT);
        UUID foreignPersonId = seedPersonDirect(OTHER_TENANT);

        mockMvc.perform(get("/api/v2/hr/people/{id}", foreignPersonId).with(authentication(principal())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HRM_PERSON_NOT_FOUND"));
    }

    @Test
    void patchPerson_updatesNamesAndBumpsVersion() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.CREATE");
        GRANTED.add("HRM.EMPLOYEE.UPDATE");
        GRANTED.add("HRM.EMPLOYEE.VIEW");
        seedTenant(TENANT);
        UUID personId = seedPersonViaApi();

        mockMvc.perform(patch("/api/v2/hr/people/{id}", personId)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Amina\",\"middleName\":\"Salem\",\"lastName\":\"Test\"," +
                                "\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Amina Salem Test"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get("/api/v2/hr/people/{id}", personId).with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void patchPerson_staleExpectedVersion_conflict409() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.UPDATE");
        seedTenant(TENANT);
        UUID personId = seedPersonViaApi();

        mockMvc.perform(patch("/api/v2/hr/people/{id}", personId)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Amina\",\"lastName\":\"Test\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        // version is now 1; expectedVersion 0 is stale.
        mockMvc.perform(patch("/api/v2/hr/people/{id}", personId)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Other\",\"lastName\":\"Name\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_CONCURRENCY_CONFLICT"));
    }

    @Test
    void patchPerson_missingExpectedVersion_isValidationError() throws Exception {
        // No server-side defaulting of the concurrency version.
        GRANTED.add("HRM.EMPLOYEE.UPDATE");
        seedTenant(TENANT);
        UUID personId = seedPersonViaApi();
        mockMvc.perform(patch("/api/v2/hr/people/{id}", personId)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Amina\",\"lastName\":\"Test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HRM_VALIDATION_FAILED"));
    }

    // ==================== PRIVATE PII (audited) ====================

    @Test
    void privatePatchAndRead_roundTrip_audited() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.UPDATE");
        GRANTED.add("HRM.PII.VIEW");
        GRANTED.add("HRM.PII.MANAGE");
        seedTenant(TENANT);
        UUID personId = seedPersonViaApi();

        mockMvc.perform(patch("/api/v2/hr/people/{id}/private", personId)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateOfBirth\":\"1992-04-15\",\"nationalityCountryCode\":\"SA\"," +
                                "\"maritalStatus\":\"MARRIED\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                // Mutations return metadata only — never echo PII values.
                .andExpect(jsonPath("$.dateOfBirth").doesNotExist())
                .andExpect(jsonPath("$.nationalityCountryCode").doesNotExist());

        mockMvc.perform(get("/api/v2/hr/people/{id}/private", personId).with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personId").value(personId.toString()))
                .andExpect(jsonPath("$.dateOfBirth").value("1992-04-15"))
                .andExpect(jsonPath("$.nationalityCountryCode").value("SA"))
                .andExpect(jsonPath("$.maritalStatus").value("MARRIED"))
                .andExpect(jsonPath("$.version").value(1));

        // Sensitive read evidence must exist in the immutable audit ledger.
        assertThat(auditRowsFor(personId)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void privateRead_withoutAnyPrivateRow_returnsEmptyProfile() throws Exception {
        GRANTED.add("HRM.PII.VIEW");
        seedTenant(TENANT);
        UUID personId = seedPersonViaApi();
        mockMvc.perform(get("/api/v2/hr/people/{id}/private", personId).with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateOfBirth").doesNotExist())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void privatePatch_staleExpectedVersion_conflict409() throws Exception {
        GRANTED.add("HRM.PII.MANAGE");
        seedTenant(TENANT);
        UUID personId = seedPersonViaApi();

        mockMvc.perform(patch("/api/v2/hr/people/{id}/private", personId)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maritalStatus\":\"SINGLE\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v2/hr/people/{id}/private", personId)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maritalStatus\":\"MARRIED\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_CONCURRENCY_CONFLICT"));
    }

    @Test
    void privatePatch_missingExpectedVersion_isValidationError() throws Exception {
        GRANTED.add("HRM.PII.MANAGE");
        seedTenant(TENANT);
        UUID personId = seedPersonViaApi();
        mockMvc.perform(patch("/api/v2/hr/people/{id}/private", personId)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maritalStatus\":\"SINGLE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HRM_VALIDATION_FAILED"));
    }

    @Test
    void privatePatch_invalidMaritalStatus_isValidationError() throws Exception {
        GRANTED.add("HRM.PII.MANAGE");
        seedTenant(TENANT);
        UUID personId = seedPersonViaApi();
        mockMvc.perform(patch("/api/v2/hr/people/{id}/private", personId)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maritalStatus\":\"COMPLICATED\",\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HRM_VALIDATION_FAILED"));
    }

    // ==================== IDENTIFIERS (write-only values) ====================

    @Test
    void addIdentifier_returnsMetadataOnly_neverPlaintext() throws Exception {
        GRANTED.add("HRM.PII.MANAGE");
        seedTenant(TENANT);
        UUID personId = seedPersonViaApi();
        String secretValue = "XYT-448290-" + UUID.randomUUID().toString().substring(0, 6);

        String body = mockMvc.perform(post("/api/v2/hr/people/{id}/identifiers", personId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifierType\":\"NATIONAL_ID\",\"issuingCountryCode\":\"SA\"," +
                                "\"value\":\"" + secretValue + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.identifierId").isNotEmpty())
                .andExpect(jsonPath("$.identifierType").value("NATIONAL_ID"))
                .andExpect(jsonPath("$.issuingCountryCode").value("SA"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        // The plaintext identity value is write-only: never echoed back.
        assertThat(body).doesNotContain(secretValue);
    }

    @Test
    void addIdentifier_duplicateActiveValue_conflict409() throws Exception {
        GRANTED.add("HRM.PII.MANAGE");
        seedTenant(TENANT);
        UUID personId = seedPersonViaApi();
        String value = "IDP-" + UUID.randomUUID().toString().substring(0, 10);
        String body = "{\"identifierType\":\"NATIONAL_ID\",\"issuingCountryCode\":\"SA\",\"value\":\"" + value + "\"}";

        mockMvc.perform(post("/api/v2/hr/people/{id}/identifiers", personId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // A second ACTIVE identifier with the same tenant+type+issuer+value is rejected.
        mockMvc.perform(post("/api/v2/hr/people/{id}/identifiers", personId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_PERSON_CONFLICT"));
    }

    @Test
    void addIdentifier_unknownPerson_notFound404() throws Exception {
        GRANTED.add("HRM.PII.MANAGE");
        seedTenant(TENANT);
        mockMvc.perform(post("/api/v2/hr/people/{id}/identifiers", UUID.randomUUID())
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validIdentifierBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HRM_PERSON_NOT_FOUND"));
    }

    // ==================== USER LINK ====================

    @Test
    void userLink_linkThenUnlink_roundTrip() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.VIEW");
        GRANTED.add("HRM.USER_LINK.MANAGE");
        seedTenant(TENANT);
        UUID personId = seedPersonViaApi();
        UUID platformUserId = seedPlatformUser();

        mockMvc.perform(post("/api/v2/hr/people/{id}/user-link", personId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + platformUserId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true));

        String summary = mockMvc.perform(get("/api/v2/hr/people/{id}", personId).with(authentication(principal())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(summary, "$.userId")).isEqualTo(platformUserId.toString());

        mockMvc.perform(delete("/api/v2/hr/people/{id}/user-link", personId).with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(false));

        // DELETE user-link is idempotent by nature.
        mockMvc.perform(delete("/api/v2/hr/people/{id}/user-link", personId).with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(false));
    }

    // ==================== SEED HELPERS (PostgreSQL Direct) ====================

    private String validCreateBody() {
        return "{\"firstName\":\"Amina\",\"lastName\":\"Test\"}";
    }

    private String validIdentifierBody() {
        return "{\"identifierType\":\"NATIONAL_ID\",\"issuingCountryCode\":\"SA\",\"value\":\"XYT-448290-SEED\"}";
    }

    private UUID seedPersonViaApi() {
        GRANTED.add("HRM.EMPLOYEE.CREATE");
        try {
            String created = mockMvc.perform(post("/api/v2/hr/people")
                            .with(authentication(principal()))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validCreateBody()))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            GRANTED.remove("HRM.EMPLOYEE.CREATE");
            return UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.personId"));
        } catch (Exception e) {
            throw new IllegalStateException("Person seed via API failed", e);
        }
    }

    private UUID seedPersonDirect(UUID tenantId) {
        UUID personId = UUID.randomUUID();
        executeAsTenant(tenantId, "INSERT INTO hr_people (id, tenant_id, user_id, first_name, last_name, display_name, version) " +
                "VALUES (?, ?, NULL, 'Foreign', 'Person', 'Foreign Person', 0)", ps -> {
            ps.setObject(1, personId);
            ps.setObject(2, tenantId);
        });
        return personId;
    }

    private UUID seedPlatformUser() {
        UUID userId = UUID.randomUUID();
        executePlain("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'Platform User', 'ACTIVE', 'test-hash', NOW(), NOW())",
                ps -> {
                    ps.setObject(1, userId);
                    ps.setObject(2, TENANT);
                    ps.setString(3, "user-" + userId.toString().substring(0, 8) + "@people-v2.test");
                });
        return userId;
    }

    private int auditRowsFor(UUID personId) {
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement st = c.createStatement()) {
            st.execute("SET app.tenant_id = '" + TENANT + "'");
            ResultSet rs = st.executeQuery(
                    "SELECT count(*) FROM hr_audit_ledger WHERE resource_type = 'HR_PERSON_PRIVATE' " +
                            "AND resource_id = '" + personId + "' AND result = 'SUCCESS'");
            rs.next();
            return rs.getInt(1);
        } catch (Exception e) {
            throw new IllegalStateException("Audit verification failed", e);
        }
    }

    private void seedTenant(UUID tenantId) {
        executePlain("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                        "VALUES (?, 'People V2 Tenant', ?, 'ACTIVE', NOW(), NOW()) ON CONFLICT (id) DO NOTHING",
                ps -> {
                    ps.setObject(1, tenantId);
                    ps.setString(2, "t-" + tenantId.toString().substring(0, 8));
                });
    }

    @FunctionalInterface
    interface SqlBinder {
        void bind(PreparedStatement ps) throws Exception;
    }

    /** Session GUC + autocommit insert (proven fail-closed RLS pattern). */
    private void executeAsTenant(UUID tenantId, String sql, SqlBinder binder) {
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement st = c.createStatement()) {
            st.execute("SET app.tenant_id = '" + tenantId + "'");
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                binder.bind(ps);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Seed failed: " + e.getMessage(), e);
        }
    }

    private void executePlain(String sql, SqlBinder binder) {
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                binder.bind(ps);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Seed failed: " + e.getMessage(), e);
        }
    }

    /** Per-test capability control: allow exactly what GRANTED holds, deny the rest. */
    @TestConfiguration
    static class AuthProbeConfig {

        @Bean
        public static BeanDefinitionRegistryPostProcessor removeRealJwtProvider() {
            return new BeanDefinitionRegistryPostProcessor() {
                @Override
                public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                    if (registry.containsBeanDefinition("jwtTokenProvider")) {
                        registry.removeBeanDefinition("jwtTokenProvider");
                    }
                }
            };
        }

        @Bean
        @Primary
        JwtTokenProvider testJwtTokenProvider() {
            return org.mockito.Mockito.mock(JwtTokenProvider.class);
        }

        @Bean
        CapabilityAuthorizationBypass capabilityAuthorizationBypass() {
            return () -> true;
        }

        @Bean
        @org.springframework.core.annotation.Order(-100)
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .securityMatcher("/**")
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
            return http.build();
        }

        @Bean
        @Primary
        CapabilityEvaluationService v2PeopleCapabilityEvaluationService() {
            CapabilityEvaluationService mock = org.mockito.Mockito.mock(CapabilityEvaluationService.class);
            org.mockito.Mockito.when(mock.evaluate(org.mockito.ArgumentMatchers.any(UUID.class),
                            org.mockito.ArgumentMatchers.any(UUID.class),
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.any()))
                    .thenAnswer(invocation -> {
                        String capability = invocation.getArgument(2);
                        boolean allowed = GRANTED.contains(capability);
                        return new AccessDecisionResponse(
                                invocation.getArgument(0), invocation.getArgument(1),
                                invocation.getArgument(3), capability,
                                allowed, allowed ? "ALLOW" : "DENY", UUID.randomUUID(),
                                allowed ? "TEST_ROLE" : null);
                    });
            return mock;
        }
    }
}
