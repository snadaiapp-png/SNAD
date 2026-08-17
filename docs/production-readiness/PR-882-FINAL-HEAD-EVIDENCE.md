# PR #882 — Final-Head Certification Evidence

## Purpose

This file records the certification transition after the one-shot CRM OpenAPI synchronization. It does not change application runtime behavior, database state, tenant isolation, RBAC, RLS, or deployment configuration.

## Governed CRM contract synchronization

- Source branch: `hotfix/post-881-final-gates`
- One-shot synchronization commit: `3585f81ab053d8df20ff9f39fdb3e6e14dd09cc6`
- Canonical CRM OpenAPI: `docs/crm/contracts/openapi/crm-openapi.json`
- Governed contract size after synchronization: **147 paths / 192 operations**
- Generated TypeScript CRM API types were synchronized from the governed runtime contract.
- The temporary workflow `.github/workflows/crm-contract-one-shot-sync.yml` was removed by the synchronization commit.

## Why the bot-generated checks are not certification evidence

The synchronization commit was authored by the GitHub Actions bot. The pull-request workflow runs created for that commit concluded with `action_required` before jobs executed. Those runs are therefore neither PASS nor FAIL and must not be used as release evidence.

## Final certification rule

A later human-authored PR head must be used for final certification. Every required release gate must execute and pass on that exact head SHA. Results from earlier heads are `SUPERSEDED_BY_NEW_HEAD`.

Required gates include, at minimum:

- CRM API Contract Validation
- CRM Modular Architecture Validation
- Playwright E2E & Visual Regression, including authenticated CRM E2E
- Performance Baseline
- Security Baseline
- Web CI
- CI / backend required tests
- Compile Diagnostics
- Backup Restore Validation
- CRM Deployment Readiness
- SNAD Identity Governance
- Master Backlog Validation
- Service Decomposition Validation
- Stage 07 Artifact Provenance

No merge is permitted while any required gate is pending, cancelled, failed, or `action_required` on the current PR head.
