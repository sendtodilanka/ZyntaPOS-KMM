package com.zyntasolutions.zyntapos.hal.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Android implementation of [ImageProcessor] using [Bitmap] APIs.
 *
 * Supports JPEG, PNG, and WEBP decoding on all API levels.
 */
class ImageProcessorImpl : ImageProcessor {

    override suspend fun compress(
        inputPath: String,
        outputPath: String,
        quality: Int,
        maxSizeKb: Int,
    ): ImageResult = withContext(Dispatchers.IO) {
        val source = BitmapFactory.decodeFile(inputPath)
            ?: error("Cannot decode image: $inputPath")

        var currentQuality = quality.coerceIn(10, 100)
        val out = ByteArrayOutputStream()

        do {
            out.reset()
            source.compress(Bitmap.CompressFormat.JPEG, currentQuality, out)
            currentQuality -= 10
        } while (out.size() > maxSizeKb * 1024 && currentQuality > 10)

        File(outputPath).writeBytes(out.toByteArray())
        source.recycle()

        ImageResult(
            outputPath = outputPath,
            widthPx = source.width,
            heightPx = source.height,
            fileSizeBytes = out.size().toLong(),
        )
    }

    override suspend fun generateThumbnail(
        inputPath: String,
        outputPath: String,
        sizePx: Int,
    ): ImageResult = withContext(Dispatchers.IO) {
        val source = BitmapFactory.decodeFile(inputPath)
            ?: error("Cannot decode image: $inputPath")

        // Centre-crop to square
        val side = minOf(source.width, source.height)
        val xOffset = (source.width - side) / 2
        val yOffset = (source.height - side) / 2
        val square = Bitmap.createBitmap(source, xOffset, yOffset, side, side)
        source.recycle()

        // Scale to target size
        val scaled = Bitmap.createScaledBitmap(square, sizePx, sizePx, true)
        square.recycle()

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        File(outputPath).writeBytes(out.toByteArray())
        scaled.recycle()

        ImageResult(
            outputPath = outputPath,
            widthPx = sizePx,
            heightPx = sizePx,
            fileSizeBytes = out.size().toLong(),
        )
    }

    override suspend fun resize(
        inputPath: String,
        outputPath: String,
        maxWidthPx: Int,
        maxHeightPx: Int,
        quality: Int,
    ): ImageResult = withContext(Dispatchers.IO) {
        val source = BitmapFactory.decodeFile(inputPath)
            ?: error("Cannot decode image: $inputPath")

        val widthRatio = maxWidthPx.toFloat() / source.width
        val heightRatio = maxHeightPx.toFloat() / source.height
        val scale = minOf(widthRatio, heightRatio, 1.0f) // Never upscale

        val matrix = Matrix().apply { postScale(scale, scale) }
        val resized = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        source.recycle()

        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(0, 100), out)
        File(outputPath).writeBytes(out.toByteArray())
        val w = resized.width
        val h = resized.height
        resized.recycle()

        ImageResult(
            outputPath = outputPath,
            widthPx = w,
            heightPx = h,
            fileSizeBytes = out.size().toLong(),
        )
    }
}
