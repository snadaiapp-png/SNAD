# G1 F-2 — Isolated restore ownership and ACL verification gate

**Status:** PROPOSED — not a production approval or a completed restore test.  
**Baseline:** `f989b68e9a2f0c7b0ec3af2534a222637a724cb2` (F-1 merged).  
**Scope:** disposable PostgreSQL recovery instance only. Do not run on production.

## Risk and required sequencing

A non-superuser restore owner can fail while restoring foreign keys against FORCE RLS-protected tables. The existing G1 production-derived rehearsal workflow restores using the isolated local `postgres` superuser with `--no-owner --no-acl`. That approach can avoid the FK restoration failure, **but leaves restored objects owned by the restore executor** and deliberately omits source ACLs. A successful `pg_restore` exit is therefore insufficient to certify migrator/runtime separation or exact production ACL equivalence.

## Approved-change prerequisites

1. Explicit recovery-change ticket, designated DBA, independently approved disposable target, and source-backup authorization.
2. Identify the authoritative production architecture and RPO/RTO targets: existing runbooks conflict (24 h/4 h vs 15 min/60 min). No automatic target selection.
3. Confirm source PostgreSQL version, extension requirements, complete object inventory, and backup provenance.
4. Isolate outbound network traffic and protect all production-derived data. Never expose connection secrets or tenant rows in CI logs.

## F-2 procedure — disposable target

1. Create the target database and required stub roles as NOLOGIN, including `sanad_migrator` and `sanad`. Verify neither has superuser/BYPASSRLS.
2. Restore as the isolated target's designated privileged restore executor, **not** as `sanad` or `sanad_migrator`. Preserve exit code, PostgreSQL version, command arguments (redacted), start/end timestamps, and sanitized errors.
3. After successful restore, explicitly transfer ownership of **every applicable restored public-schema relation, sequence, view, materialized view, foreign table, function, procedure, and type** to `sanad_migrator`, using a DBA-reviewed, inventory-driven script. Include partitioned objects and objects owned by extension or provider separately: do not blindly change extension-owned objects. Do not run an unchecked `REASSIGN OWNED` across schemas.
4. Recreate the intended role grants from an independently approved ACL baseline. Since the existing rehearsal uses `--no-acl`, do not claim restored ACL parity without a separate source ACL snapshot and comparison. Revoke unintended grants to runtime and PUBLIC.
5. Validate owner and ACL inventories against the approved baseline, and verify `sanad` cannot modify Flyway history or privileged security objects.
6. Verify `relrowsecurity` and `relforcerowsecurity` flags, policies and tenant isolation with real negative SQL probes executed as runtime role; validate security-definer functions and their owners/search_path.
7. Validate foreign keys, indexes, row counts and Flyway history. Run the security separation certification suite on the restored target.
8. Record measured RPO and RTO for the approved backup and recovery point. Archive machine-readable evidence and sanitized logs with SHA-256 checksums.

## Required fail-closed SQL inventory (run as audit-capable DBA on disposable target)

```sql
-- Non-extension public-schema relations that must not remain owned by restore executor.
SELECT n.nspname, c.relname, c.relkind, pg_get_userbyid(c.relowner) AS owner
FROM pg_class c
JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public'
  AND c.relkind IN ('r','p','v','m','S','f')
  AND NOT EXISTS (
    SELECT 1 FROM pg_depend d
    WHERE d.classid='pg_class'::regclass AND d.objid=c.oid AND d.deptype='e'
  )
  AND pg_get_userbyid(c.relowner) <> 'sanad_migrator'
ORDER BY 1,2;

-- Public-schema routines whose ownership must be reviewed.
SELECT n.nspname, p.proname, p.oid::regprocedure AS signature,
       pg_get_userbyid(p.proowner) AS owner, p.prosecdef AS security_definer
FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
WHERE n.nspname='public'
  AND pg_get_userbyid(p.proowner) <> 'sanad_migrator'
ORDER BY 1,2;

-- FORCE RLS inventory: compare each row with the independently approved source baseline.
SELECT n.nspname,c.relname,c.relrowsecurity,c.relforcerowsecurity
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relkind IN ('r','p')
ORDER BY 1,2;

-- Runtime must not read migration history.
SELECT has_table_privilege('sanad','public.flyway_schema_history','SELECT') AS runtime_can_read_flyway,
       has_table_privilege('sanad','public.flyway_schema_history','INSERT') AS runtime_can_write_flyway;
```

**Pass criteria:** the first two inventories are empty *after individually approved exceptions*; RLS/ACL/ownership match the independently captured baseline; runtime privilege booleans are false; negative tenant isolation probes and restore integrity tests pass. A zero-row owner query alone does not prove ACL equivalence.

## Evidence fields and sign-off

`source_sha`, `backup_sha256`, `restore_start_utc`, `restore_end_utc`, `pg_restore_exit_code`, `owner_inventory_before`, `owner_inventory_after`, `acl_diff`, `rls_diff`, `fk_count`, `negative_probe_results`, `rpo_measured`, `rto_measured`, `operator`, `independent_reviewer`, `approval_ticket`.

**Gate status:** F-2 remains BLOCKED until a real isolated restore and its independent verification are completed. Production access, production changes, production restore, CAB GO and payments are not authorized by this document.
