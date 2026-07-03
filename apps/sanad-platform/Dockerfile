# ============================================================
# SANAD Platform - Runtime Dockerfile (no Maven build)
# ============================================================
# The JAR is built by GitHub Actions and pushed to GHCR.
# Render pulls the pre-built image — no Maven build needed.
# ============================================================

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl
RUN addgroup -S sanad && adduser -S sanad -G sanad

WORKDIR /app

# The JAR is COPY'd during GitHub Actions Docker build
COPY target/sanad-platform-0.1.0-SNAPSHOT.jar app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=prod

USER sanad

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
