package com.sanad.platform.security.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing a refresh token in the SANAD platform.
 *
 * <p>Refresh tokens are opaque (not JWTs), single-use, and rotated on each
 * refresh. The {@code tokenHash} column stores a SHA-256 hash of the raw
 * token — the raw token is never persisted. On refresh, the old token is
 * marked {@link RefreshTokenStatus#USED} and a new token is issued.</p>
 *
 * <p>Replay protection: if a USED token is presented again, all refresh
 * tokens for that user are revoked (token family invalidation).</p>
 */
@Entity
@Table(name = "refresh_tokens")
@EntityListeners(AuditingEntityListener.class)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @NotNull
    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    /** SHA-256 hash of the raw refresh token. The raw token is never persisted. */
    @NotNull
    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefreshTokenStatus status = RefreshTokenStatus.ACTIVE;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "replaced_by_id", columnDefinition = "uuid")
    private UUID replacedById;

    // ------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------

    protected RefreshToken() {
    }

    public RefreshToken(UUID tenantId, UUID userId, String tokenHash, Instant expiresAt) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.status = RefreshTokenStatus.ACTIVE;
    }

    // ------------------------------------------------------------
    // Getters / Setters
    // ------------------------------------------------------------

    public UUID getId() { return id; }

    public UUID getTenantId() { return tenantId; }

    public UUID getUserId() { return userId; }

    public String getTokenHash() { return tokenHash; }

    public RefreshTokenStatus getStatus() { return status; }
    public void setStatus(RefreshTokenStatus status) { this.status = status; }

    public Instant getExpiresAt() { return expiresAt; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }

    public UUID getReplacedById() { return replacedById; }
    public void setReplacedById(UUID replacedById) { this.replacedById = replacedById; }

    /** Returns true if the token has expired. */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /** Returns true if the token is active and not expired. */
    public boolean isValid() {
        return status == RefreshTokenStatus.ACTIVE && !isExpired();
    }

    // ------------------------------------------------------------
    // equals / hashCode / toString
    // ------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RefreshToken that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        // Never include tokenHash in toString().
        return "RefreshToken{" +
                "id=" + id +
                ", tenantId=" + tenantId +
                ", userId=" + userId +
                ", status=" + status +
                ", expiresAt=" + expiresAt +
                ", createdAt=" + createdAt +
                '}';
    }
}
