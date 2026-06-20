# ============================================================
# SANAD Platform - Production Dockerfile
# ------------------------------------------------------------
# Multi-stage build:
#   Stage 1 (builder): Maven + JDK 21 to compile, test, package
#   Stage 2 (runtime): Eclipse Temurin JRE 21 on Alpine
#
# The runtime image runs as a non-root user, exposes port 8080,
# and includes a container health check against /actuator/health.
# All configuration is externalized via environment variables.
# ============================================================

# ---------- Stage 1: Build ----------
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /workspace

# Copy Maven project descriptor first to leverage Docker layer caching
COPY pom.xml ./

# Pre-fetch dependencies (cached unless pom.xml changes)
RUN mvn -B -q dependency:go-offline || true

# Copy source tree
COPY src ./src

# Build the artifact (skip tests; tests run in CI)
RUN mvn -B -q clean package -DskipTests

# Extract Spring Boot layers for layered Docker image
RUN java -Djarmode=layertools -jar target/*.jar extract \
    --destination target/extracted

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL org.opencontainers.image.title="SANAD Platform" \
      org.opencontainers.image.description="SANAD Global AI ERP SaaS - Platform Service" \
      org.opencontainers.image.source="https://github.com/snadaiapp-png/SNAD" \
      org.opencontainers.image.licenses="UNLICENSED"

# Install curl for the container health check
RUN apk add --no-cache curl

# Create a non-root user for runtime safety
RUN addgroup -S sanad && adduser -S sanad -G sanad

WORKDIR /app

# Copy layered application (dependencies first, application last)
COPY --from=builder /workspace/target/extracted/dependencies/ ./
COPY --from=builder /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=builder /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/target/extracted/application/ ./

# Default JVM flags (overridable at runtime via JAVA_OPTS)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=prod

USER sanad

EXPOSE 8080

# Container health check: hit the actuator health endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
