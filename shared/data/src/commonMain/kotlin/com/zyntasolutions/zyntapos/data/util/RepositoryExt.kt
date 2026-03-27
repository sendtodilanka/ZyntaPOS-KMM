package com.zyntasolutions.zyntapos.data.util

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes a SQLite/SQLDelight operation on [Dispatchers.IO] and wraps the result in [Result].
 *
 * - Any [ValidationException] thrown inside [block] is preserved as-is (it carries a typed
 *   domain error that must not be re-wrapped as a generic [DatabaseException]).
 * - All other throwables are wrapped in [DatabaseException] with the supplied [operation] label.
 *
 * Usage:
 * ```kotlin
 * override suspend fun getById(id: String): Result<Product> = dbCall("getById") {
 *     q.getProductById(id).executeAsOneOrNull()
 *         ?: throw DatabaseException("Product not found: $id", operation = "getById")
 * }
 * ```
 */
internal suspend inline fun <T> dbCall(
    operation: String = "db",
    crossinline block: () -> T,
): Result<T> = withContext(Dispatchers.IO) {
    runCatching { block() }.fold(
        onSuccess = { Result.Success(it) },
        onFailure = { t ->
            when (t) {
                is ValidationException -> Result.Error(t)
                is DatabaseException   -> Result.Error(t)
                else -> Result.Error(
                    DatabaseException(t.message ?: "$operation failed", operation = operation, cause = t)
                )
            }
        },
    )
}
