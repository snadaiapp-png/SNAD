-- ============================================================
-- V13: Session version for immediate access token revocation
-- DEFECT-001: Token Revocation After Logout (Option B)
-- ============================================================
-- Adds a monotonically-increasing session_version to each user.
-- When a user logs out, changes password, or has their password
-- reset, the session_version is incremented. The current version
-- is embedded in the JWT at mint time. The JwtAuthenticationFilter
-- compares the JWT's session_version against the DB value; a
-- mismatch means the token was issued before the session was
-- invalidated and the request is rejected with 401.
-- ============================================================

ALTER TABLE users
    ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN users.session_version IS
    'Monotonically-increasing version counter for session invalidation. '
    'Incremented on logout, password change, and password reset. '
    'Embedded in JWT at mint time; mismatch triggers 401.';
