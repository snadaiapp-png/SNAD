# Capacity and Performance Validation Plan

## Gate
Issue #32 — Capacity and Performance Validation

## Purpose
Establish measurable workload assumptions, repeatable performance tests, latency and failure thresholds, safe operating limits, and evidence for production sizing.

## Initial CI baseline

The repository baseline runs the backend with PostgreSQL and executes a k6 test against `/actuator/health`:

- 10 constant virtual users
- 60 seconds
- Failure rate below 1%
- p95 latency below 500 ms
- p99 latency below 1000 ms
- Successful health checks above 99%

This is a regression baseline only. It is not a production capacity certification and does not close issue #32.

## Production workload model required

Before commercial production approval, define and approve:

- Active tenants and users at launch, 6 months, and 12 months
- Average and peak concurrent sessions
- Requests per second by API family
- Background jobs, workflows, and integration traffic
- Database size, growth rate, and largest tenant assumptions
- File and object storage volume
- Peak business-hour and end-of-period scenarios
- AI workload and external provider limits

## Required test stages

1. API baseline by critical endpoint.
2. Load test at expected peak.
3. Stress test until controlled degradation.
4. Endurance test for memory, connection, and resource leaks.
5. Database connection-pool and query validation.
6. Multi-tenant noisy-neighbor validation.
7. Failure and recovery behavior under load.
8. Production-like test on approved infrastructure.

## Required metrics

- Request rate and throughput
- Error rate
- p50, p95, and p99 latency
- CPU and memory utilization
- JVM heap, garbage collection, and thread count
- Database connections, query latency, and locks
- Storage and network utilization
- Queue depth and background-job delay
- Scaling events and recovery time

## Exit criteria

Issue #32 may close only when:

- The workload model is approved.
- Critical user journeys are covered.
- Production-like load, stress, and endurance tests pass.
- Safe operating limits and scaling thresholds are documented.
- Critical bottlenecks are resolved or formally accepted.
- Evidence and result artifacts are stored and reviewed.
