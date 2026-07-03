package com.sanad.platform.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/login}.
 *
 * <p>Email-only login — no tenantId required. The backend searches
 * for the user by email across all tenants. If exactly one match is
 * found, login proceeds. If multiple matches exist (same email in
 * different tenants), a 409 is returned with the list of tenants
 * so the frontend can prompt for selection.</p>
 */
public class LoginRequest {

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(min = 1, max = 256)
    private String password;

    /** Optional tenantId — if provided, scopes the login to a specific tenant. */
    private java.util.UUID tenantId;

    // ------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------

    public LoginRequest() {
    }

    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    // ------------------------------------------------------------
    // Getters / Setters
    // ------------------------------------------------------------

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public java.util.UUID getTenantId() { return tenantId; }
    public void setTenantId(java.util.UUID tenantId) { this.tenantId = tenantId; }
}
