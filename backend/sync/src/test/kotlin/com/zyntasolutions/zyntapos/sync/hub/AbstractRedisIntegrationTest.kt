package com.zyntasolutions.zyntapos.sync.hub

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import org.testcontainers.containers.GenericContainer

/**
 * Base class for Redis integration tests.
 *
 * Starts a singleton `redis:7-alpine` container shared across all subclasses
 * using a static companion object (Testcontainers singleton pattern without JUnit 5 lifecycle).
 * The container is started lazily on first access and stopped by a JVM shutdown hook.
 */
abstract class AbstractRedisIntegrationTest {

    companion object {
        private val redis: GenericContainer<*> by lazy {
            GenericContainer("redis:7-alpine")
                .withExposedPorts(6379)
                .also { it.start() }
        }

        private val sharedClient: RedisClient by lazy {
            RedisClient.create(redisUrl())
        }

        val redisConnection: StatefulRedisConnection<String, String> by lazy {
            sharedClient.connect()
        }

        fun redisUrl(): String = "redis://localhost:${redis.getMappedPort(6379)}"

        init {
            // Ensure containers are stopped when JVM exits
            Runtime.getRuntime().addShutdownHook(Thread {
                try { redisConnection.close() } catch (_: Exception) {}
                try { sharedClient.shutdown() } catch (_: Exception) {}
                try { redis.stop() } catch (_: Exception) {}
            })
        }
    }
}
