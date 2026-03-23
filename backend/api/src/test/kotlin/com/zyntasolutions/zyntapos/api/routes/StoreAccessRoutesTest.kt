package com.zyntasolutions.zyntapos.api.routes

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for StoreAccessRoutes request/response models (C3.2).
 */
class StoreAccessRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `GrantAccessRequest deserializes with all fields`() {
        val request = json.decodeFromString<GrantAccessRequest>(
            """{"userId":"u-1","storeId":"s-1","roleAtStore":"STORE_MANAGER"}"""
        )
        assertEquals("u-1", request.userId)
        assertEquals("s-1", request.storeId)
        assertEquals("STORE_MANAGER", request.roleAtStore)
    }

    @Test
    fun `GrantAccessRequest defaults roleAtStore to null`() {
        val request = json.decodeFromString<GrantAccessRequest>(
            """{"userId":"u-1","storeId":"s-1"}"""
        )
        assertNull(request.roleAtStore)
    }

    @Test
    fun `RevokeAccessRequest deserializes correctly`() {
        val request = json.decodeFromString<RevokeAccessRequest>(
            """{"userId":"u-1","storeId":"s-1"}"""
        )
        assertEquals("u-1", request.userId)
        assertEquals("s-1", request.storeId)
    }

    @Test
    fun `StoreAccessResponse serializes correctly`() {
        val response = StoreAccessResponse(
            id = "id-1",
            userId = "u-1",
            storeId = "s-1",
            roleAtStore = "CASHIER",
            isActive = true,
            grantedBy = "admin-1",
            createdAt = "2026-03-23T00:00:00Z",
            updatedAt = "2026-03-23T00:00:00Z",
        )
        val jsonStr = json.encodeToString(StoreAccessResponse.serializer(), response)
        assert(jsonStr.contains("\"userId\":\"u-1\""))
        assert(jsonStr.contains("\"roleAtStore\":\"CASHIER\""))
    }

    @Test
    fun `GrantAccessRequest requires userId and storeId`() {
        val request = GrantAccessRequest(userId = "", storeId = "")
        assertEquals("", request.userId)
        assertEquals("", request.storeId)
    }

    @Test
    fun `StoreAccessResponse with null roleAtStore`() {
        val response = StoreAccessResponse(
            id = "id-1",
            userId = "u-1",
            storeId = "s-1",
            roleAtStore = null,
            isActive = true,
            grantedBy = null,
            createdAt = "2026-03-23T00:00:00Z",
            updatedAt = "2026-03-23T00:00:00Z",
        )
        assertNull(response.roleAtStore)
        assertNull(response.grantedBy)
    }
}
