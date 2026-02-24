# ZyntaPOS — Phase 3 Sprint 6: Media Repository Implementation (expect/actual HAL)

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT6-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 6 of 24 | Week 6
> **Module(s):** `:shared:hal`, `:shared:data`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 §9 (HAL) | ZYNTA-PLAN-PHASE3-SPRINT4-v1.0

---

## Goal

Implement cross-platform image processing via a new `ImageProcessor` HAL interface with Android (BitmapFactory) and JVM (AWT) platform implementations. Implement `MediaRepositoryImpl` in `:shared:data` that orchestrates local storage metadata, compression, thumbnail generation, and async backend upload.

---

## New Files to Create

### HAL Interface (commonMain)

**Location:** `shared/hal/src/commonMain/kotlin/com/zyntasolutions/zyntapos/hal/`

#### `ImageProcessor.kt`
```kotlin
package com.zyntasolutions.zyntapos.hal

/**
 * Cross-platform image processing interface.
 * Android: uses BitmapFactory + Bitmap.compress
 * JVM Desktop: uses java.awt.Image + javax.imageio.ImageIO
 *
 * All paths are absolute platform file paths.
 */
interface ImageProcessor {

    /**
     * Compress an image to reduce file size.
     * @param sourcePath Absolute path to source image file
     * @param maxWidthPx Maximum width in pixels (aspect ratio preserved)
     * @param maxHeightPx Maximum height in pixels (aspect ratio preserved)
     * @param quality JPEG quality 1–100 (ignored for PNG)
     * @return Absolute path of the compressed output file
     */
    suspend fun compress(
        sourcePath: String,
        maxWidthPx: Int = 1024,
        maxHeightPx: Int = 1024,
        quality: Int = 80
    ): Result<String>

    /**
     * Crop an image to the specified rectangle.
     * @param sourcePath Absolute path to source image
     * @param rect Crop region in pixels (origin = top-left of source)
     * @return Absolute path of the cropped output file
     */
    suspend fun crop(sourcePath: String, rect: CropRect): Result<String>

    /**
     * Generate a square thumbnail.
     * @param sourcePath Absolute path to source image
     * @param sizePx Thumbnail dimension (both width and height)
     * @return Absolute path of the thumbnail file
     */
    suspend fun generateThumbnail(sourcePath: String, sizePx: Int = 256): Result<String>

    /** Returns true if the file at [path] is a valid supported image format. */
    suspend fun isValidImage(path: String): Boolean

    /** Returns the pixel dimensions of an image file. */
    suspend fun getDimensions(path: String): Result<ImageDimensions>
}

data class CropRect(val x: Int, val y: Int, val width: Int, val height: Int)

data class ImageDimensions(val width: Int, val height: Int)
```

### Android Implementation

**Location:** `shared/hal/src/androidMain/kotlin/com/zyntasolutions/zyntapos/hal/`

#### `AndroidImageProcessor.kt`
```kotlin
package com.zyntasolutions.zyntapos.hal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.File
import java.io.FileOutputStream

class AndroidImageProcessor(
    private val cacheDir: File    // Injected via Koin: androidContext().cacheDir
) : ImageProcessor {

    private val tempDir = File(cacheDir, "zyntapos_media_temp").also { it.mkdirs() }

    override suspend fun compress(
        sourcePath: String,
        maxWidthPx: Int,
        maxHeightPx: Int,
        quality: Int
    ): Result<String> = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourcePath, options)

        val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, maxWidthPx, maxHeightPx)
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize

        val bitmap = BitmapFactory.decodeFile(sourcePath, options)
            ?: throw IllegalArgumentException("Cannot decode image: $sourcePath")

        val scaled = scaleBitmap(bitmap, maxWidthPx, maxHeightPx)
        val outputFile = File(tempDir, "compressed_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outputFile).use { fos ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, fos)
        }
        bitmap.recycle()
        if (scaled != bitmap) scaled.recycle()
        outputFile.absolutePath
    }

    override suspend fun crop(sourcePath: String, rect: CropRect): Result<String> = runCatching {
        val bitmap = BitmapFactory.decodeFile(sourcePath)
            ?: throw IllegalArgumentException("Cannot decode image: $sourcePath")

        val cropped = Bitmap.createBitmap(bitmap, rect.x, rect.y, rect.width, rect.height)
        val outputFile = File(tempDir, "crop_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outputFile).use { cropped.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        cropped.recycle()
        outputFile.absolutePath
    }

    override suspend fun generateThumbnail(sourcePath: String, sizePx: Int): Result<String> = runCatching {
        val bitmap = BitmapFactory.decodeFile(sourcePath)
            ?: throw IllegalArgumentException("Cannot decode image: $sourcePath")

        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        val squared = Bitmap.createBitmap(bitmap, x, y, size, size)
        val thumb = Bitmap.createScaledBitmap(squared, sizePx, sizePx, true)

        val outputFile = File(tempDir, "thumb_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outputFile).use { thumb.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle(); squared.recycle(); thumb.recycle()
        outputFile.absolutePath
    }

    override suspend fun isValidImage(path: String): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    override suspend fun getDimensions(path: String): Result<ImageDimensions> = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        ImageDimensions(options.outWidth, options.outHeight)
    }

    private fun calculateSampleSize(srcW: Int, srcH: Int, maxW: Int, maxH: Int): Int {
        var size = 1
        while ((srcW / (size * 2)) > maxW || (srcH / (size * 2)) > maxH) size *= 2
        return size
    }

    private fun scaleBitmap(bitmap: Bitmap, maxW: Int, maxH: Int): Bitmap {
        val ratio = minOf(maxW.toFloat() / bitmap.width, maxH.toFloat() / bitmap.height)
        if (ratio >= 1f) return bitmap
        val matrix = Matrix().apply { postScale(ratio, ratio) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
```

### JVM Desktop Implementation

**Location:** `shared/hal/src/jvmMain/kotlin/com/zyntasolutions/zyntapos/hal/`

#### `DesktopImageProcessor.kt`
```kotlin
package com.zyntasolutions.zyntapos.hal

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class DesktopImageProcessor(
    private val cacheDir: File   // Injected via Koin: File(System.getProperty("user.home"), ".zyntapos/cache")
) : ImageProcessor {

    private val tempDir = File(cacheDir, "zyntapos_media_temp").also { it.mkdirs() }

    override suspend fun compress(
        sourcePath: String,
        maxWidthPx: Int,
        maxHeightPx: Int,
        quality: Int
    ): Result<String> = runCatching {
        val source = ImageIO.read(File(sourcePath))
            ?: throw IllegalArgumentException("Cannot read image: $sourcePath")

        val scaled = scaleImage(source, maxWidthPx, maxHeightPx)
        val outputFile = File(tempDir, "compressed_${System.currentTimeMillis()}.jpg")

        val writers = ImageIO.getImageWritersByFormatName("jpeg")
        val writer = writers.next()
        val param = writer.defaultWriteParam.apply {
            compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
            compressionQuality = quality / 100f
        }
        val ios = ImageIO.createImageOutputStream(outputFile)
        writer.output = ios
        writer.write(null, javax.imageio.IIOImage(scaled, null, null), param)
        ios.close()
        writer.dispose()
        outputFile.absolutePath
    }

    override suspend fun crop(sourcePath: String, rect: CropRect): Result<String> = runCatching {
        val source = ImageIO.read(File(sourcePath))
            ?: throw IllegalArgumentException("Cannot read image: $sourcePath")
        val cropped = source.getSubimage(rect.x, rect.y, rect.width, rect.height)
        val outputFile = File(tempDir, "crop_${System.currentTimeMillis()}.jpg")
        ImageIO.write(cropped, "jpeg", outputFile)
        outputFile.absolutePath
    }

    override suspend fun generateThumbnail(sourcePath: String, sizePx: Int): Result<String> = runCatching {
        val source = ImageIO.read(File(sourcePath))
            ?: throw IllegalArgumentException("Cannot read image: $sourcePath")
        val side = minOf(source.width, source.height)
        val x = (source.width - side) / 2
        val y = (source.height - side) / 2
        val squared = source.getSubimage(x, y, side, side)
        val thumb = scaleImage(squared, sizePx, sizePx)
        val outputFile = File(tempDir, "thumb_${System.currentTimeMillis()}.jpg")
        ImageIO.write(thumb, "jpeg", outputFile)
        outputFile.absolutePath
    }

    override suspend fun isValidImage(path: String): Boolean =
        runCatching { ImageIO.read(File(path)) != null }.getOrDefault(false)

    override suspend fun getDimensions(path: String): Result<ImageDimensions> = runCatching {
        val img = ImageIO.read(File(path)) ?: throw IllegalArgumentException("Cannot read: $path")
        ImageDimensions(img.width, img.height)
    }

    private fun scaleImage(src: BufferedImage, maxW: Int, maxH: Int): BufferedImage {
        val ratio = minOf(maxW.toFloat() / src.width, maxH.toFloat() / src.height)
        if (ratio >= 1f) return src
        val newW = (src.width * ratio).toInt()
        val newH = (src.height * ratio).toInt()
        val output = BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB)
        val g = output.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(src, 0, 0, newW, newH, null)
            dispose()
        }
        return output
    }
}
```

### HAL Module DI

**File to modify:** `shared/hal/src/androidMain/.../di/HalModule.android.kt`
```kotlin
// Add to halModule():
single<ImageProcessor> {
    AndroidImageProcessor(cacheDir = androidContext().cacheDir)
}
```

**File to modify:** `shared/hal/src/jvmMain/.../di/HalModule.desktop.kt`
```kotlin
// Add to halModule():
single<ImageProcessor> {
    val cacheDir = File(System.getProperty("user.home"), ".zyntapos/cache")
    DesktopImageProcessor(cacheDir = cacheDir)
}
```

### Media Repository Implementation

**Location:** `shared/data/src/commonMain/kotlin/com/zyntasolutions/zyntapos/data/repository/`

#### `MediaRepositoryImpl.kt`
```kotlin
class MediaRepositoryImpl(
    private val db: ZyntaPosDatabase,
    private val imageProcessor: ImageProcessor,
    private val apiService: ApiService          // For remote upload
) : MediaRepository {

    override fun getForEntity(entityType: String, entityId: String): Flow<List<MediaFile>> =
        db.mediaFilesQueries.selectByEntity(entityType, entityId)
            .asFlow().mapToList(Dispatchers.IO)
            .map { it.map(MediaMapper::toDomain) }

    override fun getPrimaryForEntity(entityType: String, entityId: String): Flow<MediaFile?> =
        db.mediaFilesQueries.selectPrimaryForEntity(entityType, entityId)
            .asFlow().mapToOneOrNull(Dispatchers.IO)
            .map { it?.let(MediaMapper::toDomain) }

    override fun getAll(): Flow<List<MediaFile>> =
        db.mediaFilesQueries.selectAll()
            .asFlow().mapToList(Dispatchers.IO)
            .map { it.map(MediaMapper::toDomain) }

    override suspend fun save(mediaFile: MediaFile): Result<MediaFile> = runCatching {
        db.mediaFilesQueries.upsert(MediaMapper.toDb(mediaFile))
        mediaFile
    }

    override suspend fun delete(id: String): Result<Unit> = runCatching {
        val now = Clock.System.now().toString()
        db.mediaFilesQueries.softDelete(id = id, deletedAt = now, updatedAt = now)
    }

    override suspend fun updateUploadStatus(
        id: String, status: MediaUploadStatus, remoteUrl: String?
    ): Result<Unit> = runCatching {
        db.mediaFilesQueries.updateUploadStatus(
            status = status.name,
            remoteUrl = remoteUrl,
            updatedAt = Clock.System.now().toString(),
            id = id
        )
    }

    /**
     * Full pipeline: compress → thumbnail → save metadata locally → enqueue for upload.
     * Actual upload is handled by background sync engine, not blocking here.
     */
    suspend fun prepareAndSave(
        sourcePath: String,
        entityType: String,
        entityId: String,
        uploadedBy: String
    ): Result<MediaFile> {
        val compressedPath = imageProcessor.compress(sourcePath).getOrElse { return Result.failure(it) }
        val thumbnailPath = imageProcessor.generateThumbnail(sourcePath).getOrNull()
        val fileSize = java.io.File(compressedPath).length()
        val now = Clock.System.now().toString()

        val mediaFile = MediaFile(
            id = generateUuid(),
            fileName = java.io.File(compressedPath).name,
            filePath = compressedPath,
            remoteUrl = null,
            fileType = MediaFileType.IMAGE,
            mimeType = "image/jpeg",
            fileSize = fileSize,
            thumbnailPath = thumbnailPath,
            entityType = entityType,
            entityId = entityId,
            isPrimary = false,
            uploadedBy = uploadedBy,
            uploadStatus = MediaUploadStatus.LOCAL,  // Will be uploaded by sync engine
            createdAt = now,
            updatedAt = now
        )
        return save(mediaFile)
    }
}
```

### Mapper File

**Location:** `shared/data/src/commonMain/kotlin/com/zyntasolutions/zyntapos/data/local/mapper/`

`MediaMapper.kt` — maps between SQLDelight `MediaFiles` type and `MediaFile` domain model.

### DataModule Update

Add to `DataModule.kt`:
```kotlin
single<MediaRepository> { MediaRepositoryImpl(get(), get(), get()) }
```

---

## Unit Tests

**Location:** `shared/data/src/jvmTest/kotlin/com/zyntasolutions/zyntapos/data/`

`DesktopImageProcessorTest.kt`:
- Test compress: output file exists, size < input size, dimensions ≤ maxWidth × maxHeight
- Test crop: output dimensions match crop rect
- Test thumbnail: output is square with correct size
- Test isValidImage: returns true for valid JPEG/PNG, false for text file

---

## Tasks

- [ ] **6.1** Create `ImageProcessor.kt` interface + `CropRect` + `ImageDimensions` in `shared/hal/src/commonMain/`
- [ ] **6.2** Implement `AndroidImageProcessor.kt` in `androidMain`
- [ ] **6.3** Implement `DesktopImageProcessor.kt` in `jvmMain`
- [ ] **6.4** Add `ImageProcessor` bindings to `halModule` in both `androidMain` and `jvmMain`
- [ ] **6.5** Create `MediaMapper.kt`
- [ ] **6.6** Implement `MediaRepositoryImpl.kt` with `prepareAndSave()` pipeline
- [ ] **6.7** Add `MediaRepository` binding to `DataModule.kt`
- [ ] **6.8** Write `DesktopImageProcessorTest` (jvmTest): compress, crop, thumbnail, isValidImage
- [ ] **6.9** Run `./gradlew :shared:hal:assemble` — both Android + JVM compile
- [ ] **6.10** Run `./gradlew :shared:data:jvmTest` — image processor tests pass
- [ ] **6.11** Run `./gradlew :shared:hal:detekt && ./gradlew :shared:data:detekt`

---

## Verification

```bash
# HAL compiles for both platforms
./gradlew :shared:hal:assemble

# Data module compiles
./gradlew :shared:data:assemble

# JVM integration tests (includes ImageProcessor tests)
./gradlew :shared:data:jvmTest

# Static analysis
./gradlew :shared:hal:detekt
./gradlew :shared:data:detekt
```

---

## Size Targets

| Operation | Input | Target Output |
|-----------|-------|---------------|
| compress | Any JPEG up to 8MB, ≤ 4000px | ≤ 500KB, ≤ 1024px |
| thumbnail | Any JPEG | ≤ 50KB, exactly 256×256 |
| crop | Any JPEG | Original quality, cropped dimensions |

---

## Definition of Done

- [ ] `ImageProcessor` interface created in commonMain
- [ ] `AndroidImageProcessor` compiles in `androidMain` (no Android SDK import errors)
- [ ] `DesktopImageProcessor` compiles in `jvmMain`
- [ ] `halModule()` provides `ImageProcessor` binding on both platforms
- [ ] `MediaRepositoryImpl` with `prepareAndSave()` pipeline implemented
- [ ] `DataModule` updated
- [ ] jvmTest image processor tests pass
- [ ] Commit: `feat(hal): add ImageProcessor expect/actual for Android and Desktop`
  And: `feat(data): implement MediaRepositoryImpl with compression pipeline`
