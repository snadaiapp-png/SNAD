package com.sanad.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Governed credential supplied by an authorized tenant administrator during
 * an explicitly enabled production-recovery window.
 */
public class AdminReconcileCredentialRequest {

    @NotBlank
    @Size(min = 8, max = 256)
    private String credential;

    public AdminReconcileCredentialRequest() {
    }

    public AdminReconcileCredentialRequest(String credential) {
        this.credential = credential;
    }

    public String getCredential() {
        return credential;
    }

    public void setCredential(String credential) {
        this.credential = credential;
    }
}
