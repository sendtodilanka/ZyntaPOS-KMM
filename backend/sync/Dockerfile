# ─────────────────────────────────────────────────────────────────────────────
# ZyntaPOS Sync Server — WebSocket real-time sync engine
# ─────────────────────────────────────────────────────────────────────────────

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon --quiet || true
COPY src ./src
RUN ./gradlew shadowJar --no-daemon --quiet

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S zyntapos && adduser -S zyntapos -G zyntapos
WORKDIR /app
COPY --from=builder /build/build/libs/zyntapos-sync.jar app.jar
RUN chown -R zyntapos:zyntapos /app
USER zyntapos
EXPOSE 8082

ENV JAVA_OPTS="-Djdk.serialFilter=!* \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+ExitOnOutOfMemoryError \
    -Dfile.encoding=UTF-8"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8082/health || exit 1
