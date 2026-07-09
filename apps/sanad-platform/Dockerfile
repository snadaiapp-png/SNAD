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

ENV JAVA_OPTS="-XX:MaxRAMPercentage=35.0 -XX:+UseSerialGC -XX:MaxMetaspaceSize=80m -Xss256k -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=prod

USER sanad

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=420s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
