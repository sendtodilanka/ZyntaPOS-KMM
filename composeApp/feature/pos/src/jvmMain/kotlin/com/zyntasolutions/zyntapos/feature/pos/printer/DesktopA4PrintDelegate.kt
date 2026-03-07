package com.zyntasolutions.zyntapos.feature.pos.printer

import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/**
 * JVM/Desktop implementation of [A4PrintDelegate].
 *
 * Writes the document content to a temporary file and opens the system
 * default application (print dialog on most desktops) via [Desktop.open].
 * Phase 2 will replace this with a proper PDF renderer and `Desktop.print()`.
 */
class DesktopA4PrintDelegate : A4PrintDelegate {

    override suspend fun printDocument(title: String, content: String) {
        val tmpPath = try {
            val attrs = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
            Files.createTempFile("zyntapos_print_", ".txt", attrs)
        } catch (_: UnsupportedOperationException) {
            // Windows does not support POSIX attributes — fall back to standard temp file
            Files.createTempFile("zyntapos_print_", ".txt")
        }
        val tmpFile = tmpPath.toFile()
        tmpFile.writeText(content)
        tmpFile.deleteOnExit()
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(tmpFile)
        }
    }
}
