# ============================================================
# SANAD Platform - Production Dockerfile
# ============================================================
# Multi-stage build, non-root runtime, container-aware JVM sizing.
# Schema migration is deliberately NOT part of web process startup.
# ============================================================

FROM maven:3.9-eclipse-temurin-21 AS builder

ARG RENDER_GIT_COMMIT=unknown

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -ntp -q dependency:go-offline -DskipTests || true

COPY src ./src
RUN mvn -B -ntp -q clean package -Dmaven.test.skip=true -Dgit.commit="${RENDER_GIT_COMMIT}"

FROM eclipse-temurin:21-jre-jammy

ARG RENDER_GIT_COMMIT=unknown
ENV RENDER_GIT_COMMIT=${RENDER_GIT_COMMIT}

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r sanad \
    && useradd -r -g sanad sanad

WORKDIR /app

COPY --from=builder /workspace/target/sanad-platform-*.jar app.jar

RUN mkdir -p /app/logs && chown -R sanad:sanad /app

# Size heap as a percentage of the actual container memory limit instead of a
# hardcoded -Xmx. Keep conservative native/metaspace/direct-memory ceilings so
# the same image can run on different Render instance types without rebuilds.
ENV JAVA_OPTS="-XX:InitialRAMPercentage=20.0 -XX:MaxRAMPercentage=45.0 -XX:MaxDirectMemorySize=32m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -Xss256k -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=prod

USER sanad

EXPOSE 8080

# Liveness proves the JVM/process can serve traffic. Database reachability is
# intentionally checked by Spring's readiness group instead of liveness.
HEALTHCHECK --interval=15s --timeout=5s --start-period=120s --retries=5 \
  CMD curl -fsS "http://localhost:${PORT:-8080}/actuator/health/liveness" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
