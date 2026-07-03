# EXEC-PROMPT-030 — Organizations Live API Integration

## Status

IMPLEMENTED — PENDING CI AND PM REVIEW

## Objective

Connect the SANAD frontend to the existing tenant-scoped Organization REST API using the typed integration foundation delivered by EXEC-PROMPT-029.

## Delivered

- Typed organization request and response contracts
- Explicit tenant-scoped list operation
- Create and update operations matching backend DTOs
- Activate, deactivate, and archive lifecycle operations
- Input normalization and length validation
- Transport-level unit tests
- Arabic live-data panel with explicit Tenant UUID entry
- Loading, empty, success, and error states
- Existing demonstration dashboard retained without mutation

## Backend contract

- Collection path: `/api/v1/organizations`
- Resource path: `/api/v1/organizations/{organizationId}`
- Tenant scope: `tenantId` query parameter for read, update, and lifecycle operations
- Create request includes `tenantId`, `name`, and optional `description`
- Lifecycle actions are `activate`, `deactivate`, and `archive`

## Security and scope

- No hardcoded live Tenant UUID
- No implicit tenant selection
- No authentication or authorization bypass
- No membership or user live integration in this stage
- No production-readiness claim

## Validation

Web CI must pass dependency installation, lint, tests, and Next.js build. Repository security and backend gates must remain green before merge.

## Rollback

Revert the merged stage commit. This stage includes no database migration and no backend contract change.
