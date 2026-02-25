package com.zyntasolutions.zyntapos.feature.staff

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaSearchBar
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.Employee

/**
 * Employee directory screen — searchable list of staff profiles.
 *
 * Displays active employees by default; toggle to include inactive.
 * FAB opens [EmployeeDetailScreen] in create-new mode.
 *
 * @param state       Current [StaffState] from [StaffViewModel].
 * @param onIntent    Dispatches [StaffIntent] to the ViewModel.
 * @param onNavigateToDetail Navigation callback for the detail screen.
 * @param modifier    Optional root modifier.
 */
@Composable
fun EmployeeListScreen(
    state: StaffState,
    onIntent: (StaffIntent) -> Unit,
    onNavigateToDetail: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onIntent(StaffIntent.LoadEmployees) }

    val filtered = remember(state.employees, state.searchQuery, state.showInactive) {
        state.employees
            .filter { state.showInactive || it.isActive }
            .filter { emp ->
                state.searchQuery.isBlank() ||
                    emp.fullName.contains(state.searchQuery, ignoreCase = true) ||
                    emp.position.contains(state.searchQuery, ignoreCase = true) ||
                    emp.department?.contains(state.searchQuery, ignoreCase = true) == true
            }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigateToDetail(null) },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = "Add Employee") },
                text = { Text("Add Employee") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = ZyntaSpacing.md),
        ) {
            ZyntaSearchBar(
                query = state.searchQuery,
                onQueryChange = { onIntent(StaffIntent.SearchEmployees(it)) },
                onClear = { onIntent(StaffIntent.SearchEmployees("")) },
                onScanToggle = {},
                placeholder = "Search employees…",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = ZyntaSpacing.sm),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = ZyntaSpacing.sm),
            ) {
                Text(
                    text = "${filtered.size} employee${if (filtered.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Show inactive",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Switch(
                        checked = state.showInactive,
                        onCheckedChange = { onIntent(StaffIntent.ToggleShowInactive(it)) },
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.searchQuery.isBlank()) "No employees found.\nTap + to add one."
                        else "No results for \"${state.searchQuery}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { employee ->
                        EmployeeListItem(
                            employee = employee,
                            onClick = {
                                onIntent(StaffIntent.SelectEmployee(employee.id))
                                onNavigateToDetail(employee.id)
                            },
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) } // FAB clearance
                }
            }
        }

        // Error/success feedback
        state.error?.let { msg ->
            LaunchedEffect(msg) { onIntent(StaffIntent.DismissError) }
        }
    }
}

@Composable
private fun EmployeeListItem(
    employee: Employee,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (employee.isActive) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZyntaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .padding(end = ZyntaSpacing.sm),
                tint = if (employee.isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = employee.fullName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(employee.position)
                        employee.department?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!employee.isActive) {
                AssistChip(
                    onClick = {},
                    label = { Text("Inactive") },
                    modifier = Modifier.padding(start = ZyntaSpacing.sm),
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View",
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
