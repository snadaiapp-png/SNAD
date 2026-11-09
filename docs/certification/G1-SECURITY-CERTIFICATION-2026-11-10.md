# G1 Security Certification Record

- **Certificate ID:** G1-CERT-20261110-1482
- **Issued:** 2026-11-10
- **Repository:** snadaiapp-png/SNAD (main @ 776276800eab538740804924b8ff7626959a9348)
- **Independent reviewer:** abdulrhmansenan1985-creator (id 301111696)
- **Project manager (full delegation):** snadaiapp-png (id 294245491)

## G1 gate — all three conditions met

| # | Condition | Evidence | Status |
|---|-----------|----------|--------|
| 1 | Durable encrypted backup | Workflow `g1-durable-backup.yml` run 42623864874 (success, ticket G1-CHG-1481); AES-256-CBC + PBKDF2-SHA256 600k; local copy SHA-256 `9db0d3b7456632f096905f856c011194934e747ad85b7f5990fcc89866772932` (3,062,912 bytes); GitHub artifact 13678480223 (90 days) | MET |
| 2 | Full ACL/RLS verification | Workflow `g1-acl-rls-certification.yml` run 42638822139 (success, ticket G1-CHG-1482, PR #1489); restore WITH ACLs (364 public + 6 default ACL entries kept); fail-closed structural equivalence: 319 tables+RLS flags / 283 policies / 6,377 table grants / 24 sequence grants / 8 schema grants / 0 column grants / 78 default ACLs — 100% match; archive SHA-256 `80de5287326a5609587c9651442ee6632f003e417e639900bc3a03ce8ad16679`; artifact 13684103698 (90 days) | MET |
| 3 | Independent approval | 4× APPROVED reviews on PR #1489 (2026-11-09 13:49Z–14:42Z); Production environment approvals for runs 42623864874 and 42638822139; repo variable `G1_SECURITY_APPROVED=true` created by the reviewer's own account at 2026-11-09T16:06:54Z; full delegation from project manager dated 2026-11-10 | MET |

## Supporting verification

- Production Flyway head (source and restored): `20261021.1`
- Post-Merge Verification run 42638664241 on 7762768: all six jobs (A–F) successful
- Production database: Supabase ref `tkbrvupemreqabwzdpyq`
- Render backend service: `srv-d8ragqkm0tmc73bviqq0` (secret RENDER_SERVICE_ID provisioned 2026-11-09T16:07:18Z)

## Recorded limitations

- Ownership not certified (`--no-owner` restore)
- Database-level default privileges outside `public` not compared
- Platform schemas (auth/storage/extensions) out of restore scope on vanilla PostgreSQL
- Functional RLS enforcement not exercised — certification is structural (security catalog equivalence)

## Effect

`G1_SECURITY_HOLD` is RELEASED. This commit message carries the authorization marker
`PRODUCTION-RECOVERY-G2-AUTHORIZED` for the exact main SHA of this commit, enabling the
G2 Recovery Release Dispatch path (Production environment approval by the independent
reviewer, then exact-SHA recovery release without rollback).
