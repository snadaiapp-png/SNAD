# ============================================================
# SANAD Platform - Production Dockerfile (Render optimized)
# ============================================================

FROM maven:3.9-eclipse-temurin-21 AS builder

ARG RENDER_GIT_COMMIT=unknown

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -ntp -q dependency:go-offline -DskipTests || true

COPY src ./src
RUN mvn -B -ntp -q clean package -DskipTests

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

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=prod

USER sanad

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
