# Platform JDBC Temporal Safety — Production Release Authorization

Date: 2026-10-10

Incident: production module-user provisioning failed because a raw `java.time.Instant`
reached PostgreSQL through generic JDBC binding.

Scope: platform-wide backend. The remediation is not limited to CRM or user
permission overrides. The repository-wide CI guard covers every current Java
backend module under `apps/sanad-platform/src/main/java` and automatically
covers future modules added under the same platform source root.

Release chain authorized after exact-head CI is terminal-green:

`Publish Render Backend Image -> Workflow Y2 Production Release Orchestrator -> SANAD Production Release`

Required merge marker:

`PRODUCTION-RELEASE-AUTHORIZED`

No direct production DDL, Flyway repair, RLS weakening, tenant bypass, or
manual database mutation is authorized.
