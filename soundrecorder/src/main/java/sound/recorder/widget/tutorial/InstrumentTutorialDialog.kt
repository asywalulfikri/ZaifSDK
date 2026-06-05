package sound.recorder.widget.tutorial

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import sound.recorder.widget.R
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.builder.ZaifSDKConfig
import sound.recorder.widget.databinding.DialogTutorialSongListBinding
import sound.recorder.widget.recording.database.RecordedTap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.intuit.sdp.R as SdpR

class InstrumentTutorialDialog(
    private val lifecycleScope: LifecycleCoroutineScope? = null,
    private val onTabSelect: (slendro: Boolean) -> Unit = {},
    private val onTriggerAnim: (padIndex: Int) -> Unit = {},
    private val onHighlight: (padIndex: Int) -> Unit = {},
    private val onUnhighlight: (padIndex: Int) -> Unit = {},
    private val onClearHighlight: () -> Unit = {},
    private val onLearnStepUpdate: (step: Int, total: Int) -> Unit = { _, _ -> },
    private val onLearnVisible: (visible: Boolean) -> Unit = {},
    private val onPlaybackStatusChanged: (isPlaying: Boolean) -> Unit = {},
    private val onToast: (message: String) -> Unit = {},
    private val onRequestAd: (onComplete: () -> Unit) -> Unit = { it() },
    // Host app handles sound playback — padIndex and metadata from the recorded event
    private val onPlayNote: (padIndex: Int, metadata: String) -> Unit = { _, _ -> },
    private val onStopNote: (padIndex: Int, metadata: String) -> Unit = { _, _ -> },
) {

    private var mContext: Context? = null
    private var instrumentType: String = ""

    var zaifSDKConfig : ZaifSDKConfig? =null

    data class NoteItem(
        val recordName: String,
        val senderName: String,
        val submittedAt: Long,
        val jsonNote: String
    )

    sealed class SongItem {
        data class Local(val song: InstrumentSong, val bestScore: Int) : SongItem()
        data class Remote(val note: NoteItem) : SongItem()
    }

    companion object {
        private const val CACHE_TTL_MS   = 15 * 60 * 1000L
        private const val UNLOCK_TTL_MS  = 24 * 60 * 60 * 1000L   // 1 hari
        private const val PREFS_UNLOCKED = "zaif_note_unlocks"

        private data class CachedResult(val notes: List<NoteItem>, val fetchedAt: Long)
        private val cache = mutableMapOf<String, CachedResult>()

        private fun isCacheValid(key: String): Boolean {
            val c = cache[key] ?: return false
            return System.currentTimeMillis() - c.fetchedAt < CACHE_TTL_MS
        }

        private fun unlockKey(key: String) = "unlock_$key"

        private fun isUnlockedByKey(context: Context, key: String): Boolean {
            val ts = context.getSharedPreferences(PREFS_UNLOCKED, Context.MODE_PRIVATE)
                .getLong(unlockKey(key), -1L)
            return ts != -1L && System.currentTimeMillis() - ts < UNLOCK_TTL_MS
        }

        private fun markUnlocked(context: Context, key: String) {
            context.getSharedPreferences(PREFS_UNLOCKED, Context.MODE_PRIVATE)
                .edit().putLong(unlockKey(key), System.currentTimeMillis()).apply()
        }

        private fun keyForLocal(song: InstrumentSong)  = "local_${song.name}"
        private fun keyForRemote(note: NoteItem)     = "remote_${note.submittedAt}"

        fun clearCache() = cache.clear()
        fun clearCache(instrumentType: String) { cache.remove(instrumentType) }
    }

    private var playJob: Job? = null
    private val playHandler = Handler(Looper.getMainLooper())
    var isLearning = false
        private set
    private var learnEvents = listOf<RecordedTap>()
    private var learnStep = 0
    private var learnTypeKey = ""

    // ─── Show LOCAL + FIREBASE notes (merged) ────────────────────

    fun showLocal(
        context: Context,
        instrumentType: String,
        isPremium: Boolean = false,
        showLearn: Boolean = true,
        playRemoteAsSong: Boolean = false,
        localSongsProvider: (Context) -> List<InstrumentSong>,
        onPlay: (InstrumentSong) -> Unit,
        onLearn: (InstrumentSong) -> Unit = {}
    ) {
        this.mContext = context
        this.instrumentType = instrumentType
        val (dialog, binding) = createBottomSheet(context)

        val config = ZaifSDKBuilder.load(context)
        zaifSDKConfig = config

        val appId = config?.applicationId
        if (appId.isNullOrEmpty()) {
            binding.progressContainer.visibility = View.GONE
            return
        }

        binding.etSearch.visibility      = View.VISIBLE
        binding.dividerSearch.visibility = View.VISIBLE
        binding.progressContainer.visibility = View.VISIBLE

        val localSongs = localSongsProvider(context)
        val scores     = localSongs.associate { it.name to HighScoreManager.getHighScore(context, it.name) }
        val allItems   = mutableListOf<SongItem>()
        allItems.addAll(localSongs.map { SongItem.Local(it, scores[it.name] ?: 0) })

        fun isOpen(key: String) = isPremium || isUnlockedByKey(context, key)

        val adapter = SongListAdapter(
            items = allItems.toMutableList(),
            context = context,
            showLearn = showLearn,
            isUnlocked = { item ->
                when (item) {
                    is SongItem.Local  -> isOpen(keyForLocal(item.song))
                    is SongItem.Remote -> isOpen(keyForRemote(item.note))
                }
            },
            onPlay = { item ->
                when (item) {
                    is SongItem.Local -> {
                        val key    = keyForLocal(item.song)
                        val doPlay = { dialog.dismiss(); onPlay(item.song) }
                        if (isOpen(key)) doPlay()
                        else showAdConfirmDialog(context) { onRequestAd { markUnlocked(context, key); doPlay() } }
                    }
                    is SongItem.Remote -> {
                        val note   = item.note
                        val key    = keyForRemote(note)
                        val doPlay = {
                            dialog.dismiss()
                            if (playRemoteAsSong) {
                                val song = remoteNoteToSong(note)
                                if (song != null) onPlay(song)
                            } else {
                                playUserNote(note.jsonNote)
                            }
                        }
                        if (isOpen(key)) doPlay()
                        else showAdConfirmDialog(context) { onRequestAd { markUnlocked(context, key); doPlay() } }
                    }
                }
            },
            onLearn = { item ->
                when (item) {
                    is SongItem.Local -> {
                        val key     = keyForLocal(item.song)
                        val doLearn = { dialog.dismiss(); onLearn(item.song) }
                        if (isOpen(key)) doLearn()
                        else showAdConfirmDialog(context) { onRequestAd { markUnlocked(context, key); doLearn() } }
                    }
                    is SongItem.Remote -> {
                        val note    = item.note
                        val key     = keyForRemote(note)
                        val doLearn = { dialog.dismiss(); startLearnMode(note.jsonNote) }
                        if (isOpen(key)) doLearn()
                        else showAdConfirmDialog(context) { onRequestAd { markUnlocked(context, key); doLearn() } }
                    }
                }
            }
        )
        binding.rvSongs.layoutManager = LinearLayoutManager(context)
        binding.rvSongs.adapter = adapter
        adapter.updateItems(allItems)

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().lowercase().trim()
                val filtered: List<SongItem> = if (q.isEmpty()) allItems
                else allItems.filter { item ->
                    when (item) {
                        is SongItem.Local  -> item.song.name.lowercase().contains(q)
                        is SongItem.Remote -> item.note.recordName.lowercase().contains(q) ||
                                item.note.senderName.lowercase().contains(q)
                    }
                }
                adapter.updateItems(filtered)
            }
        })

        if (isCacheValid(instrumentType)) {
            binding.progressContainer.visibility = View.GONE
            allItems.addAll(cache[instrumentType]!!.notes.map { SongItem.Remote(it) })
            adapter.updateItems(allItems)
            return
        }

        val languageCode = Locale.getDefault().language
        FirebaseFirestore.getInstance()
            .collection(appId)
            .whereEqualTo("status", "published")
            .whereEqualTo("category", instrumentType)
            .whereArrayContainsAny("language", listOf("en", languageCode))
            .orderBy("submitted_at", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { snapshot ->
                binding.progressContainer.visibility = View.GONE
                val notes = snapshot.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val jsonNote = d["json_note"] as? String ?: ""
                    if (jsonNote.isBlank()) return@mapNotNull null
                    NoteItem(
                        recordName  = d["record_name"]  as? String ?: "-",
                        senderName  = d["sender_name"]  as? String ?: "-",
                        submittedAt = d["submitted_at"] as? Long   ?: 0L,
                        jsonNote    = jsonNote
                    )
                }
                cache[instrumentType] = CachedResult(notes, System.currentTimeMillis())
                allItems.addAll(notes.map { SongItem.Remote(it) })
                adapter.updateItems(allItems)
            }
            .addOnFailureListener {
                binding.progressContainer.visibility = View.GONE
                Toast.makeText(context, "Gagal memuat: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createBottomSheet(context: Context): Pair<BottomSheetDialog, DialogTutorialSongListBinding> {
        val dialog  = BottomSheetDialog(context)
        val binding = DialogTutorialSongListBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        // Tidak bisa ditutup dengan sentuhan luar, hanya tombol X atau back device
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener { stopAll() }

        binding.btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()

        dialog.window?.let { window ->
            window.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            )
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }

        val bottomSheet = dialog.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        )
        bottomSheet?.let {
            it.layoutParams = it.layoutParams?.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            val behavior = BottomSheetBehavior.from(it)
            behavior.state          = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed  = true
            behavior.isDraggable    = false
            behavior.peekHeight     = context.resources.displayMetrics.heightPixels
        }

        return Pair(dialog, binding)
    }

    // ─── Adapter ──────────────────────────────────────────────────

    private inner class SongListAdapter(
        private val items: MutableList<SongItem>,
        private val context: Context,
        private val showLearn: Boolean = true,
        private val isUnlocked: (SongItem) -> Boolean,
        private val onPlay: (SongItem) -> Unit,
        private val onLearn: (SongItem) -> Unit
    ) : RecyclerView.Adapter<SongListAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView  = view.findViewById(R.id.tvSongName)
            val tvInfo: TextView  = view.findViewById(R.id.tvSongInfo)
            val btnPlay: TextView = view.findViewById(R.id.btnPlay)
            val btnLearn: TextView = view.findViewById(R.id.btnLearn)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_song_tutorial, parent, false)
            return ViewHolder(view)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item       = items[position]
            val unlocked   = isUnlocked(item)
            val lockSuffix = if (unlocked) "" else " 🔒"

            when (item) {
                is SongItem.Local -> {
                    holder.tvName.text = item.song.name
                    val best  = item.bestScore
                    val total = item.song.notes.size
                    if (best > 0) {
                        holder.tvInfo.visibility = View.VISIBLE
                        holder.tvInfo.text = context.getString(R.string.best)+": $best / $total"
                    } else {
                        holder.tvInfo.visibility = View.GONE
                    }
                    holder.btnPlay.text  = "${context.getString(R.string.play).uppercase()}$lockSuffix"
                    holder.btnLearn.text = "${context.getString(R.string.learn).uppercase()}$lockSuffix"
                }
                is SongItem.Remote -> {
                    val sdf = SimpleDateFormat("dd/MM  HH:mm", Locale.getDefault())
                    holder.tvName.text      = item.note.recordName.uppercase()
                    holder.tvInfo.visibility = View.VISIBLE
                    holder.tvInfo.text = "👤 ${item.note.senderName}  ·  🕐 ${sdf.format(Date(item.note.submittedAt))}"
                    holder.btnPlay.text  = "${context.getString(R.string.play).uppercase()}$lockSuffix"
                    holder.btnLearn.text = "${context.getString(R.string.learn).uppercase()}$lockSuffix"
                }
            }

            holder.btnLearn.visibility = if (showLearn) View.VISIBLE else View.GONE
            holder.btnPlay.setOnClickListener  { onPlay(item) }
            holder.btnLearn.setOnClickListener { onLearn(item) }
        }

        override fun getItemCount() = items.size

        fun updateItems(newItems: List<SongItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }
    }

    // ─── Playback (Firebase notes) ────────────────────────────────

    fun onBilahHit(index: Int) {
        if (!isLearning) return
        val expected = learnEvents.getOrNull(learnStep) ?: return
        if (index == expected.padIndex) {
            learnStep++
            showLearnStep()
        }
    }

    private fun remoteNoteToSong(note: NoteItem): InstrumentSong? {
        return try {
            val arr   = JSONObject(note.jsonNote).getJSONArray("events")
            val notes = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                InstrumentNote(
                    padIndex = o.getInt("padIndex"),
                    timeMs = o.getLong("timestamp"),
                    durationMs = 400L
                )
            }
            InstrumentSong(name = note.recordName, notes = notes)
        } catch (e: Exception) { null }
    }

    private fun parseEvents(jsonNote: String): Pair<List<RecordedTap>, String> {
        val arr    = JSONObject(jsonNote).getJSONArray("events")
        val events = mutableListOf<RecordedTap>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            events.add(RecordedTap(
                padIndex  = o.getInt("padIndex"),
                timestamp = o.getLong("timestamp"),
                metadata  = o.optString("metadata", "")
            ))
        }
        return Pair(events, instrumentType)
    }

    private fun playUserNote(jsonNote: String) {
        if (lifecycleScope == null) return
        stopAll()
        playJob = lifecycleScope.launch {
            val eventsResult = withContext(Dispatchers.Default) {
                try { parseEvents(jsonNote) } catch (e: Exception) { null }
            }
            
            val events = eventsResult?.first
            if (events.isNullOrEmpty()) {
                onToast(mContext?.getString(R.string.invalid_note_format) ?: "Format not tidak valid")
                return@launch
            }

            onPlaybackStatusChanged(true)
            
            var lastTimestamp = 0L
            events.forEach { event ->
                val wait = event.timestamp - lastTimestamp
                if (wait > 0) delay(wait)
                lastTimestamp = event.timestamp

                val isOff = event.metadata == "OFF"
                val metadata = event.metadata.orEmpty()
                
                if (isOff) {
                    onStopNote(event.padIndex, metadata)
                    onUnhighlight(event.padIndex)
                } else {
                    onTriggerAnim(event.padIndex)
                    onPlayNote(event.padIndex, metadata)
                }
            }

            delay(200L)
            onClearHighlight()
            onPlaybackStatusChanged(false)
        }
    }

    private fun startLearnMode(jsonNote: String) {
        if (lifecycleScope == null) return
        stopAll()
        lifecycleScope.launch {
            val eventsResult = withContext(Dispatchers.Default) {
                try { parseEvents(jsonNote) } catch (e: Exception) { null }
            }
            
            val events = eventsResult?.first
            val typeKey = eventsResult?.second ?: instrumentType
            
            if (events.isNullOrEmpty()) {
                onToast(mContext?.getString(R.string.invalid_note_format) ?: "Format not tidak valid")
                return@launch
            }
            
            isLearning   = true
            learnEvents  = events
            learnStep    = 0
            learnTypeKey = typeKey
            onLearnVisible(true)
            showLearnStep()
        }
    }

    private fun showLearnStep() {
        if (learnStep >= learnEvents.size) {
            onToast(mContext?.getString(R.string.learn_complete) ?: "Selesai! Kamu sudah memainkan semua not.")
            stopAll()
            return
        }
        val event = learnEvents[learnStep]
        val key   = event.metadata?.takeIf { it.isNotBlank() } ?: learnTypeKey
        onTabSelect(key.contains(instrumentType))
        onHighlight(event.padIndex)
        onLearnStepUpdate(learnStep + 1, learnEvents.size)
    }

    fun stopAll() {
        playHandler.removeCallbacksAndMessages(null)
        playJob?.cancel()
        playJob     = null
        isLearning  = false
        learnEvents = emptyList()
        learnStep   = 0
        onClearHighlight()
        onLearnVisible(false)
        onPlaybackStatusChanged(false)
    }

    // ─── Dialog konfirmasi sebelum iklan ─────────────────────────

    @SuppressLint("UseKtx")
    private fun showAdConfirmDialog(context: Context, onConfirm: () -> Unit) {
        if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) return
        val d = AlertDialog.Builder(context).create()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A0803"))
                cornerRadius = context.sdpF(SdpR.dimen._14sdp)
                setStroke(context.sdp(SdpR.dimen._1sdp), Color.parseColor("#33F0B429"))
            }
            val p = context.sdp(SdpR.dimen._16sdp)
            setPadding(p, p, p, p)
        }

        root.addView(TextView(context).apply {
            text = "🔒  ${context.getString(R.string.note_locked)}"
            setTextColor(Color.parseColor("#F0B429"))
            textSize = 14f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        })

        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.sdp(
                SdpR.dimen._8sdp))
        })

        root.addView(TextView(context).apply {
            text = context.getString(R.string.watch_ad_unlock_note)
            setTextColor(Color.parseColor("#D2B48C"))
            textSize = 11f
            setLineSpacing(0f, 1.4f)
        })

        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.sdp(
                SdpR.dimen._12sdp))
        })

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        btnRow.addView(buildDialogBtn(context, context.getString(R.string.cancel), "777777") { d.dismiss() })

        btnRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(context.sdp(SdpR.dimen._8sdp), 1)
        })

        btnRow.addView(buildDialogBtn(context, context.getString(R.string.watch_ad_label), "F0B429") {
            d.dismiss()
            onConfirm()
        })

        root.addView(btnRow)
        d.setView(root)
        d.show()
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.80).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    @SuppressLint("UseKtx")
    private fun buildDialogBtn(ctx: Context, label: String, colorHex: String, onClick: () -> Unit) =
        TextView(ctx).apply {
            text = label
            setTextColor(Color.parseColor("#$colorHex"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val pH = ctx.sdp(SdpR.dimen._14sdp)
            val pV = ctx.sdp(SdpR.dimen._8sdp)
            setPadding(pH, pV, pH, pV)
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#40FFFFFF")),
                GradientDrawable().apply {
                    setColor(Color.parseColor("#20$colorHex"))
                    cornerRadius = ctx.sdpF(SdpR.dimen._8sdp)
                    setStroke(ctx.sdp(SdpR.dimen._1sdp), Color.parseColor("#$colorHex"))
                }, null
            )
            setOnClickListener { onClick() }
        }

    private fun Context.sdp(id: Int)  = resources.getDimensionPixelSize(id)
    private fun Context.sdpF(id: Int) = resources.getDimension(id)
}
