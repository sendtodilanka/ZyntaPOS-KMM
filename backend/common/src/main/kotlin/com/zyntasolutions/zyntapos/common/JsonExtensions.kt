package com.zyntasolutions.zyntapos.common

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Returns the string value for [key], or null if absent or JSON null. */
fun JsonObject.str(key: String): String? =
    this[key]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }

/** Returns the double value for [key], or 0.0 if absent or non-numeric. */
fun JsonObject.dbl(key: String): Double =
    this[key]?.jsonPrimitive?.doubleOrNull ?: 0.0

/** Returns the int value for [key], or 0 if absent or non-integer. */
fun JsonObject.int(key: String): Int =
    this[key]?.jsonPrimitive?.intOrNull ?: 0

/** Returns the boolean value for [key], or [default] if absent. */
fun JsonObject.bool(key: String, default: Boolean = true): Boolean =
    this[key]?.jsonPrimitive?.booleanOrNull ?: default
