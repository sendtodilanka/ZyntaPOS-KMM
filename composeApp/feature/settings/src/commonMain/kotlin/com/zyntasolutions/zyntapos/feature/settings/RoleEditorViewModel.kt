package com.zyntasolutions.zyntapos.feature.settings

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.PermissionGroup
import com.zyntasolutions.zyntapos.domain.repository.RoleRepository
import com.zyntasolutions.zyntapos.domain.usecase.rbac.GetPermissionsTreeUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rbac.SaveCustomRoleUseCase
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlin.time.Clock

/**
 * MVI state for the role editor screen.
 *
 * Default values render an empty "Create role" form while the tree is being
 * loaded. After [RoleEditorIntent.Load] resolves, [tree] is populated and
 * [isLoading] flips to `false`. When [roleId] is non-null, [name] and
 * [selected] are seeded from the existing role.
 *
 * @property roleId      `null` when creating a new role; otherwise the id
 *                       of the [CustomRole] being edited.
 * @property name        Current value of the role-name text field.
 * @property tree        Module-grouped permission catalogue, returned by
 *                       [GetPermissionsTreeUseCase]. Empty until [Load] resolves.
 * @property selected    Set of permissions currently checked in the tree.
 * @property isLoading   `true` while the editor is fetching its initial data.
 * @property isSaving    `true` while [RoleEditorIntent.Save] is in flight.
 * @property nameError   Validation message shown beneath the name field; null when valid.
 */
data class RoleEditorState(
    val roleId: String? = null,
    val name: String = "",
    val tree: List<PermissionGroup> = emptyList(),
    val selected: Set<Permission> = emptySet(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val nameError: String? = null,
    /**
     * Latest user-facing error from a load or save operation, intended for an
     * inline banner. Cleared on the next [RoleEditorIntent.Load] /
     * [RoleEditorIntent.Save] attempt.
     */
    val saveError: String? = null,
)

/** Intents accepted by [RoleEditorViewModel]. */
sealed interface RoleEditorIntent {
    /**
     * Initial / re-entry load. Pass `null` for create-new; pass an existing
     * [CustomRole.id] to seed the form for edit.
     */
    data class Load(val roleId: String?) : RoleEditorIntent

    /** Update the role-name text field. */
    data class UpdateName(val name: String) : RoleEditorIntent

    /** Toggle a single permission in the [RoleEditorState.selected] set. */
    data class TogglePermission(val permission: Permission) : RoleEditorIntent

    /**
     * Toggle every permission in [group] together.
     *
     * If all of the group's permissions are currently in [RoleEditorState.selected]
     * → remove them; otherwise → add all (so a partially-selected group becomes
     * fully selected on a single tap).
     */
    data class ToggleGroup(val group: PermissionGroup) : RoleEditorIntent

    /**
     * Persist the role.
     *
     * Validates that [RoleEditorState.name] is non-blank — otherwise emits
     * a [RoleEditorState.nameError] without a save attempt. On success emits
     * [RoleEditorEffect.Saved]; on persistence failure emits
     * [RoleEditorEffect.ShowError].
     */
    data object Save : RoleEditorIntent
}

/**
 * One-shot effects emitted by [RoleEditorViewModel].
 *
 * Errors stay on [RoleEditorState.saveError] (rendered as an inline banner) —
 * the only thing that needs to escape state is the post-save navigation cue.
 */
sealed interface RoleEditorEffect {
    /** Persistence completed; the screen should pop back to the role list. */
    data object Saved : RoleEditorEffect
}

/**
 * ViewModel for `RoleEditorScreen` (Sprint 23 task 23.5).
 *
 * Holds the editor's form state, loads the canonical permission tree from
 * [GetPermissionsTreeUseCase], seeds an existing role's permissions when
 * [RoleEditorIntent.Load] receives a non-null id, computes tri-state group
 * toggles in [ToggleGroup], and persists via [SaveCustomRoleUseCase] (with
 * `isUpdate=true` when editing).
 */
class RoleEditorViewModel(
    private val roleRepository: RoleRepository,
    private val getPermissionsTreeUseCase: GetPermissionsTreeUseCase,
    private val saveCustomRoleUseCase: SaveCustomRoleUseCase,
) : BaseViewModel<RoleEditorState, RoleEditorIntent, RoleEditorEffect>(RoleEditorState()) {

    override suspend fun handleIntent(intent: RoleEditorIntent) {
        when (intent) {
            is RoleEditorIntent.Load -> load(intent.roleId)
            is RoleEditorIntent.UpdateName -> updateState {
                copy(name = intent.name, nameError = null)
            }
            is RoleEditorIntent.TogglePermission -> updateState {
                val next = if (intent.permission in selected) selected - intent.permission
                else selected + intent.permission
                copy(selected = next)
            }
            is RoleEditorIntent.ToggleGroup -> updateState {
                val groupPermissions = intent.group.permissions.map { it.permission }.toSet()
                val allSelected = groupPermissions.all { it in selected }
                val next = if (allSelected) selected - groupPermissions else selected + groupPermissions
                copy(selected = next)
            }
            RoleEditorIntent.Save -> save()
        }
    }

    private suspend fun load(roleId: String?) {
        updateState { copy(roleId = roleId, isLoading = true, saveError = null) }
        val tree = getPermissionsTreeUseCase.invoke()
        if (roleId == null) {
            updateState { copy(tree = tree, isLoading = false, name = "", selected = emptySet()) }
            return
        }
        when (val lookup = roleRepository.getCustomRoleById(roleId)) {
            is Result.Success -> updateState {
                copy(
                    tree = tree,
                    isLoading = false,
                    name = lookup.data.name,
                    selected = lookup.data.permissions,
                )
            }
            is Result.Error -> updateState {
                copy(
                    tree = tree,
                    isLoading = false,
                    saveError = lookup.exception.message ?: "Could not load role.",
                )
            }
            Result.Loading -> updateState { copy(tree = tree) }
        }
    }

    private suspend fun save() {
        val current = state.value
        if (current.name.isBlank()) {
            updateState { copy(nameError = "Role name is required.") }
            return
        }
        updateState { copy(isSaving = true, nameError = null, saveError = null) }
        val now = Clock.System.now()
        val isUpdate = current.roleId != null
        val role = if (isUpdate) {
            // Preserve original createdAt — fetch it before assembling the update.
            val source = (roleRepository.getCustomRoleById(current.roleId!!) as? Result.Success)?.data
            CustomRole(
                id = current.roleId,
                name = current.name.trim(),
                description = source?.description.orEmpty(),
                permissions = current.selected,
                createdAt = source?.createdAt ?: now,
                updatedAt = now,
            )
        } else {
            CustomRole(
                id = IdGenerator.newId(),
                name = current.name.trim(),
                description = "",
                permissions = current.selected,
                createdAt = now,
                updatedAt = now,
            )
        }
        when (val outcome = saveCustomRoleUseCase.invoke(role, isUpdate = isUpdate)) {
            is Result.Success -> {
                updateState { copy(isSaving = false) }
                sendEffect(RoleEditorEffect.Saved)
            }
            is Result.Error -> updateState {
                copy(isSaving = false, saveError = outcome.exception.message ?: "Could not save role.")
            }
            Result.Loading -> updateState { copy(isSaving = true) }
        }
    }
}
