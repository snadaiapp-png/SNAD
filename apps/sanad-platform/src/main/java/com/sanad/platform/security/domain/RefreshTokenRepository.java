package com.sanad.platform.security.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link RefreshToken} entities.
 *
 * <p>All queries are scoped by {@code tenantId} and {@code userId} to
 * maintain tenant isolation.</p>
 *
 * <p>The {@link #findByTokenHashForUpdate} method uses a pessimistic
 * write lock ({@code SELECT ... FOR UPDATE}) to ensure atomic refresh
 * token consumption. This prevents race conditions where two concurrent
 * refresh requests with the same token both succeed.</p>
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Find a refresh token by its hash (no lock). Used for initial lookup. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Find a refresh token by its hash with a pessimistic write lock.
     * Used during refresh to ensure atomic consumption.
     * The lock prevents concurrent refresh attempts with the same token.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t WHERE t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

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
