package com.sanad.platform.security.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Public account-registration request. Password setup is completed by email. */
public class SelfRegistrationRequest {

    @NotBlank(message = "displayName must not be blank")
    @Size(max = 200, message = "displayName must be at most 200 characters")
    private String displayName;

    @NotBlank(message = "email must not be blank")
    @Email(message = "email must be valid")
    @Size(max = 255, message = "email must be at most 255 characters")
    private String email;

    @NotBlank(message = "organizationName must not be blank")
    @Size(max = 200, message = "organizationName must be at most 200 characters")
    private String organizationName;

    @NotBlank(message = "subdomain must not be blank")
    @Size(min = 3, max = 63, message = "subdomain must be between 3 and 63 characters")
    @Pattern(
            regexp = "^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])$",
            message = "subdomain must contain lowercase letters, numbers, and internal hyphens only"
    )
    private String subdomain;

    @AssertTrue(message = "terms must be accepted")
    private boolean acceptTerms;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? null : displayName.trim();
    }

    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName == null ? null : organizationName.trim();
    }

    public String getSubdomain() { return subdomain; }
    public void setSubdomain(String subdomain) {
        this.subdomain = subdomain == null ? null : subdomain.trim().toLowerCase();
    }

    public boolean isAcceptTerms() { return acceptTerms; }
    public void setAcceptTerms(boolean acceptTerms) { this.acceptTerms = acceptTerms; }
}
