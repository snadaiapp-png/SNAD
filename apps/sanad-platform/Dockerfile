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
# -Dmaven.test.skip=true skips test COMPILATION too (unlike -DskipTests), so a
# broken/unrelated test source cannot block the production image build.
RUN mvn -B -ntp -q clean package -Dmaven.test.skip=true -Dgit.commit="${RENDER_GIT_COMMIT}"

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
# 192MB heap + 160MB metaspace: startup crash-loop (nonZeroExit:1 during
#   class-loading/Hibernate init) observed at 128m/128m after G7 added mobile
#   sync entities. Steady-state usage stays well under these caps; only the
#   startup peak touches higher. 192+160+32=384MB reserved, ~80MB native
#   overhead => ~464MB, safely under the 512MB container limit.
# 256k thread stack: half the default
# TieredStopAtLevel=1: skip C2 for faster startup
# -XX:+ExitOnOutOfMemoryError: crash immediately on OOM (clean exit code)
ENV JAVA_OPTS="-Xmx192m -XX:MaxDirectMemorySize=32m -XX:+UseSerialGC -XX:MaxMetaspaceSize=160m -Xss256k -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=prod

USER sanad

EXPOSE 8080

# Start period 600s (10 min) to allow Flyway migrations + Hibernate init on Render free tier.
# The app takes 3-18 minutes to start depending on Flyway and DB connection.
# Render's platform allows up to ~30 minutes for the update phase before marking failed.
HEALTHCHECK --interval=15s --timeout=10s --start-period=600s --retries=5 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
