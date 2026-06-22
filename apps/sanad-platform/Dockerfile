# ============================================================
# SANAD Platform - Production Dockerfile (Render-optimized)
# ============================================================
# Multi-stage build with Maven cache for faster rebuilds.
# Tests are skipped (run in CI, not in Docker build).
# ============================================================

FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /workspace

# Copy only pom.xml first for dependency caching
COPY pom.xml ./

# Download dependencies (this layer is cached unless pom.xml changes)
RUN mvn -B -ntp -q dependency:go-offline -DskipTests || true

# Copy source and build (skip tests)
COPY src ./src
RUN mvn -B -ntp -q clean package -DskipTests

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl
RUN addgroup -S sanad && adduser -S sanad -G sanad

WORKDIR /app

COPY --from=builder /workspace/target/sanad-platform-*.jar app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=prod

USER sanad

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
