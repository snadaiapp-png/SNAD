-- =====================================================================
-- SANAD Platform — Password Rotation SQL Script
-- Generated: 2026-07-05T10:29:43.168783+00:00
-- =====================================================================
-- IMPORTANT:
--   1. Run this in the SANAD PostgreSQL database (Render).
--   2. Replace table/column names if your schema differs.
--   3. Commit immediately after verification.
--   4. Revoke all active sessions for both users.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------
-- Step 1: Update Admin password
-- ---------------------------------------------------------------
-- Adjust table name (users / user / account) as per your schema.
UPDATE users
SET password_hash = '$2a$10$hhpy5mJwLHeeMP7uKsEaseumj2INAUXtispKVhG7SxIuXQcAPXgy2',
    updated_at = NOW()
WHERE username = 'admin';

-- Verify row was updated (expect: UPDATE 1)
-- If 0 rows, check the username column value.

-- ---------------------------------------------------------------
-- Step 2: Update Identity B password
-- ---------------------------------------------------------------
UPDATE users
SET password_hash = '$2a$10$lD5tqhUAj5pOXL0Jv0i30.VDFyQRkjBLQV0zjrcW5mCySDjsZItUK',
    updated_at = NOW()
WHERE username = 'identity-b';

-- Verify row was updated (expect: UPDATE 1)

-- ---------------------------------------------------------------
-- Step 3: Revoke all active sessions (optional but recommended)
-- ---------------------------------------------------------------
DELETE FROM user_sessions
WHERE username IN ('admin', 'identity-b');

-- Or if you use a refresh_token table:
-- DELETE FROM refresh_tokens
-- WHERE user_id IN (SELECT id FROM users WHERE username IN ('admin', 'identity-b'));

COMMIT;

-- =====================================================================
-- Verification Queries (run AFTER COMMIT)
-- =====================================================================
-- SELECT username, LEFT(password_hash, 7) AS hash_prefix, updated_at
-- FROM users
-- WHERE username IN ('admin', 'identity-b');
--
-- Expected:
--   username   | hash_prefix | updated_at
--   -----------+-------------+---------------------------
--   admin      | $2a$10$...  | <current timestamp>
--   identity-b | $2a$10$...  | <current timestamp>
-- =====================================================================
