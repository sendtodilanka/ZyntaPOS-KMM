package com.zyntasolutions.zyntapos.seed

internal actual fun loadSeedJson(path: String): String =
    (object {}.javaClass.classLoader ?: Thread.currentThread().contextClassLoader)
        ?.getResourceAsStream(path)
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: error("Seed resource not found on classpath: $path")
