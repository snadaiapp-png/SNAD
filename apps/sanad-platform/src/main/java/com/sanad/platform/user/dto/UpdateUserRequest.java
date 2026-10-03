package com.sanad.platform.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request for updating mutable user profile fields. Lifecycle status changes
 * are intentionally handled by explicit service operations.
 */
public class UpdateUserRequest {

    @NotBlank(message = "email must not be blank")
    @Email(message = "email must be valid")
    @Size(max = 255, message = "email must be at most 255 characters")
    private String email;

    @Size(max = 100, message = "username must be at most 100 characters")
    private String username;

    @Size(max = 200, message = "displayName must be at most 200 characters")
    private String displayName;

    public UpdateUserRequest() {
    }

    public UpdateUserRequest(String email, String displayName) {
        this(email, null, displayName);
    }

    public UpdateUserRequest(String email, String username, String displayName) {
        setEmail(email);
        this.username = username;
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    /**
     * Trims transport-level surrounding whitespace before Bean Validation.
     * Case normalization remains an application-service responsibility.
     */
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}