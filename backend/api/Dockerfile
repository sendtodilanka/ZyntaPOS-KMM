# ─────────────────────────────────────────────────────────────────────────────
# ZyntaPOS API — Multi-stage Dockerfile
# Build context: backend/ (parent directory, so common/ module is accessible)
# Stage 1: Build fat JAR with Gradle
# Stage 2: Minimal JRE 21 Alpine runtime (non-root, read-only FS)
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

# Mirror the local directory layout: /build/api/ and /build/common/
# so settings.gradle.kts file("../common") resolves correctly
WORKDIR /build/api

# Copy gradle files first for layer caching
COPY api/build.gradle.kts api/settings.gradle.kts ./
COPY api/gradlew .
COPY api/gradle ./gradle

# Copy common module at sibling level (../common from WORKDIR)
COPY common /build/common

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon --quiet || true

# Copy source and build
COPY api/src ./src
RUN ./gradlew shadowJar --no-daemon --quiet

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Install curl for healthcheck (wget not available in Alpine JRE)
RUN apk add --no-cache curl

# Create non-root user
RUN addgroup -S zyntapos && adduser -S zyntapos -G zyntapos

WORKDIR /app

# Copy fat JAR from builder
COPY --from=builder /build/api/build/libs/zyntapos-api.jar app.jar

# Set ownership
RUN chown -R zyntapos:zyntapos /app

USER zyntapos

EXPOSE 8080

# Security: block Java deserialization + standard JVM flags (exec form — no shell injection)
ENTRYPOINT ["java", \
    "-Djdk.serialFilter=!*", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Dfile.encoding=UTF-8", \
    "-jar", "app.jar"]

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1
