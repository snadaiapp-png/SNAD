# ============================================================
# SANAD Platform - Production Dockerfile (Render optimized)
# ============================================================

FROM maven:3.9-eclipse-temurin-21 AS builder

ARG RENDER_GIT_COMMIT=unknown

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -ntp -q dependency:go-offline -DskipTests || true

# Cache-bust: copy a file that changes every commit to invalidate the Docker layer cache
ARG RENDER_GIT_COMMIT=unknown
COPY src ./src
RUN mvn -B -ntp -q clean package -DskipTests -Dgit.commit="${RENDER_GIT_COMMIT}"

# Fail the image build when the production-compatible Java V15 or the
# forward-only reconciler is absent from the executable Spring Boot JAR.
RUN JAR_FILE="$(find target -maxdepth 1 -name 'sanad-platform-*.jar' ! -name '*.original' | head -n 1)" && \
    test -n "$JAR_FILE" && \
    jar tf "$JAR_FILE" | grep -q 'BOOT-INF/classes/com/sanad/platform/config/migration/V15__seed_rbac_roles_and_capabilities.class' && \
    jar tf "$JAR_FILE" | grep -q 'BOOT-INF/classes/db/migration/V20260702_2__reconcile_admin_role_and_capabilities.sql' && \
    jar tf "$JAR_FILE" | grep -q 'BOOT-INF/classes/db/migration/V20260702_1__create_unified_crm_core.sql'

FROM eclipse-temurin:21-jre-alpine

ARG RENDER_GIT_COMMIT=unknown
ENV RENDER_GIT_COMMIT=${RENDER_GIT_COMMIT}

RUN apk add --no-cache curl
RUN addgroup -S sanad && adduser -S sanad -G sanad

WORKDIR /app

COPY --from=builder /workspace/target/sanad-platform-*.jar app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=60.0 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=prod

USER sanad

EXPOSE 8080

# Extended start-period (5 min) to accommodate Spring Boot + JPA + Flyway init on Render free tier (512MB)
HEALTHCHECK --interval=30s --timeout=10s --start-period=300s --retries=5 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
