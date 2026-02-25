package com.zyntasolutions.zyntapos.hal.image

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.plugins.jpeg.JPEGImageWriteParam

/**
 * JVM/Desktop implementation of [ImageProcessor] using Java AWT [ImageIO].
 *
 * Supports JPEG and PNG formats on all JVM targets (macOS, Windows, Linux).
 */
class ImageProcessorImpl : ImageProcessor {

    override suspend fun compress(
        inputPath: String,
        outputPath: String,
        quality: Int,
        maxSizeKb: Int,
    ): ImageResult = withContext(Dispatchers.IO) {
        val source = ImageIO.read(File(inputPath))
            ?: error("Cannot decode image: $inputPath")

        var currentQuality = quality.coerceIn(10, 100)
        var bytes: ByteArray

        do {
            val out = ByteArrayOutputStream()
            writeJpeg(source, out, currentQuality / 100f)
            bytes = out.toByteArray()
            currentQuality -= 10
        } while (bytes.size > maxSizeKb * 1024 && currentQuality > 10)

        File(outputPath).writeBytes(bytes)

        ImageResult(
            outputPath = outputPath,
            widthPx = source.width,
            heightPx = source.height,
            fileSizeBytes = bytes.size.toLong(),
        )
    }

    override suspend fun generateThumbnail(
        inputPath: String,
        outputPath: String,
        sizePx: Int,
    ): ImageResult = withContext(Dispatchers.IO) {
        val source = ImageIO.read(File(inputPath))
            ?: error("Cannot decode image: $inputPath")

        // Centre-crop to square
        val side = minOf(source.width, source.height)
        val xOffset = (source.width - side) / 2
        val yOffset = (source.height - side) / 2
        val square = source.getSubimage(xOffset, yOffset, side, side)

        // Scale to target size
        val scaled = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_RGB)
        val g = scaled.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(square, 0, 0, sizePx, sizePx, null)
        g.dispose()

        val out = ByteArrayOutputStream()
        writeJpeg(scaled, out, 0.85f)
        File(outputPath).writeBytes(out.toByteArray())

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
        val source = ImageIO.read(File(inputPath))
            ?: error("Cannot decode image: $inputPath")

        val widthRatio = maxWidthPx.toDouble() / source.width
        val heightRatio = maxHeightPx.toDouble() / source.height
        val scale = minOf(widthRatio, heightRatio, 1.0) // Never upscale

        val newWidth = (source.width * scale).toInt()
        val newHeight = (source.height * scale).toInt()

        val resized = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
        val g = resized.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(source, AffineTransform.getScaleInstance(scale, scale), null)
        g.dispose()

        val out = ByteArrayOutputStream()
        writeJpeg(resized, out, quality.coerceIn(0, 100) / 100f)
        File(outputPath).writeBytes(out.toByteArray())

        ImageResult(
            outputPath = outputPath,
            widthPx = newWidth,
            heightPx = newHeight,
            fileSizeBytes = out.size().toLong(),
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun writeJpeg(image: BufferedImage, out: ByteArrayOutputStream, qualityFraction: Float) {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val param = JPEGImageWriteParam(null).apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = qualityFraction
        }
        val ios = ImageIO.createImageOutputStream(out)
        writer.output = ios
        writer.write(null, IIOImage(image, null, null), param)
        writer.dispose()
        ios.close()
    }
}
