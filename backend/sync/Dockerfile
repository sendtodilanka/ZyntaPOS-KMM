# ─────────────────────────────────────────────────────────────────────────────
# ZyntaPOS Sync Server — WebSocket real-time sync engine
# Build context: backend/ (parent directory for consistency with api/license)
# ─────────────────────────────────────────────────────────────────────────────

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY sync/build.gradle.kts sync/settings.gradle.kts ./
COPY sync/gradlew .
COPY sync/gradle ./gradle
RUN ./gradlew dependencies --no-daemon --quiet || true
COPY sync/src ./src
RUN ./gradlew shadowJar --no-daemon --quiet

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN apk upgrade --no-cache && apk add --no-cache curl
RUN addgroup -S zyntapos && adduser -S zyntapos -G zyntapos
WORKDIR /app
COPY --from=builder /build/build/libs/zyntapos-sync.jar app.jar
RUN chown -R zyntapos:zyntapos /app
USER zyntapos
EXPOSE 8082

ENTRYPOINT ["java", \
    "-Djdk.serialFilter=!*", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Dfile.encoding=UTF-8", \
    "-jar", "app.jar"]

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8082/health || exit 1
