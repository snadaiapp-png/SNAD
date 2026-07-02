# SNAD CRM Runtime Environment

## Status

```text
ENVIRONMENT BASELINE: IMPLEMENTED ON FEATURE BRANCH
TARGET: LARGE MULTI-TENANT CRM
LOCAL PRODUCTION SIMULATION: AVAILABLE
PRODUCTION AUTHORIZATION: NOT GRANTED
```

This environment prepares the CRM backend, web application, mobile application, database, cache, search, attachment security, monitoring, backups, restore verification, and load tests. PostgreSQL remains the authoritative system of record. Search, cache, and local attachment storage are rebuildable projections or development facilities.

## Version baseline

| Capability | Technology | Version |
|---|---|---:|
| Relational source of truth | PostgreSQL | 18.4 |
| Semantic vectors | pgvector | 0.8.4 |
| Cache and coordination | Valkey | 9.1.0 |
| Full-text/vector search | OpenSearch | 3.6.0 |
| Attachment malware scanning | ClamAV | 1.5.2 |
| Load testing | Grafana k6 | 2.1.0 |
| Metrics | Prometheus | 3.x |
| Dashboards | Grafana | 12.x |
| Backend | Spring Boot | 3.5.6 |
| Web | Next.js | 16.2.9 |
| Mobile | Expo / React Native | SDK 57 / 0.86 |

Production container images must be pinned by immutable digest before release.

## Architecture

```text
Web / Android / iOS
          |
          v
SNAD CRM API and authorization
          |
          +---------------- PostgreSQL 18 + pgvector
          |                  - tenant-scoped source of truth
          |                  - Flyway
          |                  - outbox and indexing queues
          |                  - semantic document materialization
          |
          +---------------- Valkey
          |                  - cache
          |                  - rate coordination
          |                  - distributed short-lived state
          |
          +---------------- OpenSearch
          |                  - Arabic/English search
          |                  - tenant routing
          |                  - vector search
          |                  - rebuildable index
          |
          +---------------- Attachment storage
          |                  - local filesystem in development
          |                  - managed S3-compatible service in production
          |                  - ClamAV scan before CLEAN status
          |
          +---------------- Prometheus / Grafana
                             - health, metrics, capacity, latency
```

## Tenant scale model

The runtime establishes 128 logical shard buckets and a tenant placement registry. A tenant is assigned a stable bucket, region, service tier, record quota, and storage quota. This provides a migration path from a shared PostgreSQL cluster to regional or dedicated clusters without changing CRM entity identity.

Mandatory data rules:

1. Every CRM-owned table carries `tenant_id`.
2. Primary and unique business identities include tenant context.
3. High-cardinality indexes start with `tenant_id` where queries are tenant scoped.
4. Cache keys include tenant and authorization context.
5. OpenSearch indexing requires tenant routing.
6. Object keys begin with an immutable tenant prefix.
7. Events, exports, analytics, and logs preserve tenant context.
8. Search and cache never become sources of truth.

## Database scale primitives

The local PostgreSQL image enables:

- `vector` for semantic search.
- `pg_stat_statements` for query analysis.
- `pg_trgm` for fuzzy names and email search.
- `unaccent` for multilingual normalization.
- `btree_gin` and `btree_gist` for composite search indexes.
- `citext` for case-insensitive email handling.
- `pgcrypto` for UUID and hash helpers.

The `crm_runtime` schema contains:

- tenant capacity and placement metadata;
- a monthly partitioned transactional outbox;
- an idempotent search indexing queue;
- an attachment registry with checksum and scan state;
- semantic documents with a 1536-dimensional HNSW vector index.

Database defaults are tuned for a development workstation with approximately 2 GB available to PostgreSQL. Production values must be calculated from measured memory, CPU, storage latency, concurrency, and managed-service limits.

## Search design

OpenSearch is configured with:

- three primary shards for local scale simulation;
- required `_routing` by tenant;
- strict mappings to prevent ungoverned schema growth;
- Arabic and English analyzers;
- normalized keyword fields;
- HNSW cosine vector search;
- explicit read and write aliases;
- disabled automatic index creation.

The production cluster must use TLS, authentication, replicas, snapshots, and multi-node quorum. The local profile disables the OpenSearch security plugin only because it is bound to localhost and isolated Docker networks.

## Cache design

Valkey uses append-only persistence and bounded memory with `allkeys-lru`. Cache contents are disposable and must be derivable from PostgreSQL. Cache invalidation must be driven by committed domain events or versioned entity changes.

## Attachment design

Development attachments use a dedicated Docker volume. Production attachments use managed S3-compatible object storage with:

- server-side encryption;
- versioning;
- lifecycle rules;
- retention and legal-hold policies where required;
- tenant-prefixed object keys;
- SHA-256 checksums;
- malware scanning;
- signed short-lived download URLs;
- no public buckets.

ClamAV is available under the `crm-storage` profile. An attachment must remain `PENDING` or `QUARANTINED` until scanning succeeds.

## Commands

```bash
make bootstrap
make doctor
make crm-config
make crm-build
make crm-up
make crm-up-scale
make crm-up-storage
make crm-up-search
make crm-up-observability
make crm-up-full
```

Scale and recovery:

```bash
make crm-db-seed
make crm-db-validate
make crm-load
make crm-backup
make crm-restore-verify
make crm-readiness
```

## Scale validation levels

### CI gate

```text
Tenants:              1,000
Accounts:             100,000
Contacts:             200,000
Outbox events:         10,000
Search queue records:  10,000
Semantic vectors:         100 x 1536 dimensions
```

### Full workstation gate

```text
Tenants:              10,000
Accounts per tenant:     100
Accounts:            1,000,000
Contacts per account:      2
Contacts:            2,000,000
```

Example:

```bash
CRM_LOAD_TENANTS=10000 \
CRM_LOAD_ACCOUNTS_PER_TENANT=100 \
CRM_LOAD_CONTACTS_PER_ACCOUNT=2 \
make crm-db-seed

make crm-db-validate
```

### Production performance gate

Production authorization requires a dedicated environment with realistic data distribution, concurrency, network latency, replicas, backups, search nodes, and object storage. Passing a workstation or CI test is necessary but not sufficient for production capacity claims.

## Acceptance criteria

The environment is acceptable for the next installation phase only when:

- Compose configuration validates.
- Backend and web images build.
- PostgreSQL starts with all required extensions.
- Flyway validates without destructive repair.
- scale seed and validation scripts pass.
- tenant-scoped indexes are used for critical queries.
- Outbox partitions and search queues pass integrity checks.
- vector insert and nearest-neighbor queries pass.
- backup checksum and isolated restore pass.
- Valkey read/write smoke passes.
- OpenSearch multilingual tenant-routed indexing passes.
- ClamAV reports ready when storage scanning is enabled.
- k6 thresholds pass in the authorized test environment.
- Prometheus and Grafana report healthy.
- no secrets are committed.

## Remaining external production inputs

The repository cannot manufacture external production infrastructure. Before public release, owners must provide:

- managed PostgreSQL HA, read replicas, PITR, and regional placement;
- managed OpenSearch or an approved multi-node deployment;
- managed S3-compatible storage and encryption keys;
- DNS, TLS, WAF, CDN, and rate limits;
- production email provider credentials;
- Apple, Google, Expo, Vercel, and backend deployment credentials;
- secret-manager integration;
- alert destinations and on-call ownership;
- privacy, retention, residency, and disaster-recovery approvals.

These are release inputs, not missing source-code libraries. Production remains fail-closed until they are configured and tested.
