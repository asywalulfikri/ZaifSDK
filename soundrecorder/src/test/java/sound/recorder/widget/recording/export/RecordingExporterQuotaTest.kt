package sound.recorder.widget.recording.export

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sound.recorder.widget.recording.database.RecordedTap
import sound.recorder.widget.recording.database.RecordingEntity
import sound.recorder.widget.testutil.FakeContext
import sound.recorder.widget.testutil.dateId
import java.io.File

class RecordingExporterQuotaTest {

    private lateinit var ctx: FakeContext

    private fun prefs() = ctx.getSharedPreferences("zaif_recording_export", 0)
    private fun rec(audioPath: String? = null, musicJson: String? = null) =
        RecordingEntity(name = "Lagu", setName = "kendang", eventsJson = "[]", audioPath = audioPath, musicJson = musicJson)

    @Before
    fun setUp() {
        ctx = FakeContext()
    }

    @After
    fun tearDown() {
        RecordingExporter.freeExportsPerDay = 1
        RecordingExporter.resolver = null
    }

    // ── Jatah export gratis ─────────────────────────────────────────────────

    @Test
    fun oneFreeExportPerDayByDefault() {
        assertTrue(RecordingExporter.hasFreeExport(ctx))
        RecordingExporter.consumeFreeExport(ctx)
        assertFalse(RecordingExporter.hasFreeExport(ctx))
    }

    @Test
    fun hostCanAllowMoreFreeExports() {
        RecordingExporter.freeExportsPerDay = 2
        RecordingExporter.consumeFreeExport(ctx)
        assertTrue(RecordingExporter.hasFreeExport(ctx))
        RecordingExporter.consumeFreeExport(ctx)
        assertFalse(RecordingExporter.hasFreeExport(ctx))
    }

    @Test
    fun zeroMeansAlwaysWatchAd() {
        RecordingExporter.freeExportsPerDay = 0
        assertFalse(RecordingExporter.hasFreeExport(ctx))
    }

    @Test
    fun quotaResetsOnNewDate() {
        prefs().edit().putInt("free_export_day", dateId(-1)).putInt("free_exports_used", 5).apply()
        assertTrue(RecordingExporter.hasFreeExport(ctx))
        RecordingExporter.consumeFreeExport(ctx)
        assertEquals(1, prefs().getInt("free_exports_used", 0))
        assertEquals(dateId(), prefs().getInt("free_export_day", 0))
    }

    // ── Metadata OFF ────────────────────────────────────────────────────────

    @Test
    fun offMetadataRecognised() {
        assertTrue(RecordingExporter.isOffMetadata("OFF"))
        assertTrue(RecordingExporter.isOffMetadata("OFF|pianika|C4"))
        assertFalse(RecordingExporter.isOffMetadata(null))
        assertFalse(RecordingExporter.isOffMetadata(""))
        assertFalse(RecordingExporter.isOffMetadata("off"))
        assertFalse(RecordingExporter.isOffMetadata("OFFBEAT"))
        assertFalse(RecordingExporter.isOffMetadata("kendang"))
    }

    // ── Bisa export MP3? ────────────────────────────────────────────────────

    @Test
    fun mp3NeedsMicAudioResolverOrSong() {
        assertFalse("tanpa sumber suara", RecordingExporter.canExportMp3(rec()))
        assertTrue("ada audio mic", RecordingExporter.canExportMp3(rec(audioPath = "/x/rec.m4a")))
        assertTrue("ada lagu", RecordingExporter.canExportMp3(rec(musicJson = "[{}]")))
        RecordingExporter.resolver = object : RecordingExportResolver {
            override fun resolveSample(setName: String, tap: RecordedTap): ExportSample? = null
        }
        assertTrue("host pasang resolver", RecordingExporter.canExportMp3(rec()))
    }

    @Test
    fun micPathOnlyWhenFileExistsAndNotEmpty() {
        assertNull(RecordingExporter.micPathOf(rec()))
        assertNull(RecordingExporter.micPathOf(rec(audioPath = "/tidak/ada.m4a")))

        val empty = File.createTempFile("mic", ".m4a").apply { deleteOnExit() }
        assertNull(RecordingExporter.micPathOf(rec(audioPath = empty.path)))

        val real = File.createTempFile("mic", ".m4a").apply { writeBytes(byteArrayOf(1, 2, 3)); deleteOnExit() }
        assertEquals(real.path, RecordingExporter.micPathOf(rec(audioPath = real.path)))
    }

    @Test
    fun formatExtensionsAndMimeTypes() {
        assertEquals("mp3", RecordingExporter.Format.MP3.extension)
        assertEquals("audio/mpeg", RecordingExporter.Format.MP3.mimeType)
        assertEquals("mid", RecordingExporter.Format.MIDI.extension)
        assertEquals("audio/midi", RecordingExporter.Format.MIDI.mimeType)
    }
}
