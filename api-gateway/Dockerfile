# syntax=docker/dockerfile:1
# Multi-stage build for api-gateway (Story 10.1). Build context MUST be the repo root
# (the reactor needs common-lib + every module pom):  docker build -f api-gateway/Dockerfile .

# ---- Stage 1: build (JDK 21 + Maven) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy the reactor poms first so dependency resolution layers cache across source-only changes.
# All module poms are required — the root pom declares them, so Maven needs them to parse the reactor.
COPY pom.xml ./
COPY common-lib/pom.xml common-lib/
COPY api-gateway/pom.xml api-gateway/
COPY student-service/pom.xml student-service/
COPY recruiter-service/pom.xml recruiter-service/
COPY admin-service/pom.xml admin-service/
RUN mvn -B -pl api-gateway -am -DskipTests dependency:go-offline

# Copy only the sources this service needs (gateway + its common-lib dependency) and build.
COPY common-lib/src common-lib/src
COPY api-gateway/src api-gateway/src
RUN mvn -B -pl api-gateway -am -DskipTests clean package

# ---- Stage 2: runtime (JRE 21 only) ----
FROM eclipse-temurin:21-jre-jammy AS runtime
# curl is used by the container HEALTHCHECK against the actuator probe.
# hadolint ignore=DL3008
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar app.jar
USER app

# Tuned for the Oracle Always-Free ARM VM (architecture §14): small heap + SerialGC. Overridable.
ENV JAVA_OPTS="-Xmx384m -XX:+UseSerialGC"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
