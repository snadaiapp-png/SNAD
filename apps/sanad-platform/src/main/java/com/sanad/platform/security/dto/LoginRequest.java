package com.sanad.platform.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/auth/login}.
 *
 * <p>Requires explicit {@code tenantId} because email uniqueness is scoped
 * to (tenantId, email) — the same email can exist in different tenants.</p>
 */
public class LoginRequest {

    @NotNull
    private UUID tenantId;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(min = 1, max = 256)
    private String password;

    // ------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------

    public LoginRequest() {
    }

    public LoginRequest(UUID tenantId, String email, String password) {
        this.tenantId = tenantId;
        this.email = email;
        this.password = password;
    }

    // ------------------------------------------------------------
    // Getters / Setters
    // ------------------------------------------------------------

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
