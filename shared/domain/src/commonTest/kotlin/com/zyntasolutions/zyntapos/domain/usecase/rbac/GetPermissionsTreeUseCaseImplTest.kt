package com.zyntasolutions.zyntapos.domain.usecase.rbac

import com.zyntasolutions.zyntapos.domain.model.Permission
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetPermissionsTreeUseCaseImplTest {

    private val useCase = GetPermissionsTreeUseCaseImpl()

    @Test
    fun `every Permission appears in exactly one group`() = runTest {
        val tree = useCase.invoke()

        val flat = tree.flatMap { group -> group.permissions.map { it.permission } }
        assertEquals(
            Permission.entries.toSet(),
            flat.toSet(),
            "Permission tree must cover every value of the Permission enum.",
        )
        assertEquals(
            flat.size,
            flat.distinct().size,
            "Each permission must appear in exactly one group; duplicates were found.",
        )
    }

    @Test
    fun `tree returns the expected number of modules`() = runTest {
        val tree = useCase.invoke()
        // 12 modules — bumping this expectation must be paired with adding the new
        // module's PermissionGroup entry to GetPermissionsTreeUseCaseImpl.TREE.
        assertEquals(12, tree.size)
    }

    @Test
    fun `module ordering is deterministic across calls`() = runTest {
        val first = useCase.invoke().map { it.module }
        val second = useCase.invoke().map { it.module }
        assertEquals(first, second)
    }

    @Test
    fun `no group is empty and every item has non-blank labels`() = runTest {
        useCase.invoke().forEach { group ->
            assertTrue(group.permissions.isNotEmpty(), "Group ${group.module} has no permissions.")
            assertTrue(group.module.isNotBlank(), "Group module key is blank.")
            assertTrue(group.displayName.isNotBlank(), "Group ${group.module} has a blank displayName.")
            group.permissions.forEach { item ->
                assertTrue(
                    item.displayName.isNotBlank(),
                    "${group.module}/${item.permission} has a blank displayName.",
                )
                assertTrue(
                    item.description.isNotBlank(),
                    "${group.module}/${item.permission} has a blank description.",
                )
            }
        }
    }

    @Test
    fun `module keys are unique`() = runTest {
        val keys = useCase.invoke().map { it.module }
        assertEquals(keys.size, keys.distinct().size, "Module keys are not unique.")
    }
}
