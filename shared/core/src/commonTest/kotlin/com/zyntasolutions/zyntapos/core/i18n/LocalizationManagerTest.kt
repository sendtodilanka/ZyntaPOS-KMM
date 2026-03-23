package com.zyntasolutions.zyntapos.core.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalizationManagerTest {

    @Test
    fun defaultLocaleIsEnglish() {
        val manager = LocalizationManager()
        assertEquals(SupportedLocale.EN, manager.currentLocale.value)
    }

    @Test
    fun getStringReturnsEnglishByDefault() {
        val manager = LocalizationManager()
        assertEquals("OK", manager.getString(StringResource.COMMON_OK))
        assertEquals("Cancel", manager.getString(StringResource.COMMON_CANCEL))
    }

    @Test
    fun getStringWithArgumentsReplacesPlaceholders() {
        val manager = LocalizationManager()
        val result = manager.getString(StringResource.AUTH_LOCKED_OUT, "15")
        assertEquals("Too many attempts. Locked for 15 minutes.", result)
    }

    @Test
    fun getStringReturnsKeyNameWhenMissing() {
        val manager = LocalizationManager()
        // Register a partial table
        manager.registerStrings(
            SupportedLocale.SI,
            mapOf(StringResource.COMMON_OK to "\u0DC4\u0DBB\u0DD2"),
        )
        manager.setLocale(SupportedLocale.SI)

        assertEquals("\u0DC4\u0DBB\u0DD2", manager.getString(StringResource.COMMON_OK))
        // Missing key returns enum name
        assertEquals("COMMON_CANCEL", manager.getString(StringResource.COMMON_CANCEL))
    }

    @Test
    fun setLocaleChangesCurrentLocale() {
        val manager = LocalizationManager()
        manager.registerStrings(SupportedLocale.SI, mapOf(StringResource.COMMON_OK to "OK-SI"))

        manager.setLocale(SupportedLocale.SI)
        assertEquals(SupportedLocale.SI, manager.currentLocale.value)
    }

    @Test
    fun setLocaleToUnregisteredFallsBackToEnglish() {
        val manager = LocalizationManager()
        // Tamil table not registered
        manager.setLocale(SupportedLocale.TA)
        assertEquals(SupportedLocale.EN, manager.currentLocale.value)
    }

    @Test
    fun hasLocaleReturnsTrueForRegisteredLocale() {
        val manager = LocalizationManager()
        assertTrue(manager.hasLocale(SupportedLocale.EN))
        assertFalse(manager.hasLocale(SupportedLocale.SI))

        manager.registerStrings(SupportedLocale.SI, emptyMap())
        assertTrue(manager.hasLocale(SupportedLocale.SI))
    }

    @Test
    fun missingKeysReturnsAllForUnregisteredLocale() {
        val manager = LocalizationManager()
        val missing = manager.missingKeys(SupportedLocale.TA)
        assertEquals(StringResource.entries.toSet(), missing)
    }

    @Test
    fun missingKeysReturnsEmptyForCompleteTable() {
        val manager = LocalizationManager()
        // English table is built-in and should be complete
        val missing = manager.missingKeys(SupportedLocale.EN)
        assertTrue(
            missing.isEmpty(),
            "English table is missing ${missing.size} keys: ${missing.take(5)}",
        )
    }

    @Test
    fun englishTableCoversAllStringResources() {
        // Verify every enum value has a translation
        val allKeys = StringResource.entries.toSet()
        val tableKeys = EnglishStrings.table.keys
        val missing = allKeys - tableKeys
        assertTrue(
            missing.isEmpty(),
            "EnglishStrings.table is missing: $missing",
        )
    }

    @Test
    fun switchingLocaleAffectsSubsequentGetStringCalls() {
        val manager = LocalizationManager()
        manager.registerStrings(
            SupportedLocale.SI,
            mapOf(StringResource.COMMON_OK to "OK-SI", StringResource.COMMON_CANCEL to "Cancel-SI"),
        )

        // English first
        assertEquals("OK", manager.getString(StringResource.COMMON_OK))

        // Switch to Sinhala
        manager.setLocale(SupportedLocale.SI)
        assertEquals("OK-SI", manager.getString(StringResource.COMMON_OK))
        assertEquals("Cancel-SI", manager.getString(StringResource.COMMON_CANCEL))

        // Switch back to English
        manager.setLocale(SupportedLocale.EN)
        assertEquals("OK", manager.getString(StringResource.COMMON_OK))
    }

    @Test
    fun multipleArgumentsAreReplacedCorrectly() {
        val manager = LocalizationManager()
        manager.registerStrings(
            SupportedLocale.EN,
            EnglishStrings.table + mapOf(
                StringResource.ERROR_GENERIC to "Error %1 at %2 on %3",
            ),
        )
        val result = manager.getString(StringResource.ERROR_GENERIC, "404", "line 5", "Tuesday")
        assertEquals("Error 404 at line 5 on Tuesday", result)
    }

    @Test
    fun supportedLocaleFromTagResolvesCorrectly() {
        assertEquals(SupportedLocale.EN, SupportedLocale.fromTag("en"))
        assertEquals(SupportedLocale.SI, SupportedLocale.fromTag("si"))
        assertEquals(SupportedLocale.TA, SupportedLocale.fromTag("ta"))
        assertEquals(SupportedLocale.EN, SupportedLocale.fromTag("fr")) // unknown → default
        assertEquals(SupportedLocale.EN, SupportedLocale.fromTag("EN")) // case-insensitive
    }

    @Test
    fun registerStringsReplacesExistingTable() {
        val manager = LocalizationManager()
        val table1 = mapOf(StringResource.COMMON_OK to "V1")
        val table2 = mapOf(StringResource.COMMON_OK to "V2")

        manager.registerStrings(SupportedLocale.SI, table1)
        manager.setLocale(SupportedLocale.SI)
        assertEquals("V1", manager.getString(StringResource.COMMON_OK))

        manager.registerStrings(SupportedLocale.SI, table2)
        assertEquals("V2", manager.getString(StringResource.COMMON_OK))
    }
}
