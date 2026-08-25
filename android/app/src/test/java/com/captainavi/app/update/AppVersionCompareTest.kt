package com.captainavi.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionCompareTest {

    @Test
    fun newerPatchIsDetected() {
        assertTrue(AppVersionCompare.isNewer("1.0.1", "1.0.0"))
        assertTrue(AppVersionCompare.isNewer("v1.0.1", "1.0.0"))
    }

    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(AppVersionCompare.isNewer("1.0.0", "1.0.0"))
        assertFalse(AppVersionCompare.isNewer("v1.0.0", "1.0.0"))
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertFalse(AppVersionCompare.isNewer("1.0.0", "1.0.1"))
        assertFalse(AppVersionCompare.isNewer("0.9.9", "1.0.0"))
    }

    @Test
    fun majorMinorOrdering() {
        assertTrue(AppVersionCompare.isNewer("2.0.0", "1.9.9"))
        assertTrue(AppVersionCompare.isNewer("1.1.0", "1.0.9"))
    }

    @Test
    fun normalizeStripsPrefixAndBuildMeta() {
        assertEquals(listOf(1, 0, 1), AppVersionCompare.normalize("v1.0.1-beta+build"))
    }
}
