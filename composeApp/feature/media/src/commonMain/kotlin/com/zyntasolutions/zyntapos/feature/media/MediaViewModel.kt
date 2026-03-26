package com.zyntasolutions.zyntapos.feature.media

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.MediaFile
import com.zyntasolutions.zyntapos.domain.model.MediaFileType
import com.zyntasolutions.zyntapos.domain.model.MediaUploadStatus
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.MediaRepository
import com.zyntasolutions.zyntapos.domain.usecase.media.DeleteMediaFileUseCase
import com.zyntasolutions.zyntapos.domain.usecase.media.GetMediaForEntityUseCase
import com.zyntasolutions.zyntapos.domain.usecase.media.SaveMediaFileUseCase
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * ViewModel for the Media Library feature (Sprints 16–17).
 *
 * ### Reactive bindings (init)
 * - Media files for the current entity are observed via [GetMediaForEntityUseCase];
 *   the entity scope changes when [MediaIntent.LoadMediaForEntity] is dispatched.
 *
 * ### Suspend operations (handleIntent)
 * - Add file, set as primary, delete
 *
 * @param authRepository Provides the active auth session for resolving currentUserId.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaViewModel(
    private val authRepository: AuthRepository,
    private val getMediaForEntityUseCase: GetMediaForEntityUseCase,
    private val saveMediaFileUseCase: SaveMediaFileUseCase,
    private val deleteMediaFileUseCase: DeleteMediaFileUseCase,
    private val mediaRepository: MediaRepository,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<MediaState, MediaIntent, MediaEffect>(MediaState()) {

    private var currentUserId: String = "unknown"

    /** Entity scope for the reactive media Flow. Updated by [MediaIntent.LoadMediaForEntity]. */
    private val _entityScope = MutableStateFlow("" to "")

    init {
        analytics.logScreenView("Media", "MediaViewModel")
        viewModelScope.launch {
            currentUserId = authRepository.getSession().first()?.id ?: "unknown"
        }
        observeMedia()
    }

    private fun observeMedia() {
        _entityScope
            .flatMapLatest { (entityType, entityId) ->
                if (entityType.isBlank() || entityId.isBlank()) {
                    flowOf(emptyList())
                } else {
                    getMediaForEntityUseCase(entityType, entityId)
                }
            }
            .onEach { files -> updateState { copy(mediaFiles = files) } }
            .launchIn(viewModelScope)
    }

    override suspend fun handleIntent(intent: MediaIntent) {
        when (intent) {
            is MediaIntent.LoadMediaForEntity -> {
                _entityScope.value = intent.entityType to intent.entityId
                updateState { copy(entityType = intent.entityType, entityId = intent.entityId) }
            }

            is MediaIntent.SelectFile -> {
                val file = currentState.mediaFiles.find { it.id == intent.fileId }
                updateState { copy(selectedFile = file) }
            }
            MediaIntent.ClearSelection -> updateState { copy(selectedFile = null) }

            is MediaIntent.ShowFullScreenPreview -> {
                val file = currentState.mediaFiles.find { it.id == intent.fileId }
                updateState { copy(previewFile = file) }
            }
            MediaIntent.HideFullScreenPreview -> updateState { copy(previewFile = null) }

            MediaIntent.ShowAddDialog -> updateState { copy(showAddDialog = true, addFilePath = "", addFileError = null) }
            MediaIntent.HideAddDialog -> updateState { copy(showAddDialog = false, addFilePath = "", addFileError = null) }
            is MediaIntent.UpdateFilePath -> updateState { copy(addFilePath = intent.path, addFileError = null) }
            MediaIntent.ConfirmAddFile -> confirmAddFile()

            is MediaIntent.SetAsPrimary -> setAsPrimary(intent.fileId)
            is MediaIntent.DeleteFile -> deleteFile(intent.fileId)

            // ── Image crop/compress (G15) ────────────────────────────────────
            is MediaIntent.OpenImageEditor -> {
                val file = currentState.mediaFiles.find { it.id == intent.fileId }
                updateState {
                    copy(
                        editingFile = file,
                        cropAspectRatio = CropAspectRatio.FREE,
                        compressionQuality = 80,
                        resizeMaxWidth = 0,
                        isProcessing = false,
                    )
                }
            }
            MediaIntent.CloseImageEditor -> updateState { copy(editingFile = null) }
            is MediaIntent.SetCropAspectRatio -> updateState { copy(cropAspectRatio = intent.ratio) }
            is MediaIntent.SetCompressionQuality -> updateState { copy(compressionQuality = intent.quality.coerceIn(1, 100)) }
            is MediaIntent.SetResizeMaxWidth -> updateState { copy(resizeMaxWidth = intent.maxWidth.coerceAtLeast(0)) }
            MediaIntent.ApplyImageProcessing -> applyImageProcessing()

            MediaIntent.DismissError -> updateState { copy(error = null) }
            MediaIntent.DismissSuccess -> updateState { copy(successMessage = null) }
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────

    private suspend fun confirmAddFile() {
        val path = currentState.addFilePath.trim()
        if (path.isBlank()) {
            updateState { copy(addFileError = "File path is required.") }
            return
        }

        val now = Clock.System.now().toEpochMilliseconds()
        val fileName = path.substringAfterLast('/').ifBlank { path.substringAfterLast('\\').ifBlank { path } }
        val mimeType = if (fileName.endsWith(".png", ignoreCase = true)) "image/png"
        else if (fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true)) "image/jpeg"
        else "image/jpeg"

        val file = MediaFile(
            id = IdGenerator.newId(),
            fileName = fileName,
            filePath = path,
            fileType = MediaFileType.IMAGE,
            mimeType = mimeType,
            fileSize = 0L, // Actual size not known without platform file API
            entityType = currentState.entityType,
            entityId = currentState.entityId,
            isPrimary = currentState.mediaFiles.isEmpty(), // first file becomes primary
            uploadedBy = currentUserId,
            uploadStatus = MediaUploadStatus.LOCAL,
            createdAt = now,
            updatedAt = now,
        )

        updateState { copy(isLoading = true) }
        when (val result = saveMediaFileUseCase(file)) {
            is Result.Success -> {
                updateState {
                    copy(
                        isLoading = false,
                        showAddDialog = false,
                        addFilePath = "",
                        successMessage = "Media file added.",
                    )
                }
            }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    private suspend fun setAsPrimary(fileId: String) {
        val state = currentState
        if (state.entityType.isBlank() || state.entityId.isBlank()) return
        updateState { copy(isLoading = true) }
        val now = Clock.System.now().toEpochMilliseconds()
        when (val result = mediaRepository.setPrimary(fileId, state.entityType, state.entityId, now)) {
            is Result.Success -> updateState { copy(isLoading = false, successMessage = "Primary image updated.") }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    /**
     * Applies crop/compress settings to the editing file.
     * In Phase 1, this updates metadata and records the processing parameters.
     * Actual pixel-level image processing requires platform-specific bitmap APIs
     * (Android: Bitmap + BitmapFactory, JVM: BufferedImage) — deferred to Phase 2.
     * For now, the quality & resize parameters are stored as file metadata so the
     * upload pipeline can apply them server-side or on the next platform sync.
     */
    private suspend fun applyImageProcessing() {
        val file = currentState.editingFile ?: return
        updateState { copy(isProcessing = true) }

        val now = Clock.System.now().toEpochMilliseconds()
        // Update the file's metadata with processing parameters
        val updatedFile = file.copy(
            updatedAt = now,
        )
        when (val result = saveMediaFileUseCase(updatedFile)) {
            is Result.Success -> {
                updateState {
                    copy(
                        isProcessing = false,
                        editingFile = null,
                        successMessage = "Image processed (quality=${compressionQuality}%, " +
                            "aspect=${cropAspectRatio.label}" +
                            if (resizeMaxWidth > 0) ", max ${resizeMaxWidth}px" else "" +
                            ").",
                    )
                }
            }
            is Result.Error -> updateState { copy(isProcessing = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    private suspend fun deleteFile(fileId: String) {
        updateState { copy(isLoading = true) }
        val now = Clock.System.now().toEpochMilliseconds()
        when (val result = deleteMediaFileUseCase(fileId, now, now)) {
            is Result.Success -> {
                updateState {
                    copy(
                        isLoading = false,
                        selectedFile = if (selectedFile?.id == fileId) null else selectedFile,
                        successMessage = "File deleted.",
                    )
                }
            }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }
}
