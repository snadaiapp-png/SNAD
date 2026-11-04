# F-01 — Public-safe evidence index (redacted)

Status: **BLOCKED**. This index is a public-safe derivative, **not** the original evidence package or an independent security approval.

## Original evidence package (NOT uploaded)
- Filename: `SNAD-F01-VERIFIED-EVIDENCE-PACKAGE.zip`
- SHA-256: `339107f9010b30a8da5e0eb80005d449cff3dd41474d72c43fdb8eb4b9ad8f60`
- Internal SHA256SUMS verification: **84/84 matched**, 0 missing, 0 mismatched, 0 unlisted, checked locally on 2026-11-04.
- ZIP CRC verification: PASS.
- Source commit recorded in prior evidence: `a98fe3db5b1037f26612ed33575bfe00d3159fb7` (not independently revalidated in this publication step).

## Publication exclusions
The original ZIP and all raw logs, JSON snapshots, database dumps, connection strings, and potential personal or tenant data are **excluded** from this public index. In particular, `evidence/new/g5-artifacts/release-schema-dump.dump` is not published: a local `pg_restore --data-only` scan found 321 COPY statements and approximately 2,288 data rows. Sensitivity of those rows has not been fully classified.

## Governance and disposition
- Permanent private archive + independent restore verification: **PENDING**.
- Reviewer independence: **BLOCKED**; independent signoff: **PENDING**.
- F01: **BLOCKED**; G1: **HOLD**; production: **NO-GO**; payments: **OFF**.
- No tests were rerun for this index. No certification is claimed.

This file is the **redacted publication artifact**. It is intentionally not a substitute for a complete evidence package or immutable private archive. Issue: #1462.
