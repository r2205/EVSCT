package com.evsct.app.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The durable half of export provenance: which build wrote a backup, recorded
 * inside backup.json where it survives the renaming, share-target rewriting
 * and Android's "(1)" de-duping that a filename does not.
 *
 * Runs under Robolectric so `org.json` is the real implementation rather than
 * the stubbed android.jar one.
 */
@RunWith(AndroidJUnit4::class)
class BackupProvenanceTest {

    @Test
    fun `records name, code and sha`() {
        val json = JSONObject().putBuildProvenance("0.1.0", 247, "a1b2c3d")

        assertEquals("0.1.0", json.getString("appVersionName"))
        assertEquals(247, json.getInt("appVersionCode"))
        assertEquals("a1b2c3d", json.getString("gitSha"))
    }

    @Test
    fun `an unknown sha is recorded, not dropped`() {
        // Explicit "unknown" (a build made outside a git checkout) has to stay
        // distinguishable from a pre-provenance backup that omits the key —
        // omitting it here would collapse those two cases.
        val json = JSONObject().putBuildProvenance("0.1.0", 1, "unknown")

        assertTrue(json.has("gitSha"))
        assertEquals("unknown", json.getString("gitSha"))
    }

    @Test
    fun `a dirty sha survives verbatim`() {
        val json = JSONObject().putBuildProvenance("0.1.0", 247, "a1b2c3d-dirty")

        assertEquals("a1b2c3d-dirty", json.getString("gitSha"))
    }

    @Test
    fun `provenance is additive and leaves the schema version alone`() {
        // The keys are write-only: restore never reads them, and older builds
        // ignore what they don't recognize. That's why the schema version can
        // stay at 5 — the same call already made for the trip battery anchors.
        // If this ever needs a bump, it will be because a *reader* started
        // depending on these, and this test is where that shows up.
        val json = JSONObject()
            .put("schemaVersion", 5)
            .putBuildProvenance("0.1.0", 247, "a1b2c3d")

        assertEquals(5, json.getInt("schemaVersion"))

        val added = json.keys().asSequence().toSet() - "schemaVersion"
        assertEquals(setOf("appVersionName", "appVersionCode", "gitSha"), added)
    }

    @Test
    fun `a backup without provenance still reads as valid JSON`() {
        // Round-trips the pre-provenance shape: older files simply lack the
        // keys, and nothing on the restore path may require them.
        val legacy = JSONObject("""{"schemaVersion":5,"exportedAt":123}""")

        assertFalse(legacy.has("appVersionName"))
        assertFalse(legacy.has("gitSha"))
        assertEquals(5, legacy.getInt("schemaVersion"))
    }
}
