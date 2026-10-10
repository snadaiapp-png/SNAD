package com.sanad.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One-time initial credential bootstrap request for a credentialless account.
 * This request is intentionally separate from password recovery/reset flows.
 */
public class AdminBootstrapCredentialRequest {

    @NotBlank(message = "newCredential is required")
    @Size(min = 8, max = 100, message = "newCredential must be between 8 and 100 characters")
    private String newCredential;

    public AdminBootstrapCredentialRequest() {
    }

    public AdminBootstrapCredentialRequest(String newCredential) {
        this.newCredential = newCredential;
    }

    public String getNewCredential() {
        return newCredential;
    }

    public void setNewCredential(String newCredential) {
        this.newCredential = newCredential;
    }
}
