package com.zyntasolutions.zyntapos.feature.inventory.label

import com.zyntasolutions.zyntapos.feature.inventory.BarcodeType
import com.zyntasolutions.zyntapos.feature.inventory.ean13ModulePattern

/**
 * Encodes barcode strings to a flat [Boolean] list (true = black bar, false = white space).
 *
 * Used by platform-specific PDF renderers ([JvmLabelPdfRenderer], [AndroidLabelPdfRenderer])
 * to draw barcode bars as filled rectangles.
 *
 * The caller divides the available label width by the list size to get the per-module width.
 */
object BarcodeBarEncoder {

    /**
     * Encodes [value] as per [type].
     *
     * - EAN-13: exactly 95 modules (standard GS1).
     * - Code128: variable length based on content.
     *
     * @param value The barcode string to encode.
     * @param type  The barcode symbology.
     * @return List of booleans: `true` = draw black bar, `false` = leave white space.
     */
    fun encode(value: String, type: BarcodeType): List<Boolean> = when (type) {
        BarcodeType.EAN_13   -> encodeEan13(value)
        BarcodeType.CODE_128 -> encodeCode128(value)
    }

    // ── EAN-13 ────────────────────────────────────────────────────────────────

    private fun encodeEan13(value: String): List<Boolean> {
        val modules = mutableListOf<Boolean>()
        // Pad/truncate to 13 characters
        val digits = value.padEnd(13, '0').take(13)

        // Start guard: 1 0 1
        modules.addAll(listOf(true, false, true))

        // Left 6 digits (indices 1–6 from the 13-digit string; first digit is parity selector)
        for (idx in 1..6) {
            val digit = digits[idx].digitToIntOrNull() ?: 0
            for (module in 0 until 7) {
                modules.add(ean13ModulePattern(digit, module, isLeft = true))
            }
        }

        // Center guard: 0 1 0 1 0
        modules.addAll(listOf(false, true, false, true, false))

        // Right 6 digits (indices 7–12)
        for (idx in 7..12) {
            val digit = digits[idx].digitToIntOrNull() ?: 0
            for (module in 0 until 7) {
                modules.add(ean13ModulePattern(digit, module, isLeft = false))
            }
        }

        // End guard: 1 0 1
        modules.addAll(listOf(true, false, true))

        return modules // 3 + 42 + 5 + 42 + 3 = 95 modules
    }

    // ── Code128 ───────────────────────────────────────────────────────────────

    private fun encodeCode128(value: String): List<Boolean> {
        val modules = mutableListOf<Boolean>()

        // Start pattern: two 2-wide bars separated by 1-wide spaces
        modules.addAll(listOf(true, true, false, true, true, false))

        // Data: each character encoded as 11-bit pattern (ch.code % 128 → bit-shift)
        value.forEach { ch ->
            val code = ch.code % 128
            for (bit in 10 downTo 0) {
                modules.add((code shr bit) and 1 == 1)
            }
        }

        // Stop pattern: two 2-wide bars
        modules.addAll(listOf(true, true, false, true, true, false))

        return modules
    }
}
