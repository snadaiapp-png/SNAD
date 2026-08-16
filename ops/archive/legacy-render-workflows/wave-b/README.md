# Legacy Production Workflows — Wave B

Wave B removes manually verified incident/remediation workflows that directly mutate Render runtime configuration, database connectivity, Flyway behavior, PostgreSQL sessions, JVM settings, and/or deployment state outside the canonical Clean-Room release path.

Archived as historical evidence only:

- `enable-flyway-fix.yml`
- `reenable-flyway.yml`
- `render-set-flyway-validate.yml`
- `render-fix-flyway-session-pooler.yml`
- `render-flyway-runtime-remediation.yml`
- `render-fix-flyway-creds.yml`
- `fix-db-pool.yml`
- `render-fix-java-opts.yml`

`fix-db-url.yml` is deliberately not archived in its original form because it contains a fixed application credential in source. It is removed from the current executable tree and covered by the credential-exposure remediation gate.

Important forensic findings preserved by this wave:

- one remediation workflow could auto-run on a push to `main` and mutate Render + redeploy;
- multiple workflows independently toggled Flyway validation/baseline behavior;
- multiple workflows killed PostgreSQL sessions to clear advisory locks;
- multiple workflows rewrote database or Flyway credentials/URLs;
- the historical pooler-mode comments/configuration are not accepted as the governing database topology for Clean-Room; PostgreSQL Direct remains authoritative for migrations.

None of these files back a required `main` branch-protection status context.
