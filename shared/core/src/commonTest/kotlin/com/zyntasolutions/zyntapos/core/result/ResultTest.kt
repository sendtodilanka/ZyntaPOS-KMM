package com.zyntasolutions.zyntapos.core.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [Result] sealed class and extension functions.
 * Target: ≥ 90% coverage of result/Result.kt
 */
class ResultTest {

    // ── Construction ──────────────────────────────────────────────────────────

    @Test
    fun `Success holds correct data`() {
        val result = Result.Success("hello")
        assertEquals("hello", result.data)
    }

    @Test
    fun `Error holds correct exception`() {
        val ex = NetworkException("timeout")
        val result = Result.Error(ex)
        assertEquals(ex, result.exception)
    }

    @Test
    fun `Loading is a singleton data object`() {
        assertTrue(Result.Loading === Result.Loading)
    }

    // ── isSuccess / isError / isLoading ───────────────────────────────────────

    @Test
    fun `isSuccess true only for Success`() {
        assertTrue(Result.Success(1).isSuccess)
        assertFalse(Result.Error(DatabaseException("err")).isSuccess)
        assertFalse(Result.Loading.isSuccess)
    }

    @Test
    fun `isError true only for Error`() {
        assertFalse(Result.Success(1).isError)
        assertTrue(Result.Error(DatabaseException("err")).isError)
        assertFalse(Result.Loading.isError)
    }

    @Test
    fun `isLoading true only for Loading`() {
        assertFalse(Result.Success(1).isLoading)
        assertFalse(Result.Error(DatabaseException("err")).isLoading)
        assertTrue(Result.Loading.isLoading)
    }

    // ── onSuccess ─────────────────────────────────────────────────────────────

    @Test
    fun `onSuccess executes block for Success`() {
        var called = false
        Result.Success(42).onSuccess { called = true }
        assertTrue(called)
    }

    @Test
    fun `onSuccess does not execute block for Error`() {
        var called = false
        Result.Error(NetworkException("err")).onSuccess { called = true }
        assertFalse(called)
    }

    @Test
    fun `onSuccess returns this for chaining`() {
        val result = Result.Success("x")
        val returned = result.onSuccess { }
        assertTrue(returned === result)
    }

    // ── onError ───────────────────────────────────────────────────────────────

    @Test
    fun `onError executes block for Error`() {
        var called = false
        Result.Error(NetworkException("err")).onError { called = true }
        assertTrue(called)
    }

    @Test
    fun `onError does not execute block for Success`() {
        var called = false
        Result.Success("ok").onError { called = true }
        assertFalse(called)
    }

    // ── mapSuccess ────────────────────────────────────────────────────────────

    @Test
    fun `mapSuccess transforms Success value`() {
        val result = Result.Success(5).mapSuccess { it * 2 }
        assertEquals(Result.Success(10), result)
    }

    @Test
    fun `mapSuccess passes through Error unchanged`() {
        val ex = ValidationException("bad input")
        val result: Result<Int> = Result.Error(ex)
        val mapped = result.mapSuccess { it * 2 }
        assertEquals(Result.Error(ex), mapped)
    }

    @Test
    fun `mapSuccess passes through Loading unchanged`() {
        val result: Result<Int> = Result.Loading
        val mapped = result.mapSuccess { it * 2 }
        assertEquals(Result.Loading, mapped)
    }

    // ── getOrNull / getOrDefault ──────────────────────────────────────────────

    @Test
    fun `getOrNull returns data for Success`() {
        assertEquals("value", Result.Success("value").getOrNull())
    }

    @Test
    fun `getOrNull returns null for Error`() {
        assertNull(Result.Error(NetworkException("err")).getOrNull())
    }

    @Test
    fun `getOrNull returns null for Loading`() {
        assertNull(Result.Loading.getOrNull())
    }

    @Test
    fun `getOrDefault returns data for Success`() {
        assertEquals(42, Result.Success(42).getOrDefault(0))
    }

    @Test
    fun `getOrDefault returns default for Error`() {
        assertEquals(0, Result.Error(NetworkException("err")).getOrDefault(0))
    }

    @Test
    fun `getOrDefault returns default for Loading`() {
        assertEquals(99, Result.Loading.getOrDefault(99))
    }

    // ── getOrThrow ────────────────────────────────────────────────────────────

    @Test
    fun `getOrThrow returns data for Success`() {
        assertEquals("data", Result.Success("data").getOrThrow())
    }

    @Test
    fun `getOrThrow throws ZyntaException for Error`() {
        val ex = AuthException("no session")
        val result = Result.Error(ex)
        var caught: Throwable? = null
        try { result.getOrThrow() } catch (e: ZyntaException) { caught = e }
        assertEquals(ex, caught)
    }

    @Test
    fun `getOrThrow throws IllegalStateException for Loading`() {
        var caught: Throwable? = null
        try { Result.Loading.getOrThrow() } catch (e: IllegalStateException) { caught = e }
        assertTrue(caught is IllegalStateException)
    }
}
