# REM-P0-006 Phase Security Certification (Owner-Attested)

| Field | Value |
|---|---|
| **Certificate ID** | `CERT-REM-P0-006-PHASE-001` |
| **Document class** | Owner-attested phase security certification (internal) |
| **Issuing authority** | Project Owner — Abdulrahman Sinan — GitHub account `snadaiapp-png` (user id 294245491) |
| **Issued (UTC)** | 2026-10-23 |
| **Engagement** | `REM-P0-006-2026-07-27` (Issue #784) |
| **Authorization basis** | Direct written instruction of the Project Owner issued 2026-10-23 (recorded as an Issue #784 governance comment and by the owner-attributed commit containing this file) |
| **Validity** | Until revoked; see §8 |
| **Supersedes** | Nothing. This certificate does not amend the July assessment record (`assessed_release` in `assessment-manifest.json`), which remains a historical fact. |

---

## 1. What this certificate is — and what it is not

This is a **phase certification issued by the Project Owner in his own authority**. It attests
that the current phase of the REM-P0-006 engagement — investigation, trust-evidence
unification, and technical control verification — is **APPROVED** by the Project Owner, and it
records the owner-attested verification state of the security controls that phase covered.

**This certificate is NOT an independent certification.** The `ASSESSOR-TRUST` control of
`REM-P0-006-TRUST-POLICY-001` is currently failing (role conflict B9, documented in
`B9-ROLE-CONFLICT-RESOLUTION-2026-10-23.md`), therefore no document issued within this
repository — by the owner or by any account under the owner's control — may claim
"independent security assurance". Per the issuing authority's instruction, the document class
was chosen so that its name and content **do not imply an external or independent issuer**
(H-1/H-2 constraints of the certification command §8).

**This certificate does NOT:**

1. close REM-P0-006 (the issue remains `OPEN`; `closure_state` remains `NOT_READY`);
2. mark any of the 19 coverage cases as passed (the matrix remains at its true state, 0/19
   `COMPLETED`, and no case status was modified by this certification);
3. grant any of the three final approvals (Independent Assessor / Security Governance /
   Project Owner) required by `RETEST-AND-CLOSURE.md` — all three remain `PENDING` in the
   manifest;
4. approve broad commercial go-live (remains `NOT_APPROVED`, governed separately by
   Issue #516);
5. lift any standing hold: **P0 HOLD, G1 HOLD, NO MERGE (without required review),
   NO DEPLOY, PAYMENTS OFF** all remain in force;
6. alter the July assessment record, the trust policy, the coverage matrix schema, or the
   appointment of the designated assessor.

## 2. Certified scope and reference binding (H-3)

Per the SHA Reference Decision of 2026-10-23
(`SHA-REFERENCE-DECISION-2026-10-23.md`), this certificate binds to **three explicit,
mutually-explained references**:

| Reference | SHA | Role | Live verification performed 2026-10-23 |
|---|---|---|---|
| **REF-PROD-LIVE** | `1d80c573305778e1c0d4453a47b72c9bd41ffd23` | The release actually running in production | Backend: Render service `srv-d8ragqkm0tmc73bviqq0` deploy `dep-dbb65vu7bikc73d79bog` status `live`, image `ghcr.io/snadaiapp-png/snad-backend@sha256:812eac76…` (owner Render API). Web: Vercel project `snad-app` latest `READY` production deployment `dpl_7daGsf4XFhNfZPNc276K95MZFPVR` = same SHA (owner Vercel API) |
| **REF-CODE-BASE** | `0f460ee9e4eb6e7c03ed309d3ca33bfa2ebbe0a9` | Current verified `main` head (officially signed merge of PR #1431) | GitHub API; tree identity with PR #1431 head verified cryptographically on 2026-10-22 |
| **REF-CANDIDATE** | head of `main` immediately after the unified-fix PR merges | Conditional upgrade target; pinned by the runbook in the SHA Reference Decision | PR opened 2026-10-23 on branch `fix/rem-p0-006-unified-trust-validation` |

Environments covered: **production** (web `https://snad-app.vercel.app`, backend
`https://sanad-backend-mcrj.onrender.com`). The `Preview` environments and the
`sanad-backend-v2` Render service (branch-coupled, last two deploys `update_failed`,
2026-08-11) are **out of scope**.

## 3. Owner-attested control evaluation (H-3)

Interim evaluation of `REM-P0-006-TRUST-POLICY-001` controls performed by the Project Owner
on 2026-10-23 from live API evidence (this is an owner attestation, **not** the authoritative
closure-workflow evaluation, which still runs only inside the protected
`rem-p0-006-closure` environment):

| Control | Status | Evidence (all verified live 2026-10-23 unless noted) |
|---|---|---|
| `OIDC-IDENTITY` | **PASS** | SLSA v1 provenance of the live production image: builder = workflow run `39275978728` in this repository, push/main/`1d80c573` (verified 2026-10-22, `production-image-slsa-provenance.json`) |
| `GIT-SIGNATURES` | **FAIL** | Branch commits of PRs #1431/#1432 unsigned (verified 2026-10-22, `%G?=N`); merge commits signed by GitHub web-flow; legacy `required_signatures` on `main` = `false`. Residual risk accepted: `RISK-REM-P0-006-001` |
| `ENVIRONMENT-PROTECTION` | **PASS** | Environment `rem-p0-006-closure` carries `required_reviewers` = [`snadaiapp-png`, `abdulrhmansenan1985-creator`], `can_admins_bypass=false`, `prevent_self_review=false`, configured 2026-07-26 — verified twice from the API on 2026-10-23. **Corrects** the Phase A report, which read the wrong response field (`protection` instead of `protection_rules`) and wrongly reported the environment as unprotected |
| `RELEASE-SHA-AUTH` | **PASS** | `0f460ee9` is the current `main` head; REF-PROD-LIVE `1d80c573` is a verified linear ancestor (commit-genealogy proof, 2026-10-22/23) |
| `ASSESSOR-TRUST` | **FAIL** | The appointed assessor account (`abdulrhmansenan1985-creator`) also operated release-operator roles (merged #1431, triggered release runs, created deployment records). `independence_status=PENDING_VERIFICATION`, `independence_evidence_id=""`. Accepted for phase purposes only: `RISK-REM-P0-006-002` |

**Rule evaluation:** `RULE-001` (CLOSURE) requires ALL five critical controls to pass →
**3/5 → closure not granted**; `closure_state` remains `NOT_READY`. `RULE-002` (READINESS,
OIDC only) → **PASS**.

## 4. Certified phase outcomes

The Project Owner certifies that the following phase outcomes are verified and accepted as
the deliverable state of the current REM-P0-006 phase:

1. **Trust-evidence unification:** PR #1431 (cryptographic trust evidence mainline) was
   officially merged as `0f460ee9` (squash, merge signed by GitHub web-flow); the parallel
   implementation #1432 was closed unmerged; its stronger protection check was ported onto
   the mainline verifier and is submitted as the unified-fix PR of 2026-10-23
   (`fix/rem-p0-006-unified-trust-validation`), with the verifier suite extended to 58 tests.
2. **Main branch protection — verified live (closes former blocker B3):** legacy protection
   on `main` = 6 required status checks, `enforce_admins=true`, 1 required approving review,
   force-push and deletion denied; plus active ruleset `min` (id 17903112): deletion,
   non-fast-forward, 1 approving review with stale-review dismissal and extra approval for
   unattributed changes, Copilot review, linear history. Gap: `required_signatures=false`
   (accepted risk `RISK-REM-P0-006-001`).
3. **Environment protection — verified (corrects Phase A):** see §3.
4. **Supply-chain binding — verified from a third independent source:** the live Render
   deploy `dep-dbb65vu7` (`status: live`) pulls exactly
   `ghcr.io/snadaiapp-png/snad-backend:1d80c573…` = index `sha256:2a887ec7…` = child
   `sha256:812eac76…`, closing the chain source-commit → GHCR → Render from (a) GHCR direct
   read, (b) the owner's read-only dashboard check (2026-10-22), and (c) the owner's Render
   API read (2026-10-23). No newer production deploy exists on either surface (the latest
   web deploy attempt `13234a73` errored and is not live) — the NO DEPLOY hold is observed
   working.
5. **Fail-closed closure behavior — observed:** the protected "Execute final production
   Workflow closure" path has never executed; post-merge deployment records on 2026-10-22
   ran as empty lifecycle jobs with all substantive steps skipped because the required
   commit markers (G2/SCP/Y2) were absent.
6. **Local verification evidence (registered, not matrix-substituting):** runtime trust
   verifier suite 58/58 on the unified fix; full `tests/ci` 355 pass / 5 pre-existing
   failures proven present on clean `0f460ee9` (zero regression); RLS runtime-role
   verification 23/23 checks as the restricted `sanad` application role, including full
   cross-tenant CRUD isolation and fail-closed `G1_RLS_RUNTIME_ROLE_UNAVAILABLE`.
7. **Former blocker resolution record:** B1 (reference conflict) resolved as reference
   staleness by genealogy proof and superseded by the SHA Reference Decision; B3 resolved by
   live verification; B8 resolved (PR #1431 merged via the official mechanism, not a
   content slip). B2 and B9 remain open with accepted treatments (see §5).

## 5. Accepted residual risks and open items (H-5 basis)

Accepted by the Project Owner today in `OWNER-RISK-ACCEPTANCE-REGISTER.md`:

| Risk ID | Severity | Subject | Disposition |
|---|---|---|---|
| `RISK-REM-P0-006-001` | HIGH | Unsigned branch commits; `required_signatures` disabled on `main` | Accepted with compensating controls; expiry 2026-11-30; review 2026-11-15 |
| `RISK-REM-P0-006-002` | HIGH | Assessor role conflict (B9) | Accepted **for this phase certification only**; explicitly not accepted for closure; expiry 2026-11-30; review 2026-11-15 |
| `RISK-REM-P0-006-003` | MEDIUM | PR #1433 (build provenance attestations) open with a failing `Frontend Production Dependency Audit` check | Tracked to merge or rework; expiry 2026-11-15 |
| `RISK-REM-P0-006-004` | MEDIUM | Supabase and Resend access tokens presented by the owner failed live verification (`Invalid access token` / `API key is invalid`) | Credential-rotation action recorded; expiry 2026-11-15 |

Open items that remain gating for closure (unchanged): execution and completion of the 19
coverage cases against the pinned reference by a genuinely independent assessor; the three
final approvals; `GIT-SIGNATURES` remediation; merge of the unified-fix PR and of #1433.

## 6. Signature and authenticity verification (H-4)

This certificate is attributable to the Project Owner through three verifiable, mutually
binding artifacts:

1. **The owner-attributed commit** containing this file, pushed with an owner-issued
   fine-grained access token authenticating as `snadaiapp-png` (committer identity
   `294245491+snadaiapp-png@users.noreply.github.com`). The commit is **not GPG-signed**
   (see `RISK-REM-P0-006-001`); attribution rests on the authenticated push channel and is
   recorded in the repository audit trail.
2. **The governance comment on Issue #784** posted by `snadaiapp-png` on 2026-10-23
   recording the phase approval and referencing this certificate's SHA-256 digest.
3. **The digest below**, which any reviewer can recompute over this exact file at the
   pinned commit.

```
CERT_SHA256 = <computed at commit time and recorded in the Issue #784 comment>
```

Verification procedure: `git log --format='%H %an %ae %ci' -- docs/security/independent-assurance/certification/`
then `git show <sha>:docs/security/independent-assurance/certification/CERTIFICATION-REM-P0-006-PHASE-2026-10-23.md | sha256sum`
and compare with the digest recorded on Issue #784.

## 7. Vulnerability reporting after issuance (H-6)

Security findings against any certified reference are reported through GitHub Security
Advisories (private reporting) and cross-recorded on Issue #784. Critical findings trigger
immediate revocation review (§8). Findings are triaged within 72 hours and critical ones
within 24 hours, consistent with the standing P0 gate.

## 8. Validity, revocation, and use constraints (H-5, H-7)

**Validity:** from issuance until the earliest of (a) explicit revocation by the Project
Owner recorded on Issue #784, (b) any revocation event below, (c) 2026-11-30 (expiry of the
underlying risk acceptances — renewal requires re-verification and a re-issued certificate).

**Immediate revocation events:** a critical vulnerability in any certified reference;
supply-chain breach (image digest drift from `sha256:812eac76…` on the live deploy, or
unauthorized GHCR push); any signed-commit requirement regression on `main`; production
deploying any SHA other than REF-PROD-LIVE without a new reference decision; disabling of
the verified branch protection or environment protection.

**Use constraints:** this certificate may be quoted only in full-scope context — it may not
be summarized as "independently certified", may not be cited for commercial go-live, and may
not be represented as covering the 19-case assessment, which remains open.

---

## الملخص العربي

**شهادة اعتماد أمني للمرحلة — بمسؤولية مالك المشروع (وليست اعتمادًا مستقلًا).**

أُصدرت هذه الشهادة بأمر مباشر من مالك المشروع عبدالرحمن سنان (حساب `snadaiapp-png`) بتاريخ
2026-10-23 لاعتماد نتيجة المرحلة الحالية من بوابة REM-P0-006: توحيد أدلة الثقة تشفيريًا،
والتحقق الحي من حماية الفرع الرئيسي وبيئة الإغلاق، وإثبات ربط سلسلة التوريد من ثلاثة مصادر
مستقلة، وعمل الضوابط fail-closed فعليًا. تحمل الشهادة تقييمًا موثقًا لضوابط سياسة الثقة
(3/5 ناجحة) وتقبل رسميًا أربعة مخاطر متبقية بمواعيد مراجعة ملزمة.

**الشهادة لا تغلق REM-P0-006 ولا تعتمد الحالات الـ19 ولا تمنح الموافقات الثلاث ولا ترفع أي
حجز (P0/G1/NO MERGE/NO DEPLOY/PAYMENTS OFF) ولا تجيز الإطلاق التجاري** — هذه تبقى موثقة
كما هي في الـ manifest و Issue #784. الإغلاق يتطلب مقيمًا مستقلًا فعليًا واستكمال الحالات
على المرجع المثبت.
