# ============================================================
# SANAD Platform - Production Dockerfile (Render optimized)
# ============================================================

FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Set strict memory limits for Render free tier (512MB total)
ENV MAVEN_OPTS="-Xmx256m -XX:+UseG1GC -XX:MaxMetaspaceSize=128m"

COPY pom.xml .
COPY src ./src

# Single Maven command: resolve dependencies + compile + package
# No separate dependency:go-offline step (saves memory)
RUN mvn -B -ntp clean package -DskipTests -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl
RUN addgroup -S sanad && adduser -S sanad -G sanad

WORKDIR /app

COPY --from=builder /app/target/sanad-platform-*.jar app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=prod

USER sanad

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
