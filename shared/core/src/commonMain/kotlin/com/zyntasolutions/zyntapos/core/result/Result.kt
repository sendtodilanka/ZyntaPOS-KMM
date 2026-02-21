package com.zyntasolutions.zyntapos.core.result

/**
 * ZyntaPOS universal result wrapper for all use-case / repository return values.
 *
 * Represents three mutually-exclusive states:
 * - [Success] — operation succeeded with a typed payload
 * - [Error]   — operation failed with a [ZyntaException]
 * - [Loading] — operation is in progress (used for UI state projection)
 *
 * ### Usage
 * ```kotlin
 * val result: Result<Product> = productRepository.getById(id)
 * result
 *     .onSuccess { product -> display(product) }
 *     .onError   { ex -> showError(ex.message) }
 * ```
 */
sealed class Result<out T> {

    /** Successful outcome carrying [data] of type [T]. */
    data class Success<out T>(val data: T) : Result<T>()

    /**
     * Failed outcome carrying a [ZyntaException] describing what went wrong.
     * Optionally wraps the [cause] throwable for debugging.
     */
    data class Error(
        val exception: ZyntaException,
        val cause: Throwable? = null,
    ) : Result<Nothing>()

    /** In-progress state; no data or error yet. Useful for [StateFlow]-backed UI states. */
    data object Loading : Result<Nothing>()

    // ── Computed helpers ──────────────────────────────────────────────────────

    /** `true` only when this is a [Success]. */
    val isSuccess: Boolean get() = this is Success

    /** `true` only when this is an [Error]. */
    val isError: Boolean get() = this is Error

    /** `true` only when this is [Loading]. */
    val isLoading: Boolean get() = this is Loading
}

// ── Extension functions ───────────────────────────────────────────────────────

/**
 * Executes [block] if this is a [Result.Success], passing the unwrapped data.
 * Returns `this` for chaining.
 */
inline fun <T> Result<T>.onSuccess(block: (T) -> Unit): Result<T> {
    if (this is Result.Success) block(data)
    return this
}

/**
 * Executes [block] if this is a [Result.Error], passing the [ZyntaException].
 * Returns `this` for chaining.
 */
inline fun <T> Result<T>.onError(block: (ZyntaException) -> Unit): Result<T> {
    if (this is Result.Error) block(exception)
    return this
}

/**
 * Executes [block] if this is [Result.Loading].
 * Returns `this` for chaining.
 */
inline fun <T> Result<T>.onLoading(block: () -> Unit): Result<T> {
    if (this is Result.Loading) block()
    return this
}

/**
 * Transforms a [Result.Success] value via [transform].
 * [Result.Error] and [Result.Loading] are passed through unchanged.
 */
inline fun <T, R> Result<T>.mapSuccess(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Error   -> this
    is Result.Loading -> Result.Loading
}

/**
 * Returns the unwrapped data if [Result.Success], or `null` otherwise.
 */
fun <T> Result<T>.getOrNull(): T? = (this as? Result.Success)?.data

/**
 * Returns the unwrapped data if [Result.Success], or [default] otherwise.
 */
fun <T> Result<T>.getOrDefault(default: T): T = (this as? Result.Success)?.data ?: default

/**
 * Throws the wrapped exception if this is a [Result.Error]; otherwise returns the data.
 *
 * @throws ZyntaException when called on [Result.Error]
 * @throws IllegalStateException when called on [Result.Loading]
 */
fun <T> Result<T>.getOrThrow(): T = when (this) {
    is Result.Success -> data
    is Result.Error   -> throw exception
    is Result.Loading -> throw IllegalStateException("Result is still Loading")
}
