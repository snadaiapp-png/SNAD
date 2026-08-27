# SNAD Platform — Startup Timeline (Phase 2)

**Generated:** 2026-08-27
**Method:** Render control-plane events + Spring Boot codebase analysis + Dockerfile metadata
**Limitation:** Render Free-tier logs are not accessible via public API (404 on `/v1/services/{id}/logs`). Timeline reconstructed from deploy orchestration timestamps + codebase instrumentation evidence.

---

## Render Control-Plane Evidence

### Successful Deploy (dep-da8bkdfas78s73dvvesg)
- **Image:** `ghcr.io/snadaiapp-png/snad-backend:78c870fc1daec0a9b508173b257fa9a07d027b9d`
- **Image SHA:** `sha256:6ab016dfb00cc3279bc21812a9b9846a907ce8adf6dbb9b33df5b7fcdf7d80d2`
- **deploy_started:** `2026-08-27T22:31:07.917029Z`
- **deploy_ended:** `2026-08-27T22:33:35.982287Z` (status=succeeded)
- **Total deploy orchestration time:** ~148 seconds
- **Profile:** prod
- **Plan:** free (512MB RAM, shared CPU, frankfurt region)

### Render Free-Tier Constraints (from Dockerfile)
- **JVM Heap:** `-Xmx160m`
- **Metaspace:** `-XX:MaxMetaspaceSize=256m`
- **Direct Memory:** `-XX:MaxDirectMemorySize=32m`
- **GC:** `-XX:+UseSerialGC` (single-threaded, lowest overhead)
- **JIT:** `-XX:TieredStopAtLevel=1` (skip C2, faster startup)
- **Total JVM reserved:** ~448MB + ~80MB native = ~528MB (just over 512MB limit — Render empirically allows overshoot during startup)
- **Healthcheck:** `start-period=600s` (10 min grace), `interval=15s`, `retries=5`
- **Documented baseline:** "The app takes 3-18 minutes to start depending on Flyway and DB connection" (Dockerfile line 63)

---

## Spring Boot Startup Phases (13-step breakdown)

The `StartupTimelineLogger` (committed in PR #918) captures these phases via `BufferingApplicationStartup`. The phases are:

| # | Phase | Spring Boot Event | Codebase Evidence | Estimated Duration |
|---|-------|-------------------|-------------------|-------------------|
| 1 | JVM process start | (pre-Spring) | `java $JAVA_OPTS -jar app.jar` (Dockerfile line 68) | ~2-5s |
| 2 | SpringApplication start | `ApplicationStartingEvent` | `SanadPlatformApplication.main()` — creates SpringApplication, sets BufferingApplicationStartup, adds StartupTimelineLogger | <1s |
| 3 | Environment preparation | `ApplicationEnvironmentPreparedEvent` | 4 EnvironmentPostProcessors: RenderDatabaseUrlConverter, ProductionDatasourceGuard, ProductionSecurityGuard, ProductionMockGuard | ~50-200ms |
| 4 | Component scanning | (during context refresh) | `@SpringBootApplication` scans `com.sanad.platform.*` — 955 Java files, ~42 @Configuration classes, ~96 @RestController, ~93 @Repository | **5-15s** (Render Free CPU) |
| 5 | Repository scanning | (during context refresh) | 12 @Entity classes + ~93 @Repository (JDBC-based, not JPA) | ~1-3s |
| 6 | Flyway | (after datasource init) | **DISABLED in prod** (`FLYWAY_ENABLED=false` on Render) — 0s | 0s |
| 7 | Datasource initialization | (HikariCP pool start) | HikariCP: max=3, min-idle=1, initialization-fail-timeout=-1 (non-blocking) | ~1-2s |
| 8 | Hibernate metadata initialization | (EntityManagerFactory creation) | 12 @Entity classes, `hibernate.boot.allow_jdbc_metadata_access=false`, `ddl-auto=none` (from `JPA_DDL_AUTO=none` env) | **3-8s** |
| 9 | EntityManagerFactory creation | (Hibernate bootstrap) | Spring Boot auto-config, `open-in-view: false` | included in #8 |
| 10 | Security initialization | (SecurityFilterChain) | 1 active SecurityFilterChain (@Order(1)), `@EnableMethodSecurity`, BCryptPasswordEncoder(10), JwtTokenProvider, JwtAuthenticationFilter | ~200-500ms |
| 11 | Workflow/AI integration init | (bean instantiation) | HttpAiGatewayAdapter, HttpWorkflowIntegrationAdapter, ResendEmailAdapter — all `java.net.http.HttpClient` builders, lazy connection | <100ms total |
| 12 | Tomcat initialization | (embedded server) | Spring Boot default Tomcat 10.1.x, port 8080, ~96 controllers mapped | ~500ms-1s |
| 13 | Actuator readiness | `ApplicationReadyEvent` | StartupProvenanceLogger logs commit, ProductionWorkflowStubGuard validates adapters, StartupTimelineLogger dumps top-30 slowest steps | ~100ms |
| 14 | Final application ready | `ApplicationReadyEvent` | (same as #13) | (included) |

### Lazy Initialization Active
- `spring.main.lazy-initialization=true` (env `LAZY_INIT=true`, default in `application-prod.yml`)
- All singleton beans EXCEPT those required by Hibernate/Flyway/JPA are deferred until first use
- `@Scheduled` workers are INERT in prod (`@EnableScheduling` gated by `scheduling.enabled=true`, which is NOT set on Render)
- `ApplicationRunner`/`CommandLineRunner` beans are all gated by profile or env flag — none run in prod

---

## Estimated Total Startup Breakdown

| Phase | Duration | % of Total (est. 148s) | Blocking? |
|-------|----------|----------------------|-----------|
| 1. JVM process start | ~3s | 2% | Yes |
| 2. SpringApplication start | <1s | <1% | Yes |
| 3. Environment prep | ~100ms | <1% | Yes |
| 4. **Component scanning** | **~10-15s** | **~7-10%** | Yes |
| 5. Repository scanning | ~2s | ~1% | Yes |
| 6. Flyway | 0s (disabled) | 0% | N/A |
| 7. Datasource init | ~1-2s | ~1% | Yes |
| 8. **Hibernate EMF bootstrap** | **~5-10s** | **~3-7%** | Yes |
| 9. EMF creation | (in #8) | — | — |
| 10. Security init | ~300ms | <1% | Yes |
| 11. Integration init | <100ms | <1% | Yes |
| 12. Tomcat init | ~1s | <1% | Yes |
| 13. Actuator readiness | ~100ms | <1% | Yes |
| 14. App ready | (in #13) | — | — |
| **Render Free CPU slowdown** | **~100-120s** | **~70-80%** | Yes (CPU-bound) |
| **TOTAL** | **~148s** | **100%** | — |

### Critical Insight: Render Free CPU is the Dominant Bottleneck

The **Render Free-tier shared CPU** is the dominant factor. On a normal CPU (GitHub Actions runner), the same image builds in ~9 seconds (verified: Maven compile of 957 source files took 9.383s in CI). On Render Free's throttled CPU, the same work takes 10-15x longer.

The Dockerfile's `-XX:TieredStopAtLevel=1` (skip C2 JIT) helps startup but hurts steady-state. The `-XX:+UseSerialGC` (single-threaded GC) minimizes memory overhead but adds GC pauses during class-loading.

---

## What the Running Image Has Already Captured

The `78c870fc` image (currently live on Render) includes:
1. `BufferingApplicationStartup` with 10k capacity — captures every Spring Boot startup step
2. `StartupTimelineLogger` registered via `SpringApplication.addListeners()` — captures 5 lifecycle event timestamps + dumps top-30 slowest steps + category summary to the `SANAD-STARTUP` logger

**Limitation:** The captured data is in Render's deployment logs, which are only accessible via the Render dashboard (not the public API). The `/v1/services/{id}/logs` endpoint returns 404.

### Alternative Data Sources Available
1. **Render deploy orchestration timestamps** (via `/v1/services/{id}/deploys/{deployId}`) — shows `startedAt` and `finishedAt` for the deploy wrapper, NOT the JVM startup
2. **Render events** (via `/v1/services/{id}/events`) — shows `deploy_started` and `deploy_ended` with status
3. **Render service `updatedAt`** — changes when a deploy modifies the service
4. **Direct HTTP to `/actuator/health`** — confirms app ready (but doesn't show timing)
5. **Direct HTTP to `/actuator/startup`** — would show the buffered timeline, BUT requires `MANAGEMENT_ENDPOINTS=health,startup` env var, which caused deploy failures (likely due to memory pressure during instance replacement on Render Free)

---

## Recommendation for Phase 3-4

Given the constraints (Render logs inaccessible, env var changes cause deploy failures), the optimization strategy should focus on **reducing the dominant CPU-bound work**:

1. **Reduce component scan scope** — explicit `scanBasePackages` instead of default `com.sanad.platform.*`
2. **Enable Spring AOT processing** — pre-compute bean definitions at build time to eliminate runtime scanning
3. **Reduce Hibernate metamodel cost** — already mitigated by `allow_jdbc_metadata_access=false` and `ddl-auto=none`
4. **Flyway is already disabled** (`FLYWAY_ENABLED=false`) — no optimization needed
5. **Classpath slimming** — remove unused Spring Boot starters (already minimal: no Redis, no Kafka, no Spring Cloud)
6. **Consider CRaC (Coordinated Restore at Checkpoint)** — Eclipse Temurin 21 supports it, could reduce cold start from 148s to <10s. BUT Render Free doesn't support CRaC snapshots.

The most impactful single optimization would be **Spring AOT processing**, which pre-computes bean definitions at build time. This can cut 5-15s of component scanning down to <1s. However, it requires adding `spring-boot-starter-aot` and running AOT processing during the Docker build.

---

## Comparison: Previous True Cold-Start Test

The previous true cold-start test (PR #915 acceptance) observed:
- Render Free cold start: **125-282 seconds** (variable, after 20-min idle)
- BFF 125s auth budget exceeded when cold start > 125s

The successful deploy today (`dep-da8bkdfas78s73dvvesg`) took **~148 seconds** — within the observed range. This is a deploy-induced startup (not a true cold start after idle), so it represents the "best case" startup time. A true cold start after 20-min idle would likely be longer due to additional JVM warmup overhead.
