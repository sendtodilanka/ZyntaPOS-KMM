# ─────────────────────────────────────────────────────────────────────────────
# ZyntaPOS License Server — Multi-stage Dockerfile
# Build context: backend/ (parent directory, so common/ module is accessible)
# Stage 1: Build fat JAR
# Stage 2: Minimal JRE 21 Alpine runtime (non-root, read-only FS)
# ─────────────────────────────────────────────────────────────────────────────

FROM eclipse-temurin:21-jdk-alpine AS builder

# Mirror the local directory layout: /build/license/ and /build/common/
# so settings.gradle.kts file("../common") resolves correctly
WORKDIR /build/license
COPY license/build.gradle.kts license/settings.gradle.kts ./
COPY license/gradlew .
COPY license/gradle ./gradle

# Copy common module at sibling level (../common from WORKDIR)
COPY common /build/common

RUN ./gradlew dependencies --no-daemon --quiet || true
COPY license/src ./src
RUN ./gradlew shadowJar --no-daemon --quiet

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN apk add --no-cache curl
RUN addgroup -S zyntapos && adduser -S zyntapos -G zyntapos
WORKDIR /app
COPY --from=builder /build/license/build/libs/zyntapos-license.jar app.jar
RUN chown -R zyntapos:zyntapos /app
USER zyntapos
EXPOSE 8083

ENTRYPOINT ["java", \
    "-Djdk.serialFilter=!*", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Dfile.encoding=UTF-8", \
    "-jar", "app.jar"]

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8083/health || exit 1
