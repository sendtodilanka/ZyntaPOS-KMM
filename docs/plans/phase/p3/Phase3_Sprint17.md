# ZyntaPOS — Phase 3 Sprint 17: Media Feature Part 2 — Media Library & Entity Assignment

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT17-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 17 of 24 | Week 17
> **Module(s):** `:composeApp:feature:media`, `:composeApp:feature:inventory`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 | ADR-001

---

## Goal

Implement the Media Library screen (grid view of all uploaded media, filterable by entity type) and the Media Assignment screen (attach existing media to any entity without re-upload). Integrate the image picker trigger into the Inventory `ProductFormScreen` so product images can be set via the media pipeline. This completes `:composeApp:feature:media` (M20).

---

## New Screen Files

**Location:** `composeApp/feature/media/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/media/screen/`

```
screen/
├── MediaLibraryScreen.kt         # Grid view of all media, filterable by entity type
└── MediaAssignmentScreen.kt      # Pick existing media and assign to an entity
```

---

## Media Library Screen

### `MediaLibraryScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.media.screen

/**
 * Media library screen — grid view of all uploaded media.
 *
 * Layout:
 * - Filter chips row: All / Product / Category / Store / Employee
 * - Lazy grid: 3 columns of ZyntaImageCard composables
 *   Each card: thumbnail + entity type badge + upload status badge
 * - Tap → MediaAssignmentScreen or image preview
 * - Long-press → delete confirmation dialog
 *
 * FAB: "Upload New" → navigates to ImagePickerScreen(entityType=null, entityId="")
 *
 * Empty state: "No media files yet. Upload images for your products and entities."
 */
@Composable
fun MediaLibraryScreen(
    viewModel: MediaViewModel,
    onNavigateToUpload: (entityType: String, entityId: String) -> Unit,
    onNavigateToAssignment: (mediaId: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var selectedEntityType by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.handleIntent(MediaIntent.LoadMediaLibrary())
    }

    LaunchedEffect(selectedEntityType) {
        viewModel.handleIntent(MediaIntent.LoadMediaLibrary(selectedEntityType))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Media Library") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { /* back */ } }
            )
        },
        floatingActionButton = {
            ZyntaFab(
                text = "Upload",
                onClick = { onNavigateToUpload("", "") }
            )
        }
    ) { padding ->
        Column(modifier = modifier.padding(padding)) {
            // Entity type filter chips
            EntityTypeFilterRow(
                selected = selectedEntityType,
                onSelect = { selectedEntityType = it }
            )

            if (state.mediaFiles.isEmpty() && !state.isUploading) {
                EmptyStateView(
                    message = "No media files yet.\nUpload images for your products and entities.",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.mediaFiles, key = { it.id }) { mediaFile ->
                        ZyntaImageCard(
                            mediaFile = mediaFile,
                            onClick = { onNavigateToAssignment(mediaFile.id) },
                            onDelete = { deleteTarget = mediaFile.id },
                            showStatus = true
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation
    deleteTarget?.let { mediaId ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Image?") },
            text = { Text("This image will be removed from the media library and all associated entities.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.handleIntent(MediaIntent.DeleteMedia(mediaId))
                        deleteTarget = null
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EntityTypeFilterRow(
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val types = listOf("All", "Product", "Category", "Store", "Employee")
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(types) { type ->
            FilterChip(
                selected = (type == "All" && selected == null) || type == selected,
                onClick = { onSelect(if (type == "All") null else type) },
                label = { Text(type) }
            )
        }
    }
}
```

---

## Media Assignment Screen

### `MediaAssignmentScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.media.screen

/**
 * Assign an existing media file to a different entity.
 *
 * This screen is shown when the user taps a media file in the library and wants
 * to assign it to a specific product, category, store, or employee without re-uploading.
 *
 * Layout:
 * - Large image preview of the selected MediaFile
 * - Metadata: original entity (if assigned), upload date, file size
 * - Entity type dropdown: Product / Category / Store / Employee
 * - Entity ID text field (or search picker for products)
 * - "Assign" primary button → MediaIntent.AssignMedia(mediaId, entityId)
 * - "Delete Image" danger button
 *
 * On assign success: NavigateBack with result (entity gets updated imageUrl)
 */
@Composable
fun MediaAssignmentScreen(
    mediaId: String,
    viewModel: MediaViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val mediaFile = state.mediaFiles.find { it.id == mediaId } ?: state.selectedFile ?: return

    var targetEntityId by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MediaEffect.NavigateBack -> onNavigateBack()
                is MediaEffect.ShowError    -> { /* snackbar */ }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Media Details") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { /* back */ } }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Image preview
            AsyncImage(
                model = mediaFile.displayUrl,
                contentDescription = mediaFile.fileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            // Metadata card
            ZyntaCard {
                Column(Modifier.padding(12.dp)) {
                    MetadataRow("File", mediaFile.fileName)
                    MetadataRow("Size", "${mediaFile.fileSizeKb} KB (${mediaFile.fileSizeMb.format(2)} MB)")
                    MetadataRow("Type", mediaFile.mimeType)
                    MetadataRow("Uploaded", formatDateTime(mediaFile.createdAt))
                    MetadataRow("Status", mediaFile.uploadStatus.name)
                    if (mediaFile.entityType != null) {
                        MetadataRow("Assigned to", "${mediaFile.entityType} ${mediaFile.entityId}")
                    }
                }
            }

            // Re-assign section
            Text("Re-assign to Entity", style = MaterialTheme.typography.titleSmall)
            ZyntaTextField(
                label = "Entity ID",
                value = targetEntityId,
                onValueChange = { targetEntityId = it },
                helperText = "Enter the product/category/store/employee ID"
            )
            ZyntaButton(
                text = "Assign",
                enabled = targetEntityId.isNotBlank(),
                onClick = { viewModel.handleIntent(MediaIntent.AssignMedia(mediaId, targetEntityId)) },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            // Delete button
            ZyntaOutlinedButton(
                text = "Delete Image",
                color = MaterialTheme.colorScheme.error,
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Image?") },
            text = { Text("This will permanently remove the image.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.handleIntent(MediaIntent.DeleteMedia(mediaId))
                        showDeleteConfirm = false
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun Double.format(decimals: Int) = "%.${decimals}f".format(this)
```

---

## Inventory Integration

Update `ProductFormScreen` (`:composeApp:feature:inventory`) to add an image section:

```kotlin
// In ProductFormScreen.kt — add Image section before the Save button:

// Product image section
Text("Product Image", style = MaterialTheme.typography.titleSmall)
if (state.selectedProduct?.imageUrl != null) {
    // Show current image with replace/remove options
    AsyncImage(
        model = state.selectedProduct.imageUrl,
        contentDescription = "Product image",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ZyntaOutlinedButton(
            text = "Replace",
            onClick = { onNavigateToMediaPicker(productId) },
            modifier = Modifier.weight(1f)
        )
        ZyntaOutlinedButton(
            text = "Remove",
            onClick = { /* clear imageUrl in form state */ },
            modifier = Modifier.weight(1f)
        )
    }
} else {
    ZyntaButton(
        text = "+ Add Image",
        onClick = { onNavigateToMediaPicker(productId) },
        modifier = Modifier.fillMaxWidth()
    )
}
```

The `onNavigateToMediaPicker` callback navigates to:
`ZyntaRoute.MediaPicker(entityType = "Product", entityId = productId)`

When the media picker returns (via `NavBackStackEntry` saved state), the selected `imageUrl` is set on the product form state.

---

## MediaViewModel Updates

Add library loading support to `MediaViewModel`:

```kotlin
private fun loadLibrary(entityType: String?) {
    // If entityType is null: load all media
    // If entityType provided: filter by entity_type
    // Uses MediaRepository.getAll() or MediaRepository.getForEntity()
    // Flows into state.mediaFiles
}
```

---

## Navigation Additions

```kotlin
// In MainNavGraph.kt — complete media sub-graph
composable<ZyntaRoute.MediaLibrary> {
    val vm = koinViewModel<MediaViewModel>()
    MediaLibraryScreen(
        viewModel = vm,
        onNavigateToUpload = { type, id ->
            navController.navigate(ZyntaRoute.MediaPicker(type, id))
        },
        onNavigateToAssignment = { mediaId ->
            navController.navigate(ZyntaRoute.MediaAssignment(mediaId))
        },
        onNavigateBack = { navController.popBackStack() }
    )
}
composable<ZyntaRoute.MediaAssignment> { backStackEntry ->
    val route: ZyntaRoute.MediaAssignment = backStackEntry.toRoute()
    val vm = koinViewModel<MediaViewModel>()
    LaunchedEffect(route.mediaId) {
        vm.handleIntent(MediaIntent.SelectFile(/* find by id */))
    }
    MediaAssignmentScreen(
        mediaId = route.mediaId,
        viewModel = vm,
        onNavigateBack = { navController.popBackStack() }
    )
}
```

Add `MediaAssignment` route to `ZyntaRoute.kt`:

```kotlin
data class MediaAssignment(val mediaId: String) : ZyntaRoute()
```

---

## Tasks

- [ ] **17.1** Implement `MediaLibraryScreen.kt` with `LazyVerticalGrid`, entity type filter chips, and delete confirmation
- [ ] **17.2** Implement `MediaAssignmentScreen.kt` with image preview, metadata card, and assign form
- [ ] **17.3** Update `MediaViewModel.loadLibrary()` to support optional `entityType` filter
- [ ] **17.4** Add `MediaAssignment` route to `ZyntaRoute.kt` and wire in `MainNavGraph.kt`
- [ ] **17.5** Update `ProductFormScreen` in `:composeApp:feature:inventory` to add image section with "Add Image" button
- [ ] **17.6** Handle `MediaPicker` → `ProductFormScreen` return value via `NavBackStackEntry` saved state
- [ ] **17.7** Add "Media Library" nav item to navigation (MANAGE_MEDIA permission)
- [ ] **17.8** Write `MediaLibraryViewModelTest` — test filter by entity type, delete removes from state
- [ ] **17.9** Verify media pipeline end-to-end: Product → Add Image → Pick → Compress → Upload → Product imageUrl updated
- [ ] **17.10** Verify: `./gradlew :composeApp:feature:media:assemble && ./gradlew :composeApp:feature:inventory:assemble`

---

## Verification

```bash
./gradlew :composeApp:feature:media:assemble
./gradlew :composeApp:feature:inventory:assemble
./gradlew :composeApp:feature:media:test
./gradlew :composeApp:feature:media:detekt
```

---

## Definition of Done

- [ ] `MediaLibraryScreen` shows all uploaded media in a 3-column grid with entity type filtering
- [ ] `MediaAssignmentScreen` shows image metadata and allows reassignment to any entity
- [ ] Delete confirmation available from both library and assignment screens
- [ ] `ProductFormScreen` has "Add Image" button that launches the media picker
- [ ] On media upload success, product `imageUrl` is updated
- [ ] Media library filter tests pass
- [ ] `:composeApp:feature:media` module fully assembled without errors
- [ ] Commit: `feat(media): add media library, entity assignment, and product image integration`
