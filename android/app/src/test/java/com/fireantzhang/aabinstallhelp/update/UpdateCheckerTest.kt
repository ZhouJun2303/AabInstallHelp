package com.fireantzhang.aabinstallhelp.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun pickAndroidApkName_ignoresOlderVersionInSameRelease() {
        val names = listOf(
            "AabInstalllHelp-1.0.3-android.apk",
            "AabInstalllHelp-1.0.3-windows-x64.exe",
            "AabInstalllHelp-1.0.4-android.apk",
            "AabInstalllHelp-1.0.4-windows-x64.exe",
            "SHA256SUMS.txt"
        )
        assertEquals(
            "AabInstalllHelp-1.0.4-android.apk",
            UpdateChecker.pickAndroidApkName(names, "1.0.4")
        )
        assertEquals(
            "AabInstalllHelp-1.0.3-android.apk",
            UpdateChecker.pickAndroidApkName(names, "1.0.3")
        )
        assertNull(UpdateChecker.pickAndroidApkName(names, "9.9.9"))
    }

    @Test
    fun isNewer_comparesSemanticVersions() {
        assertTrue(UpdateChecker.isNewer("1.0.4", "1.0.3"))
        assertFalse(UpdateChecker.isNewer("1.0.4", "1.0.4"))
        assertFalse(UpdateChecker.isNewer("1.0.3", "1.0.4"))
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.9.9"))
    }

    @Test
    fun formatBytes_usesMegabytesForApkSizedFiles() {
        val text = UpdateChecker.formatBytes((70.7 * 1024 * 1024).toLong())
        assertTrue(text.contains("MB"))
    }
}
