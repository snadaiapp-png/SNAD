package com.sanad.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/v1/auth/admin-reset-password} (WS5). */
public class AdminResetPasswordRequest {

    @NotBlank
    @Size(min = 8, max = 256)
    private String newPassword;

    /** Whether to force the user to change their password on next login. */
    private boolean forceChange = true;

    public AdminResetPasswordRequest() {
    }

    public AdminResetPasswordRequest(String newPassword, boolean forceChange) {
        this.newPassword = newPassword;
        this.forceChange = forceChange;
    }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }

    public boolean isForceChange() { return forceChange; }
    public void setForceChange(boolean forceChange) { this.forceChange = forceChange; }
}
