# SNAD Infrastructure Hardening — Stage 01 Version Matrix

| Domain | Discovered Value | Source | Notes |
|---|---|---|---|
| Java source compatibility | 17 | `pom.xml` `<java.version>17</java.version>` | Language features limited to Java 17 |
| Java target compatibility | 17 | `pom.xml` `<maven.compiler.target>17</maven.compiler.target>` | Bytecode targets Java 17 |
| JDK used in Docker build | 21 | `Dockerfile` `FROM maven:3.9-eclipse-temurin-21` | JDK 21 builds Java 17 bytecode |
| JRE used in Docker runtime | 21 | `Dockerfile` `FROM eclipse-temurin:21-jre-alpine` | JRE 21 runs Java 17 bytecode |
| JDK in CI | 21 (implicit) | `ci.yml` uses `ubuntu-latest` with Temurin setup | GitHub Actions runner default |
| Maven version (CI) | System Maven on runner | `ci.yml` `run: mvn test -B` | NOT using wrapper yet — Stage 02 gap |
| Maven version (Docker) | 3.9 | `Dockerfile` `FROM maven:3.9-eclipse-temurin-21` | Maven 3.9.x in Docker build |
| Maven version (Wrapper) | 3.9.9 | `.mvn/wrapper/maven-wrapper.properties` | Added in Stage 01 |
| Spring Boot | 3.5.6 | `pom.xml` `<version>3.5.6</version>` | |
| Node.js required | 24.x | `package.json` engines, `.nvmrc` | |
| npm | Bundled with Node 24 | — | No separate version pin |
| Next.js | 16.2.9 | `package.json` dependencies | |
| React | 19.2.4 | `package.json` dependencies | |
| TypeScript | ^5.9.3 | `package.json` devDependencies | |
| Vitest | ^4.1.9 | `package.json` devDependencies | |
| Python (CI) | 3.12 | `master-backlog-validation.yml` `python-version: '3.12'` | |
| Python (local) | 3.12.13 | `python3 --version` | Matches CI |
| PostgreSQL (prod) | 16 | `docker-compose.prod.yml` `postgres:16-alpine` | |
| PostgreSQL (CI) | 16 | `backup-restore-validation.yml` `postgres:16-alpine` | Matches prod |
| Gitleaks | 8.24.3 | `security-baseline.yml` Docker image | |
| OWASP Dependency-Check | 12.1.0 | `pom.xml` `<dependency-check.version>` | |
| k6 | (latest runner) | `performance-baseline.yml` uses `grafana/k6-action` | No pinned version |
| GitHub Actions runner | ubuntu-latest | All workflows | Implicit — may vary over time |

## Version Pinning Files Added in Stage 01

| File | Content | Purpose |
|---|---|---|
| `.java-version` | `17` | SDKMAN/Jabba/jenv compatibility |
| `.nvmrc` | `24` | nvm/fnm/volta compatibility |
| `.tool-versions` | `java temurin-21`, `nodejs 24`, `python 3.12` | asdf compatibility |

## Discrepancies

| Item | Local | CI | Docker | Resolution |
|---|---|---|---|---|
| Maven | 3.9.9 (via wrapper) | System (unknown exact) | 3.9 | CI should adopt `./mvnw` in Stage 02 |
| Backend tests | NOT_RUN locally | PASS (434+ tests) | N/A | CI is authority; local compile verified |
| Docker build | NOT_RUN (no Docker) | PASS (CI Docker build) | N/A | CI is authority |
