package com.zyntasolutions.zyntapos.sync.hub

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Base class for Redis integration tests.
 *
 * Starts a singleton `redis:7-alpine` container shared across all subclasses
 * in the same JVM (Testcontainers singleton pattern).  Each test is responsible
 * for flushing state as needed.
 */
@Testcontainers
abstract class AbstractRedisIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)

        private lateinit var sharedClient: RedisClient
        lateinit var redisConnection: StatefulRedisConnection<String, String>
            private set

        fun redisUrl(): String = "redis://localhost:${redis.getMappedPort(6379)}"

        @BeforeAll
        @JvmStatic
        fun connectRedis() {
            sharedClient = RedisClient.create(redisUrl())
            redisConnection = sharedClient.connect()
        }

        @AfterAll
        @JvmStatic
        fun disconnectRedis() {
            if (::redisConnection.isInitialized) redisConnection.close()
            if (::sharedClient.isInitialized) sharedClient.shutdown()
        }
    }
}
