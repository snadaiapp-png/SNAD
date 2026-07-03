package com.sanad.platform.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** JPA entity representing a tenant-scoped SANAD user. */
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_users_tenant_email",
                columnNames = {"tenant_id", "email"}
        )
)
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @NotBlank
    @Email
    @Size(max = 255)
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Size(max = 200)
    @Column(name = "display_name", length = 200)
    private String displayName;

    @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$")
    @Size(max = 20)
    @Column(name = "mobile_number", length = 20)
    private String mobileNumber;

    @Pattern(regexp = "^[A-Z]{2}$")
    @Size(max = 2)
    @Column(name = "mobile_region", length = 2)
    private String mobileRegion;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    /** BCrypt password hash. Never expose through APIs or logs. */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Audit timestamp for the most recent controlled credential enrollment. */
    @Column(name = "password_set_at")
    private Instant passwordSetAt;

    /** Non-secret actor identifier for the credential enrollment. */
    @Size(max = 100)
    @Column(name = "password_set_by", length = 100)
    private String passwordSetBy;

    /** Restricts the session until the bootstrap credential is rotated. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    /**
     * Explicit platform control-plane privilege. This flag is separate from
     * tenant roles so a tenant ADMIN cannot cross tenant boundaries.
     */
    @Column(name = "platform_admin", nullable = false)
    private boolean platformAdmin;

    /**
     * Monotonically-increasing session version for immediate token revocation.
     * Incremented on logout, password change, and password reset.
     */
    @Column(name = "session_version", nullable = false)
    private long sessionVersion;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(UUID tenantId, String email, String displayName, UserStatus status) {
        this.tenantId = tenantId;
        this.email = normalizeEmail(email);
        this.displayName = displayName;
        this.status = status;
    }

    public UUID getId() { return id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = normalizeEmail(email); }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber == null ? null : mobileNumber.trim();
    }

    public String getMobileRegion() { return mobileRegion; }
    public void setMobileRegion(String mobileRegion) {
        this.mobileRegion = mobileRegion == null ? null : mobileRegion.trim().toUpperCase(Locale.ROOT);
    }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public Instant getPasswordSetAt() { return passwordSetAt; }
    public void setPasswordSetAt(Instant passwordSetAt) { this.passwordSetAt = passwordSetAt; }

    public String getPasswordSetBy() { return passwordSetBy; }
    public void setPasswordSetBy(String passwordSetBy) { this.passwordSetBy = passwordSetBy; }

    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public boolean isPlatformAdmin() { return platformAdmin; }
    public void setPlatformAdmin(boolean platformAdmin) { this.platformAdmin = platformAdmin; }

    public long getSessionVersion() { return sessionVersion; }
    public void setSessionVersion(long sessionVersion) {
        this.sessionVersion = sessionVersion;
    }

    public long incrementSessionVersion() {
        return ++this.sessionVersion;
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User that)) return false;
        if (id != null && that.id != null) return Objects.equals(id, that.id);
        return Objects.equals(tenantId, that.tenantId)
                && Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, email);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", tenantId=" + tenantId +
                ", email='" + email + '\'' +
                ", displayName='" + displayName + '\'' +
                ", status=" + status +
                ", lastLoginAt=" + lastLoginAt +
                ", mustChangePassword=" + mustChangePassword +
                ", platformAdmin=" + platformAdmin +
                ", sessionVersion=" + sessionVersion +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
