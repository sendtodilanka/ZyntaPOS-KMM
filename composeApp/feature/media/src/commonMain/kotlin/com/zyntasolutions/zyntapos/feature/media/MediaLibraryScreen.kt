package com.zyntasolutions.zyntapos.feature.media

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
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
    val s = LocalStrings.current
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onIntent(MediaIntent.ShowAddDialog) },
                icon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null) },
                text = { Text(s[StringResource.MEDIA_ADD]) },
            )
        },
    ) { innerPadding ->

        // G15: Native file picker — must be called unconditionally at the screen level,
        // not inside the conditionally-shown AddMediaDialog.
        val pickFile = rememberNativeFilePicker { path ->
            if (path != null) onIntent(MediaIntent.UpdateFilePath(path))
        }

        // Camera launcher — called unconditionally; returns null on platforms without camera.
        val takePhoto = rememberNativeCameraLauncher { path ->
            if (path != null) onIntent(MediaIntent.UpdateFilePath(path))
        }

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
                ZyntaEmptyState(
                    title = s[StringResource.MEDIA_NO_MEDIA],
                    icon = Icons.Default.PhotoLibrary,
                    subtitle = s[StringResource.MEDIA_TAP_ADD],
                    modifier = Modifier.fillMaxSize(),
                )
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
                            onClick = { onIntent(MediaIntent.ShowFullScreenPreview(file.id)) },
                            onLongClick = { onIntent(MediaIntent.SelectFile(file.id)) },
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
                onEdit = { onIntent(MediaIntent.OpenImageEditor(file.id)) },
                onDismiss = { onIntent(MediaIntent.ClearSelection) },
            )
        }

        // Full-screen image preview (G15)
        state.previewFile?.let { file ->
            FullScreenImagePreview(
                file = file,
                onDismiss = { onIntent(MediaIntent.HideFullScreenPreview) },
                onShowActions = {
                    onIntent(MediaIntent.HideFullScreenPreview)
                    onIntent(MediaIntent.SelectFile(file.id))
                },
            )
        }

        // Image crop/compress editor (G15)
        state.editingFile?.let { file ->
            ImageEditorDialog(
                file = file,
                aspectRatio = state.cropAspectRatio,
                compressionQuality = state.compressionQuality,
                resizeMaxWidth = state.resizeMaxWidth,
                isProcessing = state.isProcessing,
                onAspectRatioChange = { onIntent(MediaIntent.SetCropAspectRatio(it)) },
                onQualityChange = { onIntent(MediaIntent.SetCompressionQuality(it)) },
                onResizeChange = { onIntent(MediaIntent.SetResizeMaxWidth(it)) },
                onApply = { onIntent(MediaIntent.ApplyImageProcessing) },
                onDismiss = { onIntent(MediaIntent.CloseImageEditor) },
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
                onBrowse = pickFile,
                onTakePhoto = takePhoto,
            )
        }
    }
}

// ─── Private composables ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaFileCell(
    file: MediaFile,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val s = LocalStrings.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
                        contentDescription = s[StringResource.MEDIA_PRIMARY],
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
    onEdit: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${s[StringResource.MEDIA_SIZE]}: %.2f MB".format(file.fileSizeMb), style = MaterialTheme.typography.bodySmall)
                Text("${s[StringResource.COMMON_STATUS]}: ${file.uploadStatus.name}", style = MaterialTheme.typography.bodySmall)
                Text("${s[StringResource.MEDIA_PRIMARY]}: ${if (file.isPrimary) s[StringResource.COMMON_YES] else s[StringResource.COMMON_NO]}", style = MaterialTheme.typography.bodySmall)
                file.remoteUrl?.let {
                    Text("URL: $it", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs)) {
                if (!file.isPrimary) {
                    TextButton(onClick = {
                        onSetPrimary()
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(s[StringResource.MEDIA_SET_PRIMARY])
                    }
                }
                TextButton(onClick = {
                    onEdit()
                    onDismiss()
                }) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(s[StringResource.COMMON_EDIT])
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
                    Text(s[StringResource.COMMON_DELETE])
                }
                TextButton(onClick = onDismiss) { Text(s[StringResource.COMMON_CLOSE]) }
            }
        },
    )
}

@Composable
private fun FullScreenImagePreview(
    file: MediaFile,
    onDismiss: () -> Unit,
    onShowActions: () -> Unit,
) {
    val s = LocalStrings.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // Dim background
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f),
            ) {}

            // Full-size image
            AsyncImage(
                model = file.displayUrl,
                contentDescription = file.fileName,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ZyntaSpacing.md),
                contentScale = ContentScale.Fit,
            )

            // Top-right close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(ZyntaSpacing.sm),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = s[StringResource.MEDIA_CLOSE_PREVIEW],
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Bottom actions bar
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            ) {
                Row(
                    modifier = Modifier.padding(ZyntaSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            file.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "%.1f KB · ${file.uploadStatus.name}".format(file.fileSizeKb),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onShowActions) {
                        Text(s[StringResource.COMMON_ACTIONS])
                    }
                }
            }
        }
    }
}

/**
 * Image crop/compress editor dialog (G15).
 *
 * Provides controls for:
 * - Crop aspect ratio selection (Free, 1:1, 4:3, 16:9, 3:4)
 * - JPEG compression quality slider (1–100)
 * - Max width resize input
 * - Preview of the original image
 *
 * Actual pixel-level cropping requires platform-specific bitmap APIs and will
 * be enhanced in Phase 2. This dialog captures the user's intent and stores
 * parameters for server-side or deferred processing.
 */
@Composable
private fun ImageEditorDialog(
    file: MediaFile,
    aspectRatio: CropAspectRatio,
    compressionQuality: Int,
    resizeMaxWidth: Int,
    isProcessing: Boolean,
    onAspectRatioChange: (CropAspectRatio) -> Unit,
    onQualityChange: (Int) -> Unit,
    onResizeChange: (Int) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            val s = LocalStrings.current
            Column(modifier = Modifier.padding(ZyntaSpacing.md)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        s[StringResource.MEDIA_EDIT_IMAGE],
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = s[StringResource.COMMON_CLOSE])
                    }
                }

                Spacer(Modifier.height(ZyntaSpacing.sm))

                // Image preview
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = file.displayUrl,
                            contentDescription = file.fileName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        // Aspect ratio overlay indicator
                        if (aspectRatio != CropAspectRatio.FREE) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp),
                            ) {
                                Text(
                                    aspectRatio.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(ZyntaSpacing.md))

                // Crop aspect ratio selector
                Text(s[StringResource.MEDIA_CROP_ASPECT_RATIO], style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(ZyntaSpacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
                ) {
                    CropAspectRatio.entries.forEach { ratio ->
                        FilterChip(
                            selected = aspectRatio == ratio,
                            onClick = { onAspectRatioChange(ratio) },
                            label = { Text(ratio.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                Spacer(Modifier.height(ZyntaSpacing.md))

                // Compression quality slider
                Text(
                    "Compression Quality: ${compressionQuality}%",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = compressionQuality.toFloat(),
                    onValueChange = { onQualityChange(it.toInt()) },
                    valueRange = 10f..100f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(s[StringResource.MEDIA_SMALL_FILE], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(s[StringResource.MEDIA_BEST_QUALITY], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(ZyntaSpacing.md))

                // Resize max width
                Text(s[StringResource.MEDIA_MAX_WIDTH], style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(ZyntaSpacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                ) {
                    listOf(0, 640, 1024, 1920, 2560).forEach { size ->
                        FilterChip(
                            selected = resizeMaxWidth == size,
                            onClick = { onResizeChange(size) },
                            label = {
                                Text(
                                    if (size == 0) s[StringResource.MEDIA_ORIGINAL] else "${size}px",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }

                Spacer(Modifier.height(ZyntaSpacing.md))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(ZyntaSpacing.sm))
                    }
                    TextButton(onClick = onDismiss, enabled = !isProcessing) {
                        Text(s[StringResource.COMMON_CANCEL])
                    }
                    Spacer(Modifier.width(ZyntaSpacing.sm))
                    Button(onClick = onApply, enabled = !isProcessing) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(ZyntaSpacing.xs))
                        Text(s[StringResource.MEDIA_APPLY])
                    }
                }
            }
        }
    }
}

@Composable
private fun AddMediaDialog(
    filePath: String,
    error: String?,
    onPathChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onBrowse: () -> Unit = {},
    onTakePhoto: (() -> Unit)? = null,
) {
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s[StringResource.MEDIA_ADD_FILE]) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                Text(
                    s[StringResource.MEDIA_ADD_INSTRUCTIONS],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // G15: Native file picker + camera buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                ) {
                    OutlinedButton(
                        onClick = onBrowse,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(ZyntaSpacing.xs))
                        Text("Browse\u2026")
                    }
                    if (onTakePhoto != null) {
                        OutlinedButton(
                            onClick = onTakePhoto,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(ZyntaSpacing.xs))
                            Text(s[StringResource.MEDIA_TAKE_PHOTO])
                        }
                    }
                }
                OutlinedTextField(
                    value = filePath,
                    onValueChange = onPathChange,
                    label = { Text(s[StringResource.MEDIA_FILE_PATH]) },
                    placeholder = { Text("/storage/emulated/0/DCIM/photo.jpg") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = filePath.isNotBlank()) { Text(s[StringResource.MEDIA_ADD]) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s[StringResource.COMMON_CANCEL]) }
        },
    )
}
