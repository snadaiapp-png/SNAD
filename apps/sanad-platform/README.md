# SANAD Platform

> **Status:** Stage 0 — Repository skeleton (no business logic, no APIs yet).

The Java backend service of the **SANAD Global AI ERP SaaS** platform. This module provides the multi-tenant platform foundation on top of which ERP, CRM, and AI workflow capabilities will be built in subsequent stages.

---

## Repository Location

This service lives under `apps/sanad-platform/` in the SNAD monorepo, alongside the Next.js web application in `apps/web/`.

---

## Architecture

The package layout follows a **Domain-Driven / Hexagonal (Ports & Adapters)** organization. Each top-level package under `com.sanad.platform` has a single, well-defined responsibility:

```
src/main/java/com/sanad/platform/
├── tenant/            Multi-tenant context, tenant lifecycle, tenant isolation boundaries
├── api/               Inbound adapters: REST controllers, GraphQL resolvers, DTOs, mappers
├── application/       Application services, use-case orchestration, transaction scripts
├── domain/            Domain model: entities, value objects, aggregates, domain events, repository ports
├── infrastructure/    Outbound adapters: JPA repositories, external API clients, messaging, persistence
├── organization/      Organization / workspace bounded context (org membership, roles within an org)
├── shared/            Cross-cutting kernel: common value objects, base types, utilities, exceptions
└── security/          Authentication, authorization, identity, token issuance, RBAC enforcement
```

### Dependency Direction

```
api ──► application ──► domain ◄── infrastructure
                                ▲
                                │
                  shared  ◄─────┘
                  security
```

- `domain` depends on **nothing** (pure Java).
- `application` depends on `domain` and `shared`.
- `api` and `infrastructure` depend on `application` and `domain` (implement ports).
- `shared` and `security` are cross-cutting and may be referenced by any layer, but must not depend on `api`, `application`, `infrastructure`, or each other circularly.

---

## Tech Stack (Planned)

| Concern        | Choice                         |
|----------------|--------------------------------|
| Language       | Java 17 (LTS)                  |
| Build          | Maven (single-module for now)  |
| Web framework  | Spring Boot (to be added)      |
| Persistence    | PostgreSQL + Spring Data JPA   |
| Cache          | Redis                          |
| Vector store   | Qdrant                         |
| AI orchestration | LangChain4j                  |
| Observability  | OpenTelemetry + Langfuse       |

> The current `pom.xml` intentionally includes **only** the Maven Compiler Plugin and packaging plugins. Spring Boot starters and infrastructure drivers will be introduced when the first feature stage begins.

---

## Prerequisites

- **Java 17** (Temurin / OpenJDK)
- **Maven 3.9+**
- **Docker 24+** and **Docker Compose v2+** (for containerized builds)

---

## Build

```bash
# From the service root (apps/sanad-platform/)
mvn clean package
```

Artifact is produced at `target/sanad-platform-0.1.0-SNAPSHOT.jar`.

## Run via Docker

```bash
docker compose up --build
```

The service (when an entry point is added in a later stage) will listen on **http://localhost:8080**.

---

## Stage 0 Scope

This initial skeleton intentionally includes **only**:

- Maven POM with Java 17 + compiler/jar/surefire plugins
- Empty package tree following the SANAD architecture
- Multi-stage Dockerfile
- Docker Compose service definition
- Java/Maven `.gitignore`
- This README

The following are **explicitly out of scope** for Stage 0:

- No Spring Boot dependencies or auto-configuration
- No application main class
- No REST controllers / API endpoints
- No domain entities, repositories, or services
- No database migrations
- No test code

---

## Parent Monorepo

This service is part of the **SNAD** monorepo (`snadaiapp-png/SNAD`). Other modules:

- `apps/web/` — Next.js 16 frontend (Stage 0 feasibility study)
- `apps/sanad-platform/` — this service
