-- Isolated PostgreSQL 17 proof: migrator owns objects, runtime cannot own or bypass RLS.
-- This test does NOT replace real Flyway + application integration tests.
\set ON_ERROR_STOP on
CREATE ROLE sanad_migrator NOLOGIN NOSUPERUSER NOBYPASSRLS;
CREATE ROLE sanad NOLOGIN NOSUPERUSER NOBYPASSRLS;
CREATE SCHEMA sanad_g1 AUTHORIZATION sanad_migrator;
GRANT USAGE ON SCHEMA sanad_g1 TO sanad;
SET ROLE sanad_migrator;
CREATE TABLE sanad_g1.tenant_probe (
  tenant_id text NOT NULL,
  id integer NOT NULL,
  PRIMARY KEY (tenant_id, id)
);
ALTER TABLE sanad_g1.tenant_probe ENABLE ROW LEVEL SECURITY;
ALTER TABLE sanad_g1.tenant_probe FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_probe_policy ON sanad_g1.tenant_probe
  USING (tenant_id = current_setting('app.tenant_id', true))
  WITH CHECK (tenant_id = current_setting('app.tenant_id', true));
GRANT SELECT, INSERT, UPDATE, DELETE ON sanad_g1.tenant_probe TO sanad;
RESET ROLE;
DO $$
BEGIN
 IF (SELECT rolbypassrls OR rolsuper FROM pg_roles WHERE rolname='sanad') THEN
   RAISE EXCEPTION 'runtime role bypasses RLS';
 END IF;
 IF EXISTS (SELECT 1 FROM pg_class WHERE oid='sanad_g1.tenant_probe'::regclass AND relowner='sanad'::regrole) THEN
   RAISE EXCEPTION 'runtime owns protected table';
 END IF;
END $$;
SET ROLE sanad;
SET app.tenant_id = 'A';
INSERT INTO sanad_g1.tenant_probe VALUES ('A',1);
DO $$
BEGIN
  BEGIN
    INSERT INTO sanad_g1.tenant_probe VALUES ('B',2);
    RAISE EXCEPTION 'cross tenant insert incorrectly allowed';
  EXCEPTION WHEN insufficient_privilege THEN NULL;
  END;
  BEGIN
    EXECUTE 'ALTER TABLE sanad_g1.tenant_probe ADD COLUMN forbidden integer';
    RAISE EXCEPTION 'runtime DDL incorrectly allowed';
  EXCEPTION WHEN insufficient_privilege THEN NULL;
  END;
  BEGIN
    EXECUTE 'TRUNCATE sanad_g1.tenant_probe';
    RAISE EXCEPTION 'runtime TRUNCATE incorrectly allowed';
  EXCEPTION WHEN insufficient_privilege THEN NULL;
  END;
END $$;
SET app.tenant_id = 'B';
DO $$
BEGIN
 IF (SELECT count(*) FROM sanad_g1.tenant_probe) <> 0 THEN
   RAISE EXCEPTION 'cross tenant SELECT exposed data';
 END IF;
END $$;
RESET app.tenant_id;
DO $$
BEGIN
 IF (SELECT count(*) FROM sanad_g1.tenant_probe) <> 0 THEN
   RAISE EXCEPTION 'missing tenant GUC exposed data';
 END IF;
END $$;
RESET ROLE;
SELECT 'G1_SPLIT_IDENTITY_ISOLATED_PROOF_PASS' AS result;
