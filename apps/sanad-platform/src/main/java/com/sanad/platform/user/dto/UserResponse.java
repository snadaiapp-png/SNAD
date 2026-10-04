package com.sanad.platform.user.dto;

import com.sanad.platform.user.domain.UserStatus;

import java.time.Instant;
import java.util.UUID;

/** Read-only transport representation of a tenant-scoped user. */
public class UserResponse {

    private UUID id;
    private UUID tenantId;
    private String email;
    private String username;
    private String displayName;
    private String mobileNumber;
    private String mobileRegion;
    private UserStatus status;
    private Instant lastLoginAt;
    private boolean credentialInitialized;
    private boolean credentialRotationRequired;
    private Instant createdAt;
    private Instant updatedAt;

    public UserResponse() {
    }

    public UserResponse(UUID id, UUID tenantId, String email, String displayName,
                        UserStatus status, Instant createdAt, Instant updatedAt) {
        this(id, tenantId, email, null, displayName, null, null, status, null, false, false, createdAt, updatedAt);
    }

    public UserResponse(UUID id, UUID tenantId, String email, String username, String displayName,
                        UserStatus status, Instant createdAt, Instant updatedAt) {
        this(id, tenantId, email, username, displayName, null, null, status, null, false, false, createdAt, updatedAt);
    }

    public UserResponse(UUID id, UUID tenantId, String email, String username, String displayName,
                        String mobileNumber, String mobileRegion, UserStatus status, Instant lastLoginAt,
                        boolean credentialInitialized, boolean credentialRotationRequired,
                        Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.email = email;
        this.username = username;
        this.displayName = displayName;
        this.mobileNumber = mobileNumber;
        this.mobileRegion = mobileRegion;
        this.status = status;
        this.lastLoginAt = lastLoginAt;
        this.credentialInitialized = credentialInitialized;
        this.credentialRotationRequired = credentialRotationRequired;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getMobileRegion() {
        return mobileRegion;
    }

    public void setMobileRegion(String mobileRegion) {
        this.mobileRegion = mobileRegion;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public boolean isCredentialInitialized() {
        return credentialInitialized;
    }

    public void setCredentialInitialized(boolean credentialInitialized) {
        this.credentialInitialized = credentialInitialized;
    }

    public boolean isCredentialRotationRequired() {
        return credentialRotationRequired;
    }

    public void setCredentialRotationRequired(boolean credentialRotationRequired) {
        this.credentialRotationRequired = credentialRotationRequired;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}