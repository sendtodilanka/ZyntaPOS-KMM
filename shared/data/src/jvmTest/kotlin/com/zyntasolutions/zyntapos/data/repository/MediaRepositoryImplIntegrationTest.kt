package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.MediaFile
import com.zyntasolutions.zyntapos.domain.model.MediaFileType
import com.zyntasolutions.zyntapos.domain.model.MediaUploadStatus
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — MediaRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [MediaRepositoryImpl] against a real in-memory SQLite database.
 * media_files has no external FK constraints (entity_id is a polymorphic TEXT).
 *
 * Coverage:
 *  A. insert → getById round-trip preserves all fields
 *  B. getByEntity emits files for entity via Turbine
 *  C. getByEntity excludes soft-deleted files
 *  D. getPrimaryForEntity returns primary file
 *  E. getPendingUpload returns files with LOCAL status
 *  F. updateUploadStatus changes LOCAL to UPLOADED
 *  G. setPrimary marks file as primary and clears others
 *  H. delete soft-deletes by setting deletedAt
 */
class MediaRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: MediaRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = MediaRepositoryImpl(db, SyncEnqueuer(db))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeFile(
        id: String = "media-01",
        fileName: String = "product-front.jpg",
        filePath: String = "/local/images/product-front.jpg",
        entityType: String? = "Product",
        entityId: String? = "prod-01",
        isPrimary: Boolean = false,
        uploadStatus: MediaUploadStatus = MediaUploadStatus.LOCAL,
        fileType: MediaFileType = MediaFileType.IMAGE,
    ) = MediaFile(
        id = id,
        fileName = fileName,
        filePath = filePath,
        remoteUrl = null,
        fileType = fileType,
        mimeType = "image/jpeg",
        fileSize = 204800L,
        thumbnailPath = null,
        entityType = entityType,
        entityId = entityId,
        isPrimary = isPrimary,
        uploadedBy = "user-01",
        uploadStatus = uploadStatus,
        createdAt = now,
        updatedAt = now,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert then getById round-trip preserves all fields`() = runTest {
        val file = makeFile(
            id = "media-01",
            fileName = "product-hero.jpg",
            filePath = "/local/images/product-hero.jpg",
            entityType = "Product",
            entityId = "prod-01",
            isPrimary = true,
        )
        val insertResult = repo.insert(file)
        assertIs<Result.Success<Unit>>(insertResult)

        val fetchResult = repo.getById("media-01")
        assertIs<Result.Success<MediaFile>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("media-01", fetched.id)
        assertEquals("product-hero.jpg", fetched.fileName)
        assertEquals("/local/images/product-hero.jpg", fetched.filePath)
        assertEquals("Product", fetched.entityType)
        assertEquals("prod-01", fetched.entityId)
        assertTrue(fetched.isPrimary)
        assertEquals(MediaUploadStatus.LOCAL, fetched.uploadStatus)
    }

    @Test
    fun `B - getByEntity emits files for entity via Turbine`() = runTest {
        repo.insert(makeFile(id = "media-01", entityId = "prod-01"))
        repo.insert(makeFile(id = "media-02", entityId = "prod-01", fileName = "product-back.jpg",
            filePath = "/local/images/product-back.jpg"))
        repo.insert(makeFile(id = "media-03", entityId = "prod-02", fileName = "other.jpg",
            filePath = "/local/images/other.jpg"))

        repo.getByEntity("Product", "prod-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.all { it.entityId == "prod-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getByEntity excludes soft-deleted files`() = runTest {
        repo.insert(makeFile(id = "media-01", entityId = "prod-01"))
        repo.insert(makeFile(id = "media-02", entityId = "prod-01",
            fileName = "product-back.jpg", filePath = "/local/images/product-back.jpg"))
        repo.delete("media-02", now, now)

        repo.getByEntity("Product", "prod-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("media-01", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - getPrimaryForEntity returns primary file`() = runTest {
        repo.insert(makeFile(id = "media-01", entityId = "prod-01", isPrimary = false))
        repo.insert(makeFile(id = "media-02", entityId = "prod-01",
            fileName = "product-primary.jpg", filePath = "/local/images/product-primary.jpg",
            isPrimary = true))

        val result = repo.getPrimaryForEntity("Product", "prod-01")
        assertIs<Result.Success<MediaFile?>>(result)
        assertNotNull(result.data)
        assertEquals("media-02", result.data!!.id)
    }

    @Test
    fun `E - getPendingUpload returns files with LOCAL status`() = runTest {
        repo.insert(makeFile(id = "media-01", uploadStatus = MediaUploadStatus.LOCAL))
        repo.insert(makeFile(id = "media-02", entityId = "prod-02",
            fileName = "file2.jpg", filePath = "/local/file2.jpg",
            uploadStatus = MediaUploadStatus.UPLOADED))

        val result = repo.getPendingUpload()
        assertIs<Result.Success<List<MediaFile>>>(result)
        assertEquals(1, result.data.size)
        assertEquals("media-01", result.data.first().id)
    }

    @Test
    fun `F - updateUploadStatus changes LOCAL to UPLOADED`() = runTest {
        repo.insert(makeFile(id = "media-01", uploadStatus = MediaUploadStatus.LOCAL))

        val updateResult = repo.updateUploadStatus(
            id = "media-01",
            status = MediaUploadStatus.UPLOADED,
            remoteUrl = "https://cdn.example.com/media-01.jpg",
            updatedAt = now,
        )
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getById("media-01") as Result.Success).data
        assertEquals(MediaUploadStatus.UPLOADED, fetched.uploadStatus)
        assertEquals("https://cdn.example.com/media-01.jpg", fetched.remoteUrl)
    }

    @Test
    fun `G - setPrimary marks file as primary and clears previous primary`() = runTest {
        repo.insert(makeFile(id = "media-01", entityId = "prod-01", isPrimary = true))
        repo.insert(makeFile(id = "media-02", entityId = "prod-01",
            fileName = "product-back.jpg", filePath = "/local/images/product-back.jpg",
            isPrimary = false))

        val setPrimaryResult = repo.setPrimary(
            id = "media-02",
            entityType = "Product",
            entityId = "prod-01",
            updatedAt = now,
        )
        assertIs<Result.Success<Unit>>(setPrimaryResult)

        val primary = (repo.getPrimaryForEntity("Product", "prod-01") as Result.Success).data
        assertNotNull(primary)
        assertEquals("media-02", primary.id)
    }

    @Test
    fun `H - delete soft-deletes by setting deletedAt`() = runTest {
        repo.insert(makeFile(id = "media-01"))

        val deleteResult = repo.delete("media-01", now, now)
        assertIs<Result.Success<Unit>>(deleteResult)

        // Soft-deleted file is excluded from getByEntity
        repo.getByEntity("Product", "prod-01").test {
            val list = awaitItem()
            assertEquals(0, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
