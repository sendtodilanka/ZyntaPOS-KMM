package com.zyntasolutions.zyntapos.data.util

import kotlinx.serialization.json.Json

/**
 * Shared [Json] instance used by all repository `upsertFromSync` methods to decode
 * server-originated sync payloads.
 *
 * Configuration:
 * - [ignoreUnknownKeys] — tolerates new server-side fields without crashing older clients.
 * - [isLenient] — accepts non-strict JSON (unquoted keys, single quotes) from legacy payloads.
 *
 * Declaring this once avoids the ~40-byte overhead of creating a new [Json] configuration
 * object in every repository companion object.
 */
internal val SyncJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
