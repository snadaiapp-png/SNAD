# G1 Evidence Reconciliation — exact-SHA production baseline

**Date:** 2026-11-10  
**Repository:** `snadaiapp-png/SNAD`  
**Certificate record / earlier main SHA:** `776276800eab538740804924b8ff7626959a9348`  
**Production / current main SHA assessed:** `e7c876c2f8d0e9fe972c4ede56e4d2188e91b405`  
**Scope:** read-only evidence inventory and gap analysis; **NOT an authorization, independent security certificate, or release approval.**  
**Decision:** `G1_EXACT_SHA_CERTIFICATION=UNVERIFIED`; `PAYMENTS=OFF`; **NO-GO for new G1 certification or payment activation**. The already executed technical deployment is separately recorded and is not undone by this assessment.

## 1. Exact ancestry / change-impact record

GitHub compare `776276800eab538740804924b8ff7626959a9348...e7c876c2f8d0e9fe972c4ede56e4d2188e91b405` returned **ahead by 2 commits, behind by 0, total 2**, with exactly five changed paths:

| Path | Change | Security impact / necessary acceptance |
|---|---|---|
| `.github/workflows/vercel-main-production-reconcile.yml` | Modified (132 lines affected) | **Production release control surface** changed: staged `--skip-domain`, deployment identity, promotion, rollback attempt and cancellation semantics. Positive-path CI success is not a demonstrated rollback-after-failure test. Independently review for alias races, identity and authorization. |
| `.github/workflows/vercel-reconcile-safety-tests.yml` | Added (41 lines) | Static YAML parsing and regression tests added; does not exercise actual production cutover/reversal. |
| `tests/ci/test_release_gate_topology.py` | Modified (17 lines) | Release topology expectations updated; regression test PASS is not operational recovery proof. |
| `tests/ci/test_vercel_main_reconcile_workflow.py` | Modified (56 lines) | Staged/promotion and rollback contract assertions. |
| `docs/certification/G1-SECURITY-CERTIFICATION-2026-11-10.md` | Added (36 lines) | Historical certification record inserted **after** the earlier SHA. Record declares earlier `main@7762768`; it does not certify `e7c876c2`. |

No `db/migration/`, `tests/security/`, runtime application source, or database-role definition changes were returned **in this specific two-commit comparison**. This is a path-level finding, not evidence of unchanged live database configuration or a waiver of version binding.

Source: https://github.com/snadaiapp-png/SNAD/compare/776276800eab538740804924b8ff7626959a9348...e7c876c2f8d0e9fe972c4ede56e4d2188e91b405

## 2. Source-to-evidence matrix

Evidence classifications are **VERIFIED JOB SUCCESS**, **DOCUMENTED LIMITATION**, **UNVERIFIED**, or **BLOCKED**, not a blanket assertion of certification.

| Gate | Evidence on record | Evidence finding | Outstanding scope |
|---|---|---|---|
| Main / frontend release SHA | GitHub `main@e7c876c2`; Vercel reconcile run [42715835859](https://github.com/snadaiapp-png/SNAD/actions/runs/42715835859), succeeded; live `/api/system/release` reported `commitSha=e7c876c2`, `environment=production` in authorized operator PowerShell output | VERIFIED for release identity at audit time | Continue to use exact-SHA controls for future changes |
| Post-merge verification | Run [42714247463](https://github.com/snadaiapp-png/SNAD/actions/runs/42714247463) on `e7c876c2`: jobs A/B/C/D/E/F completed/success | VERIFIED JOB SUCCESS | Inspect attached reports for test-level assertions where G1 control mapping is claimed |
| PostgreSQL Direct / full Maven suite | Same run, JOB C `129491508600`, completed/success at 2026-11-09T23:43:51Z; `pmv-surefire-reports` artifact `13721705837` and `pmv-fragment-pg-direct` `13720648560` available | VERIFIED JOB SUCCESS and artifacts present | Full Surefire case coverage and real production-runtime equivalence not independently inspected here |
| HRM focused security/RLS | Same PMV run, JOB D completed/success; `hrm-surefire-reports` artifact `13718637310` | VERIFIED JOB SUCCESS | Specific six-table F05/tenant-boundary cases need traceable test IDs and production-role mapping |
| Production release / live routes | Production SCP run [42714388553](https://github.com/snadaiapp-png/SNAD/actions/runs/42714388553), live route gate [42716123202](https://github.com/snadaiapp-png/SNAD/actions/runs/42716123202), both successful | VERIFIED JOB SUCCESS | Not a replacement for direct catalog, role, ACL and tenant A/B acceptance evidence |
| Protected encrypted backup | `g1-durable-backup.yml` run [42623864874](https://github.com/snadaiapp-png/SNAD/actions/runs/42623864874) completed/success, artifact `13678480223` present and unexpired at audit | VERIFIED JOB SUCCESS | Do not expose backup bytes; retention and recovery custody must remain governed |
| Restored production ACL/RLS structural comparison | `g1-acl-rls-certification.yml` run [42638822139](https://github.com/snadaiapp-png/SNAD/actions/runs/42638822139) completed/success, artifact `13684103698` present | VERIFIED JOB SUCCESS | Certificate itself excludes ownership, non-public default privileges, platform schemas, and **functional RLS execution** on restored copy |
| Previous certificate | `docs/certification/G1-SECURITY-CERTIFICATION-2026-11-10.md`, `G1-CERT-20261110-1482`, names `7762768` | DOCUMENTED HISTORICAL CERTIFICATE | Approval scope for `e7c876c2` not shown |
| Review / separation of duties | PR #1489 has four `APPROVED` entries from `abdulrhmansenan1985-creator` (two explanatory, two empty); certificate names this individual as independent reviewer; owner recorded as `snadaiapp-png` | REVIEW RECORDS VERIFIED; INDEPENDENCE ATTESTATION UNVERIFIED | Validate role segregation, exact reviewed scope and documented approval for current SHA, without treating empty-body approval as a full audit |
| Fail-after-promotion rollback rehearsal | Vercel reconcile successful run: rollback step was `skipped` | **NOT TESTED** | Non-production or safely isolated controlled failure rehearsal, with previous-alias identity proof |
| G1 current-SHA issuance | No matching certificate for `e7c876c2` found in consulted certification record; `docs/security/g1-governance-controls.md` C6 requires exact-SHA certification | UNVERIFIED / NOT ISSUED BY THIS RECORD | Authorized security decision must explicitly attest change-impact assessment and any approved exceptions |
| Payments / commercial | Existing decision states payments OFF; earlier G1 certificate cannot automatically authorize payment collection on a new release | **OFF / NO-GO** | Separate payment-provider, security, regulatory, financial and owner release decision |

**Artifact availability** was checked from GitHub Actions' artifact index; ZIP contents/hashes were not independently downloaded or validated in this reconciliation. A successful workflow step is not a substitute for inspecting raw assertions.

## 3. Historical conflicts and governance boundaries

1. The historical [2026-11-02 G1 decision](../security/g1-final-closure/10-G1-CERTIFICATION-DECISION.md) recorded `G1_CERTIFICATION=HOLD` at `f95a30b6`. The [2026-11-10 certificate](G1-SECURITY-CERTIFICATION-2026-11-10.md) records a **later** scoped G1 approval at `7762768`. Preserve both; dates and SHA explain why they cannot be used interchangeably.
2. The [2026-10-24 governance revocation](../governance/SANAD-SECURITY-CERTIFICATION-REQUIREMENT-REVOCATION-2026-10-24.md) removed an **external independent certificate** as a universal completion prerequisite. It **explicitly did not** automatically lift G1/P0, authorize deployment, or enable payments. The separate optional assurance `assessment-manifest.json` remains `NOT_READY`; it must not be silently repurposed into a mandatory external certificate after revocation.
3. [Issue #1462](https://github.com/snadaiapp-png/SNAD/issues/1462) still lists six open closure checkboxes and a historical security HOLD. Current successful runs supersede older `in_progress` statements for the tested SHA only; issue body remains an auditable history until reviewed evidence and authorization support changing any checkbox.
4. A secure backup and structural ACL comparison do not negate explicitly excluded dynamic RLS/ownership/role assertions. Status must remain conditional until those exact claims are tested or expressly scoped out by an authorized risk decision.
5. The issue's independent-review requirement and the later cancellation of the **external certificate** requirement are not logically identical. Resolve the applicable G1 internal separation-of-duties policy in a recorded decision, rather than assuming either that all independent reviews are mandated or that none are needed.

## 4. Prioritized unresolved requirements

- **P0 — Current-SHA G1 decision:** authorized issuer must explicitly assess the five-path delta and bind their signoff to `e7c876c2f8d0e9fe972c4ede56e4d2188e91b405`, with findings, exceptions and reviewer identity. **BLOCKED for issuance**.
- **P0 — Security boundary coverage:** map six deferred tables, runtime `NOSUPERUSER/NOBYPASSRLS`, `FORCE RLS`, no-context deny, A/B tenant isolation, operation authorization, SEC-URITY DEFINER search paths, F05 and worker flows to actual test IDs/raw SQLSTATE and production identity. Current job success is supporting evidence, not a complete case-level mapping. **UNVERIFIED at this depth**.
- **P0 — Production DB reconciliation:** authorized, least-privilege read-only verification for live Flyway head, effective runtime grants and owner/definer surfaces, versus restored catalog. No SQL writes or credentials in issues. **UNVERIFIED**.
- **P1 — Operational recovery:** test rollback/alias restoration using a controlled non-production rehearsal. Backup restore structural test already succeeded, but covers a different failure mode. **UNVERIFIED**.
- **P1 — Reviewer independence and residual risk:** record reviewer's role and any conflict, including exceptions from the earlier certificate. **PARTIAL**.
- **P1 — Evidence retention and auditability:** index exact artifact IDs and checksums if they can be independently validated without exposing secrets or backups; note 90-day artifact retention. **PARTIAL**.
- **Separate gate — payments:** explicit commercial/payment authorization and negative-smoke evidence, distinct from G1. **OFF**.

## 5. Authorized closure procedure (NOT executed)

1. Obtain case-level source-to-assertion matrix and independently check protected production's **read-only** identity evidence.
2. Resolve reviewer authority under current governance and record an exact-SHA acceptance/risk decision in a separately approved change.
3. Add a new, approved decision record for `e7c876c2`; do **not** overwrite historical certificates, issue text, or retroactively mark prior checks as PASS.
4. Once fully supported, update Issue #1462 with exact URLs and decision IDs, then update closure checkboxes individually.
5. Treat enabling real payments as a separate, authorized release with its own tests, approvals and rollback.

**No code execution, deployment, production SQL, payment configuration change, certification issuance, or issue checkbox mutation occurred as part of this report. No Docker used.**
