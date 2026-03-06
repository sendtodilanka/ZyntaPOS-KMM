package com.zyntasolutions.zyntapos.common.validation

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall

/**
 * Collects validation errors for a single request. Use in every POST/PUT route handler.
 *
 * Returns HTTP 422 with the error list if [validate] returns non-empty.
 *
 * Usage:
 * ```kotlin
 * val errors = ValidationScope().apply {
 *     requireNotBlank("name", request.name)
 *     requireLength("name", request.name, 1, 100)
 *     requirePositive("price", request.price)
 *     requireUUID("productId", request.productId)
 * }.validate()
 * if (errors.isNotEmpty()) {
 *     call.respond(HttpStatusCode.UnprocessableEntity, mapOf("errors" to errors))
 *     return@post
 * }
 * ```
 */
class ValidationScope {
    private val errors = mutableListOf<String>()

    fun requireNotBlank(field: String, value: String?) {
        if (value.isNullOrBlank()) errors += "$field must not be blank"
    }

    fun requireLength(field: String, value: String?, min: Int, max: Int) {
        val len = value?.length ?: 0
        if (len < min || len > max) errors += "$field must be between $min and $max characters"
    }

    fun requireMaxLength(field: String, value: String?, max: Int) {
        if (value != null && value.length > max) errors += "$field must be at most $max characters"
    }

    fun requirePositive(field: String, value: Double) {
        if (value <= 0.0) errors += "$field must be positive"
    }

    fun requireNonNegative(field: String, value: Int) {
        if (value < 0) errors += "$field must be non-negative"
    }

    fun requireNonNegative(field: String, value: Long) {
        if (value < 0L) errors += "$field must be non-negative"
    }

    fun requireNonNegative(field: String, value: Double) {
        if (value < 0.0) errors += "$field must be non-negative"
    }

    fun requireUUID(field: String, value: String?) {
        val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        if (value.isNullOrBlank() || !uuidRegex.matches(value)) {
            errors += "$field must be a valid UUID"
        }
    }

    fun requireInRange(field: String, value: Double, min: Double, max: Double) {
        if (value < min || value > max) errors += "$field must be between $min and $max"
    }

    fun requireInRange(field: String, value: Int, min: Int, max: Int) {
        if (value < min || value > max) errors += "$field must be between $min and $max"
    }

    fun requirePattern(field: String, value: String?, pattern: Regex, message: String? = null) {
        if (value == null || !pattern.matches(value)) {
            errors += message ?: "$field has invalid format"
        }
    }

    fun requireNotEmpty(field: String, collection: Collection<*>?) {
        if (collection.isNullOrEmpty()) errors += "$field must not be empty"
    }

    fun requireMaxSize(field: String, collection: Collection<*>?, max: Int) {
        if (collection != null && collection.size > max) errors += "$field must contain at most $max items"
    }

    fun validate(): List<String> = errors.toList()
}

/**
 * Validates the request using [block] and responds with HTTP 422 if errors exist.
 *
 * @return `true` if validation passed (no errors), `false` if 422 was sent.
 */
suspend fun RoutingCall.validateOr422(block: ValidationScope.() -> Unit): Boolean {
    val errors = ValidationScope().apply(block).validate()
    if (errors.isNotEmpty()) {
        respond(HttpStatusCode.UnprocessableEntity, mapOf("errors" to errors))
        return false
    }
    return true
}
