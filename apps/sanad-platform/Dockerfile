# ============================================================
# SANAD Platform — Dockerfile
# ------------------------------------------------------------
# Multi-stage build:
#   Stage 1 (builder): Maven + JDK 17 to compile and package
#   Stage 2 (runtime): JRE 17 on a slim base for minimal image size
#
# Stage 0 repository skeleton: the build will succeed even with
# no application entry point yet (produces an empty-ish JAR).
# Future stages will set the entry point to the Spring Boot main class.
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

# Build the artifact (skip tests for skeleton stage)
RUN mvn -B -q clean package -DskipTests

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:17-jre-alpine AS runtime

LABEL org.opencontainers.image.title="SANAD Platform" \
      org.opencontainers.image.description="SANAD Global AI ERP SaaS — Platform Service" \
      org.opencontainers.image.source="https://github.com/snadaiapp-png/SNAD"

# Create a non-root user for runtime safety
RUN addgroup -S sanad && adduser -S sanad -G sanad

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /workspace/target/*.jar /app/app.jar

# Default JVM flags (overridable at runtime via JAVA_OPTS)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Dfile.encoding=UTF-8"

USER sanad

# Placeholder port — actual port will be defined when API layer is added
EXPOSE 8080

# Entry point will be updated once the Spring Boot main class exists
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
