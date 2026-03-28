package com.zyntasolutions.zyntapos.core.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * ZyntaPOS — AppTimezoneTest Unit Tests (commonTest)
 *
 * Validates the application-wide timezone registry in [AppTimezone].
 *
 * Coverage:
 *  A. default is Asia/Colombo
 *  B. set with valid IANA id updates current
 *  C. set with invalid id is silently ignored and previous value retained
 *  D. id property returns the IANA string of the current timezone
 *  E. restores original timezone after test to avoid state leakage
 */
class AppTimezoneTest {

    // Remember and restore so tests don't pollute each other
    private val originalId = AppTimezone.id

    private fun restore() {
        AppTimezone.set(originalId)
    }

    @Test
    fun `A - default timezone is Asia Colombo`() {
        restore() // ensure clean state
        assertEquals("Asia/Colombo", AppTimezone.id)
    }

    @Test
    fun `B - set with valid IANA id updates current timezone`() {
        try {
            AppTimezone.set("America/New_York")
            assertEquals("America/New_York", AppTimezone.id)
        } finally {
            restore()
        }
    }

    @Test
    fun `C - set with invalid IANA id is silently ignored`() {
        try {
            AppTimezone.set("America/New_York")
            val before = AppTimezone.id

            AppTimezone.set("not-a-real-timezone")

            // Must remain unchanged after invalid input
            assertEquals(before, AppTimezone.id)
        } finally {
            restore()
        }
    }

    @Test
    fun `D - id property returns IANA string of current timezone`() {
        try {
            AppTimezone.set("Europe/London")
            assertEquals("Europe/London", AppTimezone.id)
        } finally {
            restore()
        }
    }

    @Test
    fun `E - changing timezone affects current TimeZone instance`() {
        try {
            val colombo = AppTimezone.current
            AppTimezone.set("Pacific/Auckland")
            val auckland = AppTimezone.current
            assertNotEquals(colombo.id, auckland.id)
        } finally {
            restore()
        }
    }
}
