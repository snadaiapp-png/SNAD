# Render Environment Governance

## Production runtime ownership

The Render web service owns application runtime only. It does not own schema migration.

Allowed runtime configuration includes application profile, port binding, datasource credentials, JWT/application secrets, pool sizing, logging, notification provider configuration, and health-probe behavior.

Forbidden release-time behavior from the web process:

```text
FLYWAY_ENABLED=true
JPA_DDL_AUTO=create|create-drop|update
Flyway clean
blind baseline
pooler migration fallback
schema reset/recreation
```

## Secret handling

- Secret values are never committed to source.
- Secret values are never copied into Clean-Room evidence or workflow archives.
- Credentials identified as previously exposed must be rotated at the authoritative provider before Green certification.
- The Render API token exposed outside protected secret storage is forbidden for Green.
- Replacement credentials belong only in provider/GitHub protected secret stores.

## Blueprint governance

`render.yaml` is configuration-as-code evidence and remains frozen until release gates pass.

Current contract:

```text
autoDeployTrigger=off
runtime=image
FLYWAY_ENABLED=false
JPA_DDL_AUTO=validate
DATABASE_POOL_MAX=3
DATABASE_POOL_MIN=0
healthCheckPath=/actuator/health/readiness
plan=free
```

No instance-plan upgrade is authorized by this document.

## Cutover governance

A Green service may be created/deployed only after:

1. protected CI passes on the integration SHA;
2. exposed credentials are rotated;
3. canonical rollback authority exists and passes its contract;
4. PostgreSQL Direct migration gate is available and authorized;
5. an immutable GHCR image digest exists for the certified SHA.

Vercel backend cutover follows Green readiness and non-destructive smoke tests; it never precedes them.
