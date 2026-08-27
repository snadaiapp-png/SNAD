# SNAD Platform — Top 5 Startup Bottlenecks (Phase 3)

**Source:** Codebase analysis (Phase 2a) + Render control-plane evidence + Dockerfile metadata
**Target:** `COLD_START_P95 < 90 seconds` (preferred: `< 60 seconds`)

---

## BOTTLENECK_1: Render Free-Tier CPU Throttling (DOMINANT)

**Estimated contribution:** ~100-120s of the ~148s total (~70-80%)

**Evidence:**
- Same image builds in 9.383s on GitHub Actions runner (CI compile log)
- Same image takes ~148s to start on Render Free
- Render Free: 0.1 CPU, shared, throttled
- Dockerfile uses `-XX:TieredStopAtLevel=1` (skip C2 JIT) and `-XX:+UseSerialGC` — already optimized for slow CPU

**Why it can't be optimized away:**
- Render Free is fundamentally a shared, throttled CPU
- No amount of code optimization can fix CPU throttling
- Only options: (a) upgrade to paid plan (REJECTED by user), (b) reduce CPU work via AOT/lazy-init (already done), (c) use CRaC (not supported on Render Free)

**Mitigation already in place:**
- `LAZY_INIT=true` (lazy bean initialization)
- `-XX:TieredStopAtLevel=1` (faster startup JIT)
- `-XX:+UseSerialGC` (lowest GC overhead)
- `@EnableScheduling` gated off in prod
- All `ApplicationRunner`/`CommandLineRunner` gated off in prod

**Residual optimization potential:** Low. The CPU work is dominated by Spring Boot's classpath scanning and Hibernate metamodel generation, both of which are already minimized.

---

## BOTTLENECK_2: Spring Boot Component Scanning (~10-15s)

**Estimated contribution:** ~10-15s of the ~148s total (~7-10%)

**Evidence:**
- 955 Java source files in `apps/sanad-platform/src/main/java/`
- `@SpringBootApplication` scans `com.sanad.platform.*` by default
- ~42 `@Configuration` classes
- ~96 `@RestController`/`@Controller` classes
- ~93 `@Repository` classes
- ~55 `@Bean` methods
- CI compile of 957 files takes 9.383s — classpath scanning is similar order of magnitude

**Optimization options:**

### A. Spring AOT Processing (RECOMMENDED — biggest single win)
- Pre-compute bean definitions at build time
- Eliminates runtime classpath scanning
- Expected savings: 5-10s
- Implementation: Add `spring-boot-starter-aot` dependency + run AOT processing in Dockerfile
- Risk: LOW — Spring AOT is well-supported in Spring Boot 3.x
- Trade-off: Longer build time, but runtime startup is much faster

### B. Explicit `scanBasePackages`
- Replace default scan with explicit package list
- Expected savings: 1-2s (marginal)
- Risk: HIGH — easy to miss a package, causes bean creation failures

### C. `@Indexed` Annotation
- Uses Spring's `spring-context-indexer` to generate `META-INF/spring.components` at build time
- Expected savings: 1-3s
- Risk: LOW
- Trade-off: Must annotate each `@Configuration`/`@Component`/`@Controller` with `@Indexed`

**Selected optimization:** Option A (Spring AOT) — see Phase 4 for implementation plan.

---

## BOTTLENECK_3: Hibernate EntityManagerFactory Bootstrap (~5-10s)

**Estimated contribution:** ~5-10s of the ~148s total (~3-7%)

**Evidence:**
- 12 `@Entity` classes scanned
- Hibernate builds metamodel even with `ddl-auto=none`
- `hibernate.boot.allow_jdbc_metadata_access=false` (already disabled — skips JDBC metadata calls)
- `open-in-view: false` (already disabled)

**Mitigation already in place:**
- `hibernate.boot.allow_jdbc_metadata_access: false` — skips JDBC metadata lookups during EMF boot
- `open-in-view: false` — no OSIV warning/deferred session
- `ddl-auto=none` (from `JPA_DDL_AUTO=none` env var) — no schema validation
- `format_sql: false` — no SQL formatting overhead

**Residual optimization potential:** Low. The remaining cost is Hibernate's metamodel generation, which is unavoidable for JPA. Could be eliminated by removing JPA entirely (using JDBC only), but that would require major refactoring.

**NOT recommended for optimization** — already optimized.

---

## BOTTLENECK_4: JVM Memory Pressure During Instance Replacement

**Evidence:**
- 11 deploy attempts with image `78c870fc` — only 1 succeeded (`dep-da8bkdfas78s73dvvesg`)
- All subsequent deploys failed with `nonZeroExit: 1` (JVM exited)
- Dockerfile comment: "160+256+32=448MB reserved, ~80MB native overhead => ~528MB which is just over the 512MB limit"

**Root cause:**
- When Render replaces the old instance with a new one, both instances run concurrently for a brief period
- Combined memory: ~528MB (old) + ~528MB (new) = ~1056MB on a 512MB instance
- OOM killer terminates the new JVM with exit code 1

**Mitigation:**
- The new image (`5cf065ec`) uses a 2k buffer (vs 10k in `78c870fc`) — saves ~1.6MB
- This is marginal and unlikely to fix the issue
- The real fix is reducing overall JVM memory footprint

**Optimization options:**

### A. Reduce JVM Heap (RISKY)
- Current: `-Xmx160m`
- Could try: `-Xmx128m` (saves 32MB)
- Risk: HIGH — may cause OOM during request handling
- Not recommended

### B. Reduce Metaspace (RISKY)
- Current: `-XX:MaxMetaspaceSize=256m`
- Could try: `-XX:MaxMetaspaceSize=192m` (saves 64MB)
- Risk: HIGH — already crashed at 160m, 192m may be insufficient
- Not recommended

### C. Reduce Class Count (MODERATE IMPACT)
- Remove unused Spring Boot starters
- Already minimal: no Redis, no Kafka, no Spring Cloud, no OAuth2
- Could potentially remove: `springdoc-openapi` (already disabled in prod), `micrometer-registry-prometheus` (already not exposed in prod)
- Expected savings: 10-30MB metaspace

### D. Switch to Spring Boot Native (GRAALVM)
- Compile to native image — eliminates JVM overhead entirely
- Cold start: <1s
- Memory: <100MB
- Risk: HIGH — requires significant code changes (reflection config, etc.)
- Build time: 5-10 minutes
- Not recommended for now (too much risk for the optimization gain)

**Selected optimization:** Option C (remove unused starters) — see Phase 4.

---

## BOTTLENECK_5: Spring Security Filter Chain + AOP Proxy Creation (~500ms-2s)

**Estimated contribution:** ~500ms-2s of the ~148s total (<2%)

**Evidence:**
- `@EnableMethodSecurity` activates `@PreAuthorize` and custom `@RequireCapability` aspect
- `spring-boot-starter-aop` creates CGLIB proxies for many service beans
- 1 active SecurityFilterChain in prod (the `@Order(0)` H2 console chain is `@Profile("local")` only)
- BCryptPasswordEncoder(10) instantiation is trivial, but first hash is ~50ms

**Mitigation already in place:**
- `SessionCreationPolicy.STATELESS` — no HTTP session overhead
- CSRF disabled
- Only 1 SecurityFilterChain active in prod

**Residual optimization potential:** Low. AOP proxy creation is necessary for `@RequireCapability` security enforcement. Removing it would break RBAC.

**NOT recommended for optimization** — security-critical, already minimal.

---

## Summary: Top 5 Bottlenecks Ranked

| Rank | Bottleneck | Est. Duration | % of 148s | Optimizable? |
|------|------------|---------------|-----------|--------------|
| 1 | Render Free CPU throttling | ~100-120s | ~70-80% | NO (infra constraint) |
| 2 | Component scanning | ~10-15s | ~7-10% | YES — Spring AOT |
| 3 | Hibernate EMF bootstrap | ~5-10s | ~3-7% | NO (already optimized) |
| 4 | JVM memory pressure | (causes failures) | N/A | PARTIAL — remove unused starters |
| 5 | Security + AOP proxies | ~500ms-2s | <2% | NO (security-critical) |

---

## Phase 4 Optimization Plan

Based on the bottleneck analysis, the following optimizations are **safe and recommended**:

### P0: Spring AOT Processing (expected savings: 5-10s)
- Add `spring-boot-starter-aot` dependency
- Enable AOT processing in Dockerfile
- Risk: LOW
- Impact: Reduces component scanning from 10-15s to <1s

### P1: Remove Unused Starters (expected savings: 10-30MB metaspace)
- Remove `springdoc-openapi-starter-webmvc-ui` (already disabled in prod via `springdoc.api-docs.enabled=false`)
- Remove `micrometer-registry-prometheus` (already not exposed via `MANAGEMENT_ENDPOINTS=health`)
- Risk: LOW (both already disabled in prod)
- Impact: Reduces metaspace pressure, may fix instance-replacement OOM

### P2: @Indexed Annotation (expected savings: 1-3s)
- Add `spring-context-indexer` as annotation processor
- Annotate `@Configuration`/`@Component`/`@Controller` with `@Indexed`
- Risk: LOW
- Impact: Generates `META-INF/spring.components` at build time

### NOT RECOMMENDED (rejected optimizations)
- ❌ Spring Native (GraalVM) — too much risk, too much refactoring
- ❌ Disable `@EnableMethodSecurity` — breaks RBAC
- ❌ Reduce `-Xmx` or `-XX:MaxMetaspaceSize` — risks OOM during request handling
- ❌ Disable `hibernate.boot.allow_jdbc_metadata_access` — already disabled
- ❌ Enable `spring.flyway.enabled=true` — Flyway is already disabled in prod (`FLYWAY_ENABLED=false`)

### Target After Optimization
- Current: ~148s (deploy-induced startup)
- After P0 (Spring AOT): ~138-143s
- After P1 (remove starters): ~135-140s + fixes instance-replacement OOM
- After P2 (@Indexed): ~132-138s
- **Target: ~130-140s** (still above 90s P95 target due to Render Free CPU)

### Honest Assessment
The Render Free-tier CPU throttling is the dominant factor (~70-80% of startup time). Even with all optimizations, the startup will remain ~130-140s because the CPU-bound work (class loading, Hibernate metamodel, Spring bean wiring) cannot be reduced below the CPU's throughput limit.

The only way to achieve `COLD_START_P95 < 90 seconds` on Render Free would be:
1. Spring Native (GraalVM) — eliminates JVM warmup entirely, cold start <1s. BUT requires major refactoring.
2. CRaC (Coordinated Restore at Checkpoint) — snapshot the JVM after warmup, restore on cold start. BUT not supported on Render Free.

Both options are significant engineering efforts beyond the scope of "safe startup optimizations" (Phase 4 constraint).
