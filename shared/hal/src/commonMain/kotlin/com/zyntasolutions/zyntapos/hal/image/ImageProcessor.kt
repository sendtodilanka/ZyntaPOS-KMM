package com.zyntasolutions.zyntapos.hal.image

/**
 * Hardware Abstraction Layer interface for image processing operations.
 *
 * Provides platform-agnostic operations for compressing, resizing, and generating
 * thumbnails from image files. Concrete implementations use platform-specific
 * APIs (Android Bitmap / JVM ImageIO).
 *
 * All operations are pure functions — they read from [inputPath] and write to [outputPath].
 * The caller is responsible for managing the output file lifecycle.
 */
interface ImageProcessor {

    /**
     * Compresses an image to the target quality and maximum file size.
     *
     * @param inputPath  Absolute path to the source image (JPEG, PNG, WEBP supported).
     * @param outputPath Absolute path to write the compressed image.
     * @param quality    JPEG/WEBP quality (0–100). Ignored for PNG (lossless).
     * @param maxSizeKb  Maximum output size in KB. Compression iterates until satisfied.
     * @return [ImageResult] with the output dimensions and final file size.
     */
    suspend fun compress(
        inputPath: String,
        outputPath: String,
        quality: Int = 80,
        maxSizeKb: Int = 500,
    ): ImageResult

    /**
     * Generates a square thumbnail from the source image.
     *
     * The image is centre-cropped to the largest possible square, then scaled
     * down to [sizePx] × [sizePx] pixels.
     *
     * @param inputPath  Absolute path to the source image.
     * @param outputPath Absolute path to write the thumbnail.
     * @param sizePx     Thumbnail side length in pixels (default 256).
     * @return [ImageResult] with the thumbnail dimensions and file size.
     */
    suspend fun generateThumbnail(
        inputPath: String,
        outputPath: String,
        sizePx: Int = 256,
    ): ImageResult

    /**
     * Resizes an image to fit within [maxWidthPx] × [maxHeightPx] while preserving aspect ratio.
     *
     * @param inputPath   Absolute path to the source image.
     * @param outputPath  Absolute path to write the resized image.
     * @param maxWidthPx  Maximum output width in pixels.
     * @param maxHeightPx Maximum output height in pixels.
     * @param quality     JPEG/WEBP quality (0–100).
     * @return [ImageResult] with the output dimensions and file size.
     */
    suspend fun resize(
        inputPath: String,
        outputPath: String,
        maxWidthPx: Int,
        maxHeightPx: Int,
        quality: Int = 85,
    ): ImageResult
}

/**
 * Result of an [ImageProcessor] operation.
 *
 * @property outputPath   Absolute path of the written file.
 * @property widthPx      Output image width in pixels.
 * @property heightPx     Output image height in pixels.
 * @property fileSizeBytes File size of the output in bytes.
 */
data class ImageResult(
    val outputPath: String,
    val widthPx: Int,
    val heightPx: Int,
    val fileSizeBytes: Long,
)
