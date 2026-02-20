package com.zyntasolutions.zyntapos.core

/**
 * Represents the current host platform.
 *
 * Implemented via `expect/actual` in each platform source set:
 * - androidMain: [AndroidPlatform] — reports Android API level
 * - jvmMain:     [JVMPlatform]     — reports JVM version
 */
interface Platform {
    val name: String
}

/**
 * Returns the current [Platform] implementation for the running target.
 * Resolved at compile time via the KMP `expect/actual` mechanism.
 */
expect fun getPlatform(): Platform
