package com.sanad.platform.user.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.dto.CreateUserRequest;
import com.sanad.platform.user.dto.UpdateUserRequest;
import com.sanad.platform.user.dto.UserResponse;
import com.sanad.platform.user.exception.DuplicateUserEmailException;
import com.sanad.platform.user.exception.UserNotFoundException;
import com.sanad.platform.user.service.UserService;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(UserApiExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final Instant now = Instant.parse("2026-06-18T00:00:00Z");

    private UserResponse response(UserStatus status) {
        return new UserResponse(userId, tenantId, "alice@example.com", "Alice", status, now, now);
    }

    @Test
    @DisplayName("CASE 1: POST valid user -> 201 Created")
    void createUser_valid_returns201() throws Exception {
        CreateUserRequest request = new CreateUserRequest("alice@example.com", "Alice", null);
        when(userService.createUser(eq(tenantId), any(CreateUserRequest.class)))
                .thenReturn(response(UserStatus.INVITED));

        mockMvc.perform(post("/api/v1/users")
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.status").value("INVITED"));
    }

    @Test
    @DisplayName("CASE 2: POST includes Location header")
    void createUser_returnsLocationHeader() throws Exception {
        CreateUserRequest request = new CreateUserRequest("alice@example.com", "Alice", null);
        when(userService.createUser(eq(tenantId), any(CreateUserRequest.class)))
                .thenReturn(response(UserStatus.INVITED));

        mockMvc.perform(post("/api/v1/users")
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(userId.toString())));
    }

    @Test
    @DisplayName("CASE 3: POST invalid body -> 400")
    void createUser_invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "", "displayName", "Alice"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("CASE 4: POST duplicate email -> 409")
    void createUser_duplicate_returns409() throws Exception {
        CreateUserRequest request = new CreateUserRequest("alice@example.com", "Alice", null);
        when(userService.createUser(eq(tenantId), any(CreateUserRequest.class)))
                .thenThrow(new DuplicateUserEmailException(tenantId, "alice@example.com"));

        mockMvc.perform(post("/api/v1/users")
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("CASE 5: POST unknown tenant -> 404")
    void createUser_unknownTenant_returns404() throws Exception {
        CreateUserRequest request = new CreateUserRequest("alice@example.com", "Alice", null);
        when(userService.createUser(eq(tenantId), any(CreateUserRequest.class)))
                .thenThrow(new EntityNotFoundException("Tenant not found"));

        mockMvc.perform(post("/api/v1/users")
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("CASE 6: GET list -> 200")
    void listUsers_returns200() throws Exception {
        when(userService.listUsers(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class))).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(response(UserStatus.ACTIVE))));

        mockMvc.perform(get("/api/v1/users").param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].email").value("alice@example.com"));
    }

    @Test
    @DisplayName("CASE 7: GET list without tenantId -> 400")
    void listUsers_missingTenantId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("CASE 8: GET user -> 200")
    void getUser_returns200() throws Exception {
        when(userService.getUser(tenantId, userId)).thenReturn(response(UserStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/users/{userId}", userId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()));
    }

    @Test
    @DisplayName("CASE 9: GET unknown user -> 404")
    void getUser_notFound_returns404() throws Exception {
        when(userService.getUser(tenantId, userId))
                .thenThrow(new UserNotFoundException(tenantId, userId));

        mockMvc.perform(get("/api/v1/users/{userId}", userId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("CASE 10: PUT valid user -> 200")
    void updateUser_returns200() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest("updated@example.com", "Updated");
        UserResponse updated = new UserResponse(userId, tenantId, "updated@example.com", "Updated",
                UserStatus.ACTIVE, now, now);
        when(userService.updateUser(eq(tenantId), eq(userId), any(UpdateUserRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/users/{userId}", userId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("updated@example.com"));
    }

    @Test
    @DisplayName("CASE 11: PUT invalid user -> 400")
    void updateUser_invalidBody_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/users/{userId}", userId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "", "displayName", "Updated"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("CASE 12: PATCH activate -> 200")
    void activateUser_returns200() throws Exception {
        when(userService.activateUser(tenantId, userId)).thenReturn(response(UserStatus.ACTIVE));

        mockMvc.perform(patch("/api/v1/users/{userId}/activate", userId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("CASE 13: PATCH deactivate -> 200")
    void deactivateUser_returns200() throws Exception {
        when(userService.deactivateUser(tenantId, userId)).thenReturn(response(UserStatus.INACTIVE));

        mockMvc.perform(patch("/api/v1/users/{userId}/deactivate", userId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    @DisplayName("CASE 14: PATCH suspend -> 200")
    void suspendUser_returns200() throws Exception {
        when(userService.suspendUser(tenantId, userId)).thenReturn(response(UserStatus.SUSPENDED));

        mockMvc.perform(patch("/api/v1/users/{userId}/suspend", userId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    @DisplayName("CASE 15: PATCH archive -> 200")
    void archiveUser_returns200() throws Exception {
        when(userService.archiveUser(tenantId, userId)).thenReturn(response(UserStatus.ARCHIVED));

        mockMvc.perform(patch("/api/v1/users/{userId}/archive", userId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("CASE 16: malformed tenantId -> structured 400")
    void malformedTenantId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("tenantId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").exists());
    }
}
