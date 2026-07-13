# ADR: Modular CRM Monolith Architecture

## Status
ACCEPTED

## Context
The CRM module grew to 6,393 lines across 23 files, with `CrmExtendedService` alone at 2,042 lines. Controllers accessed JDBC directly, domain logic was mixed with infrastructure, and no module boundaries existed.

## Decision
Adopt a **Modular Monolith** architecture with 8 bounded context modules. Each module has `api/application/domain/infrastructure` layers. Dependencies flow inward only. Cross-module communication via declared ports.

## Rationale
- Microservices are premature — the team is small, deployment is single-container
- A modular monolith preserves deployability while enforcing boundaries
- ArchUnit tests prevent architectural drift
- Future extraction to microservices is straightforward if boundaries are clean

## Consequences
- Clear ownership for each entity and use case
- Testable domain logic without Spring context
- Controllers become thin HTTP adapters
- Repository ports enable future swapping of persistence
