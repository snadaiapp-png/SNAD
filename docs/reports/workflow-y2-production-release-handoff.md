# Workflow Y2 — Production Release Handoff

<!-- STATUS_AUTHORITY: PRODUCTION_ENTRY_CONTROL -->

Repository: `snadaiapp-png/SNAD`  
Product scope: Workflow Orchestration Y2 V1  
Implementation authority: PR #997 / G4 complete  
G4 merged baseline before post-closure cleanup: `cf27d0e260691ce16b4ef884e408f8943977870c`

## 1. Purpose

This document opens the **production release path** after G4 implementation closure. It does not bypass protected-branch review, the GitHub `production` environment, or the canonical production-release workflow.

The release path is intentionally split:

```text
POST-G4 CLEANUP PR
        |
        v
PROTECTED MERGE TO main
        |
        v
RESOLVE EXACT CURRENT main SHA
        |
        v
SANAD Production Release (exact immutable image)
        |
        v
PLATFORM PRODUCTION VERIFICATION
        |
        v
WORKFLOW Y2 READ-ONLY PREFLIGHT
        |
        v
TARGET FAMILY AUTHORIZATION
        |
        v
ONE Y2 CANARY
        |
        v
OBSERVATION / ROLLBACK DECISION
        |
        v
CONTROLLED EXPANSION OF FUTURE STARTS
        |
        v
PRODUCTION CLOSURE EVIDENCE
```

## 2. Current entry status

```text
WORKFLOW_Y2_V1_IMPLEMENTATION = COMPLETE
TASKS_1_22                    = COMPLETE
GATES_G0_G4                   = COMPLETE
PR_997                        = MERGED
POST_G4_DOCUMENT_CLEANUP      = IN_PROGRESS
PRODUCTION_DEPLOYMENT         = NOT_STARTED
Y2_FAMILY_CUTOVER             = NOT_STARTED
LEGACY_RETIREMENT             = OUT_OF_SCOPE
```

The production release candidate is **not hard-coded in this pre-merge document**. It is the protected squash-merge SHA of the post-G4 cleanup PR, provided that SHA is still the current `main` head when the release is dispatched.

## 3. Canonical platform release control

Workflow: `.github/workflows/production-release.yml`  
Display name: `SANAD Production Release`

Required dispatch values after the cleanup PR is merged:

```text
commit_sha           = <exact current main SHA>
pull_request_number  = <post-G4 cleanup / production change record PR>
rollback_on_failure  = true
```

The workflow rejects a commit that is not current `main` and verifies the exact SHA-tagged GHCR image before deployment.

## 4. Platform release acceptance gates

The canonical workflow must prove all of the following:

```text
CURRENT_MAIN_MATCH             = PASS
EXACT_GHCR_IMAGE_EXISTS        = PASS
RENDER_ENVIRONMENT_PROOF       = PASS
BOOTSTRAP_ENABLED_FALSE        = PASS
CONTROL_PLANE_TENANT           = PASS
PREVIOUS_LIVE_IMAGE_CAPTURED   = PASS
EXACT_IMAGE_DEPLOYED           = PASS
RENDER_IMAGE_IDENTITY_MATCH    = PASS
READINESS_UP                   = PASS
FLYWAY_RUNTIME_INVARIANTS      = PASS
FLYWAY_PRODUCTION_COMPAT       = PASS
SECURITY_BOUNDARY              = PASS
SCP_CONTRACT_SMOKE             = PASS
VERCEL_CONTROL_PLANE           = PASS
VERCEL_BFF                     = PASS
SANITIZED_RELEASE_EVIDENCE     = CAPTURED
```

Any failure = `PRODUCTION_PLATFORM_RELEASE=BLOCKED` or rollback according to the workflow. No different SHA may be substituted without a new exact-main verification.

## 5. Workflow Y2 cutover entry gates

A successful platform deployment alone does not switch a business Workflow family to Y2.

Before a family-level cutover, the change record must contain:

```text
TARGET_TENANT_OR_SCOPE
DEFINITION_FAMILY_ID
CURRENT_START_TARGET
TARGET_Y2_VERSION_ID
TARGET_Y2_VERSION
TARGET_VALIDATION_RESULT = PASS
ROLLBACK_TARGET
CANARY_OWNER
OBSERVATION_WINDOW
ROLLBACK_THRESHOLDS
```

Read-only production database preflight from `docs/runbooks/workflow-y2-cutover.md` must return no invalid engine-generation rows, no missing definition references, no wrong engine/definition pairings, and no orphan step instances.

## 6. Canary contract

The first Y2 production start for a selected family is a controlled canary through the normal authorized start API.

Required canary proof:

```text
ENGINE_GENERATION      = Y2
DEFINITION_VERSION_PIN = EXPECTED_TARGET
FIRST_GRAPH_ADVANCE    = PASS
HUMAN_WORKITEMS        = EXPECTED
APPROVAL_STATE         = EXPECTED
AUDIT_CONTINUITY       = PASS
TENANT_SCOPE           = PASS
IDEMPOTENCY            = PASS
WRONG_ENGINE_ROUTING   = NONE
LEGACY_INSTANCE_DRIFT  = NONE
```

## 7. Rollback authority

Two independent rollback layers exist:

### Platform rollback

If the production release fails after deployment begins, the canonical workflow may redeploy the previously captured live immutable image when `rollback_on_failure=true`.

### Workflow functional rollback

If the Y2 canary/rollout fails, stop or repoint **future starts only** to a known-good published target. Never rewrite a running instance's `engine_generation` or `definition_version_id`.

## 8. Production closure package

The final production closure record must bind these artifacts to one exact deployed SHA:

- protected `main` SHA;
- immutable GHCR image;
- production workflow run ID;
- Render deployment ID and prior image;
- readiness/Flyway/security/SCP/BFF evidence;
- read-only Workflow DB preflight outputs;
- selected family/version;
- canary instance proof;
- audit/metrics observation;
- rollback thresholds and decision;
- post-cutover state snapshot.

Final production verdict is one of:

```text
PRODUCTION_Y2_VERDICT = PASS
PRODUCTION_Y2_VERDICT = ROLLED_BACK
PRODUCTION_Y2_VERDICT = BLOCKED
```

## 9. Explicit boundary

This production path deploys the completed Y2 V1 capability and permits controlled future-start cutover for explicitly selected families. It does not authorize:

- automatic migration of running LEGACY instances;
- LEGACY runtime removal;
- QUORUM/N_OF_M expansion;
- full BPMN expansion;
- arbitrary scripting;
- unrelated production changes.

Legacy retirement remains a separate future project after all retirement conditions in the cutover runbook are proven.
