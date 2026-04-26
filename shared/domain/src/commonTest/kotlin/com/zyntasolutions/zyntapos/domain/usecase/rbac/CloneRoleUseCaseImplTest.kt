package com.zyntasolutions.zyntapos.domain.usecase.rbac

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeRoleRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CloneRoleUseCaseImplTest {

    private fun makeSource(
        id: String = "source-01",
        name: String = "Senior Cashier",
        permissions: Set<Permission> = setOf(
            Permission.PROCESS_SALE,
            Permission.APPLY_DISCOUNT,
            Permission.VIEW_REPORTS,
        ),
    ) = CustomRole(
        id = id,
        name = name,
        description = "Source description",
        permissions = permissions,
        createdAt = Instant.fromEpochSeconds(1_000_000),
        updatedAt = Instant.fromEpochSeconds(1_000_000),
    )

    @Test
    fun `blank newName returns ValidationException`() = runTest {
        val repo = FakeRoleRepository().apply { seedRoles(makeSource()) }

        val result = CloneRoleUseCaseImpl(repo).invoke(sourceRoleId = "source-01", newName = "  ")

        val error = assertIs<Result.Error>(result)
        val ex = assertIs<ValidationException>(error.exception)
        assertEquals("name", ex.field)
        assertEquals("REQUIRED", ex.rule)
        assertTrue(repo.createdRoles.isEmpty(), "No persistence should occur on validation failure.")
    }

    @Test
    fun `unknown sourceRoleId surfaces repository error`() = runTest {
        val repo = FakeRoleRepository()

        val result = CloneRoleUseCaseImpl(repo).invoke(sourceRoleId = "missing", newName = "Clone")

        val error = assertIs<Result.Error>(result)
        assertIs<DatabaseException>(error.exception)
        assertTrue(repo.createdRoles.isEmpty())
    }

    @Test
    fun `successful clone copies permissions and persists with fresh id and timestamps`() = runTest {
        val source = makeSource()
        val repo = FakeRoleRepository().apply { seedRoles(source) }

        val result = CloneRoleUseCaseImpl(repo).invoke(
            sourceRoleId = source.id,
            newName = "Senior Cashier (Clone)",
        )

        val ok = assertIs<Result.Success<CustomRole>>(result)
        val clone = ok.data

        assertEquals("Senior Cashier (Clone)", clone.name)
        assertEquals(source.permissions, clone.permissions)
        assertNotEquals(source.id, clone.id)
        assertTrue(clone.id.isNotBlank())
        assertEquals("", clone.description)
        // createdAt == updatedAt on a fresh clone
        assertEquals(clone.createdAt, clone.updatedAt)
        // Source's old timestamp was 1_000_000s (year 1970); the clone must be fresh
        assertTrue(clone.createdAt > source.createdAt, "Clone timestamp must be newer than source.")
        // Persistence delegated exactly once
        assertEquals(1, repo.createdRoles.size)
        assertEquals(clone, repo.createdRoles.single())
    }

    @Test
    fun `repository failure during persistence is surfaced`() = runTest {
        val source = makeSource()
        val repo = FakeRoleRepository().apply {
            seedRoles(source)
            shouldFail = true
        }

        val result = CloneRoleUseCaseImpl(repo).invoke(sourceRoleId = source.id, newName = "Clone")

        val error = assertIs<Result.Error>(result)
        assertIs<DatabaseException>(error.exception)
    }

    @Test
    fun `clone does not mutate the source role`() = runTest {
        val source = makeSource()
        val repo = FakeRoleRepository().apply { seedRoles(source) }

        CloneRoleUseCaseImpl(repo).invoke(sourceRoleId = source.id, newName = "Clone")

        val refetched = assertIs<Result.Success<CustomRole>>(repo.getCustomRoleById(source.id)).data
        assertEquals(source, refetched, "Source role must remain unmodified after a clone operation.")
        assertFalse(repo.createdRoles.any { it.id == source.id })
    }
}
