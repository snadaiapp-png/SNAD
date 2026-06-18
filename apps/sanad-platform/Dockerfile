# ============================================================
# SANAD Platform - Dockerfile (Spring Boot layered jar)
# ------------------------------------------------------------
# Multi-stage build:
#   Stage 1 (builder): Maven + JDK 17 to compile and extract layers
#   Stage 2 (runtime): Eclipse Temurin JRE 17 on Alpine
#
# Uses Spring Boot's layered jar feature for optimal Docker layer
# caching: dependencies layer changes rarely, application layer
# changes on every build.
# ============================================================

# ---------- Stage 1: Build ----------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /workspace

# Copy Maven project descriptor first to leverage Docker layer caching
COPY pom.xml ./

# Pre-fetch dependencies (cached unless pom.xml changes)
RUN mvn -B -q dependency:go-offline || true

# Copy source tree
COPY src ./src

# Build the artifact (skip tests for image build; tests run in CI)
RUN mvn -B -q clean package -DskipTests

# Extract Spring Boot layers for layered Docker image
RUN java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:17-jre-alpine AS runtime

LABEL org.opencontainers.image.title="SANAD Platform" \
      org.opencontainers.image.description="SANAD Global AI ERP SaaS - Platform Service" \
      org.opencontainers.image.source="https://github.com/snadaiapp-png/SNAD"

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
ENV SPRING_PROFILES_ACTIVE=dev

USER sanad

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
