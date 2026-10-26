package sound.recorder.widget.recording.export

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sound.recorder.widget.recording.database.RecordedTap
import sound.recorder.widget.recording.RecordedMusicJson
import sound.recorder.widget.recording.database.RecordingEntity
import java.io.File

/**
 * Export rekaman ke MP3 / MIDI, lalu share (chooser, WhatsApp) atau simpan ke penyimpanan perangkat.
 *
 * MIDI selalu bisa dibuat dari data ketukan.
 * MP3 butuh salah satu: audio mic rekaman, atau [resolver] dari host app untuk merender suara pad.
 */
@Keep
object RecordingExporter {

    const val OFF_METADATA = "OFF"

    private const val WHATSAPP = "com.whatsapp"
    private const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    /** Cache hasil ekspor yang lebih tua dari ini dihapus saat ekspor berikutnya. */
    private const val CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L

    /** jump3r (LAME) tidak dirancang untuk dipakai paralel; render MP3 dijalankan satu per satu. */
    private val mp3Mutex = Mutex()

    /**
     * Dipasang host app agar suara pad bisa dirender ke MP3 dan nada MIDI sesuai instrumen.
     * Disimpan statis: jangan berikan objek yang menyimpan Activity/Fragment/View,
     * atau set null di onDestroy/onDestroyView agar tidak memory leak.
     */
    @Volatile
    var resolver: RecordingExportResolver? = null

    var mp3BitrateKbps = 128

    /**
     * Jumlah maksimal suara pad yang berbunyi bersamaan saat render MP3.
     * Samakan dengan `SoundPool.Builder().setMaxStreams(n)` di host app agar hasil MP3 = yang terdengar di app.
     * 0 = tanpa batas.
     */
    var maxPolyphony = 15

    @Keep
    enum class Format(val extension: String, val mimeType: String) {
        MP3("mp3", "audio/mpeg"),
        MIDI("mid", "audio/midi")
    }

    /** Cek cepat tanpa akses disk (aman di main thread). Keberadaan file mic dicek ulang saat ekspor. */
    fun canExportMp3(rec: RecordingEntity): Boolean =
        !rec.audioPath.isNullOrEmpty() || resolver != null || !rec.musicJson.isNullOrEmpty()

    /**
     * Membuat file export di cache (siap di-share / disimpan). Jalankan dari coroutine.
     * @throws Mp3SourceUnavailableException jika MP3 tidak punya sumber suara.
     */
    suspend fun export(
        context: Context,
        rec: RecordingEntity,
        format: Format,
        onProgress: (Int) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val dir = File(appContext.cacheDir, "exports").apply { mkdirs() }
        deleteOldExports(dir)
        val outFile = File(dir, "${safeFileName(rec.name)}_${rec.id}.${format.extension}")
        val taps = parseTaps(rec.eventsJson)
        val currentResolver = resolver

        try {
            when (format) {
                Format.MIDI -> {
                    outFile.writeBytes(MidiFileWriter.write(rec.name, rec.setName, taps, currentResolver))
                    onProgress(100)
                }
                Format.MP3 -> mp3Mutex.withLock {
                    val micPath = rec.audioPath?.takeIf { hasMicAudio(rec) }
                    // Rekaman mic lewat speaker sudah menangkap suara instrumen → cukup audio mic.
                    // Rekaman pakai earphone hanya berisi suara mic → suara pad ikut dirender.
                    val renderTaps = micPath == null || rec.isEarphoneRecording
                    Mp3Renderer(appContext).render(
                        setName = rec.setName,
                        taps = taps,
                        micPath = micPath,
                        renderTaps = renderTaps,
                        resolver = currentResolver,
                        durationMs = rec.durationMs,
                        outFile = outFile,
                        bitrateKbps = mp3BitrateKbps,
                        maxPolyphony = maxPolyphony,
                        onProgress = { p -> onProgress(p) },
                        music = RecordedMusicJson.parse(rec.musicJson)
                    )
                }
            }
        } catch (e: Throwable) {
            outFile.delete()
            throw e
        }
        outFile
    }

    // ─── SHARE ───────────────────────────────────────────────────────────────

    /** Share lewat chooser Android (WhatsApp, Telegram, Instagram, Drive, dll). */
    suspend fun share(context: Context, file: File, format: Format, title: String) {
        val uri = contentUri(context, file)
        val intent = buildSendIntent(uri, format.mimeType, title)
        val chooser = Intent.createChooser(intent, title)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /** Share langsung ke WhatsApp / WhatsApp Business. @return false jika tidak terpasang. */
    suspend fun shareToWhatsApp(context: Context, file: File, format: Format, title: String): Boolean {
        val uri = contentUri(context, file)
        // MIDI tidak selalu cocok dengan filter audio WhatsApp → coba juga sebagai dokumen
        val mimeTypes = if (format == Format.MIDI) listOf(format.mimeType, "*/*") else listOf(format.mimeType)
        for (pkg in listOf(WHATSAPP, WHATSAPP_BUSINESS)) {
            for (mime in mimeTypes) {
                val intent = buildSendIntent(uri, mime, title).setPackage(pkg)
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                    return true
                } catch (_: ActivityNotFoundException) {
                    // coba kombinasi berikutnya
                }
            }
        }
        return false
    }

    /** FileProvider bisa menyentuh filesystem → jalankan di IO (sama seperti shareAudioFile di kode lama). */
    private suspend fun contentUri(context: Context, file: File): Uri = withContext(Dispatchers.IO) {
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    private fun buildSendIntent(uri: Uri, mime: String, title: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TITLE, title)
            clipData = ClipData.newRawUri(title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    // ─── SAVE TO DEVICE ──────────────────────────────────────────────────────

    /** Android 9 ke bawah butuh izin WRITE_EXTERNAL_STORAGE untuk menyimpan ke folder publik. */
    fun needsStoragePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED

    /**
     * Menyalin file export ke folder publik: MP3 → Music/<NamaApp>, MIDI → Download/<NamaApp>.
     * @return lokasi yang bisa ditampilkan ke user, contoh "Music/Gamelan/Lagu_3.mp3".
     */
    suspend fun saveToDevice(context: Context, file: File, format: Format): String = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val folder = safeFileName(appContext.applicationInfo.loadLabel(appContext.packageManager).toString())
        val baseDir = if (format == Format.MP3) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_DOWNLOADS
        val relativePath = "$baseDir/$folder"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            val collection: Uri = if (format == Format.MP3) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: error("MediaStore insert failed")
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: error("Cannot open output stream")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            "$relativePath/${file.name}"
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(baseDir), folder).apply { mkdirs() }
            val target = uniqueFile(dir, file.name)
            file.copyTo(target, overwrite = false)
            MediaScannerConnection.scanFile(appContext, arrayOf(target.absolutePath), arrayOf(format.mimeType), null)
            "$relativePath/${target.name}"
        }
    }

    // ─── UTIL ────────────────────────────────────────────────────────────────

    private fun hasMicAudio(rec: RecordingEntity): Boolean =
        !rec.audioPath.isNullOrEmpty() && File(rec.audioPath).let { it.exists() && it.length() > 0 }

    private fun deleteOldExports(dir: File) {
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { if (now - it.lastModified() > CACHE_MAX_AGE_MS) it.delete() }
    }

    private fun parseTaps(json: String): List<RecordedTap> {
        if (json.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<RecordedTap>>() {}.type
            Gson().fromJson<List<RecordedTap>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun safeFileName(name: String): String =
        name.trim().replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").trim('_').take(40).ifEmpty { "Recording" }

    private fun uniqueFile(dir: File, name: String): File {
        var target = File(dir, name)
        var i = 1
        while (target.exists()) {
            target = File(dir, "${name.substringBeforeLast('.')}_$i.${name.substringAfterLast('.')}")
            i++
        }
        return target
    }
}
