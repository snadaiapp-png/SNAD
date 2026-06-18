package com.sanad.platform.organization.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.organization.dto.CreateOrganizationRequest;
import com.sanad.platform.organization.dto.OrganizationResponse;
import com.sanad.platform.organization.exception.OrganizationAlreadyExistsException;
import com.sanad.platform.organization.service.OrganizationService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
@Import(OrganizationApiExceptionHandler.class)
class OrganizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrganizationService organizationService;

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
        CreateOrganizationRequest request = new CreateOrganizationRequest(
                tenantId, "Acme Riyadh Branch", "Main Riyadh operations");

        OrganizationResponse serviceResponse = new OrganizationResponse(
                organizationId, tenantId, "Acme Riyadh Branch", "Main Riyadh operations",
                OrganizationStatus.ACTIVE, now, now);

        when(organizationService.createOrganization(any(CreateOrganizationRequest.class)))
                .thenReturn(serviceResponse);

        // --- Act + Assert ---
        mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
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
        CreateOrganizationRequest request = new CreateOrganizationRequest(
                tenantId, "Acme Riyadh Branch", "Main Riyadh operations");

        when(organizationService.createOrganization(any(CreateOrganizationRequest.class)))
                .thenThrow(new OrganizationAlreadyExistsException(tenantId, "Acme Riyadh Branch"));

        // --- Act + Assert ---
        mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Organization already exists for this tenant"))
                .andExpect(jsonPath("$.path").value("/api/v1/organizations"));
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
        CreateOrganizationRequest request = new CreateOrganizationRequest(
                tenantId, "Acme Riyadh Branch", "Main Riyadh operations");

        when(organizationService.createOrganization(any(CreateOrganizationRequest.class)))
                .thenThrow(new EntityNotFoundException("Tenant not found with id: " + tenantId));

        // --- Act + Assert ---
        mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Tenant not found with id")))
                .andExpect(jsonPath("$.path").value("/api/v1/organizations"));
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
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/organizations"));
    }
}
