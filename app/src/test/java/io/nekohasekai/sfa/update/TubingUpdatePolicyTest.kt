package io.nekohasekai.sfa.update

import org.junit.Assert.*
import org.junit.Test

class TubingUpdatePolicyTest {
    @Test
    fun deviceABIOrderDeterminesApkSelection() {
        val assets = listOf("Tubing-1.14.3-x86_64.apk", "Tubing-1.14.3-arm64-v8a.apk", "Tubing-1.14.3-universal.apk")
        assertEquals(assets[1], TubingUpdatePolicy.selectApkName(assets, listOf("arm64-v8a", "armeabi-v7a"), false))
    }

    @Test
    fun unsupportedABIUsesOnlyUniversalFallback() {
        assertNull(TubingUpdatePolicy.selectApkName(listOf("Tubing-1-x86_64.apk"), listOf("arm64-v8a"), false))
        assertEquals("Tubing-1-universal.apk", TubingUpdatePolicy.selectApkName(listOf("Tubing-1-universal.apk"), listOf("arm64-v8a"), false))
    }

    @Test
    fun officialPlayAndLegacyApksAreNotMixed() {
        val assets = listOf("SFA-1-arm64-v8a.apk", "Tubing-1-arm64-v8a-play.apk", "Tubing-1-arm64-v8a-legacy-android-5.apk")
        assertNull(TubingUpdatePolicy.selectApkName(assets, listOf("arm64-v8a"), false))
        assertEquals(assets[2], TubingUpdatePolicy.selectApkName(assets, listOf("arm64-v8a"), true))
    }

    @Test
    fun alreadyInstalledAndOlderBuildsAreNotOfferedAgain() {
        assertFalse(TubingUpdatePolicy.isNewerVersion(1000010, 1000010))
        assertFalse(TubingUpdatePolicy.isNewerVersion(1000009, 1000010))
        assertTrue(TubingUpdatePolicy.isNewerVersion(1000011, 1000010))
    }
}
