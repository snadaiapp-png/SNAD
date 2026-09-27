package com.sanad.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Initialization-only credential supplied by an authorized tenant administrator.
 *
 * This request is valid only for a user that has no credential yet.
 */
public class AdminInitializeCredentialRequest {

    @NotBlank
    @Size(min = 8, max = 256)
    private String initialCredential;

    public AdminInitializeCredentialRequest() {
    }

    public AdminInitializeCredentialRequest(String initialCredential) {
        this.initialCredential = initialCredential;
    }

    public String getInitialCredential() {
        return initialCredential;
    }

    public void setInitialCredential(String initialCredential) {
        this.initialCredential = initialCredential;
    }
}
