package com.zyntasolutions.zyntapos.hal.printer

/**
 * ZyntaPOS — Hardware Abstraction Layer
 *
 * Builds the ESC/POS `GS ( L` NV user-defined logo sequences for Epson-compatible printers.
 *
 * ### Protocol (Epson ESC/POS Application Manual)
 * 1. **Define NV bit-image** — `GS ( L pL pH 0x30 0x43 kc1 kc2 b xl xh yl yh [raster data]`
 *    Uploads the image to volatile memory.
 * 2. **Store NV bit-image** — `GS ( L 0x06 0x00 0x30 0x45 kc1 kc2`
 *    Commits the image to non-volatile RAM (survives power cycle).
 * 3. **Print NV bit-image** — `FS p kc1 kc2` (alias: `0x1C 0x70 kc1 kc2`)
 *    Emitted in [EscPosReceiptBuilder] header when `showLogo = true`.
 *
 * ### Usage
 * ```kotlin
 * val logoBytes  = logoNvInjector.buildNvDefineSequence(384, 128, rasterData)
 * printerManager.print(logoBytes)  // upload + store
 * // From now on, every receipt with showLogo=true prints the logo via FS p
 * ```
 */
object LogoNvInjector {

    private val GS: Byte = 0x1D.toByte()
    private val FS: Byte = 0x1C.toByte()

    /** Key-code byte 1 for NV slot 1 (space character, Epson standard). */
    const val DEFAULT_KC1: Byte = 0x20

    /** Key-code byte 2 for NV slot 1 (image count = 1). */
    const val DEFAULT_KC2: Byte = 0x01

    /**
     * Builds the ESC/POS byte sequence to define and store a 1-bit raster logo
     * in the printer's NV RAM slot 1.
     *
     * @param widthDots  Width of the 1-bit raster in dots. **Must be a multiple of 8.**
     * @param heightDots Height of the raster in dots.
     * @param rasterData Raw 1-bit raster bytes in row-major order
     *                   (`widthDots / 8 * heightDots` bytes total).
     * @return           Complete ESC/POS byte sequence (define + store).
     */
    fun buildNvDefineSequence(
        widthDots: Int,
        heightDots: Int,
        rasterData: ByteArray,
    ): ByteArray {
        require(widthDots % 8 == 0) {
            "widthDots must be a multiple of 8, got $widthDots"
        }
        require(rasterData.size == (widthDots / 8) * heightDots) {
            "rasterData size mismatch: expected ${(widthDots / 8) * heightDots}, got ${rasterData.size}"
        }

        val buf = mutableListOf<ByteArray>()

        // ── Function 67: Define NV bit-image ─────────────────────────────────
        // GS ( L pL pH m fn kc1 kc2 b xl xh yl yh [data]
        // params: m(1) + fn(1) + kc1(1) + kc2(1) + b(1) + xl(1) + xh(1) + yl(1) + yh(1) + raster
        val xl = (widthDots % 256).toByte()
        val xh = (widthDots / 256).toByte()
        val yl = (heightDots % 256).toByte()
        val yh = (heightDots / 256).toByte()
        val paramLen67 = 9 + rasterData.size          // m + fn + kc1 + kc2 + b + xl + xh + yl + yh + data
        val pL67 = (paramLen67 % 256).toByte()
        val pH67 = (paramLen67 / 256).toByte()

        buf += byteArrayOf(
            GS, 0x28, 0x4C,                           // GS ( L
            pL67, pH67,                               // pL pH
            0x30,                                     // m = 48 (function class)
            0x43,                                     // fn = 67
            DEFAULT_KC1, DEFAULT_KC2,                 // kc1 kc2
            0x01,                                     // b = 1 (no scaling)
            xl, xh, yl, yh,                           // image dimensions in dots
        )
        buf += rasterData

        // ── Function 69: Store to NV RAM ─────────────────────────────────────
        // GS ( L 06 00 30 45 kc1 kc2
        buf += byteArrayOf(
            GS, 0x28, 0x4C,
            0x06, 0x00,
            0x30, 0x45,
            DEFAULT_KC1, DEFAULT_KC2,
        )

        val totalSize = buf.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        for (chunk in buf) { chunk.copyInto(result, offset); offset += chunk.size }
        return result
    }

    /**
     * Builds the `FS p kc1 kc2` command to print the stored NV logo.
     * Emit this at the top of each receipt when `PrinterConfig.showLogo = true`.
     */
    fun buildPrintNvLogoCommand(
        kc1: Byte = DEFAULT_KC1,
        kc2: Byte = DEFAULT_KC2,
    ): ByteArray = byteArrayOf(FS, 0x70, kc1, kc2)
}
