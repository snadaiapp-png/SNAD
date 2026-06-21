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
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing a User in the SANAD platform.
 *
 * <p>A User is scoped to a Tenant. The {@code tenantId} is a plain UUID
 * column (no entity relationship) to keep user queries lightweight — the
 * same pattern used for {@code OrganizationMembership}. Tenant isolation
 * is enforced at the repository query level (every method takes a
 * {@code tenantId} parameter).</p>
 *
 * <p>This is the persistence foundation only. Authentication credential
 * storage (password_hash) was added in EXEC-PROMPT-032A, but the actual
 * authentication logic lives in the security package. Role/permission
 * models exist in the access package.</p>
 *
 * <h2>Email Normalization</h2>
 * <p>Emails are normalized to lowercase before persistence (see
 * {@link #normalizeEmail(String)}) so that uniqueness checks are
 * case-insensitive. The unique constraint {@code uk_users_tenant_email}
 * backs this at the database level.</p>
 */
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

    /** Tenant scope. Plain UUID (no entity relationship) for query efficiency. */
    @NotNull
    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    /** Email address. Stored lowercase for case-insensitive uniqueness. */
    @NotBlank
    @Email
    @Size(max = 255)
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /** Optional human-readable display name. */
    @Size(max = 200)
    @Column(name = "display_name", length = 200)
    private String displayName;

    /** Current lifecycle status. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    /** BCrypt password hash. Nullable — users created before auth was enabled have no password. */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    /** Timestamp of the last successful login. Nullable if never logged in. */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------

    protected User() {
    }

    public User(UUID tenantId, String email, String displayName, UserStatus status) {
        this.tenantId = tenantId;
        this.email = normalizeEmail(email);
        this.displayName = displayName;
        this.status = status;
    }

    // ------------------------------------------------------------
    // Getters / Setters
    // ------------------------------------------------------------

    public UUID getId() { return id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = normalizeEmail(email); }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    /**
     * Returns the BCrypt password hash.
     * <p><strong>Never expose this in API responses or logs.</strong></p>
     */
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    // ------------------------------------------------------------
    // equals / hashCode / toString
    // ------------------------------------------------------------

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
        // Never include passwordHash in toString() — it could leak via logs.
        return "User{" +
                "id=" + id +
                ", tenantId=" + tenantId +
                ", email='" + email + '\'' +
                ", displayName='" + displayName + '\'' +
                ", status=" + status +
                ", lastLoginAt=" + lastLoginAt +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    // ------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------

    /** Lowercase + trim the email so uniqueness is case-insensitive. */
    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
