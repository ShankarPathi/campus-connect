# syntax=docker/dockerfile:1
# Multi-stage build for recruiter-service (Story 10.1). Build context MUST be the repo root:
#   docker build -f recruiter-service/Dockerfile .

# ---- Stage 1: build (JDK 21 + Maven) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY common-lib/pom.xml common-lib/
COPY api-gateway/pom.xml api-gateway/
COPY student-service/pom.xml student-service/
COPY recruiter-service/pom.xml recruiter-service/
COPY admin-service/pom.xml admin-service/
RUN mvn -B -pl recruiter-service -am -DskipTests dependency:go-offline

COPY common-lib/src common-lib/src
COPY recruiter-service/src recruiter-service/src
RUN mvn -B -pl recruiter-service -am -DskipTests clean package

# ---- Stage 2: runtime (JRE 21 only) ----
FROM eclipse-temurin:21-jre-jammy AS runtime
# hadolint ignore=DL3008
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/recruiter-service/target/recruiter-service-0.0.1-SNAPSHOT.jar app.jar
USER app

ENV JAVA_OPTS="-Xmx384m -XX:+UseSerialGC"
EXPOSE 8082
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8082/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
