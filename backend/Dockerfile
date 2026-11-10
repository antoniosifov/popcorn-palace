# syntax=docker/dockerfile:1

# --- Build stage: compile and package the application ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Resolve dependencies first so this layer is cached until pom.xml changes.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline

COPY src ./src
# Tests run in CI; the image build only packages the jar.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package -DskipTests

# --- Runtime stage: JRE only, non-root user ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/target/popcorn-palace-*.jar app.jar
USER app

# 8080: the API. 8081: health and metrics, for the stack only.
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
