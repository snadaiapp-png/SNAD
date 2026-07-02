package com.sanad.platform.organization.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.organization.dto.CreateOrganizationRequest;
import com.sanad.platform.organization.dto.OrganizationResponse;
import com.sanad.platform.organization.dto.UpdateOrganizationRequest;
import com.sanad.platform.organization.exception.OrganizationAlreadyExistsException;
import com.sanad.platform.organization.service.OrganizationService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link OrganizationController}.
 *
 * <p>Uses Spring Boot's {@link WebMvcTest @WebMvcTest} to load only the web
 * layer (controller + exception handler + JSON serializers) without starting
 * the full application context. The {@link OrganizationService} is mocked
 * via {@link MockBean} so the test focuses purely on HTTP contract and
 * controller wiring.</p>
 *
 * <p>The {@link OrganizationApiExceptionHandler} is explicitly
 * {@link Import @Import}ed because {@code @WebMvcTest} only auto-discovers
 * {@code @ControllerAdvice} beans in the same package as the controller
 * under test — importing it explicitly is safer and clearer.</p>
 *
 * <h2>Test Cases (required by EXEC-PROMPT-007)</h2>
 * <ol>
 *   <li>POST valid request -> 201</li>
 *   <li>POST duplicate organization -> 409</li>
 *   <li>POST invalid tenant -> 404</li>
 *   <li>POST invalid request body -> 400</li>
 * </ol>
 */
@WebMvcTest(OrganizationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(OrganizationApiExceptionHandler.class)
class OrganizationControllerTest {

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.sanad.platform.audit.service.TenantSecurityDenialAuditService tenantSecurityDenialAuditService;
    @org.junit.jupiter.api.BeforeEach
    void mockTenantResolver() {
        org.mockito.Mockito.when(tenantResolver.requireTenantId()).thenReturn(tenantId);
        org.mockito.Mockito.when(tenantResolver.validateClientSelector(org.mockito.ArgumentMatchers.any())).thenReturn(tenantId);
    }


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean     private OrganizationService organizationService;

    @MockBean     private com.sanad.platform.security.tenant.TenantResolver tenantResolver;

    @MockBean     private com.sanad.platform.idempotency.service.IdempotentCommandExecutor idempotentCommandExecutor;

    @org.junit.jupiter.api.BeforeEach
    void setupIdempotencyMock() {
        // Mock the executor to call the actual service and return the result.
        // This allows the service mock to control the outcome (success, 404, 409).
        org.mockito.Mockito.when(idempotentCommandExecutor.execute(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> {
                // The last argument is the Supplier<T> (business action)
                @SuppressWarnings("unchecked")
                java.util.function.Supplier<OrganizationResponse> action =
                    (java.util.function.Supplier<OrganizationResponse>) invocation.getArgument(5);
                try {
                    OrganizationResponse result = action.get();
                    return new com.sanad.platform.idempotency.service.IdempotentHttpResult<>(
                        201,
                        java.util.Map.of(),
                        null,
                        result != null ? result.getId() : null,
                        false,
                        1L,
                        result
                    );
                } catch (RuntimeException e) {
                    throw e; // Propagate service exceptions (404, 409, etc.)
                }
            });
    }

    // Common fixtures
    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID organizationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final Instant now = Instant.now();

    /**
     * CASE 1 — POST valid request returns 201 Created.
     *
     * <p>Verifies that the controller:</p>
     * <ul>
     *   <li>Accepts a well-formed JSON body</li>
     *   <li>Delegates to the service</li>
     *   <li>Returns HTTP 201</li>
     *   <li>Sets the Location header to the new resource URI</li>
     *   <li>Serializes the response body as JSON</li>
     * </ul>
     */
    @Test
    @DisplayName("CASE 1: POST valid request -> 201 Created with Location header")
    void createOrganization_validRequest_returns201() throws Exception {
        // --- Arrange ---
        CreateOrganizationRequest request = new CreateOrganizationRequest("Acme Riyadh Branch", "Main Riyadh operations");

        OrganizationResponse serviceResponse = new OrganizationResponse(
                organizationId, tenantId, "Acme Riyadh Branch", "Main Riyadh operations",
                OrganizationStatus.ACTIVE, now, now);

        when(organizationService.createOrganization(eq(tenantId), any(CreateOrganizationRequest.class)))
                .thenReturn(serviceResponse);

        // --- Act + Assert ---
        mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .requestAttr("sanad.idempotency.key", "test-key")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.containsString("/api/v1/organizations/" + organizationId)))
                .andExpect(jsonPath("$.id").value(organizationId.toString()))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.name").value("Acme Riyadh Branch"))
                .andExpect(jsonPath("$.description").value("Main Riyadh operations"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    /**
     * CASE 2 — POST duplicate organization returns 409 Conflict.
     *
     * <p>When the service throws {@link OrganizationAlreadyExistsException},
     * the exception handler must translate it to HTTP 409 with a structured
     * error body containing the timestamp, status, error, message, and path.</p>
     */
    @Test
    @DisplayName("CASE 2: POST duplicate organization -> 409 Conflict")
    void createOrganization_duplicateOrganization_returns409() throws Exception {
        // --- Arrange ---
        CreateOrganizationRequest request = new CreateOrganizationRequest("Acme Riyadh Branch", "Main Riyadh operations");

        when(organizationService.createOrganization(eq(tenantId), any(CreateOrganizationRequest.class)))
                .thenThrow(new OrganizationAlreadyExistsException(tenantId, "Acme Riyadh Branch"));

        // --- Act + Assert ---
        mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .requestAttr("sanad.idempotency.key", "test-key")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value("/api/v1/organizations"));
    }

    /**
     * CASE 3 — POST with invalid (non-existent) tenant returns 404 Not Found.
     *
     * <p>When the service throws {@link EntityNotFoundException} (because
     * tenantRepository.findById returned empty), the exception handler must
     * translate it to HTTP 404.</p>
     */
    @Test
    @DisplayName("CASE 3: POST invalid tenant -> 404 Not Found")
    void createOrganization_invalidTenant_returns404() throws Exception {
        // --- Arrange ---
        CreateOrganizationRequest request = new CreateOrganizationRequest("Acme Riyadh Branch", "Main Riyadh operations");

        when(organizationService.createOrganization(eq(tenantId), any(CreateOrganizationRequest.class)))
                .thenThrow(new EntityNotFoundException("Tenant not found with id: " + tenantId));

        // --- Act + Assert ---
        mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .requestAttr("sanad.idempotency.key", "test-key")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value("/api/v1/organizations"));
    }

    /**
     * CASE 4 — POST invalid request body returns 400 Bad Request.
     *
     * <p>Sends a request with a blank name and a missing tenantId to verify
     * that the {@code @Valid} annotation on the controller triggers Bean
     * Validation, which the exception handler translates to HTTP 400.</p>
     */
    @Test
    @DisplayName("CASE 4: POST invalid request body -> 400 Bad Request")
    void createOrganization_invalidRequestBody_returns400() throws Exception {
        // --- Arrange ---
        // Missing tenantId + blank name + description too long (>1000 chars)
        String tooLongDescription = "x".repeat(1001);
        String invalidJson = "{\"description\":\"" + tooLongDescription + "\"}";

        // Service should NEVER be called because validation fails first
        // (no need to stub; if it gets called, the test will fail unexpectedly)

        // --- Act + Assert ---
        mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .requestAttr("sanad.idempotency.key", "test-key")
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value("/api/v1/organizations"));
    }

    // ============================================================
    // EXEC-PROMPT-008 — Read endpoint tests
    // ============================================================

    /**
     * CASE 5 — GET by id valid request returns 200 OK.
     */
    @Test
    @DisplayName("CASE 5: GET by id valid -> 200 OK with OrganizationResponse")
    void getOrganization_validRequest_returns200() throws Exception {
        OrganizationResponse serviceResponse = new OrganizationResponse(
                organizationId, tenantId, "Acme Riyadh Branch", "Main Riyadh operations",
                OrganizationStatus.ACTIVE, now, now);

        when(organizationService.getOrganization(eq(tenantId), eq(organizationId)))
                .thenReturn(serviceResponse);

        mockMvc.perform(get("/api/v1/organizations/{id}", organizationId)
                        .param("tenantId", tenantId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(organizationId.toString()))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.name").value("Acme Riyadh Branch"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    /**
     * CASE 6 — GET by id not found returns 404.
     */
    @Test
    @DisplayName("CASE 6: GET by id not found -> 404 Not Found")
    void getOrganization_notFound_returns404() throws Exception {
        when(organizationService.getOrganization(eq(tenantId), eq(organizationId)))
                .thenThrow(new EntityNotFoundException(
                        "Organization not found with id: " + organizationId + " for tenant: " + tenantId));

        mockMvc.perform(get("/api/v1/organizations/{id}", organizationId)
                        .param("tenantId", tenantId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value("/api/v1/organizations/" + organizationId));
    }

    /**
     * CASE 7 — GET by id missing tenantId returns 400.
     */
    @Test
    @DisplayName("CASE 7: GET by id missing tenantId -> 400 Bad Request")
    void getOrganization_missingTenantId_returns400() throws Exception {
        // Note: no .param("tenantId", ...) on this request
        mockMvc.perform(get("/api/v1/organizations/{id}", organizationId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value("/api/v1/organizations/" + organizationId));
    }

    /**
     * CASE 8 — GET list by tenantId returns 200 OK.
     */
    @Test
    @DisplayName("CASE 8: GET list by tenantId -> 200 OK with array of OrganizationResponse")
    void listOrganizations_validTenantId_returns200() throws Exception {
        OrganizationResponse r1 = new OrganizationResponse(
                organizationId, tenantId, "Acme Riyadh Branch", "Main Riyadh",
                OrganizationStatus.ACTIVE, now, now);
        UUID secondId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        OrganizationResponse r2 = new OrganizationResponse(
                secondId, tenantId, "Acme Jeddah Branch", "Jeddah ops",
                OrganizationStatus.ACTIVE, now, now);

        when(organizationService.listOrganizations(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(r1, r2)));

        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", tenantId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Acme Riyadh Branch"))
                .andExpect(jsonPath("$.content[1].name").value("Acme Jeddah Branch"));
    }

    /**
     * CASE 9 — GET list missing tenantId returns 400.
     */
    @Test
    @DisplayName("CASE 9: GET list missing tenantId -> 400 Bad Request")
    void listOrganizations_missingTenantId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value("/api/v1/organizations"));
    }

    // ============================================================
    // EXEC-PROMPT-009 — Update + Status Management tests
    // ============================================================

    /**
     * CASE 10 — PUT valid update returns 200.
     */
    @Test
    @DisplayName("CASE 10: PUT valid update -> 200 OK")
    void updateOrganization_validUpdate_returns200() throws Exception {
        UpdateOrganizationRequest update = new UpdateOrganizationRequest("Acme Riyadh v2", "Updated desc");
        OrganizationResponse response = new OrganizationResponse(
                organizationId, tenantId, "Acme Riyadh v2", "Updated desc",
                OrganizationStatus.ACTIVE, now, now);

        when(organizationService.updateOrganization(eq(tenantId), eq(organizationId),
                any(UpdateOrganizationRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/organizations/{id}", organizationId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .requestAttr("sanad.idempotency.key", "test-key")
                        .content(objectMapper.writeValueAsString(update))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(organizationId.toString()))
                .andExpect(jsonPath("$.name").value("Acme Riyadh v2"))
                .andExpect(jsonPath("$.description").value("Updated desc"));
    }

    /**
     * CASE 11 — PUT duplicate name returns 409.
     */
    @Test
    @DisplayName("CASE 11: PUT duplicate name -> 409 Conflict")
    void updateOrganization_duplicateName_returns409() throws Exception {
        UpdateOrganizationRequest update = new UpdateOrganizationRequest("Existing Name", "x");

        when(organizationService.updateOrganization(eq(tenantId), eq(organizationId),
                any(UpdateOrganizationRequest.class)))
                .thenThrow(new OrganizationAlreadyExistsException(tenantId, "Existing Name"));

        mockMvc.perform(put("/api/v1/organizations/{id}", organizationId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .requestAttr("sanad.idempotency.key", "test-key")
                        .content(objectMapper.writeValueAsString(update))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists());
    }

    /**
     * CASE 12 — PUT organization not found returns 404.
     */
    @Test
    @DisplayName("CASE 12: PUT organization not found -> 404")
    void updateOrganization_notFound_returns404() throws Exception {
        UpdateOrganizationRequest update = new UpdateOrganizationRequest("New Name", "x");

        when(organizationService.updateOrganization(eq(tenantId), eq(organizationId),
                any(UpdateOrganizationRequest.class)))
                .thenThrow(new EntityNotFoundException(
                        "Organization not found with id: " + organizationId + " for tenant: " + tenantId));

        mockMvc.perform(put("/api/v1/organizations/{id}", organizationId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .requestAttr("sanad.idempotency.key", "test-key")
                        .content(objectMapper.writeValueAsString(update))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists());
    }

    /**
     * CASE 13 — PUT invalid body returns 400.
     */
    @Test
    @DisplayName("CASE 13: PUT invalid body (blank name) -> 400")
    void updateOrganization_invalidBody_returns400() throws Exception {
        // name is blank -> @NotBlank violation
        String invalidJson = "{\"name\":\"\",\"description\":\"x\"}";

        mockMvc.perform(put("/api/v1/organizations/{id}", organizationId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .requestAttr("sanad.idempotency.key", "test-key")
                        .content(invalidJson)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists());
    }

    /**
     * CASE 14 — PATCH activate valid returns 200.
     */
    @Test
    @DisplayName("CASE 14: PATCH activate valid -> 200 OK")
    void activateOrganization_valid_returns200() throws Exception {
        OrganizationResponse response = new OrganizationResponse(
                organizationId, tenantId, "Acme Riyadh Branch", "Main Riyadh operations",
                OrganizationStatus.ACTIVE, now, now);
        when(organizationService.activateOrganization(eq(tenantId), eq(organizationId)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/organizations/{id}/activate", organizationId)
                        .param("tenantId", tenantId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    /**
     * CASE 15 — PATCH deactivate valid returns 200.
     */
    @Test
    @DisplayName("CASE 15: PATCH deactivate valid -> 200 OK")
    void deactivateOrganization_valid_returns200() throws Exception {
        OrganizationResponse response = new OrganizationResponse(
                organizationId, tenantId, "Acme Riyadh Branch", "Main Riyadh operations",
                OrganizationStatus.INACTIVE, now, now);
        when(organizationService.deactivateOrganization(eq(tenantId), eq(organizationId)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/organizations/{id}/deactivate", organizationId)
                        .param("tenantId", tenantId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    /**
     * CASE 16 — PATCH activate missing tenantId returns 400.
     */
    @Test
    @DisplayName("CASE 16: PATCH activate missing tenantId -> 400")
    void activateOrganization_missingTenantId_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/organizations/{id}/activate", organizationId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value("/api/v1/organizations/" + organizationId + "/activate"));
    }

    /**
     * CASE 17 — PATCH deactivate organization not found returns 404.
     */
    @Test
    @DisplayName("CASE 17: PATCH deactivate not found -> 404")
    void deactivateOrganization_notFound_returns404() throws Exception {
        when(organizationService.deactivateOrganization(eq(tenantId), eq(organizationId)))
                .thenThrow(new EntityNotFoundException(
                        "Organization not found with id: " + organizationId + " for tenant: " + tenantId));

        mockMvc.perform(patch("/api/v1/organizations/{id}/deactivate", organizationId)
                        .param("tenantId", tenantId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists());
    }

    // ============================================================
    // EXEC-PROMPT-010 — Archive (soft delete) tests
    // ============================================================

    /**
     * CASE 18 — PATCH archive valid returns 200.
     */
    @Test
    @DisplayName("CASE 18: PATCH archive valid -> 200 OK")
    void archiveOrganization_valid_returns200() throws Exception {
        OrganizationResponse response = new OrganizationResponse(
                organizationId, tenantId, "Acme Riyadh Branch", "Main Riyadh operations",
                OrganizationStatus.ARCHIVED, now, now);
        when(organizationService.archiveOrganization(eq(tenantId), eq(organizationId)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/organizations/{id}/archive", organizationId)
                        .param("tenantId", tenantId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    /**
     * CASE 19 — PATCH archive organization not found returns 404.
     */
    @Test
    @DisplayName("CASE 19: PATCH archive not found -> 404")
    void archiveOrganization_notFound_returns404() throws Exception {
        when(organizationService.archiveOrganization(eq(tenantId), eq(organizationId)))
                .thenThrow(new EntityNotFoundException(
                        "Organization not found with id: " + organizationId + " for tenant: " + tenantId));

        mockMvc.perform(patch("/api/v1/organizations/{id}/archive", organizationId)
                        .param("tenantId", tenantId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists());
    }

    /**
     * CASE 20 — PATCH archive missing tenantId returns 400.
     */
    @Test
    @DisplayName("CASE 20: PATCH archive missing tenantId -> 400")
    void archiveOrganization_missingTenantId_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/organizations/{id}/archive", organizationId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value("/api/v1/organizations/" + organizationId + "/archive"));
    }
}
