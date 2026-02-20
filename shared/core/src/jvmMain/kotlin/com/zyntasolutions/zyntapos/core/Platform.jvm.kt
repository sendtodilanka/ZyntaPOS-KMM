package com.zyntasolutions.zyntapos.core

/**
 * Desktop JVM [Platform] actual — reports the running JVM version.
 */
class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()
