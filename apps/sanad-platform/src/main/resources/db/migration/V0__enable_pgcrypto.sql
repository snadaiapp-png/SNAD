-- Enable pgcrypto extension for gen_random_uuid() function
-- This must run before any migration that uses gen_random_uuid()
-- Supabase PostgreSQL may not have this extension enabled by default
CREATE EXTENSION IF NOT EXISTS pgcrypto;
