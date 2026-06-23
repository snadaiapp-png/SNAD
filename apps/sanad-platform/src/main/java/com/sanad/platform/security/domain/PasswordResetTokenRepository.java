package com.sanad.platform.security.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Repository for password reset tokens. */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /** Find an active token by its hash (with pessimistic write lock for atomic use). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PasswordResetToken t WHERE t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /** Revoke all active reset tokens for a user (used when password is reset or account is recovered). */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.status = 'REVOKED' WHERE t.tenantId = :tenantId AND t.userId = :userId AND t.status = 'ACTIVE'")
    int revokeAllActive(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    /** Mark expired tokens. Called periodically or on lookup. */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.status = 'EXPIRED' WHERE t.status = 'ACTIVE' AND t.expiresAt < :now")
    int markExpired(@Param("now") Instant now);
}
