# CRM Dependency Rules

## Allowed Dependencies
- api → application (controllers call use cases)
- application → domain (use cases call repository ports)
- infrastructure → domain (adapters implement ports)
- Cross-module: only via declared ports, never via implementation classes

## Forbidden Dependencies
- controller → JDBC / SQL
- controller → infrastructure packages
- domain → Spring Web / JDBC / JPA
- domain → application or API
- module A infrastructure → module B infrastructure
- query module → write operations (@Transactional)

## Enforcement
- ArchUnit tests: 4 active rules (domain isolation, query read-only)
- modular-architecture-check.sh: domain isolation + Map<String,Object> + placeholder checks
- CRM Modular Architecture Validation workflow: CI-enforced
