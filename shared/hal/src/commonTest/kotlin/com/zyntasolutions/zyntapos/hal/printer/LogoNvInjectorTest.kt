package com.zyntasolutions.zyntapos.hal.printer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for [LogoNvInjector].
 *
 * Verifies that ESC/POS `GS ( L` sequences are assembled correctly for NV logo upload.
 */
class LogoNvInjectorTest {

    // ── 1. buildNvDefineSequence ──────────────────────────────────────────────

    @Test
    fun `output starts with GS ( L`() {
        val raster = ByteArray(8 * 4) // 64px wide × 4px tall
        val bytes = LogoNvInjector.buildNvDefineSequence(64, 4, raster)
        // GS = 0x1D, '(' = 0x28, 'L' = 0x4C
        assertEquals(0x1D.toByte(), bytes[0], "GS byte")
        assertEquals(0x28.toByte(), bytes[1], "( byte")
        assertEquals(0x4C.toByte(), bytes[2], "L byte")
    }

    @Test
    fun `output contains fn=67 (0x43) for define command`() {
        val raster = ByteArray(8 * 4)
        val bytes = LogoNvInjector.buildNvDefineSequence(64, 4, raster)
        // GS ( L pL pH m fn ...
        // bytes[5] = m = 0x30, bytes[6] = fn = 0x43
        assertEquals(0x30.toByte(), bytes[5], "m byte must be 0x30")
        assertEquals(0x43.toByte(), bytes[6], "fn byte must be 0x43 (function 67)")
    }

    @Test
    fun `output contains store command fn=69 (0x45)`() {
        val raster = ByteArray(8 * 4)
        val bytes = LogoNvInjector.buildNvDefineSequence(64, 4, raster)
        val str = bytes.toHex()
        // Store sequence: GS ( L 06 00 30 45 kc1 kc2
        assertTrue(str.contains("1d284c06003045"), "Store command not found in output: $str")
    }

    @Test
    fun `throws on width not multiple of 8`() {
        assertFailsWith<IllegalArgumentException> {
            LogoNvInjector.buildNvDefineSequence(65, 4, ByteArray(33))
        }
    }

    @Test
    fun `throws on raster size mismatch`() {
        assertFailsWith<IllegalArgumentException> {
            // widthDots=64 → 64/8=8 bytes per row × heightDots=4 = 32 bytes, but we pass 10
            LogoNvInjector.buildNvDefineSequence(64, 4, ByteArray(10))
        }
    }

    @Test
    fun `output length equals header + raster + store sequence`() {
        val widthDots = 384
        val heightDots = 128
        val raster = ByteArray((widthDots / 8) * heightDots)
        val bytes = LogoNvInjector.buildNvDefineSequence(widthDots, heightDots, raster)

        // Define header: 3 (GS(L) + 2 (pL pH) + 1 (m) + 1 (fn) + 2 (kc1/kc2) + 1 (b) + 4 (xl xh yl yh) = 14 bytes
        // Store: 3 (GS(L)) + 2 (pL pH) + 2 (m fn) + 2 (kc1 kc2) = 9 bytes
        val expectedMin = 14 + raster.size + 9
        assertTrue(bytes.size >= expectedMin, "Output too short: expected >= $expectedMin, got ${bytes.size}")
    }

    @Test
    fun `default kc1 and kc2 are embedded in define command`() {
        val raster = ByteArray(8 * 2)
        val bytes = LogoNvInjector.buildNvDefineSequence(64, 2, raster)
        assertEquals(LogoNvInjector.DEFAULT_KC1, bytes[7], "kc1 should match DEFAULT_KC1")
        assertEquals(LogoNvInjector.DEFAULT_KC2, bytes[8], "kc2 should match DEFAULT_KC2")
    }

    // ── 2. buildPrintNvLogoCommand ────────────────────────────────────────────

    @Test
    fun `print command is exactly 4 bytes`() {
        val cmd = LogoNvInjector.buildPrintNvLogoCommand()
        assertEquals(4, cmd.size)
    }

    @Test
    fun `print command starts with FS byte 0x1C`() {
        val cmd = LogoNvInjector.buildPrintNvLogoCommand()
        assertEquals(0x1C.toByte(), cmd[0], "Expected FS (0x1C)")
    }

    @Test
    fun `print command second byte is 0x70`() {
        val cmd = LogoNvInjector.buildPrintNvLogoCommand()
        assertEquals(0x70.toByte(), cmd[1])
    }

    @Test
    fun `print command uses provided kc1 and kc2`() {
        val cmd = LogoNvInjector.buildPrintNvLogoCommand(kc1 = 0x21, kc2 = 0x02)
        assertEquals(0x21.toByte(), cmd[2])
        assertEquals(0x02.toByte(), cmd[3])
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun ByteArray.toHex(): String =
        joinToString("") { it.toInt().and(0xFF).toString(16).padStart(2, '0') }
}
