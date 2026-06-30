# ADR-001: Toolchain and Environment Parity

## Status
Accepted

## Date
2026-06-30

## Context

Stage 00 baseline discovery revealed that the SNAD repository lacked a unified toolchain:
- No Maven Wrapper — backend builds depended on whatever Maven version was installed locally or on the CI runner.
- No `.nvmrc` or `.java-version` — Node.js and Java versions were only specified in `package.json` engines and `pom.xml`.
- No `.tool-versions` for asdf/SDKMAN users.
- Docker was not available in all environments, making Flyway live validation and container builds impossible locally.
- There was no single verification script to run all checks in CI-equivalent order.

## Decision

1. **Add Maven Wrapper** using Maven 3.9.9 — matching the Docker build image.
2. **Pin tool versions** via `.java-version`, `.nvmrc`, `.tool-versions`.
3. **Create verification scripts**: `verify-local.sh`, `check-docker-parity.sh`, `check-flyway-postgres.sh`.
4. **Docker absence strategy**: `SKIPPED_DOCKER_NOT_AVAILABLE` — not a failure.
5. **No Kubernetes, Redis, Message Broker** — architecture remains Modular Monolith.

## Consequences

- Positive: Any developer/agent/CI can run `./mvnw` without pre-installing Maven. `verify-local.sh --fast` gives 60-second confidence check.
- Negative: Maven Wrapper script is custom (not official `mvn wrapper:wrapper` output). Should be regenerated officially at first opportunity.
- Risk: CI still uses system `mvn` — parity gap to be closed in Stage 02.

## Toolchain Versions

| Tool | Version | Source |
|---|---|---|
| Java source/target | 17 | pom.xml |
| JDK build | 21 (temurin) | Dockerfile, .tool-versions |
| JRE runtime | 21 (temurin-alpine) | Dockerfile |
| Maven | 3.9.9 | .mvn/wrapper/maven-wrapper.properties |
| Spring Boot | 3.5.6 | pom.xml |
| Node.js | 24.x | .nvmrc, package.json |
| Next.js | 16.2.9 | package.json |
| React | 19.2.4 | package.json |
| TypeScript | ^5.9.3 | package.json |
| Python | 3.12 | CI workflows, .tool-versions |
| PostgreSQL | 16 | docker-compose.prod.yml |
| Gitleaks | 8.24.3 | security-baseline.yml |
| OWASP Dependency-Check | 12.1.0 | pom.xml |

## Local Verification

```bash
scripts/dev/verify-local.sh --fast   # Quick (no Docker)
scripts/dev/verify-local.sh --full   # Full (includes Docker + Flyway)
```

## Known Gaps

1. CI uses system `mvn`, not `./mvnw` — Stage 02 will address.
2. Maven Wrapper script is custom — should be regenerated with official tool.
3. Backend tests not run locally (compile only) — delegated to CI.

## Rollback Plan

Remove: `mvnw`, `mvnw.cmd`, `.mvn/`, `.java-version`, `.nvmrc`, `.tool-versions`, `scripts/dev/`. No production code modified.
