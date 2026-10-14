package com.sanad.platform.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;\n\nimport java.util.UUID;

/** Request body for {@code POST /api/v1/auth/forgot-password}. */
public class ForgotPasswordRequest {

    @NotBlank
    @Email
    private String email;\n\n    /** Tenant lookup scope; required for RLS-compatible recovery. */\n    private UUID tenantId;

    public ForgotPasswordRequest() {
    }

    public ForgotPasswordRequest(String email) {
        this.email = email;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }\n\n    public UUID getTenantId() { return tenantId; }\n    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
}
