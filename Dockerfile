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

# Use the project's Gradle wrapper for reproducible builds. --chmod keeps the
# exec bit without a separate RUN layer.
COPY --chmod=+x gradlew .
COPY gradle gradle
COPY settings.gradle build.gradle ./

# Resolve dependencies in a cacheable layer. The BuildKit cache mount keeps the
# Gradle caches out of the image layers and warm across local rebuilds.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon

# Copy application source only after dependency resolution.
COPY src src

# CI runs tests separately. Produce the Boot jar and split it into its layers
# so the runtime stage can copy each as a separate image layer (Boot 4 jarmode
# emits application/nexxauth.jar + dependencies/lib/ + empty loader layers).
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon -x test \
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
    MANAGEMENT_SERVER_ADDRESS=0.0.0.0

# Health probe for any orchestrator; compose's depends_on: service_healthy
# uses this image healthcheck too (compose overrides with its own if defined).
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=12 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

# Container-aware heap (75% of the cgroup limit) and exit on OOM so an
# orchestrator restarts a wedged JVM instead of serving degraded. The thin
# application jar resolves its dependencies from lib/ next to it.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "nexxauth.jar"]