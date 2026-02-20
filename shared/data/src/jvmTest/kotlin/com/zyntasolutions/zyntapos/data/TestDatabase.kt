package com.zyntasolutions.zyntapos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import java.util.Properties

/**
 * ZentaPOS — In-Memory SQLite database factory for JVM integration tests.
 *
 * Uses [JdbcSqliteDriver.IN_MEMORY] so every call returns a fresh, isolated
 * database instance — no file I/O, no encryption overhead, no cross-test pollution.
 *
 * Usage:
 * ```kotlin
 * val db = createTestDatabase()
 * val repo = ProductRepositoryImpl(db, syncEnqueuer)
 * ```
 *
 * Schema is created via [ZyntaDatabase.Schema.create], mirroring exactly what
 * [DatabaseMigrations.migrateIfNeeded] does on first app launch.
 */
fun createTestDatabase(): ZyntaDatabase {
    val driver = JdbcSqliteDriver(
        url        = JdbcSqliteDriver.IN_MEMORY,
        properties = Properties().apply { put("foreign_keys", "true") },
    )
    // Create full schema from compiled SQLDelight definitions
    ZyntaDatabase.Schema.create(driver)
    return ZyntaDatabase(driver)
}
