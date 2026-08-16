# syntax=docker/dockerfile:1

# --- Build stage: compile the Spring Boot app with Gradle -------------------
FROM gradle:9.5.1-jdk21 AS build
WORKDIR /workspace

# Copy the whole repo; the build context is the repo root.
COPY . .

# Compile and package the boot jar (skips tests — CI runs them).
RUN gradle --no-daemon bootJar -x test

# --- Runtime stage: minimal JRE image ----------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Non-root user for the container.
RUN groupadd --system nexxauth && useradd --system --gid nexxauth --no-create-home nexxauth \
    && mkdir -p /var/log/nexxauth && chown nexxauth:nexxauth /var/log/nexxauth

COPY --from=build /workspace/build/libs/*.jar /app/nexxauth.jar

# Actuator runs on 8081 (management port); the API on 8080. Bind the management
# port to 0.0.0.0 so container health checks can reach it.
EXPOSE 8080 8081

USER nexxauth

# Runtime env is supplied by the orchestrator (compose/k8s): datasource, JWT
# secret, rate-limit store, etc. The prod profile switches to ECS JSON logging.
ENV SPRING_PROFILES_ACTIVE=prod \
    SPRING_DOCKER_COMPOSE_ENABLED=false \
    MANAGEMENT_SERVER_ADDRESS=0.0.0.0

ENTRYPOINT ["java", "-jar", "/app/nexxauth.jar"]
