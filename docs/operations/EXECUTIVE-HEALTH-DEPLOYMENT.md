# Executive Health Intelligence Deployment

## Scope

This release adds executive platform health monitoring, tenant-level pressure signals, risk forecasting, controlled diagnostics, and allow-listed self-healing actions.

## Pull Request Gate

The Stage 07 artifact provenance workflow must execute for pull requests targeting `main` and again after the final mainline merge.

## Production Route

- Web control plane: `/control-plane`
- Health snapshot API: `GET /api/v1/control-plane/health`
- Controlled action API: `POST /api/v1/control-plane/health/actions`

## Safety Boundary

Operational actions are capability-protected, reason-required, allow-listed, and recorded in the platform audit log. Arbitrary command execution is not supported.
