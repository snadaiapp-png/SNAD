package com.sanad.platform.security.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link RefreshToken} entities.
 *
 * <p>All queries are scoped by {@code tenantId} and {@code userId} to
 * maintain tenant isolation.</p>
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Find an active refresh token by its hash. Used during refresh. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Find all refresh tokens for a user (any status). Used for replay invalidation. */
    @Query("SELECT t FROM RefreshToken t WHERE t.tenantId = :tenantId AND t.userId = :userId")
    List<RefreshToken> findAllByTenantIdAndUserId(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    /** Find active refresh tokens for a user. Used for logout (revoke all active). */
    @Query("SELECT t FROM RefreshToken t WHERE t.tenantId = :tenantId AND t.userId = :userId AND t.status = 'ACTIVE'")
    List<RefreshToken> findActiveByTenantIdAndUserId(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    /** Revoke all active refresh tokens for a user (logout or replay protection). */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.status = 'REVOKED' WHERE t.tenantId = :tenantId AND t.userId = :userId AND t.status = 'ACTIVE'")
    int revokeAllActive(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);
}
