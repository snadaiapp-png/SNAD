# 06 — BACKUP RESTORE CERTIFICATION (Phase 4.2)

**BACKUP_RESTORE_CERTIFICATION = BLOCKED**
**G1_CERTIFICATION impact: HOLD** (this gate is mandatory and was previously unproven; it remains unproven).

## What the command requires vs. what was available

| Requirement | Status |
|---|---|
| Authorized production backup or snapshot delivered via a secure channel | **NOT PROVIDED** — no backup was delivered to the executor by any channel during this execution window |
| Access/authorization validation before use | NOT_APPLICABLE_WITH_EVIDENCE (nothing was delivered; nothing was used) |
| Isolated restore environment with proper access controls | READY (isolated host-native PostgreSQL 17.6 cluster already stood up and used for 03/04/05; restore procedure prepared) |
| Customer-data leak prevention (no customer rows in logs/evidence) | Enforced by design for any future restore: probes are count/schema-only; the rehearsal tooling never dumps row payloads into logs |
| Restore → schema/data integrity → apply new migrations → RLS/Auth/Subscriptions probes → rollback plan → measured restore time | NOT EXECUTED — blocked by the missing artifact |

## Why the executor could not self-supply a backup

1. The command correctly conditions this phase on an **owner-authorized** backup ("اطلب نسخة احتياطية إنتاجية معتمدة أو snapshot مصرحًا به عبر قناة آمنة"). Requesting and receiving one requires an out-of-band owner action that did not occur during the window.
2. The standing credential policy (established when plaintext production tokens were pasted into chat: all such tokens are treated as **compromised and must be rotated**) forbids the executor from using the previously exposed Supabase/Render/Vercel/Resend secrets for any direct production access. No alternative production read/backup credential was issued.
3. Per the explicit rule, artificial/synthetic databases are **not** substitutes for the required restore rehearsal — none was used, and Scenario D was left blocked rather than simulated.

## Exact unblocking requirements (what the owner must provide)

1. A backup/snapshot of the production database (Supabase PITR snapshot, `pg_dump` custom-format dump, or storage snapshot) with: provenance record (creation time, source instance, producing account), integrity evidence (size + checksum), delivered out-of-band (not via chat), plus a written authorization naming this engagement and permitting restore into the isolated rehearsal cluster.
2. A scoped read-only production credential for the post-restore verification queries (or pre-approval to reuse the rehearsal cluster for the verification step).
3. After delivery, the executor will run: restore → `flyway validate` (checksum identity vs f95a30b6 ledger) → pending-migration apply if any → the 03 six-table matrix → the 04 E2E subset → measured restore time → rollback/containment plan, and produce this certificate as PASS or FAIL with real measured numbers.

**Until then: `BACKUP_RESTORE_CERTIFICATION=BLOCKED`; item 7 of the G1 checklist cannot pass; `G1_CERTIFICATION=HOLD`.**
