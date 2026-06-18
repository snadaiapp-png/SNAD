package com.sanad.platform.organization.membership.domain;

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
 * JPA entity representing a membership row linking an email address to an
 * Organization within a Tenant.
 *
 * <p>A membership is the persistence foundation for "who belongs to which
 * organization". It does NOT yet reference a User entity (which doesn't
 * exist in the platform); instead it captures the email address that will
 * eventually be linked to a User when the User domain is introduced.</p>
 *
 * <h2>Tenant Isolation</h2>
 * <p>Every query method on the repository is tenant-scoped. The
 * {@code tenantId} column is NOT a foreign key to a Tenant entity on
 * this aggregate (unlike Organization's {@code @ManyToOne Tenant}); it is
 * a plain UUID column. This is intentional: membership is a join/edge
 * aggregate that should remain lightweight, and enforcing a Tenant entity
 * reference here would force an extra join on every membership query.
 * Tenant isolation is enforced at the repository query level (every
 * method takes a {@code tenantId} parameter).</p>
 *
 * <h2>Email Normalization</h2>
 * <p>Emails are normalized to lowercase before persistence (see
 * {@link #normalizeEmail()}) so that uniqueness checks are case-insensitive.
 * The unique constraint {@code uk_org_memberships_tenant_org_email} backs
 * this at the database level.</p>
 */
@Entity
@Table(
        name = "organization_memberships",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_org_memberships_tenant_org_email",
                columnNames = {"tenant_id", "organization_id", "email"}
        )
)
@EntityListeners(AuditingEntityListener.class)
public class OrganizationMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    /** Tenant scope. Plain UUID (no entity relationship) for query efficiency. */
    @NotNull
    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    /** The organization this membership belongs to. Plain UUID (no entity relationship). */
    @NotNull
    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid")
    private UUID organizationId;

    /** Email address of the member. Stored lowercase for case-insensitive uniqueness. */
    @NotBlank
    @Email
    @Size(max = 255)
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /** Optional human-readable name for display. */
    @Size(max = 200)
    @Column(name = "display_name", length = 200)
    private String displayName;

    /** Current lifecycle status. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MembershipStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------

    protected OrganizationMembership() {
    }

    public OrganizationMembership(UUID tenantId, UUID organizationId, String email,
                                   String displayName, MembershipStatus status) {
        this.tenantId = tenantId;
        this.organizationId = organizationId;
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

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = normalizeEmail(email); }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public MembershipStatus getStatus() { return status; }
    public void setStatus(MembershipStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    // ------------------------------------------------------------
    // equals / hashCode / toString
    // ------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrganizationMembership that)) return false;
        if (id != null && that.id != null) return Objects.equals(id, that.id);
        return Objects.equals(tenantId, that.tenantId)
                && Objects.equals(organizationId, that.organizationId)
                && Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, organizationId, email);
    }

    @Override
    public String toString() {
        return "OrganizationMembership{" +
                "id=" + id +
                ", tenantId=" + tenantId +
                ", organizationId=" + organizationId +
                ", email='" + email + '\'' +
                ", displayName='" + displayName + '\'' +
                ", status=" + status +
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
