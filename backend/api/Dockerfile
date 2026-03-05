# ─────────────────────────────────────────────────────────────────────────────
# ZyntaPOS API — Multi-stage Dockerfile
# Stage 1: Build fat JAR with Gradle
# Stage 2: Minimal JRE 21 Alpine runtime (non-root, read-only FS)
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copy gradle files first for layer caching
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradlew .
COPY gradle ./gradle

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon --quiet || true

# Copy source and build
COPY src ./src
RUN ./gradlew shadowJar --no-daemon --quiet

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Create non-root user
RUN addgroup -S zyntapos && adduser -S zyntapos -G zyntapos

WORKDIR /app

# Copy fat JAR from builder
COPY --from=builder /build/build/libs/zyntapos-api.jar app.jar

# Set ownership
RUN chown -R zyntapos:zyntapos /app

USER zyntapos

EXPOSE 8080

# Security: block Java deserialization + standard JVM flags
ENV JAVA_OPTS="-Djdk.serialFilter=!* \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+ExitOnOutOfMemoryError \
    -Dfile.encoding=UTF-8"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/health || exit 1
