# ============================================================
# SANAD Platform - Production Dockerfile (Render free-tier optimized)
# ============================================================
# Optimizations for 512MB RAM / shared CPU:
#   1. Serial GC + TieredStopAtLevel=1 → faster startup, lower memory
#   2. CDS (Class Data Sharing) → faster class loading on restart
#   3. -XX:SharedArchiveFile → archive generated at build time
#   4. Spring lazy initialization → deferred bean creation
#   5. Smaller HikariCP pool → less memory at idle
# ============================================================

FROM maven:3.9-eclipse-temurin-21 AS builder

ARG RENDER_GIT_COMMIT=unknown

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -ntp -q dependency:go-offline -DskipTests || true

ARG RENDER_GIT_COMMIT=unknown
COPY src ./src
RUN mvn -B -ntp -q clean package -DskipTests -Dgit.commit="${RENDER_GIT_COMMIT}"

# Verify required migrations are in the JAR
RUN JAR_FILE="$(find target -maxdepth 1 -name 'sanad-platform-*.jar' ! -name '*.original' | head -n 1)" && \
    test -n "$JAR_FILE" && \
    jar tf "$JAR_FILE" | grep -q 'BOOT-INF/classes/com/sanad/platform/config/migration/V15__seed_rbac_roles_and_capabilities.class' && \
    jar tf "$JAR_FILE" | grep -q 'BOOT-INF/classes/db/migration/V20260702_2__reconcile_admin_role_and_capabilities.sql' && \
    jar tf "$JAR_FILE" | grep -q 'BOOT-INF/classes/db/migration/V20260702_1__create_unified_crm_core.sql'

# Generate CDS archive at build time for faster startup
RUN JAR_FILE="$(find target -maxdepth 1 -name 'sanad-platform-*.jar' ! -name '*.original' | head -n 1)" && \
    java -XX:ArchiveClassesAtExit=/workspace/app.jsa -Dspring.context.exit=onRefresh \
    -jar "$JAR_FILE" > /dev/null 2>&1 || true

FROM eclipse-temurin:21-jre-alpine

ARG RENDER_GIT_COMMIT=unknown
ENV RENDER_GIT_COMMIT=${RENDER_GIT_COMMIT}

RUN apk add --no-cache curl
RUN addgroup -S sanad && adduser -S sanad -G sanad

WORKDIR /app

COPY --from=builder /workspace/target/sanad-platform-*.jar app.jar
COPY --from=builder /workspace/app.jsa app.jsa

# JVM flags optimized for Render free tier (512MB RAM, shared CPU):
#   -XX:+UseSerialGC        : Single-threaded GC, lowest overhead for small heaps
#   -XX:TieredStopAtLevel=1 : C1 compiler only, faster startup (no C2 JIT)
#   -XX:MaxRAMPercentage=50 : Leave room for non-heap (Metaspace, threads, code cache)
#   -Xss512k                : Smaller thread stack (default 1MB)
#   -XX:SharedArchiveFile   : Use CDS archive generated at build time
#   -Djava.security.egd     : Faster SecureRandom seeding (avoid /dev/random blocking)
#   -XX:+ExitOnOutOfMemory  : Fast fail on OOM (Render will restart)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=50.0 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:SharedArchiveFile=/app/app.jsa -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=prod

USER sanad

EXPOSE 8080

# Start period 4 min (reduced from 5 — lazy init + CDS should help)
HEALTHCHECK --interval=30s --timeout=10s --start-period=240s --retries=5 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
