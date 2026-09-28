package com.sanad.platform.access.evaluation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Authoritative per-subject authorization version counter (Wave 1 Task 12
 * surface, introduced by the override service in Task 9). Every audited
 * authorization mutation bumps {@code users.authorization_version} so caches
 * and projections can invalidate deterministically — revocation never relies
 * on TTL.
 */
@Service
public class AuthorizationVersionService {

    private final JdbcTemplate jdbc;

    public AuthorizationVersionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Bumps and returns the subject's authorization version. */
    @Transactional
    public long bump(UUID tenantId, UUID userId) {
        if (tenantId == null || userId == null) {
            throw new IllegalArgumentException("tenantId and userId are required");
        }
        Long version = jdbc.queryForObject(
                "UPDATE users SET authorization_version = authorization_version + 1, "
                        + "updated_at = CURRENT_TIMESTAMP "
                        + "WHERE tenant_id = ? AND id = ? "
                        + "RETURNING authorization_version",
                Long.class, tenantId, userId);
        if (version == null) {
            throw new IllegalStateException("Authorization version bump failed for subject");
        }
        return version;
    }

    /** Current version without mutation (0 when the subject has none yet). */
    @Transactional(readOnly = true)
    public long current(UUID tenantId, UUID userId) {
        if (tenantId == null || userId == null) {
            throw new IllegalArgumentException("tenantId and userId are required");
        }
        Long version = jdbc.queryForObject(
                "SELECT authorization_version FROM users WHERE tenant_id = ? AND id = ?",
                Long.class, tenantId, userId);
        return version == null ? 0L : version;
    }
}
