package com.zyntasolutions.zyntapos.feature.pos.printer

import java.awt.Desktop
import java.io.File

/**
 * JVM/Desktop implementation of [A4PrintDelegate].
 *
 * Writes the document content to a temporary file and opens the system
 * default application (print dialog on most desktops) via [Desktop.open].
 * Phase 2 will replace this with a proper PDF renderer and `Desktop.print()`.
 */
class DesktopA4PrintDelegate : A4PrintDelegate {

    override suspend fun printDocument(title: String, content: String) {
        val sanitized = title.replace(Regex("[^A-Za-z0-9_\\-. ]"), "_")
        val tmpFile = File.createTempFile(sanitized, ".txt")
        tmpFile.writeText(content)
        tmpFile.deleteOnExit()
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(tmpFile)
        }
    }
}
