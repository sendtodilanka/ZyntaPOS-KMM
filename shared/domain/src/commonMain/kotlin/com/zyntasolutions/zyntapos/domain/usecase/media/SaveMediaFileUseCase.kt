package com.zyntasolutions.zyntapos.domain.usecase.media

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.MediaFile
import com.zyntasolutions.zyntapos.domain.repository.MediaRepository

/**
 * Saves a media file record to the local database.
 *
 * ### Business Rules
 * 1. [MediaFile.fileName] must not be blank.
 * 2. [MediaFile.filePath] must not be blank.
 * 3. [MediaFile.fileSize] must be greater than 0.
 * 4. [MediaFile.uploadedBy] must not be blank.
 */
class SaveMediaFileUseCase(
    private val mediaRepository: MediaRepository,
) {
    suspend operator fun invoke(file: MediaFile): Result<Unit> {
        if (file.fileName.isBlank()) {
            return Result.Error(
                ValidationException("File name must not be blank.", field = "fileName", rule = "REQUIRED"),
            )
        }
        if (file.filePath.isBlank()) {
            return Result.Error(
                ValidationException("File path must not be blank.", field = "filePath", rule = "REQUIRED"),
            )
        }
        if (file.fileSize <= 0) {
            return Result.Error(
                ValidationException("File size must be greater than 0.", field = "fileSize", rule = "MIN_VALUE"),
            )
        }
        if (file.uploadedBy.isBlank()) {
            return Result.Error(
                ValidationException("Uploader must not be blank.", field = "uploadedBy", rule = "REQUIRED"),
            )
        }
        return mediaRepository.insert(file)
    }
}
