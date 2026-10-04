package com.sanad.platform.user.dto;

import com.sanad.platform.user.domain.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request used by the application service to create a tenant-scoped user.
 * Tenant identity is supplied separately to the service method so it cannot
 * be overridden by a payload.
 */
public class CreateUserRequest {

    @NotBlank(message = "email must not be blank")
    @Email(message = "email must be valid")
    @Size(max = 255, message = "email must be at most 255 characters")
    private String email;

    @Size(max = 100, message = "username must be at most 100 characters")
    private String username;

    @Size(max = 200, message = "displayName must be at most 200 characters")
    private String displayName;

    private UserStatus status;

    public CreateUserRequest() {
    }

    public CreateUserRequest(String email, String displayName, UserStatus status) {
        this(email, null, displayName, status);
    }

    public CreateUserRequest(String email, String username, String displayName, UserStatus status) {
        setEmail(email);
        this.username = username;
        this.displayName = displayName;
        this.status = status;
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

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }
}