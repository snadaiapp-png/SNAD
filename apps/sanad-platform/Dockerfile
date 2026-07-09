# ============================================================
# SANAD Platform - Simple Production Dockerfile
# ============================================================
# Minimal Dockerfile — no optimizations, just working.
# ============================================================

FROM maven:3.9-eclipse-temurin-21 AS builder

ARG RENDER_GIT_COMMIT=unknown

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -ntp -q dependency:go-offline -DskipTests || true

COPY src ./src
RUN mvn -B -ntp -q clean package -DskipTests -Dgit.commit="${RENDER_GIT_COMMIT}"

FROM eclipse-temurin:21-jre-alpine

ARG RENDER_GIT_COMMIT=unknown
ENV RENDER_GIT_COMMIT=${RENDER_GIT_COMMIT}

RUN apk add --no-cache curl
RUN addgroup -S sanad && adduser -S sanad -G sanad

WORKDIR /app

COPY --from=builder /workspace/target/sanad-platform-*.jar app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=50.0 -XX:+UseG1GC -XX:MaxMetaspaceSize=128m -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=prod

USER sanad

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=300s --retries=5 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Capture Java output to a log file while still sending it to stdout/stderr
# (via tee). If the JVM exits with a non-zero code (startup failure), print
# a marker and the last 200 lines of the log. The `exec` ensures Java
# receives signals directly (SIGTERM for graceful shutdown).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar 2>&1 | tee /tmp/startup.log; EXIT_CODE=${PIPESTATUS[0]}; if [ $EXIT_CODE -ne 0 ]; then echo '=== STARTUP FAILED (exit code '$EXIT_CODE') ==='; echo '=== Last 200 lines of startup log ==='; tail -200 /tmp/startup.log; fi; exit $EXIT_CODE"]
