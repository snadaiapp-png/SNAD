# Production Cutover Gate

Production cutover is explicitly forbidden until all conditions below are PASS:

```text
SOURCE_SHA_SYNC=PASS
PROTECTED_CI=PASS
CONTROL_PLANE_AUDIT=PASS
RELEASE_CONTRACTS=PASS
PRODUCTION_DB_CREDENTIAL_ROTATION=PASS
RENDER_API_TOKEN_ROTATION=PASS
POSTGRESQL_DIRECT_NETWORK_GATE=PASS
POSTGRESQL_DIRECT_FLYWAY_GATE=PASS
IMMUTABLE_IMAGE_DIGEST=VERIFIED
CANONICAL_RENDER_DEPLOY=AVAILABLE
CANONICAL_RENDER_ROLLBACK=AVAILABLE
GREEN_READINESS=PASS
GREEN_SMOKE=PASS
```

If any condition is `BLOCKED`, `UNKNOWN`, `IN_PROGRESS`, or unverified:

```text
PRODUCTION_CUTOVER=NOT_EXECUTED
FINAL_VERDICT=BLOCKED
```

No exception is granted for a manual deploy, deploy hook, mutable `latest` image, pooler migration fallback, destructive database operation, or reuse of an exposed credential.
