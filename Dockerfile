# syntax=docker/dockerfile:1.7

# ---- Base image args (must be declared before the first FROM) ----------------
# Overridable via build args so CI can pin to digest-pinned tags for fully
# reproducible builds without editing this file:
#   docker build --build-arg BUILD_JAVA_IMAGE=eclipse-temurin:21-jdk@sha256:...
ARG BUILD_JAVA_IMAGE=eclipse-temurin:21-jdk
ARG RUNTIME_JAVA_IMAGE=eclipse-temurin:21-jre

# ---- Build stage ------------------------------------------------------------
FROM ${BUILD_JAVA_IMAGE} AS build

WORKDIR /workspace

# Use the project's Gradle wrapper for reproducible builds. Only classic-builder
# syntax is used here (no --chmod, no cache mounts) so the same Dockerfile builds
# on Heroku, whose builders do not run BuildKit.
COPY gradlew .
RUN chmod +x gradlew
COPY gradle gradle
COPY settings.gradle build.gradle ./

# Resolve dependencies in a cacheable layer: they are downloaded before the
# source is copied, so source-only changes reuse this layer.
RUN ./gradlew dependencies --no-daemon

# Copy application source only after dependency resolution.
COPY src src

# CI runs tests separately. Produce the Boot jar and split it into its layers
# so the runtime stage can copy each as a separate image layer (Boot 4 jarmode
# emits application/nexxauth.jar + dependencies/lib/ + empty loader layers).
RUN ./gradlew bootJar --no-daemon -x test \
    && java -Djarmode=tools -jar build/libs/nexxauth.jar extract --layers --destination extracted

# ---- Runtime stage ----------------------------------------------------------
FROM ${RUNTIME_JAVA_IMAGE}

WORKDIR /app

# curl for the healthcheck; non-root user; log directory owned by that user so
# the prod profile can write /var/log/nexxauth/nexxauth.log (also the compose
# app-logs volume mount point).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system nexxauth \
    && useradd --system --gid nexxauth --no-create-home nexxauth \
    && mkdir -p /var/log/nexxauth \
    && chown -R nexxauth:nexxauth /var/log/nexxauth

# Boot layers, least to most frequently changing, so deploy pushes only
# re-transfer the application layer. Boot 4 extracts to a thin
# application/nexxauth.jar whose manifest Class-Path resolves the dependency
# jars from lib/ next to it.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

EXPOSE 8080

USER nexxauth

ENV SPRING_PROFILES_ACTIVE=prod \
    SPRING_DOCKER_COMPOSE_ENABLED=false \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50"

# Heap sizing lives in JAVA_TOOL_OPTIONS rather than the command line so a
# deployment can retune it per environment (heroku config:set JAVA_TOOL_OPTIONS)
# without a rebuild. 50%, not the usual 75%: MaxRAMPercentage sizes the heap
# against the container limit, but total RSS is heap + ~210MB of non-heap
# (metaspace for Boot/Hibernate/Jackson/Netty, JIT code cache, thread stacks,
# G1 card table/remembered sets, Tomcat NIO direct buffers). At 75% on a 512MB
# dyno that measured 602MB RSS — 117% of quota, which is Heroku R14 and leaves
# the JVM one allocation away from -XX:+ExitOnOutOfMemoryError killing it.

# Health probe for any orchestrator; compose's depends_on: service_healthy
# uses this image healthcheck too (compose overrides with its own if defined).
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=12 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

# Exit on OOM so an orchestrator restarts a wedged JVM instead of serving
# degraded. Kept on the command line (not in JAVA_TOOL_OPTIONS) so it cannot be
# lost by an environment override.
ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "-jar", "nexxauth.jar"]