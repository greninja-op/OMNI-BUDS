package com.omnibuds.core.verification

import com.omnibuds.core.capability.CoreFeature
import com.omnibuds.core.config.ConfigurationValue
import com.omnibuds.core.configuration.InMemoryConfigurationStorage
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 18 §13.4: repository, codec, and scope tests.
 */
class VerificationRepositoryTest {

    private val value = ConfigurationValue.ModeValue("anc-on", "ANC On")

    private fun storage() = object : VerificationStorage {
        val delegate = InMemoryConfigurationStorage()
        override suspend fun read(key: String) = delegate.read(key)
        override suspend fun write(key: String, value: String) = delegate.write(key, value)
        override suspend fun delete(key: String) = delegate.delete(key)
    }

    private fun record(deviceKey: String = "device-key") = VerificationRecord.create(
        deviceKey,
        VerificationPlan.writeOnly(CoreFeature.ANC, value),
        1000L,
    )

    @Test
    fun `save and load round-trips`() = runTest {
        val repo = VerificationRepository(storage())
        val r = record()
        assertTrue(repo.save(r))
        repo.index(r)
        val loaded = repo.load(r.id)
        assertNotNull(loaded)
        assertEquals(r.id, loaded!!.id)
        assertEquals(r.deviceKey, loaded.deviceKey)
        assertEquals(r.stage, loaded.stage)
    }

    @Test
    fun `missing record returns null`() = runTest {
        val repo = VerificationRepository(storage())
        assertNull(repo.load(VerificationId.of("nope")))
    }

    @Test
    fun `corrupt data returns null`() = runTest {
        val s = storage()
        val repo = VerificationRepository(s)
        s.write("verification:bad", "not-json{{{")
        assertNull(repo.load(VerificationId.of("bad")))
    }

    @Test
    fun `device index lists verifications`() = runTest {
        val repo = VerificationRepository(storage())
        val r1 = record("dev-a")
        val r2 = record("dev-a")
        val r3 = record("dev-b")
        repo.save(r1); repo.index(r1)
        repo.save(r2); repo.index(r2)
        repo.save(r3); repo.index(r3)
        assertEquals(2, repo.listForDevice("dev-a").size)
        assertEquals(1, repo.listForDevice("dev-b").size)
    }

    @Test
    fun `recoverInterrupted returns only non-terminal`() = runTest {
        val repo = VerificationRepository(storage())
        val active = record("dev-a")
        var done = record("dev-a")
        done = done.copy(
            stage = VerificationStage.COMPLETED,
            outcome = VerificationOutcome.VERIFIED,
            completedAtMillis = 2000L,
        )
        repo.save(active); repo.index(active)
        repo.save(done); repo.index(done)
        val recovered = repo.recoverInterrupted("dev-a")
        assertEquals(1, recovered.size)
        assertEquals(active.id, recovered.first().id)
    }

    @Test
    fun `delete removes record and de-indexes`() = runTest {
        val repo = VerificationRepository(storage())
        val r = record("dev-a")
        repo.save(r); repo.index(r)
        assertTrue(repo.delete(r.id, "dev-a"))
        assertNull(repo.load(r.id))
        assertTrue(repo.listForDevice("dev-a").isEmpty())
    }

    @Test
    fun `codec rejects wrong schema version`() {
        val r = record()
        val json = VerificationRecordCodec.encode(r).replace("\"v\":1", "\"v\":99")
        assertNull(VerificationRecordCodec.decode(json))
    }

    @Test
    fun `terminal record codec round-trips`() {
        var r = record()
        r = r.copy(
            stage = VerificationStage.COMPLETED,
            outcome = VerificationOutcome.VERIFIED,
            applicationStatus = ApplicationStatus.READ_BACK_CONFIRMED,
            provenScope = PersistenceScope.SESSION_ONLY,
            completedAtMillis = 2000L,
        )
        val decoded = VerificationRecordCodec.decode(VerificationRecordCodec.encode(r))
        assertNotNull(decoded)
        assertEquals(VerificationOutcome.VERIFIED, decoded!!.outcome)
        assertEquals(PersistenceScope.SESSION_ONLY, decoded.provenScope)
    }
}

class VerificationScopeTest {

    private val verificationDir = Paths.get("src/main/kotlin/com/omnibuds/core/verification")

    private fun sources(): Sequence<String> {
        if (!Files.isDirectory(verificationDir)) return emptySequence()
        return Files.walk(verificationDir).asSequence()
            .filter { it.toString().endsWith(".kt") }
            .map { Files.readString(it) }
    }

    @Test
    fun `no UI vocabulary`() {
        val banned = listOf("androidx.compose", "android.widget", "android.view.View")
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned UI term: $term")
            }
        }
    }

    @Test
    fun `no media audio vocabulary`() {
        // Note: "decode(" alone is not banned — ConfigurationValueJson.decode
        // is the JSON codec, not audio decoding. We ban audio-specific terms.
        val banned = listOf("AudioRecord", "MediaCodec", "android.media")
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned audio term: $term")
            }
        }
    }

    @Test
    fun `no vendor command invention`() {
        val banned = listOf("opcode", "UUID(", "0x")
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned protocol term: $term")
            }
        }
    }

    @Test
    fun `no hidden APIs`() {
        val banned = listOf("getDeclaredMethod", "setAccessible", "Runtime.getRuntime().exec")
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned term: $term")
            }
        }
    }

    @Test
    fun `no sideways feature imports`() {
        for (src in sources()) {
            assertFalse(
                src.contains("import com.omnibuds.core.feature."),
                "sideways feature import forbidden",
            )
        }
    }

    @Test
    fun `no fabrication vocabulary`() {
        val banned = listOf("assumePersisted", "inferPersistence", "fakeReadBack")
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned fabrication term: $term")
            }
        }
    }
}
