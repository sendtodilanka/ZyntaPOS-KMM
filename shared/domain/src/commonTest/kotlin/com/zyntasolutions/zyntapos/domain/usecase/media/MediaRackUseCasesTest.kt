package com.zyntasolutions.zyntapos.domain.usecase.media

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeMediaRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeWarehouseRackRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildMediaFile
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildWarehouseRack
import com.zyntasolutions.zyntapos.domain.usecase.rack.DeleteWarehouseRackUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.SaveWarehouseRackUseCase
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for media and warehouse rack use cases.
 *
 * Media use cases: [SaveMediaFileUseCase], [DeleteMediaFileUseCase], [GetMediaForEntityUseCase]
 * Rack use cases:  [SaveWarehouseRackUseCase], [DeleteWarehouseRackUseCase]
 *
 * Covers:
 * ── SaveMediaFileUseCase ──────────────────────────────────────────────────────
 * - Blank fileName → ValidationException (field=fileName, rule=REQUIRED)
 * - Blank filePath → ValidationException (field=filePath, rule=REQUIRED)
 * - fileSize <= 0 → ValidationException (field=fileSize, rule=MIN_VALUE)
 * - Blank uploadedBy → ValidationException (field=uploadedBy, rule=REQUIRED)
 * - Valid file → delegates to MediaRepository.insert
 * - Repository failure → propagates Result.Error
 *
 * ── DeleteMediaFileUseCase ────────────────────────────────────────────────────
 * - Delegates delete to MediaRepository.delete with correct id, deletedAt, updatedAt
 * - Non-existent id → propagates Result.Error
 * - Repository failure → propagates Result.Error
 *
 * ── GetMediaForEntityUseCase ──────────────────────────────────────────────────
 * - Returns list for entityId from Flow
 * - Only returns files for the requested entityType + entityId
 * - New inserts re-emit through the Flow
 *
 * ── SaveWarehouseRackUseCase ──────────────────────────────────────────────────
 * - Blank name → ValidationException (field=name, rule=REQUIRED)
 * - Blank warehouseId → ValidationException (field=warehouseId, rule=REQUIRED)
 * - Non-positive capacity → ValidationException (field=capacity, rule=MIN_VALUE)
 * - Null capacity (unlimited) → accepted
 * - Valid new rack (isUpdate=false) → delegates to repository.insert
 * - Valid updated rack (isUpdate=true) → delegates to repository.update
 *
 * ── DeleteWarehouseRackUseCase ────────────────────────────────────────────────
 * - Delegates delete with correct id, deletedAt, updatedAt
 * - Non-existent id → propagates Result.Error
 * - Repository failure → propagates Result.Error
 */
class MediaRackUseCasesTest {

    // ─── SaveMediaFileUseCase ─────────────────────────────────────────────────

    private fun makeSaveMediaUseCase(repo: FakeMediaRepository = FakeMediaRepository()) =
        SaveMediaFileUseCase(repo) to repo

    @Test
    fun `blank fileName returns REQUIRED ValidationException without DB write`() = runTest {
        val (useCase, repo) = makeSaveMediaUseCase()
        val file = buildMediaFile(fileName = "   ")

        val result = useCase(file)

        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("fileName", ex.field)
        assertEquals("REQUIRED", ex.rule)
        assertTrue(repo.files.isEmpty(), "No write should occur for blank fileName")
    }

    @Test
    fun `blank filePath returns REQUIRED ValidationException without DB write`() = runTest {
        val (useCase, repo) = makeSaveMediaUseCase()
        val file = buildMediaFile(filePath = "")

        val result = useCase(file)

        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("filePath", ex.field)
        assertEquals("REQUIRED", ex.rule)
        assertTrue(repo.files.isEmpty())
    }

    @Test
    fun `fileSize of zero returns MIN_VALUE ValidationException without DB write`() = runTest {
        val (useCase, repo) = makeSaveMediaUseCase()
        val file = buildMediaFile(fileSize = 0L)

        val result = useCase(file)

        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("fileSize", ex.field)
        assertEquals("MIN_VALUE", ex.rule)
        assertTrue(repo.files.isEmpty())
    }

    @Test
    fun `negative fileSize returns MIN_VALUE ValidationException`() = runTest {
        val (useCase, repo) = makeSaveMediaUseCase()
        val file = buildMediaFile(fileSize = -1L)

        val result = useCase(file)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertEquals("MIN_VALUE", (result.exception as ValidationException).rule)
    }

    @Test
    fun `blank uploadedBy returns REQUIRED ValidationException without DB write`() = runTest {
        val (useCase, repo) = makeSaveMediaUseCase()
        val file = buildMediaFile(uploadedBy = "   ")

        val result = useCase(file)

        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("uploadedBy", ex.field)
        assertEquals("REQUIRED", ex.rule)
        assertTrue(repo.files.isEmpty())
    }

    @Test
    fun `valid file delegates to repository insert and returns Success`() = runTest {
        val (useCase, repo) = makeSaveMediaUseCase()
        val file = buildMediaFile(id = "media-01")

        val result = useCase(file)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.files.size)
        assertEquals("media-01", repo.files.first().id)
    }

    @Test
    fun `repository failure on insert propagates as Result Error`() = runTest {
        val repo = FakeMediaRepository().also { it.shouldFail = true }
        val useCase = SaveMediaFileUseCase(repo)
        val file = buildMediaFile()

        val result = useCase(file)

        assertIs<Result.Error>(result)
    }

    // ─── DeleteMediaFileUseCase ───────────────────────────────────────────────

    @Test
    fun `delegates delete to repository with correct id and timestamps`() = runTest {
        val repo = FakeMediaRepository()
        repo.insert(buildMediaFile(id = "media-01"))
        val useCase = DeleteMediaFileUseCase(repo)
        val deletedAt = 1_700_000_000_000L
        val updatedAt = 1_700_000_001_000L

        val result = useCase("media-01", deletedAt, updatedAt)

        assertIs<Result.Success<Unit>>(result)
        assertEquals("media-01", repo.lastDeletedId)
        assertEquals(deletedAt, repo.lastDeletedAt)
        assertTrue(repo.files.isEmpty(), "Soft-deleted file should no longer appear in listing")
    }

    @Test
    fun `delete of non-existent id propagates Result Error`() = runTest {
        val repo = FakeMediaRepository()
        val useCase = DeleteMediaFileUseCase(repo)

        val result = useCase("non-existent", 1_700_000_000_000L, 1_700_000_000_000L)

        assertIs<Result.Error>(result)
    }

    @Test
    fun `repository failure on delete propagates as Result Error`() = runTest {
        val repo = FakeMediaRepository().also { it.shouldFail = true }
        val useCase = DeleteMediaFileUseCase(repo)

        val result = useCase("media-01", 1_700_000_000_000L, 1_700_000_000_000L)

        assertIs<Result.Error>(result)
    }

    // ─── GetMediaForEntityUseCase ─────────────────────────────────────────────

    @Test
    fun `returns list for entityType and entityId`() = runTest {
        val repo = FakeMediaRepository()
        repo.insert(buildMediaFile(id = "m1", entityType = "Product", entityId = "prod-01"))
        val useCase = GetMediaForEntityUseCase(repo)

        useCase("Product", "prod-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("m1", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `only returns files for requested entityType and entityId`() = runTest {
        val repo = FakeMediaRepository()
        repo.insert(buildMediaFile(id = "m1", entityType = "Product", entityId = "prod-01"))
        repo.insert(buildMediaFile(id = "m2", entityType = "Product", entityId = "prod-02"))
        repo.insert(buildMediaFile(id = "m3", entityType = "Category", entityId = "prod-01"))
        val useCase = GetMediaForEntityUseCase(repo)

        useCase("Product", "prod-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("m1", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `new insert re-emits through the Flow`() = runTest {
        val repo = FakeMediaRepository()
        val useCase = GetMediaForEntityUseCase(repo)

        useCase("Product", "prod-01").test {
            val empty = awaitItem()
            assertTrue(empty.isEmpty())

            repo.insert(buildMediaFile(id = "m-new", entityType = "Product", entityId = "prod-01"))

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("m-new", updated.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `entity with no media returns empty list`() = runTest {
        val repo = FakeMediaRepository()
        val useCase = GetMediaForEntityUseCase(repo)

        useCase("Product", "unknown-entity").test {
            val list = awaitItem()
            assertTrue(list.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── SaveWarehouseRackUseCase ─────────────────────────────────────────────

    private fun makeSaveRackUseCase(repo: FakeWarehouseRackRepository = FakeWarehouseRackRepository()) =
        SaveWarehouseRackUseCase(repo) to repo

    @Test
    fun `blank rack name returns REQUIRED ValidationException without DB write`() = runTest {
        val (useCase, repo) = makeSaveRackUseCase()
        val rack = buildWarehouseRack(name = "   ")

        val result = useCase(rack)

        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("name", ex.field)
        assertEquals("REQUIRED", ex.rule)
        assertFalse(repo.insertCalled, "No write should occur for blank rack name")
    }

    @Test
    fun `blank warehouseId returns REQUIRED ValidationException without DB write`() = runTest {
        val (useCase, repo) = makeSaveRackUseCase()
        val rack = buildWarehouseRack(warehouseId = "")

        val result = useCase(rack)

        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("warehouseId", ex.field)
        assertEquals("REQUIRED", ex.rule)
        assertFalse(repo.insertCalled)
    }

    @Test
    fun `zero capacity returns MIN_VALUE ValidationException`() = runTest {
        val (useCase, repo) = makeSaveRackUseCase()
        val rack = buildWarehouseRack(capacity = 0)

        val result = useCase(rack)

        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("capacity", ex.field)
        assertEquals("MIN_VALUE", ex.rule)
        assertFalse(repo.insertCalled)
    }

    @Test
    fun `negative capacity returns MIN_VALUE ValidationException`() = runTest {
        val (useCase, repo) = makeSaveRackUseCase()
        val rack = buildWarehouseRack(capacity = -5)

        val result = useCase(rack)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertEquals("MIN_VALUE", (result.exception as ValidationException).rule)
    }

    @Test
    fun `null capacity is accepted as unlimited`() = runTest {
        val (useCase, repo) = makeSaveRackUseCase()
        val rack = buildWarehouseRack(capacity = null)

        val result = useCase(rack)

        assertIs<Result.Success<Unit>>(result)
        assertTrue(repo.insertCalled)
    }

    @Test
    fun `valid new rack delegates to repository insert`() = runTest {
        val (useCase, repo) = makeSaveRackUseCase()
        val rack = buildWarehouseRack(id = "rack-01", name = "A1", capacity = 50)

        val result = useCase(rack, isUpdate = false)

        assertIs<Result.Success<Unit>>(result)
        assertTrue(repo.insertCalled, "insert should be called for new rack")
        assertFalse(repo.updateCalled, "update should NOT be called for new rack")
        assertEquals(1, repo.racks.size)
        assertEquals("A1", repo.racks.first().name)
    }

    @Test
    fun `valid update delegates to repository update not insert`() = runTest {
        val (useCase, repo) = makeSaveRackUseCase()
        val rack = buildWarehouseRack(id = "rack-01", name = "A1-Updated", capacity = 100)

        val result = useCase(rack, isUpdate = true)

        assertIs<Result.Success<Unit>>(result)
        assertTrue(repo.updateCalled, "update should be called for isUpdate=true")
        assertFalse(repo.insertCalled, "insert should NOT be called for isUpdate=true")
    }

    @Test
    fun `rack repository failure on insert propagates as Result Error`() = runTest {
        val repo = FakeWarehouseRackRepository().also { it.shouldFail = true }
        val useCase = SaveWarehouseRackUseCase(repo)
        val rack = buildWarehouseRack()

        val result = useCase(rack, isUpdate = false)

        assertIs<Result.Error>(result)
    }

    // ─── DeleteWarehouseRackUseCase ───────────────────────────────────────────

    @Test
    fun `rack delegates delete to repository with correct id and timestamps`() = runTest {
        val repo = FakeWarehouseRackRepository()
        repo.insert(buildWarehouseRack(id = "rack-01"))
        val useCase = DeleteWarehouseRackUseCase(repo)
        val deletedAt = 1_700_000_000_000L
        val updatedAt = 1_700_000_001_000L

        val result = useCase("rack-01", deletedAt, updatedAt)

        assertIs<Result.Success<Unit>>(result)
        assertEquals("rack-01", repo.lastDeletedId)
        assertEquals(deletedAt, repo.lastDeletedAt)
        assertTrue(repo.racks.isEmpty(), "Deleted rack should no longer appear in listing")
    }

    @Test
    fun `rack delete of non-existent id propagates Result Error`() = runTest {
        val repo = FakeWarehouseRackRepository()
        val useCase = DeleteWarehouseRackUseCase(repo)

        val result = useCase("non-existent-rack", 1_700_000_000_000L, 1_700_000_000_000L)

        assertIs<Result.Error>(result)
    }

    @Test
    fun `rack repository failure on delete propagates as Result Error`() = runTest {
        val repo = FakeWarehouseRackRepository().also { it.shouldFail = true }
        val useCase = DeleteWarehouseRackUseCase(repo)

        val result = useCase("rack-01", 1_700_000_000_000L, 1_700_000_000_000L)

        assertIs<Result.Error>(result)
    }
}
