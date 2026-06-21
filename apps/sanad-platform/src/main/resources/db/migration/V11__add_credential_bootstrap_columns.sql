-- ============================================================
-- V11: Add bootstrap admin credential columns
-- EXEC-PROMPT-032A: Credential Bootstrap
-- ============================================================
-- Adds a column to track whether a user's password was set
-- via bootstrap (initial admin setup) vs. normal password flow.
-- This is for auditing only — no security logic depends on it.
-- ============================================================

ALTER TABLE users
    ADD COLUMN password_set_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE users
    ADD COLUMN password_set_by VARCHAR(100);
