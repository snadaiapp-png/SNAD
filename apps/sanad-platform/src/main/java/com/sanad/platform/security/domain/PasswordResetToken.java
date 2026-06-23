package com.sanad.platform.security.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** JPA entity representing a one-time password reset token. */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    /** SHA-256 hash of the raw token. The raw value is only in the reset link. */
    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PasswordResetTokenStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    /** Best-effort IP address of the requestor for audit. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    protected PasswordResetToken() {
    }

    public PasswordResetToken(UUID tenantId, UUID userId, String tokenHash, Instant expiresAt, String ipAddress) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.status = PasswordResetTokenStatus.ACTIVE;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.ipAddress = ipAddress;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isUsable() {
        return status == PasswordResetTokenStatus.ACTIVE && !isExpired();
    }

    // --- Getters and setters ---

    public UUID getId() { return id; }

    public UUID getTenantId() { return tenantId; }

    public UUID getUserId() { return userId; }

    public String getTokenHash() { return tokenHash; }

    public PasswordResetTokenStatus getStatus() { return status; }
    public void setStatus(PasswordResetTokenStatus status) { this.status = status; }

    public Instant getExpiresAt() { return expiresAt; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }

    public String getIpAddress() { return ipAddress; }
}
