package com.sanad.platform.user.repository;

import com.sanad.platform.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link User}.
 *
 * <p>Every method is tenant-scoped. There is no {@code findAll()} that
 * crosses tenant boundaries.</p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * List all users for a tenant.
     */
    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId")
    List<User> findByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * Fetch a single user by id, scoped to a tenant.
     */
    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND u.id = :id")
    Optional<User> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /**
     * Fetch a single user by email, scoped to a tenant.
     */
    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND u.email = :email")
    Optional<User> findByTenantIdAndEmail(@Param("tenantId") UUID tenantId, @Param("email") String email);

    /**
     * Check if a user with the given email already exists within a tenant.
     * Backs the unique constraint {@code uk_users_tenant_email}.
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN TRUE ELSE FALSE END "
            + "FROM User u WHERE u.tenantId = :tenantId AND u.email = :email")
    boolean existsByTenantIdAndEmail(@Param("tenantId") UUID tenantId, @Param("email") String email);

    /**
     * Find users by email across ALL tenants (for email-only login).
     * Returns a list because the same email can exist in different tenants.
     */
    @Query("SELECT u FROM User u WHERE u.email = :email")
    List<User> findAllByEmail(@Param("email") String email);

    /**
     * Returns only the session_version for a tenant-scoped user.
     * Used by JwtAuthenticationFilter to validate token session version
     * without loading the full User entity.
     */
    @Query("SELECT u.sessionVersion FROM User u WHERE u.tenantId = :tenantId AND u.id = :id")
    Long findSessionVersionByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
