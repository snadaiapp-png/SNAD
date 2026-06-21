package com.sanad.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for authenticated credential rotation. */
public class ChangeCredentialRequest {

    @NotBlank
    @Size(max = 256)
    private String currentCredential;

    @NotBlank
    @Size(min = 14, max = 256)
    private String newCredential;

    public ChangeCredentialRequest() {
    }

    public ChangeCredentialRequest(String currentCredential, String newCredential) {
        this.currentCredential = currentCredential;
        this.newCredential = newCredential;
    }

    public String getCurrentCredential() { return currentCredential; }
    public void setCurrentCredential(String currentCredential) {
        this.currentCredential = currentCredential;
    }

    public String getNewCredential() { return newCredential; }
    public void setNewCredential(String newCredential) {
        this.newCredential = newCredential;
    }
}
