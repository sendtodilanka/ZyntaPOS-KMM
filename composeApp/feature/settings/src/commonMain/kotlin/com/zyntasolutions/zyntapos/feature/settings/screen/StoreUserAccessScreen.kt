package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.result.onError
import com.zyntasolutions.zyntapos.core.result.onSuccess
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.model.UserStoreAccess
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
import com.zyntasolutions.zyntapos.domain.repository.UserStoreAccessRepository
import com.zyntasolutions.zyntapos.domain.usecase.auth.GrantStoreAccessUseCase
import com.zyntasolutions.zyntapos.domain.usecase.auth.RevokeStoreAccessUseCase
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// ─── MVI contracts ────────────────────────────────────────────────────────────

data class StoreUserAccessState(
    val storeId: String = "",
    val accessList: List<UserStoreAccess> = emptyList(),
    val allUsers: List<User> = emptyList(),
    val isLoading: Boolean = false,
    // Grant form
    val showGrantForm: Boolean = false,
    val formUserId: String = "",
    val formRole: Role? = null,
    // Revoke confirmation
    val pendingRevokeEntry: UserStoreAccess? = null,
)

sealed interface StoreUserAccessIntent {
    data class LoadForStore(val storeId: String) : StoreUserAccessIntent
    data object OpenGrantForm : StoreUserAccessIntent
    data object DismissGrantForm : StoreUserAccessIntent
    data class UpdateFormUserId(val userId: String) : StoreUserAccessIntent
    data class UpdateFormRole(val role: Role?) : StoreUserAccessIntent
    data object SubmitGrant : StoreUserAccessIntent
    data class RequestRevoke(val entry: UserStoreAccess) : StoreUserAccessIntent
    data object ConfirmRevoke : StoreUserAccessIntent
    data object CancelRevoke : StoreUserAccessIntent
}

sealed interface StoreUserAccessEffect {
    data class ShowError(val message: String) : StoreUserAccessEffect
    data class ShowSuccess(val message: String) : StoreUserAccessEffect
    data object NavigateBack : StoreUserAccessEffect
}

// ─── ViewModel ───────────────────────────────────────────────────────────────

class StoreUserAccessViewModel(
    private val userRepository: UserRepository,
    private val accessRepository: UserStoreAccessRepository,
    private val grantStoreAccessUseCase: GrantStoreAccessUseCase,
    private val revokeStoreAccessUseCase: RevokeStoreAccessUseCase,
) : BaseViewModel<StoreUserAccessState, StoreUserAccessIntent, StoreUserAccessEffect>(
    initialState = StoreUserAccessState()
) {

    override suspend fun handleIntent(intent: StoreUserAccessIntent) {
        when (intent) {
            is StoreUserAccessIntent.LoadForStore     -> loadForStore(intent.storeId)
            is StoreUserAccessIntent.OpenGrantForm    -> openGrantForm()
            is StoreUserAccessIntent.DismissGrantForm -> updateState { copy(showGrantForm = false, formUserId = "", formRole = null) }
            is StoreUserAccessIntent.UpdateFormUserId -> updateState { copy(formUserId = intent.userId) }
            is StoreUserAccessIntent.UpdateFormRole   -> updateState { copy(formRole = intent.role) }
            is StoreUserAccessIntent.SubmitGrant      -> submitGrant()
            is StoreUserAccessIntent.RequestRevoke    -> updateState { copy(pendingRevokeEntry = intent.entry) }
            is StoreUserAccessIntent.ConfirmRevoke    -> confirmRevoke()
            is StoreUserAccessIntent.CancelRevoke     -> updateState { copy(pendingRevokeEntry = null) }
        }
    }

    private fun loadForStore(storeId: String) {
        updateState { copy(isLoading = true, storeId = storeId) }

        // Observe all users (for the grant form dropdown)
        viewModelScope.launch {
            userRepository.getAll().collectLatest { users ->
                updateState { copy(allUsers = users) }
            }
        }

        // Observe access grants for this store
        viewModelScope.launch {
            accessRepository.getUsersForStore(storeId).collectLatest { grants ->
                updateState { copy(accessList = grants, isLoading = false) }
            }
        }
    }

    private fun openGrantForm() {
        updateState { copy(showGrantForm = true, formUserId = "", formRole = null) }
    }

    private suspend fun submitGrant() {
        val s = state.value
        if (s.formUserId.isBlank()) {
            sendEffect(StoreUserAccessEffect.ShowError("Please select a user"))
            return
        }
        val result = grantStoreAccessUseCase(
            GrantStoreAccessUseCase.Params(
                userId = s.formUserId,
                storeId = s.storeId,
                roleAtStore = s.formRole,
            )
        )
        result
            .onSuccess {
                updateState { copy(showGrantForm = false, formUserId = "", formRole = null) }
                sendEffect(StoreUserAccessEffect.ShowSuccess("Access granted"))
            }
            .onError { sendEffect(StoreUserAccessEffect.ShowError(it.message)) }
    }

    private suspend fun confirmRevoke() {
        val entry = state.value.pendingRevokeEntry ?: return
        val result = revokeStoreAccessUseCase(entry.userId, entry.storeId)
        result
            .onSuccess {
                updateState { copy(pendingRevokeEntry = null) }
                sendEffect(StoreUserAccessEffect.ShowSuccess("Access revoked"))
            }
            .onError { sendEffect(StoreUserAccessEffect.ShowError(it.message)) }
    }
}

// ─── Screen ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreUserAccessScreen(
    storeId: String,
    state: StoreUserAccessState,
    effects: kotlinx.coroutines.flow.Flow<StoreUserAccessEffect>,
    onIntent: (StoreUserAccessIntent) -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(storeId) {
        onIntent(StoreUserAccessIntent.LoadForStore(storeId))
    }

    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is StoreUserAccessEffect.ShowError   -> snackbarHostState.showSnackbar(effect.message)
                is StoreUserAccessEffect.ShowSuccess -> snackbarHostState.showSnackbar(effect.message)
                is StoreUserAccessEffect.NavigateBack -> onBack()
            }
        }
    }

    // Revoke confirmation dialog
    if (state.pendingRevokeEntry != null) {
        val entry = state.pendingRevokeEntry
        val userName = state.allUsers.find { it.id == entry.userId }?.name ?: entry.userId
        AlertDialog(
            onDismissRequest = { onIntent(StoreUserAccessIntent.CancelRevoke) },
            title = { Text(s[StringResource.SETTINGS_USER_ACCESS_REVOKE_TITLE]) },
            text = { Text(s[StringResource.SETTINGS_USER_ACCESS_REVOKE_MSG, userName]) },
            confirmButton = {
                TextButton(onClick = { onIntent(StoreUserAccessIntent.ConfirmRevoke) }) {
                    Text(s[StringResource.SETTINGS_USER_ACCESS_REVOKE], color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(StoreUserAccessIntent.CancelRevoke) }) {
                    Text(s[StringResource.COMMON_CANCEL])
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s[StringResource.SETTINGS_USER_ACCESS_TITLE]) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = s[StringResource.COMMON_BACK])
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.showGrantForm) {
                FloatingActionButton(onClick = { onIntent(StoreUserAccessIntent.OpenGrantForm) }) {
                    Icon(Icons.Default.Add, contentDescription = s[StringResource.SETTINGS_USER_ACCESS_GRANT_CD])
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Grant form
            if (state.showGrantForm) {
                item {
                    GrantAccessForm(
                        state = state,
                        onIntent = onIntent,
                    )
                }
            }

            // Empty state
            if (state.accessList.isEmpty() && !state.isLoading && !state.showGrantForm) {
                item {
                    ZyntaEmptyState(
                        title = s[StringResource.SETTINGS_USER_ACCESS_EMPTY_TITLE],
                        subtitle = s[StringResource.SETTINGS_USER_ACCESS_EMPTY_DESC],
                    )
                }
            }

            // Access grant rows
            items(state.accessList, key = { it.id }) { entry ->
                val user = state.allUsers.find { it.id == entry.userId }
                val userName = user?.name ?: entry.userId
                val userRole = entry.roleAtStore?.name ?: user?.role?.name ?: s[StringResource.SETTINGS_USER_ACCESS_DEFAULT_ROLE]

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (entry.isActive)
                            MaterialTheme.colorScheme.surface
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = s[StringResource.SETTINGS_USER_ACCESS_ROLE_AT_STORE, userRole],
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!entry.isActive) {
                                Text(
                                    text = s[StringResource.SETTINGS_USER_ACCESS_INACTIVE],
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        IconButton(
                            onClick = { onIntent(StoreUserAccessIntent.RequestRevoke(entry)) },
                        ) {
                            Icon(
                                Icons.Default.PersonRemove,
                                contentDescription = s[StringResource.SETTINGS_USER_ACCESS_REVOKE_CD],
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GrantAccessForm(
    state: StoreUserAccessState,
    onIntent: (StoreUserAccessIntent) -> Unit,
) {
    // Users who don't already have an active grant for this store
    val grantableUsers = state.allUsers.filter { user ->
        state.accessList.none { it.userId == user.id && it.isActive }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
        ),
    ) {
        val s = LocalStrings.current
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(s[StringResource.SETTINGS_USER_ACCESS_GRANT_TITLE], style = MaterialTheme.typography.titleMedium)

            // User selector
            UserDropdown(
                label = s[StringResource.SETTINGS_USER_ACCESS_SELECT_USER],
                selectedUserId = state.formUserId,
                users = grantableUsers,
                onUserSelected = { onIntent(StoreUserAccessIntent.UpdateFormUserId(it)) },
            )

            // Optional role override
            RoleDropdown(
                label = s[StringResource.SETTINGS_USER_ACCESS_ROLE_LABEL],
                selectedRole = state.formRole,
                onRoleSelected = { onIntent(StoreUserAccessIntent.UpdateFormRole(it)) },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ZyntaButton(
                    text = s[StringResource.COMMON_CANCEL],
                    onClick = { onIntent(StoreUserAccessIntent.DismissGrantForm) },
                    modifier = Modifier.weight(1f),
                )
                ZyntaButton(
                    text = s[StringResource.SETTINGS_USER_ACCESS_GRANT_ACTION],
                    onClick = { onIntent(StoreUserAccessIntent.SubmitGrant) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserDropdown(
    label: String,
    selectedUserId: String,
    users: List<User>,
    onUserSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = users.find { it.id == selectedUserId }?.name ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        ZyntaTextField(
            value = selectedName,
            onValueChange = {},
            label = label,
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            users.forEach { user ->
                DropdownMenuItem(
                    text = { Text(s[StringResource.COMMON_NAME_PAREN_FORMAT, user.name, user.role.name]) },
                    onClick = {
                        onUserSelected(user.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleDropdown(
    label: String,
    selectedRole: Role?,
    onRoleSelected: (Role?) -> Unit,
) {
    val s = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    val roleOptions = listOf(null) + Role.entries.toList()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        ZyntaTextField(
            value = selectedRole?.name ?: s[StringResource.SETTINGS_USER_ACCESS_DEFAULT_ROLE_DISPLAY],
            onValueChange = {},
            label = label,
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            roleOptions.forEach { role ->
                DropdownMenuItem(
                    text = { Text(role?.name ?: s[StringResource.SETTINGS_USER_ACCESS_DEFAULT_ROLE_DISPLAY]) },
                    onClick = {
                        onRoleSelected(role)
                        expanded = false
                    },
                )
            }
        }
    }
}
