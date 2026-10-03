-- SANAD User Administration expansion
-- Add an optional tenant-scoped username without fabricating legacy values.
-- Username remains nullable for backward compatibility; application code
-- normalizes newly supplied values to lowercase before persistence.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS username VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_tenant_username_ci
    ON users (tenant_id, LOWER(username))
    WHERE username IS NOT NULL;
