package com.zyntasolutions.zyntapos.feature.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.MediaFile

/**
 * Media library screen (Sprint 16).
 *
 * Displays all media files for a specific entity in a responsive 2-column grid.
 * Each cell shows a thumbnail (via Coil), the file name, size, and upload status.
 * The primary image is marked with a star badge.
 *
 * Actions:
 * - FAB → opens "Add Media" dialog (enter file path)
 * - Tap cell → select for detail actions (set primary / delete)
 *
 * @param state     Current [MediaState].
 * @param onIntent  Dispatches intents to [MediaViewModel].
 * @param modifier  Optional modifier.
 */
@Composable
fun MediaLibraryScreen(
    state: MediaState,
    onIntent: (MediaIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onIntent(MediaIntent.ShowAddDialog) },
                icon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null) },
                text = { Text("Add Media") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = ZyntaSpacing.sm),
        ) {
            if (state.entityType.isNotBlank()) {
                Text(
                    text = "${state.entityType} · ${state.entityId}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = ZyntaSpacing.sm),
                )
            }

            if (state.isLoading && state.mediaFiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.mediaFiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No media attached yet.\nTap + to add an image.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(state.mediaFiles, key = { it.id }) { file ->
                        MediaFileCell(
                            file = file,
                            isSelected = state.selectedFile?.id == file.id,
                            onClick = { onIntent(MediaIntent.SelectFile(file.id)) },
                        )
                    }
                }
            }
        }

        // Selected file — bottom action sheet
        state.selectedFile?.let { file ->
            FileActionBottomSheet(
                file = file,
                onSetPrimary = { onIntent(MediaIntent.SetAsPrimary(file.id)) },
                onDelete = { onIntent(MediaIntent.DeleteFile(file.id)) },
                onDismiss = { onIntent(MediaIntent.ClearSelection) },
            )
        }

        // Add media dialog
        if (state.showAddDialog) {
            AddMediaDialog(
                filePath = state.addFilePath,
                error = state.addFileError,
                onPathChange = { onIntent(MediaIntent.UpdateFilePath(it)) },
                onConfirm = { onIntent(MediaIntent.ConfirmAddFile) },
                onDismiss = { onIntent(MediaIntent.HideAddDialog) },
            )
        }
    }
}

// ─── Private composables ──────────────────────────────────────────────────────

@Composable
private fun MediaFileCell(
    file: MediaFile,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        border = if (isSelected)
            CardDefaults.outlinedCardBorder()
        else null,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Image thumbnail
            AsyncImage(
                model = file.displayUrl,
                contentDescription = file.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            // Primary star badge
            if (file.isPrimary) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Primary",
                        modifier = Modifier.padding(2.dp).size(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            // File name + size overlay
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    Text(
                        file.fileName,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "%.1f KB · ${file.uploadStatus.name}".format(file.fileSizeKb),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileActionBottomSheet(
    file: MediaFile,
    onSetPrimary: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Size: %.2f MB".format(file.fileSizeMb), style = MaterialTheme.typography.bodySmall)
                Text("Status: ${file.uploadStatus.name}", style = MaterialTheme.typography.bodySmall)
                Text("Primary: ${if (file.isPrimary) "Yes" else "No"}", style = MaterialTheme.typography.bodySmall)
                file.remoteUrl?.let {
                    Text("URL: $it", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        confirmButton = {
            if (!file.isPrimary) {
                TextButton(onClick = {
                    onSetPrimary()
                    onDismiss()
                }) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Set as Primary")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                TextButton(
                    onClick = {
                        onDelete()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun AddMediaDialog(
    filePath: String,
    error: String?,
    onPathChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Media File") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                Text(
                    "Enter the absolute file path to the image on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = filePath,
                    onValueChange = onPathChange,
                    label = { Text("File path *") },
                    placeholder = { Text("/storage/emulated/0/DCIM/photo.jpg") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
