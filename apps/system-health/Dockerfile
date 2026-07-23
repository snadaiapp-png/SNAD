FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./pom.xml
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B verify

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home sanad

COPY --from=build /workspace/target/sanad-system-health-0.1.0-SNAPSHOT.jar /app/application.jar

USER 10001
EXPOSE 8090
ENV PORT=8090

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD curl --fail --silent --show-error "http://127.0.0.1:${PORT}/api/v1/system-health/live" >/dev/null || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/application.jar"]
