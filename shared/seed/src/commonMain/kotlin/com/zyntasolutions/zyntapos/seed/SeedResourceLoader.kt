package com.zyntasolutions.zyntapos.seed

/**
 * Loads a raw seed JSON file from the module's classpath resources.
 *
 * @param path Resource path relative to the classpath root (e.g. "seeds/demo.json")
 * @return Raw JSON string
 * @throws IllegalStateException if the resource cannot be found on the classpath
 */
internal expect fun loadSeedJson(path: String): String
