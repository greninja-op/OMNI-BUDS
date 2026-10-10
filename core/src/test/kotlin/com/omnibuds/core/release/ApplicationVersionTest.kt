package com.omnibuds.core.release

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplicationVersionTest {

    @Test
    fun currentApplicationVersionIsOneZeroZero() {
        val current = ApplicationVersion.CURRENT
        assertEquals(1, current.major)
        assertEquals(0, current.minor)
        assertEquals(0, current.patch)
        assertEquals("1.0.0", current.versionName)
        assertEquals(1000000, current.versionCode)
    }

    @Test
    fun parseSemanticVersions() {
        val v = ApplicationVersion.parse("1.2.3")
        assertNotNull(v)
        assertEquals(1, v.major)
        assertEquals(2, v.minor)
        assertEquals(3, v.patch)
        assertEquals("1.2.3", v.versionName)
        assertEquals(1020300, v.versionCode)

        val rc = ApplicationVersion.parse("2.0.1-rc1")
        assertNotNull(rc)
        assertEquals(2, rc.major)
        assertEquals(0, rc.minor)
        assertEquals(1, rc.patch)
        assertEquals("rc1", rc.preRelease)
        assertEquals("2.0.1-rc1", rc.versionName)
    }

    @Test
    fun parseInvalidVersions() {
        assertNull(ApplicationVersion.parse("invalid"))
        assertNull(ApplicationVersion.parse("1.0"))
        assertNull(ApplicationVersion.parse("v1.0.0"))
    }

    @Test
    fun versionCodeIncreasesMonotonically() {
        val v1 = ApplicationVersion(1, 0, 0, buildNumber = 0)
        val v2 = ApplicationVersion(1, 0, 0, buildNumber = 1)
        val v3 = ApplicationVersion(1, 0, 1, buildNumber = 0)
        val v4 = ApplicationVersion(1, 1, 0, buildNumber = 0)
        val v5 = ApplicationVersion(2, 0, 0, buildNumber = 0)

        assertTrue(v1.versionCode < v2.versionCode)
        assertTrue(v2.versionCode < v3.versionCode)
        assertTrue(v3.versionCode < v4.versionCode)
        assertTrue(v4.versionCode < v5.versionCode)
    }

    @Test
    fun releaseArtifactInfoValidatesSha256Checksum() {
        val artifact = ReleaseArtifactInfo(
            productName = "OmniBuds",
            applicationVersion = "1.0.0",
            platform = "desktop",
            architecture = "x64",
            filename = "omnibuds-desktop-1.0.0.jar",
            format = "jar",
            sizeBytes = 1024,
            sha256Checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            gitRevision = "b842934",
            buildTask = "release_build.sh:desktop",
            buildTimestamp = "2026-10-10T00:00:00Z",
            signingStatus = SigningStatus.UNSIGNED,
            validationStatus = "VERIFIED",
        )
        assertEquals("omnibuds-desktop-1.0.0.jar", artifact.filename)
    }
}
