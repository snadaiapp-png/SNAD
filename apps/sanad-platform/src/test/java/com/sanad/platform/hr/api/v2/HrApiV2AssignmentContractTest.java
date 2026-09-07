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
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HRM-G0 / WS5 Task 4 — Assignment v2 contract (6 canonical operations).
 *
 * <p>Locks the Assignment slice of the canonical 58-operation surface:
 *
 * <ul>
 *   <li>reads gate on HRM.ASSIGNMENT.VIEW, mutations on
 *       HRM.ASSIGNMENT.MANAGE (independent capabilities, enforced
 *       server-side; denial is the canonical 403 HRM_SCOPE_DENIED
 *       envelope)</li>
 *   <li>end / change-manager / transfer require an explicit effective date
 *       and expected version — no server-side defaulting; stale versions
 *       yield 409 HRM_CONCURRENCY_CONFLICT</li>
 *   <li>transfer supersedes the current effective period and creates the
 *       new period atomically; historical placement is preserved (the old
 *       period row remains visible with a closed effective_to)</li>
 *   <li>critical POSTs require an explicit {@code Idempotency-Key}; replay
 *       of the same key+fingerprint returns the SAME response, the same key
 *       with a different fingerprint yields 409 HRM_IDEMPOTENCY_CONFLICT</li>
 *   <li>cross-tenant reads fail closed as 404</li>
 * </ul>
 *
 * <p>PostgreSQL Direct only — structural rows are seeded over real JDBC
 * with the tenant GUC (fail-closed FORCE RLS), never mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(HrApiV2AssignmentContractTest.AuthProbeConfig.class)
class HrApiV2AssignmentContractTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://127.0.0.1:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "sanad_pass");

    static final Set<String> GRANTED = new HashSet<>();

    static final UUID TENANT = UUID.fromString("88888888-8888-8888-8888-888888888888");
    static final UUID OTHER_TENANT = UUID.fromString("99999999-9999-9999-9999-999999999999");
    static final UUID USER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

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
    void listAssignments_withoutCapability_scopeDenied() throws Exception {
        mockMvc.perform(get("/api/v2/hr/assignments").with(authentication(principal())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    @Test
    void createAssignment_withoutCapability_scopeDenied() throws Exception {
        seedStructure();
        mockMvc.perform(post("/api/v2/hr/assignments")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    @Test
    void read_requiresAssignmentView_notEmployeeView() throws Exception {
        // Employee directory capability must NOT authorize assignment reads.
        GRANTED.add("HRM.EMPLOYEE.VIEW");
        mockMvc.perform(get("/api/v2/hr/assignments").with(authentication(principal())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    // ==================== CREATE / READ ====================

    @Test
    void createAssignment_thenReadBack() throws Exception {
        GRANTED.add("HRM.ASSIGNMENT.MANAGE");
        GRANTED.add("HRM.ASSIGNMENT.VIEW");
        var ids = seedStructure();

        String created = mockMvc.perform(post("/api/v2/hr/assignments")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignmentId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.assignmentType").value("PRIMARY"))
                .andExpect(jsonPath("$.effectiveTo").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String assignmentId = com.jayway.jsonpath.JsonPath.read(created, "$.assignmentId");
        mockMvc.perform(get("/api/v2/hr/assignments/{id}", assignmentId).with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(assignmentId))
                .andExpect(jsonPath("$.employmentId").value(ids.employmentId().toString()))
                .andExpect(jsonPath("$.orgUnitId").value(ids.orgUnitId().toString()))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void createAssignment_missingEffectiveFrom_isValidationError() throws Exception {
        GRANTED.add("HRM.ASSIGNMENT.MANAGE");
        seedStructure();
        String body = """
                {"employmentId":"%s","organizationId":"%s","orgUnitId":"%s","positionId":"%s",
                 "assignmentType":"PRIMARY","occupancyMode":"OCCUPYING","allocationPercent":100}
                """.formatted(seedEmploymentId(), orgId, orgUnitId, positionId);
        mockMvc.perform(post("/api/v2/hr/assignments")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HRM_VALIDATION_FAILED"));
    }

    @Test
    void getAssignment_unknownId_notFound404() throws Exception {
        GRANTED.add("HRM.ASSIGNMENT.VIEW");
        seedStructure();
        mockMvc.perform(get("/api/v2/hr/assignments/{id}", UUID.randomUUID()).with(authentication(principal())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HRM_ASSIGNMENT_NOT_FOUND"));
    }

    @Test
    void crossTenant_assignmentRead_failsClosedAs404() throws Exception {
        GRANTED.add("HRM.ASSIGNMENT.VIEW");
        seedStructureForTenant(OTHER_TENANT);
        UUID foreignAssignmentId = seedAssignmentDirect(OTHER_TENANT);
        mockMvc.perform(get("/api/v2/hr/assignments/{id}", foreignAssignmentId).with(authentication(principal())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HRM_ASSIGNMENT_NOT_FOUND"));
    }

    // ==================== END (terminal close) ====================

    @Test
    void endAssignment_closesPeriodAndPreservesHistory() throws Exception {
        GRANTED.add("HRM.ASSIGNMENT.MANAGE");
        GRANTED.add("HRM.ASSIGNMENT.VIEW");
        seedStructure();
        UUID assignmentId = createAssignmentViaApi();

        mockMvc.perform(post("/api/v2/hr/assignments/{id}/end", assignmentId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveDate\":\"2026-12-31\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.effectiveTo").value("2026-12-31"));

        // The ended period remains readable — history is preserved.
        mockMvc.perform(get("/api/v2/hr/assignments/{id}", assignmentId).with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.effectiveTo").value("2026-12-31"));
    }

    @Test
    void endAssignment_staleExpectedVersion_conflict409() throws Exception {
        GRANTED.add("HRM.ASSIGNMENT.MANAGE");
        seedStructure();
        UUID assignmentId = createAssignmentViaApi();

        mockMvc.perform(post("/api/v2/hr/assignments/{id}/end", assignmentId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveDate\":\"2026-12-31\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v2/hr/assignments/{id}/end", assignmentId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveDate\":\"2027-01-31\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_CONCURRENCY_CONFLICT"));
    }

    @Test
    void endAssignment_missingExpectedVersion_isValidationError() throws Exception {
        GRANTED.add("HRM.ASSIGNMENT.MANAGE");
        seedStructure();
        UUID assignmentId = createAssignmentViaApi();
        mockMvc.perform(post("/api/v2/hr/assignments/{id}/end", assignmentId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveDate\":\"2026-12-31\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HRM_VALIDATION_FAILED"));
    }

    // ==================== CHANGE MANAGER / TRANSFER ====================

    @Test
    void changeManager_supersedesPeriodWithNewManager() throws Exception {
        GRANTED.add("HRM.ASSIGNMENT.MANAGE");
        GRANTED.add("HRM.ASSIGNMENT.VIEW");
        seedStructure();
        UUID assignmentId = createAssignmentViaApi();
        UUID managerAssignmentId = seedAssignmentDirect(TENANT);

        String revised = mockMvc.perform(post("/api/v2/hr/assignments/{id}/change-manager", assignmentId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportsToAssignmentId\":\"" + managerAssignmentId + "\"," +
                                "\"effectiveDate\":\"2026-10-01\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportsToAssignmentId").value(managerAssignmentId.toString()))
                .andReturn().getResponse().getContentAsString();

        // Supersede semantics: a NEW period row is created; the old row keeps
        // its identity and gains a closed effective_to (history preserved).
        String newAssignmentId = com.jayway.jsonpath.JsonPath.read(revised, "$.assignmentId");
        assertThat(newAssignmentId).isNotEqualTo(assignmentId.toString());

        mockMvc.perform(get("/api/v2/hr/assignments/{id}", assignmentId).with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveTo").value("2026-09-30"));
    }

    @Test
    void transfer_supersedesPlacementAtomically() throws Exception {
        GRANTED.add("HRM.ASSIGNMENT.MANAGE");
        GRANTED.add("HRM.ASSIGNMENT.VIEW");
        var ids = seedStructure();
        UUID assignmentId = createAssignmentViaApi();
        UUID newOrgUnitId = seedOrgUnit("TRANSFER-TARGET");

        String transferred = mockMvc.perform(post("/api/v2/hr/assignments/{id}/transfer", assignmentId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgUnitId\":\"" + newOrgUnitId + "\"," +
                                "\"positionId\":\"" + ids.positionId() + "\"," +
                                "\"effectiveDate\":\"2026-11-01\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgUnitId").value(newOrgUnitId.toString()))
                .andReturn().getResponse().getContentAsString();

        String newAssignmentId = com.jayway.jsonpath.JsonPath.read(transferred, "$.assignmentId");
        assertThat(newAssignmentId).isNotEqualTo(assignmentId.toString());

        // Historical placement preserved.
        mockMvc.perform(get("/api/v2/hr/assignments/{id}", assignmentId).with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgUnitId").value(ids.orgUnitId().toString()))
                .andExpect(jsonPath("$.effectiveTo").value("2026-10-31"));
    }

    // ==================== IDEMPOTENCY ====================

    @Test
    void createAssignment_duplicateKeyReplaysSameAssignmentId() throws Exception {
        GRANTED.add("HRM.ASSIGNMENT.MANAGE");
        seedStructure();
        String key = UUID.randomUUID().toString();

        String first = mockMvc.perform(post("/api/v2/hr/assignments")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(post("/api/v2/hr/assignments")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) com.jayway.jsonpath.JsonPath.read(second, "$.assignmentId"))
                .isEqualTo(com.jayway.jsonpath.JsonPath.read(first, "$.assignmentId"));
    }

    @Test
    void createAssignment_sameKeyDifferentFingerprint_conflict409() throws Exception {
        GRANTED.add("HRM.ASSIGNMENT.MANAGE");
        seedStructure();
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v2/hr/assignments")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v2/hr/assignments")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody().replace("100", "80")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void createAssignment_missingIdempotencyKey_isRejected() throws Exception {
        GRANTED.add("HRM.ASSIGNMENT.MANAGE");
        seedStructure();
        mockMvc.perform(post("/api/v2/hr/assignments")
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isBadRequest());
    }

    // ==================== SEED HELPERS (PostgreSQL Direct) ====================

    record StructureIds(UUID orgId, UUID orgUnitId, UUID positionId, UUID employmentId, UUID personId) {
    }

    private UUID orgId;
    private UUID orgUnitId;
    private UUID positionId;
    private UUID employmentId;
    private UUID personId;

    private String validCreateBody() {
        return """
                {"employmentId":"%s","organizationId":"%s","orgUnitId":"%s","positionId":"%s",
                 "assignmentType":"PRIMARY","occupancyMode":"OCCUPYING","allocationPercent":100,
                 "effectiveFrom":"2026-09-01"}
                """.formatted(employmentId, orgId, orgUnitId, positionId);
    }

    private StructureIds seedStructure() {
        seedStructureForTenant(TENANT);
        return new StructureIds(orgId, orgUnitId, positionId, employmentId, personId);
    }

    private UUID createAssignmentViaApi() {
        GRANTED.add("HRM.ASSIGNMENT.MANAGE");
        try {
            String created = mockMvc.perform(post("/api/v2/hr/assignments")
                            .with(authentication(principal()))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validCreateBody()))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.assignmentId"));
        } catch (Exception e) {
            throw new IllegalStateException("Assignment seed via API failed", e);
        }
    }

    private void seedStructureForTenant(UUID tenantId) {
        seedTenant(tenantId);
        orgId = seedOrganization(tenantId);
        orgUnitId = seedOrgUnitFor(tenantId, orgId, "SEED-UNIT");
        positionId = seedPosition(tenantId);
        personId = seedPerson(tenantId);
        employmentId = seedEmployee(tenantId, personId);
    }

    private UUID seedEmploymentId() {
        return employmentId;
    }

    private void seedTenant(UUID tenantId) {
        executePlain("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                        "VALUES (?, 'Assignment V2 Tenant', ?, 'ACTIVE', NOW(), NOW()) ON CONFLICT (id) DO NOTHING",
                ps -> {
                    ps.setObject(1, tenantId);
                    ps.setString(2, "t-" + tenantId.toString().substring(0, 8));
                });
    }

    private UUID seedOrganization(UUID tenantId) {
        UUID id = UUID.randomUUID();
        executeAsTenant(tenantId, "INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) " +
                "VALUES (?, ?, 'V2 Org ' || ?, 'ACTIVE', NOW(), NOW())", ps -> {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setString(3, UUID.randomUUID().toString().substring(0, 8));
        });
        return id;
    }

    private UUID seedOrgUnit(String stableCode) {
        return seedOrgUnitFor(TENANT, orgId, stableCode);
    }

    private UUID seedOrgUnitFor(UUID tenantId, UUID organizationId, String stableCode) {
        UUID id = UUID.randomUUID();
        executeAsTenant(tenantId, "INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at) " +
                "VALUES (?, ?, ?, ?, NOW())", ps -> {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, organizationId);
            ps.setString(4, stableCode + "-" + UUID.randomUUID().toString().substring(0, 6));
        });
        executeAsTenant(tenantId, "INSERT INTO hr_org_unit_versions (id, tenant_id, org_unit_id, name, code, unit_type, " +
                "parent_org_unit_id, effective_from, effective_to, status) " +
                "VALUES (?, ?, ?, 'Seed Unit', 'SU', 'DEPARTMENT', NULL, ?, NULL, 'ACTIVE')", ps -> {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, id);
            ps.setObject(4, java.sql.Date.valueOf(LocalDate.of(2026, 1, 1)));
        });
        return id;
    }

    private UUID seedPosition(UUID tenantId) {
        UUID id = UUID.randomUUID();
        executeAsTenant(tenantId, "INSERT INTO hr_positions (id, tenant_id, title, code, status, created_at, updated_at) " +
                "VALUES (?, ?, 'V2 Position', ?, 'ACTIVE', NOW(), NOW())", ps -> {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setString(3, "POS-" + UUID.randomUUID().toString().substring(0, 8));
        });
        executeAsTenant(tenantId, "INSERT INTO hr_position_versions (id, tenant_id, position_id, organization_id, job_id, " +
                "org_unit_id, title, effective_from, effective_to, status) " +
                "VALUES (?, ?, ?, NULL, NULL, NULL, 'V2 Position', ?, NULL, 'ACTIVE')", ps -> {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, id);
            ps.setObject(4, java.sql.Date.valueOf(LocalDate.of(2026, 1, 1)));
        });
        return id;
    }

    private UUID seedPerson(UUID tenantId) {
        UUID id = UUID.randomUUID();
        executeAsTenant(tenantId, "INSERT INTO hr_people (id, tenant_id, user_id, first_name, last_name, display_name, version) " +
                "VALUES (?, ?, NULL, 'Assign', 'Seed', 'Assign Seed', 0)", ps -> {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
        });
        return id;
    }

    private UUID seedEmployee(UUID tenantId, UUID personId) {
        UUID legalEntityId = seedLegalEntity(tenantId);
        executeAsTenant(tenantId, "INSERT INTO organization_legal_entities (id, tenant_id, organization_id, " +
                        "legal_entity_id, effective_from, effective_to, status, created_at) " +
                        "VALUES (?, ?, ?, ?, ?::date, NULL, 'ACTIVE', NOW())",
                ps -> {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, tenantId);
                    ps.setObject(3, orgId);
                    ps.setObject(4, legalEntityId);
                    ps.setString(5, "2026-01-01");
                });
        UUID id = UUID.randomUUID();
        executeAsTenant(tenantId, "INSERT INTO hr_employees (id, tenant_id, person_id, legal_entity_id, employee_number, " +
                        "first_name, last_name, display_name, employment_type, status, hire_date, version) " +
                        "VALUES (?, ?, ?, ?, ?, 'Assign', 'Seed', 'Assign Seed', 'FULL_TIME', 'ACTIVE', ?, 0)",
                ps -> {
                    ps.setObject(1, id);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, personId);
                    ps.setObject(4, legalEntityId);
                    ps.setString(5, "EMP-AS-" + UUID.randomUUID().toString().substring(0, 8));
                    ps.setObject(6, LocalDate.of(2026, 1, 1));
                });
        return id;
    }

    private UUID seedLegalEntity(UUID tenantId) {
        UUID id = UUID.randomUUID();
        executeAsTenant(tenantId, "INSERT INTO legal_entities (id, tenant_id, code, name, registered_country_code, " +
                        "statutory_country_code, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'V2 LE', 'SA', 'SA', 'ACTIVE', NOW(), NOW())",
                ps -> {
                    ps.setObject(1, id);
                    ps.setObject(2, tenantId);
                    ps.setString(3, "LE-" + UUID.randomUUID().toString().substring(0, 8));
                });
        return id;
    }

    /** Direct seeded assignment for manager/cross-tenant fixtures. */
    private UUID seedAssignmentDirect(UUID tenantId) {
        UUID personId = seedPerson(tenantId);
        UUID legalEntityId = seedLegalEntity(tenantId);
        UUID employmentId = UUID.randomUUID();
        UUID orgId = seedOrganization(tenantId);
        UUID orgUnitId = seedOrgUnitFor(tenantId, orgId, "MGR-UNIT");
        UUID positionId = seedPosition(tenantId);
        executeAsTenant(tenantId, "INSERT INTO organization_legal_entities (id, tenant_id, organization_id, " +
                        "legal_entity_id, effective_from, effective_to, status, created_at) " +
                        "VALUES (?, ?, ?, ?, ?::date, NULL, 'ACTIVE', NOW())",
                ps -> {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, tenantId);
                    ps.setObject(3, orgId);
                    ps.setObject(4, legalEntityId);
                    ps.setString(5, "2026-01-01");
                });
        executeAsTenant(tenantId, "INSERT INTO hr_employees (id, tenant_id, person_id, legal_entity_id, employee_number, " +
                        "first_name, last_name, display_name, employment_type, status, hire_date, version) " +
                        "VALUES (?, ?, ?, ?, ?, 'Assign', 'Seed', 'Assign Seed', 'FULL_TIME', 'ACTIVE', ?, 0)",
                ps -> {
                    ps.setObject(1, employmentId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, personId);
                    ps.setObject(4, legalEntityId);
                    ps.setString(5, "EMP-AS-" + UUID.randomUUID().toString().substring(0, 8));
                    ps.setObject(6, LocalDate.of(2026, 1, 1));
                });
        UUID id = UUID.randomUUID();
        executeAsTenant(tenantId, "INSERT INTO hr_employee_assignments (id, tenant_id, employment_id, organization_id, " +
                        "org_unit_id, position_id, assignment_type, occupancy_mode, allocation_percent, " +
                        "effective_from, status, version, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'PRIMARY', 'OCCUPYING', 100, ?, 'ACTIVE', 0, NOW(), NOW())",
                ps -> {
                    ps.setObject(1, id);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, employmentId);
                    ps.setObject(4, orgId);
                    ps.setObject(5, orgUnitId);
                    ps.setObject(6, positionId);
                    ps.setObject(7, LocalDate.of(2026, 1, 1));
                });
        return id;
    }

    @FunctionalInterface
    interface SqlBinder {
        void bind(PreparedStatement ps) throws Exception;
    }

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
        CapabilityEvaluationService v2AssignmentCapabilityEvaluationService() {
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
