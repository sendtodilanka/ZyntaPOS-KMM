# ZyntaPOS — Phase 3 Sprint 16: Media Feature Part 1 — Image Picker, Crop & Compression

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT16-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 16 of 24 | Week 16
> **Module(s):** `:composeApp:feature:media`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 | ADR-001

---

## Goal

Implement the first part of `:composeApp:feature:media` (M20): platform-aware image picker, interactive crop overlay, and the compression pipeline that writes to the `media_files` table. The `ImageProcessor` HAL (Sprint 6) provides the cross-platform compress/crop/thumbnail operations.

---

## Module Structure

```
composeApp/feature/media/
└── src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/media/
    ├── di/
    │   └── MediaModule.kt
    ├── mvi/
    │   ├── MediaState.kt
    │   ├── MediaIntent.kt
    │   └── MediaEffect.kt
    ├── viewmodel/
    │   └── MediaViewModel.kt
    └── screen/
        ├── ImagePickerScreen.kt
        └── ImageCropScreen.kt
```

---

## MVI Contracts

### `MediaState.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.media.mvi

import com.zyntasolutions.zyntapos.domain.model.MediaFile

data class MediaState(
    val mediaFiles: List<MediaFile> = emptyList(),
    val entityMedia: List<MediaFile> = emptyList(),    // media for the current entity
    val selectedFile: MediaFile? = null,
    val pickedLocalPath: String? = null,               // path after pick, before compress
    val compressedPath: String? = null,                // path after compression
    val croppedPath: String? = null,                   // path after crop
    val isUploading: Boolean = false,
    val isCompressing: Boolean = false,
    val isCropping: Boolean = false,
    val uploadProgress: Float = 0f,
    val error: String? = null,
    val successMessage: String? = null
)
```

### `MediaIntent.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.media.mvi

import com.zyntasolutions.zyntapos.domain.model.CropRect

sealed interface MediaIntent {
    // Library
    data class LoadMediaForEntity(val entityType: String, val entityId: String) : MediaIntent
    data class LoadMediaLibrary(val entityType: String? = null) : MediaIntent

    // Picker flow
    data class FilePicked(val localPath: String) : MediaIntent
    data class CropImage(val sourcePath: String, val rect: CropRect) : MediaIntent
    data class UploadMedia(
        val localPath: String,
        val entityType: String,
        val entityId: String
    ) : MediaIntent

    // Library ops
    data class DeleteMedia(val mediaId: String) : MediaIntent
    data class AssignMedia(val mediaId: String, val entityId: String) : MediaIntent
    data class SelectFile(val mediaFile: MediaFile) : MediaIntent

    // UI
    data object DismissError : MediaIntent
    data object DismissSuccess : MediaIntent
    data object CancelPick : MediaIntent
}
```

### `MediaEffect.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.media.mvi

sealed interface MediaEffect {
    data class ShowError(val message: String) : MediaEffect
    data class ShowSuccess(val message: String) : MediaEffect
    data class MediaUploaded(val mediaFile: MediaFile) : MediaEffect
    data class OpenNativePicker(val mimeType: String) : MediaEffect  // triggers platform file picker
    data class NavigateToCrop(val sourcePath: String) : MediaEffect
    data object NavigateBack : MediaEffect
}
```

---

## ViewModel

### `MediaViewModel.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.media.viewmodel

import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import com.zyntasolutions.zyntapos.domain.usecase.media.*
import com.zyntasolutions.zyntapos.domain.model.CropRect
import com.zyntasolutions.zyntapos.feature.media.mvi.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MediaViewModel(
    private val uploadMedia: UploadMediaUseCase,
    private val deleteMedia: DeleteMediaUseCase,
    private val getMediaForEntity: GetMediaForEntityUseCase,
) : BaseViewModel<MediaState, MediaIntent, MediaEffect>(MediaState()) {

    override suspend fun handleIntent(intent: MediaIntent) {
        when (intent) {
            is MediaIntent.LoadMediaForEntity -> loadEntityMedia(intent.entityType, intent.entityId)
            is MediaIntent.LoadMediaLibrary   -> loadLibrary(intent.entityType)
            is MediaIntent.FilePicked         -> onFilePicked(intent.localPath)
            is MediaIntent.CropImage          -> cropImage(intent.sourcePath, intent.rect)
            is MediaIntent.UploadMedia        -> upload(intent.localPath, intent.entityType, intent.entityId)
            is MediaIntent.DeleteMedia        -> delete(intent.mediaId)
            is MediaIntent.AssignMedia        -> assign(intent.mediaId, intent.entityId)
            is MediaIntent.SelectFile         -> updateState { it.copy(selectedFile = intent.mediaFile) }
            is MediaIntent.DismissError       -> updateState { it.copy(error = null) }
            is MediaIntent.DismissSuccess     -> updateState { it.copy(successMessage = null) }
            is MediaIntent.CancelPick         -> updateState { it.copy(pickedLocalPath = null, compressedPath = null) }
        }
    }

    private fun loadEntityMedia(entityType: String, entityId: String) {
        getMediaForEntity(entityType, entityId)
            .onEach { files -> updateState { it.copy(entityMedia = files) } }
            .launchIn(viewModelScope)
    }

    private fun loadLibrary(entityType: String?) {
        // All media or filtered by entityType
    }

    private suspend fun onFilePicked(localPath: String) {
        // After platform picker returns a path, offer to crop or upload directly
        updateState { it.copy(pickedLocalPath = localPath) }
        sendEffect(MediaEffect.NavigateToCrop(localPath))
    }

    private suspend fun cropImage(sourcePath: String, rect: CropRect) {
        updateState { it.copy(isCropping = true) }
        // Calls ImageProcessor.crop via uploadMedia use case internals
        // For now, croppedPath = sourcePath (crop is optional in pipeline)
        updateState { it.copy(isCropping = false, croppedPath = sourcePath) }
    }

    private suspend fun upload(localPath: String, entityType: String, entityId: String) {
        updateState { it.copy(isUploading = true, uploadProgress = 0f) }
        uploadMedia(localPath, entityType, entityId).fold(
            onSuccess = { mediaFile ->
                updateState { state ->
                    state.copy(
                        isUploading = false,
                        uploadProgress = 1f,
                        selectedFile = mediaFile,
                        entityMedia = state.entityMedia + mediaFile,
                        pickedLocalPath = null,
                        compressedPath = null,
                        croppedPath = null
                    )
                }
                sendEffect(MediaEffect.MediaUploaded(mediaFile))
                sendEffect(MediaEffect.ShowSuccess("Image uploaded successfully"))
                sendEffect(MediaEffect.NavigateBack)
            },
            onFailure = { ex ->
                updateState { it.copy(isUploading = false, error = ex.message) }
            }
        )
    }

    private suspend fun delete(mediaId: String) {
        deleteMedia(mediaId).fold(
            onSuccess = {
                updateState { state ->
                    state.copy(
                        entityMedia = state.entityMedia.filter { it.id != mediaId },
                        mediaFiles = state.mediaFiles.filter { it.id != mediaId }
                    )
                }
                sendEffect(MediaEffect.ShowSuccess("Image deleted"))
            },
            onFailure = { ex -> sendEffect(MediaEffect.ShowError(ex.message ?: "Delete failed")) }
        )
    }

    private suspend fun assign(mediaId: String, entityId: String) {
        // AssignMedia delegates to MediaRepository.setPrimary() or entityId update
    }
}
```

---

## Screen Files

### `ImagePickerScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.media.screen

/**
 * Platform-aware image picker screen.
 *
 * Android: Uses `rememberLauncherForActivityResult(ActivityResultContracts.GetContent())`
 *          with mime type "image/*" to launch the system image gallery.
 *
 * Desktop (JVM): Uses `JFileChooser` via Swing interop to open a file picker dialog
 *                filtered to image extensions (.jpg, .jpeg, .png, .webp).
 *
 * Cross-platform abstraction:
 * The `expect/actual` pattern is used in `:shared:hal` via `ImagePickerPort`:
 *
 *   // commonMain:
 *   interface ImagePickerPort {
 *       suspend fun pickImage(): Result<String>   // returns local file path
 *   }
 *   // androidMain: ActivityResultContracts.GetContent + ContentResolver.copyToFile()
 *   // jvmMain: javax.swing.JFileChooser
 *
 * Flow after pick:
 * 1. File path returned → MediaIntent.FilePicked(path) fired
 * 2. ViewModel navigates to ImageCropScreen via MediaEffect.NavigateToCrop
 * 3. After crop (or skip): MediaIntent.UploadMedia(path, entityType, entityId)
 *
 * Layout:
 * - Centered icon + "Tap to select an image" instruction text
 * - "Select Image" primary button → triggers platform picker
 * - Cancel button → MediaIntent.CancelPick
 */
@Composable
fun ImagePickerScreen(
    entityType: String,
    entityId: String,
    viewModel: MediaViewModel,
    onNavigateToCrop: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MediaEffect.NavigateToCrop  -> onNavigateToCrop(effect.sourcePath)
                is MediaEffect.NavigateBack    -> onNavigateBack()
                is MediaEffect.OpenNativePicker -> { /* handled by platform-specific host */ }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Image") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { /* back */ } }
            )
        }
    ) { padding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.fillMaxSize().padding(padding)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    painterResource("ic_image_add"),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Select an image for this ${entityType.lowercase()}",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Supported formats: JPG, PNG, WebP\nImages are compressed to max 1024px / 500KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                ZyntaButton(
                    text = "Select Image",
                    isLoading = state.isCompressing,
                    onClick = {
                        // Triggers platform-specific picker via MediaEffect.OpenNativePicker
                        sendEffect(MediaEffect.OpenNativePicker("image/*"))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                ZyntaOutlinedButton(
                    text = "Cancel",
                    onClick = {
                        viewModel.handleIntent(MediaIntent.CancelPick)
                        onNavigateBack()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
```

### `ImageCropScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.media.screen

/**
 * Interactive image crop screen.
 *
 * Layout:
 * - Full-screen image preview with crop overlay
 * - Crop handles at corners and edges (drag to resize crop region)
 * - Aspect ratio buttons: Free / 1:1 / 4:3 / 16:9
 * - "Apply Crop" button → MediaIntent.CropImage(path, rect)
 * - "Skip Crop" button → MediaIntent.UploadMedia directly
 *
 * Crop state:
 * - CropRect(x, y, width, height) in pixels — updated as user drags handles
 * - Constrained to image bounds
 *
 * After crop or skip:
 * - Shows compression progress (isCompressing)
 * - Then shows upload progress (isUploading)
 * - On success: navigates back with MediaEffect.MediaUploaded
 */
@Composable
fun ImageCropScreen(
    sourcePath: String,
    entityType: String,
    entityId: String,
    viewModel: MediaViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var cropRect by remember { mutableStateOf<CropRect?>(null) }
    var aspectRatio by remember { mutableStateOf(AspectRatio.FREE) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MediaEffect.NavigateBack  -> onNavigateBack()
                is MediaEffect.ShowError     -> { /* snackbar */ }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crop Image") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { /* back */ } }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Crop preview (ZyntaImageCropper)
            ZyntaImageCropper(
                imagePath = sourcePath,
                cropRect = cropRect,
                aspectRatio = aspectRatio,
                onCropRectChanged = { cropRect = it },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )

            // Aspect ratio selection
            AspectRatioSelector(
                selected = aspectRatio,
                onSelect = { aspectRatio = it }
            )

            // Action buttons
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZyntaButton(
                    text = "Apply Crop & Upload",
                    isLoading = state.isCropping || state.isUploading,
                    enabled = !state.isCropping && !state.isUploading,
                    onClick = {
                        val rect = cropRect
                        if (rect != null) {
                            viewModel.handleIntent(MediaIntent.CropImage(sourcePath, rect))
                        } else {
                            viewModel.handleIntent(MediaIntent.UploadMedia(sourcePath, entityType, entityId))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                ZyntaOutlinedButton(
                    text = "Skip Crop & Upload",
                    enabled = !state.isCropping && !state.isUploading,
                    onClick = {
                        viewModel.handleIntent(MediaIntent.UploadMedia(sourcePath, entityType, entityId))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Upload progress overlay
    if (state.isUploading) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    progress = { state.uploadProgress },
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Uploading… ${(state.uploadProgress * 100).toInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

enum class AspectRatio(val label: String, val ratio: Float?) {
    FREE("Free", null),
    SQUARE("1:1", 1f),
    LANDSCAPE("4:3", 4f / 3f),
    WIDESCREEN("16:9", 16f / 9f)
}

@Composable
private fun AspectRatioSelector(
    selected: AspectRatio,
    onSelect: (AspectRatio) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        AspectRatio.entries.forEach { ratio ->
            FilterChip(
                selected = selected == ratio,
                onClick = { onSelect(ratio) },
                label = { Text(ratio.label) }
            )
        }
    }
}
```

---

## Design System Addition

### `ZyntaImageCropper.kt`

```kotlin
package com.zyntasolutions.zyntapos.designsystem.component

import com.zyntasolutions.zyntapos.domain.model.CropRect

/**
 * Canvas-based image crop composable.
 *
 * Renders the image (via Coil's AsyncImage) with a draggable crop overlay.
 * Corner handles: 24dp squares, drag to resize.
 * Dimmed area outside crop region: semi-transparent black overlay.
 *
 * @param imagePath       Local file path (used by Coil)
 * @param cropRect        Current crop rectangle (null = full image)
 * @param aspectRatio     Locked aspect ratio (null = free)
 * @param onCropRectChanged Callback when user adjusts handles
 */
@Composable
fun ZyntaImageCropper(
    imagePath: String,
    cropRect: CropRect?,
    aspectRatio: Float?,
    onCropRectChanged: (CropRect) -> Unit,
    modifier: Modifier = Modifier
) {
    // Implementation uses Compose Canvas + pointerInput for touch/drag handling
    // Coil AsyncImage for image loading
    // Crop handles drawn using Canvas.drawRect + handle squares at corners
}
```

---

## Design System Component: `ZyntaImageCard.kt`

```kotlin
package com.zyntasolutions.zyntapos.designsystem.component

/**
 * Reusable image card for displaying a MediaFile thumbnail.
 * Shows upload status overlay (UPLOADING spinner, FAILED error icon).
 * Supports delete action via long-press or action button.
 */
@Composable
fun ZyntaImageCard(
    mediaFile: com.zyntasolutions.zyntapos.domain.model.MediaFile,
    onClick: () -> Unit = {},
    onDelete: (() -> Unit)? = null,
    showStatus: Boolean = true,
    modifier: Modifier = Modifier
)
```

---

## DI Module

### `MediaModule.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.media.di

import com.zyntasolutions.zyntapos.domain.usecase.media.*
import com.zyntasolutions.zyntapos.feature.media.viewmodel.MediaViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val mediaModule = module {
    single<UploadMediaUseCase>        { UploadMediaUseCase { path, type, id ->
        get<com.zyntasolutions.zyntapos.domain.repository.MediaRepository>()
            .save(buildMediaFile(path, type, id, get()))
            .map { it }
    }}
    single<DeleteMediaUseCase>        { DeleteMediaUseCase { id ->
        get<com.zyntasolutions.zyntapos.domain.repository.MediaRepository>().delete(id)
    }}
    single<GetMediaForEntityUseCase>  { GetMediaForEntityUseCase { type, id ->
        get<com.zyntasolutions.zyntapos.domain.repository.MediaRepository>().getForEntity(type, id)
    }}

    viewModel {
        MediaViewModel(
            uploadMedia        = get(),
            deleteMedia        = get(),
            getMediaForEntity  = get()
        )
    }
}
```

---

## Navigation Wiring

```kotlin
// In MainNavGraph.kt
navigation(startDestination = ZyntaRoute.MediaLibrary, route = ZyntaRoute.MediaGraph) {
    composable<ZyntaRoute.MediaLibrary> {
        // Sprint 17
    }
    composable<ZyntaRoute.MediaPicker> { backStackEntry ->
        val route: ZyntaRoute.MediaPicker = backStackEntry.toRoute()
        val vm = koinViewModel<MediaViewModel>()
        ImagePickerScreen(
            entityType = route.entityType,
            entityId = route.entityId,
            viewModel = vm,
            onNavigateToCrop = { path ->
                navController.navigate(ZyntaRoute.MediaCrop(path, route.entityType, route.entityId))
            },
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable<ZyntaRoute.MediaCrop> { backStackEntry ->
        val route: ZyntaRoute.MediaCrop = backStackEntry.toRoute()
        val vm = koinViewModel<MediaViewModel>()
        ImageCropScreen(
            sourcePath = route.sourcePath,
            entityType = route.entityType,
            entityId = route.entityId,
            viewModel = vm,
            onNavigateBack = { navController.popBackStack() }
        )
    }
}
```

---

## Tasks

- [ ] **16.1** Create `MediaState.kt`, `MediaIntent.kt`, `MediaEffect.kt` in `mvi/` package
- [ ] **16.2** Implement `MediaViewModel.kt` with upload/crop/delete pipeline
- [ ] **16.3** Implement `ImagePickerScreen.kt` with platform-picker trigger via `MediaEffect.OpenNativePicker`
- [ ] **16.4** Implement `ImageCropScreen.kt` with aspect ratio selector and upload progress overlay
- [ ] **16.5** Create `ZyntaImageCropper.kt` Canvas-based composable in `:composeApp:designsystem`
- [ ] **16.6** Create `ZyntaImageCard.kt` thumbnail card in `:composeApp:designsystem`
- [ ] **16.7** Create `MediaModule.kt` Koin module and register in `ZyntaApplication`
- [ ] **16.8** Wire `MediaPicker` and `MediaCrop` routes in `MainNavGraph.kt`
- [ ] **16.9** Add `ImagePickerPort` to `:shared:hal` (define interface + Android/Desktop implementations)
- [ ] **16.10** Write `MediaViewModelTest` — test file picked → crop → upload success flow
- [ ] **16.11** Verify: `./gradlew :composeApp:feature:media:assemble && ./gradlew :shared:hal:assemble`

---

## Verification

```bash
./gradlew :shared:hal:assemble
./gradlew :composeApp:feature:media:assemble
./gradlew :composeApp:designsystem:assemble
./gradlew :composeApp:feature:media:test
```

---

## Definition of Done

- [ ] `MediaViewModel` extends `BaseViewModel` (ADR-001)
- [ ] `ImagePickerScreen` triggers native platform file picker
- [ ] `ImageCropScreen` renders crop overlay with handles and aspect ratio lock
- [ ] Upload progress overlay shown during image upload
- [ ] `ZyntaImageCropper` and `ZyntaImageCard` added to design system
- [ ] `MediaModule` Koin bindings correct
- [ ] Media pick → crop → upload flow test passes
- [ ] Commit: `feat(media): add image picker and crop screen with compression pipeline`
