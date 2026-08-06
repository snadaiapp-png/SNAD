# ============================================================
# SANAD Platform - Production Dockerfile (Render-optimized)
# ============================================================
# Uses debian-slim instead of Alpine for better JVM compatibility.
# Aggressive memory optimization for Render's 512MB free tier.
# ============================================================

FROM maven:3.9-eclipse-temurin-21 AS builder

ARG RENDER_GIT_COMMIT=unknown

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -ntp -q dependency:go-offline -DskipTests || true

COPY src ./src
RUN mvn -B -ntp -q clean package -DskipTests -Dgit.commit="${RENDER_GIT_COMMIT}"

FROM eclipse-temurin:21-jre-jammy

ARG RENDER_GIT_COMMIT=unknown
ENV RENDER_GIT_COMMIT=${RENDER_GIT_COMMIT}

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN groupadd -r sanad && useradd -r -g sanad sanad

WORKDIR /app

COPY --from=builder /workspace/target/sanad-platform-*.jar app.jar

# Create logs directory and grant sanad user write access.
# logback-spring.xml configures RollingFileAppenders with relative paths
# (logs/sanad-platform.log, logs/sanad-platform-error.log, logs/sanad-platform-audit.log).
# These resolve to /app/logs/ inside the container. Without this directory,
# the sanad user (non-root) cannot create log files and the appender fails.
RUN mkdir -p /app/logs && chown -R sanad:sanad /app

# Memory optimization for Render 512MB free tier
# SerialGC: lowest memory overhead GC
# 128MB heap: sufficient for Spring Boot with CRM-009
# 128MB metaspace: required for CRM-009 class loading (48m was insufficient)
# 256k thread stack: half the default
# TieredStopAtLevel=1: skip C2 for faster startup
# -XX:+ExitOnOutOfMemoryError: crash immediately on OOM (clean exit code)
ENV JAVA_OPTS="-Xmx128m -XX:MaxDirectMemorySize=32m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -Xss256k -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=prod

USER sanad

EXPOSE 8080

# Start period 600s (10 min) to allow Flyway migrations + Hibernate init on Render free tier.
# The app takes 3-18 minutes to start depending on Flyway and DB connection.
# Render's platform allows up to ~30 minutes for the update phase before marking failed.
HEALTHCHECK --interval=15s --timeout=10s --start-period=600s --retries=5 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
