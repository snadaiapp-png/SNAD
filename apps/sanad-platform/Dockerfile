# ============================================================
# SANAD Platform - Production Dockerfile (Render-optimized)
# ============================================================
# Multi-stage build with Maven cache for faster rebuilds.
# Tests are skipped (run in CI, not in Docker build).
#
# Stage 05A.2.9.1 (Flyway V15 Reconciliation):
#   - Added V15-in-JAR verification step after build.
#   - Added GIT_COMMIT build arg for startup provenance logging.
#   - Docker build FAILS if V15 is not resolved inside the JAR.
# ============================================================

FROM maven:3.9-eclipse-temurin-21 AS builder

# RENDER_GIT_COMMIT is the actual env var Render injects at build time.
# It is baked into the JAR as a manifest attribute and logged
# at application startup for provenance verification.
ARG RENDER_GIT_COMMIT=unknown
ENV RENDER_GIT_COMMIT=${RENDER_GIT_COMMIT}

WORKDIR /workspace

# Copy only pom.xml first for dependency caching
COPY pom.xml ./

# Download dependencies (this layer is cached unless pom.xml changes)
RUN mvn -B -ntp -q dependency:go-offline -DskipTests || true

# Copy source and build (skip tests — run in CI)
COPY src ./src

# Clean before package to guarantee no stale artifacts.
# Pass RENDER_GIT_COMMIT as a Maven property for the JAR manifest.
RUN mvn -B -ntp -q clean package -DskipTests \
      -Dgit.commit="${RENDER_GIT_COMMIT}"

# --- Verify V15 is resolved inside the built JAR ---
# The JAR must contain BOTH the Java V15 class (JDBC migration)
# AND the reconciler SQL migration. If either is missing, the
# Docker build fails immediately — preventing a stale-artifact
# deploy that would trigger "Detected applied migration not
# resolved locally: 15" at runtime.
RUN JAR_FILE="$(find target -maxdepth 1 -name 'sanad-platform-*.jar' ! -name '*.original' | head -n 1)" && \
    test -n "$JAR_FILE" && \
    echo "Verifying V15 in: $JAR_FILE" && \
    jar tf "$JAR_FILE" | grep -q 'BOOT-INF/classes/db/migration/V20260702_1__reconcile_admin_role_and_capabilities.sql' || { \
        echo "FAIL: V20260702_1 reconciler SQL not found in JAR"; exit 1; \
    } && \
    jar tf "$JAR_FILE" | grep -q 'BOOT-INF/classes/com/sanad/platform/config/migration/V15__seed_rbac_roles_and_capabilities.class' || { \
        echo "FAIL: V15 Java migration class not found in JAR"; exit 1; \
    } && \
    echo "V15 verification PASS: both Java V15 class and reconciler SQL present in JAR"

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl
RUN addgroup -S sanad && adduser -S sanad -G sanad

WORKDIR /app

# Copy only the repackaged JAR from the builder stage.
# The .original file is the pre-repackage JAR and must NOT be copied.
COPY --from=builder /workspace/target/sanad-platform-*.jar app.jar

# Carry RENDER_GIT_COMMIT into the runtime image for startup logging.
ARG RENDER_GIT_COMMIT=unknown
ENV RENDER_GIT_COMMIT=${RENDER_GIT_COMMIT}

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=prod

USER sanad

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
