package sound.recorder.widget.recording

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import sound.recorder.widget.recording.database.RecordedMusicSegment

/** Mencari sumber lagu yang tercatat di rekaman. Lagu yang sudah tidak ada → null (rekaman tetap jalan tanpa lagu). */
internal object RecordedMusicSource {
    fun rawResId(context: Context, rawName: String): Int =
        context.resources.getIdentifier(rawName, "raw", context.packageName)

    /** @return true jika data source berhasil dipasang. */
    fun setDataSource(context: Context, mp: MediaPlayer, seg: RecordedMusicSegment): Boolean {
        seg.rawName?.let { name ->
            val id = rawResId(context, name)
            if (id == 0) return false
            val afd = context.resources.openRawResourceFd(id) ?: return false // resource terkompresi
            afd.use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            return true
        }
        val uri = seg.uri ?: return false
        mp.setDataSource(context, Uri.parse(uri))
        return true
    }
}

/**
 * Memutar ulang lagu yang berbunyi saat rekaman, sinkron dengan ketukan.
 * Alur: [prepare] (semua lagu disiapkan & di-seek dulu) → onReady → caller memulai ketukan/audio
 * bersamaan dengan [start]. Lagu yang gagal disiapkan dilewati. Dipakai di main thread.
 */
internal class RecordingMusicPlayer(context: Context) {

    private companion object {
        const val TAG = "RecordingMusicPlayer"
        const val MAX_TRACKS = 3               // batasi player bersamaan (HP low-end)
        const val PREPARE_TIMEOUT_MS = 4_000L   // sumber lagu macet → lanjut tanpa lagu itu
    }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val players = HashMap<String, MediaPlayer>()
    /** Posisi yang sudah di-seek saat prepare → tidak perlu seek ulang saat potongan pertama mulai. */
    private val preSeeked = HashMap<String, Long>()
    private val prepared = HashSet<String>()
    private val timeoutToken = Any()
    private var segments: List<RecordedMusicSegment> = emptyList()
    private var token = 0

    val isActive: Boolean get() = players.isNotEmpty()

    fun prepare(list: List<RecordedMusicSegment>, onReady: () -> Unit) {
        stop()
        val myToken = token
        val firstBySource = LinkedHashMap<String, RecordedMusicSegment>()
        list.sortedBy { it.atMs }.forEach { s ->
            val key = s.sourceKey ?: return@forEach
            if (key !in firstBySource && firstBySource.size < MAX_TRACKS) firstBySource[key] = s
        }
        segments = list.filter { it.sourceKey in firstBySource && it.endMs > it.atMs }
        if (firstBySource.isEmpty()) {
            onReady()
            return
        }

        var pending = firstBySource.size
        var readyCalled = false
        val finishOne = { key: String, ok: Boolean ->
            if (myToken == token && !readyCalled) {
                if (ok) prepared.add(key)
                if (--pending == 0) {
                    readyCalled = true
                    handler.removeCallbacksAndMessages(timeoutToken)
                    onReady()
                }
            }
        }
        // Jaring pengaman: sumber yang tidak selesai prepare dilepas, sisanya tetap jalan
        handler.postAtTime({
            if (myToken != token || readyCalled) return@postAtTime
            readyCalled = true
            Log.w(TAG, "prepare timeout, lanjut dengan lagu yang sudah siap")
            players.keys.filter { it !in prepared }.forEach { k -> players.remove(k)?.release() }
            onReady()
        }, timeoutToken, android.os.SystemClock.uptimeMillis() + PREPARE_TIMEOUT_MS)

        firstBySource.forEach { (key, first) ->
            val mp = MediaPlayer()
            try {
                if (!RecordedMusicSource.setDataSource(appContext, mp, first)) {
                    mp.release()
                    finishOne(key, false)
                    return@forEach
                }
                players[key] = mp
                mp.setOnPreparedListener {
                    if (myToken != token) return@setOnPreparedListener
                    preSeeked[key] = first.posMs
                    if (first.posMs > 0L) {
                        mp.setOnSeekCompleteListener { p -> p.setOnSeekCompleteListener(null); finishOne(key, true) }
                        mp.seekTo(first.posMs.toInt())
                    } else finishOne(key, true)
                }
                mp.setOnErrorListener { p, what, extra ->
                    Log.e(TAG, "lagu gagal diputar ($key): what=$what extra=$extra")
                    if (players[key] === p) {
                        players.remove(key)
                        p.release()
                    }
                    finishOne(key, false)
                    true
                }
                mp.prepareAsync()
            } catch (e: Exception) {
                // Lagu dihapus / izin media dicabut → lewati lagu ini saja
                Log.w(TAG, "sumber lagu tidak tersedia ($key): ${e.message}")
                players.remove(key)
                mp.release()
                finishOne(key, false)
            }
        }
    }

    /** Mulai jadwal lagu, dihitung dari sekarang (panggil bersamaan dengan mulainya ketukan). */
    fun start(onComplete: () -> Unit) {
        val myToken = token
        val playable = segments.filter { players.containsKey(it.sourceKey) }
        if (playable.isEmpty()) {
            onComplete()
            return
        }
        // Akhir dijadwalkan sebelum awal: potongan bersambung pada lagu yang sama tidak terpotong
        playable.forEach { s -> handler.postDelayed({ if (myToken == token) players[s.sourceKey]?.pauseSafe() }, s.endMs) }
        playable.forEach { s ->
            handler.postDelayed({
                if (myToken != token) return@postDelayed
                val key = s.sourceKey ?: return@postDelayed
                val mp = players[key] ?: return@postDelayed
                try {
                    if (preSeeked.remove(key) != s.posMs) mp.seekTo(s.posMs.toInt())
                    val v = s.volume.coerceIn(0f, 1f)
                    mp.setVolume(v, v)
                    mp.start()
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "start lagu gagal: ${e.message}")
                }
            }, s.atMs)
        }
        handler.postDelayed({ if (myToken == token) { stop(); onComplete() } }, playable.maxOf { it.endMs } + 50L)
    }

    fun stop() {
        token++
        handler.removeCallbacksAndMessages(null)
        players.values.forEach { mp ->
            try { if (mp.isPlaying) mp.stop() } catch (_: IllegalStateException) { }
            mp.release()
        }
        players.clear()
        preSeeked.clear()
        prepared.clear()
        segments = emptyList()
    }

    private fun MediaPlayer.pauseSafe() {
        try { if (isPlaying) pause() } catch (_: IllegalStateException) { }
    }
}
