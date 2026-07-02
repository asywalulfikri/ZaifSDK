package sound.recorder.widget.tutorial

import android.annotation.SuppressLint
import android.app.Activity
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
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.DocumentSnapshot
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
import sound.recorder.widget.util.CoinManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.intuit.sdp.R as SdpR

class InstrumentTutorialDialog(
    private val lifecycleScope: LifecycleCoroutineScope? = null,
    private val onTabSelect: (metadata: String) -> Unit = {},
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
    private var instrumentPrefix: String = ""
    private var isSustained: Boolean = true

    var zaifSDKConfig: ZaifSDKConfig? = null

    data class NoteItem(
        val recordName: String,
        val senderName: String,
        val submittedAt: Long,
        val status: String,
        val jsonNote: String,
        val isFree: Boolean = false
    )

    sealed class SongItem {
        data class Local(val song: InstrumentSong, val bestScore: Int) : SongItem()
        data class Remote(val note: NoteItem) : SongItem()
    }

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
        private const val UNLOCK_TTL_MS = 24 * 60 * 60 * 1000L   // 1 hari
        private const val PREFS_UNLOCKED = "zaif_note_unlocks"

        private data class CachedResult(val notes: List<NoteItem>, val fetchedAt: Long)
        private val cache = mutableMapOf<String, CachedResult>()

    private fun isAppDebuggable(context: Context?): Boolean {
        return context?.applicationInfo?.let {
            (it.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } ?: false
    }

    private fun isCacheValid(context: Context?, key: String): Boolean {
        if (isAppDebuggable(context)) return false
        if (cache.size > 20) cache.clear()
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
            context.getSharedPreferences(PREFS_UNLOCKED, Context.MODE_PRIVATE).edit {
                putLong(unlockKey(key), System.currentTimeMillis())
            }
        }

        private fun keyForLocal(song: InstrumentSong) = "local_${song.name}"
        private fun keyForRemote(note: NoteItem) = "remote_${note.submittedAt}"

        fun clearCache() = cache.clear()
        fun clearCache(instrumentType: String) {
            cache.remove(instrumentType)
        }
    }

    private var playJob: Job? = null
    private val playHandler = Handler(Looper.getMainLooper())
    private var currentDialog: BottomSheetDialog? = null
    private var lastDocument: DocumentSnapshot? = null
    private var isLoadingMore = false
    private var isLastPage = false
    private val PAGE_SIZE = 50L

    var isLearning = false
        private set
    private var learnEvents = listOf<RecordedTap>()
    private var learnStep = 0
    private var learnTypeKey = ""
    private var dismissShouldStop = true

    // ─── Show LOCAL + FIREBASE notes (merged) ────────────────────

    @SuppressLint("SetTextI18n")
    fun showLocal(
        context: Context,
        instrumentType: String,
        instrumentPrefix: String = "",
        isSustained: Boolean = true,
        isPremium: Boolean = false,
        showLearn: Boolean = true,
        playRemoteAsSong: Boolean = false,
        freeSongKeys: Set<String> = setOf("local_DORAEMON INTRO", "local_HAPPY BIRTHDAY"),
        localSongsProvider: (Context) -> List<InstrumentSong>,
        onPlay: (InstrumentSong) -> Unit,
        onLearn: (InstrumentSong) -> Unit = {}
    ) {
        this.mContext = context
        this.instrumentType = instrumentType
        this.instrumentPrefix = if (instrumentPrefix.isNotBlank()) instrumentPrefix else instrumentType.uppercase()
        this.isSustained = isSustained
        val (dialog, binding) = createBottomSheet(context)

        val config = ZaifSDKBuilder.load(context)
        zaifSDKConfig = config

        val appId = config?.applicationId
        if (appId.isNullOrEmpty()) {
            binding.progressContainer.visibility = View.GONE
            return
        }

        binding.etSearch.visibility = View.VISIBLE
        binding.dividerSearch.visibility = View.VISIBLE
        binding.progressContainer.visibility = View.VISIBLE

        if (zaifSDKConfig?.isCoin == true) {
            binding.tvSubtitle.visibility = View.VISIBLE
            binding.tvSubtitle.text = "🪙 ${CoinManager.getBalance(context)} " + context.getString(R.string.coin)
        } else {
            binding.tvSubtitle.visibility = View.GONE
        }

        val adapter = SongListAdapter(
            items = mutableListOf(),
            context = context,
            showLearn = showLearn,
            isUnlocked = { item ->
                when (item) {
                    is SongItem.Local -> isPremium || keyForLocal(item.song) in freeSongKeys || isUnlockedByKey(context, keyForLocal(item.song))
                    is SongItem.Remote -> item.note.isFree || isPremium || isUnlockedByKey(context, keyForRemote(item.note))
                }
            },
            onPlay = { item ->
                when (item) {
                    is SongItem.Local -> {
                        val key = keyForLocal(item.song)
                        val doPlay = { dismissShouldStop = false; dialog.dismiss(); onPlay(item.song) }
                        if (isPremium || key in freeSongKeys || isUnlockedByKey(context, key)) doPlay()
                        else showUnlockDialog(context, key, onCoinUnlock = { doPlay() }) { onRequestAd { markUnlocked(context, key); doPlay() } }
                    }
                    is SongItem.Remote -> {
                        val note = item.note
                        val key = keyForRemote(note)
                        val doPlay = {
                            dismissShouldStop = false
                            dialog.dismiss()
                            if (playRemoteAsSong) {
                                val song = remoteNoteToSong(note)
                                if (song != null) onPlay(song)
                            } else {
                                playUserNote(note.jsonNote)
                            }
                        }
                        if (note.isFree || isPremium || isUnlockedByKey(context, key)) doPlay()
                        else showUnlockDialog(context, key, onCoinUnlock = { doPlay() }) { onRequestAd { markUnlocked(context, key); doPlay() } }
                    }
                }
            },
            onLearn = { item ->
                when (item) {
                    is SongItem.Local -> {
                        val key = keyForLocal(item.song)
                        val doLearn = { dismissShouldStop = false; dialog.dismiss(); onLearn(item.song) }
                        if (isPremium || key in freeSongKeys || isUnlockedByKey(context, key)) doLearn()
                        else showUnlockDialog(context, key, onCoinUnlock = { doLearn() }) { onRequestAd { markUnlocked(context, key); doLearn() } }
                    }
                    is SongItem.Remote -> {
                        val note = item.note
                        val key = keyForRemote(note)
                        val doLearn = { dismissShouldStop = false; dialog.dismiss(); startLearnMode(note.jsonNote) }
                        if (note.isFree || isPremium || isUnlockedByKey(context, key)) doLearn()
                        else showUnlockDialog(context, key, onCoinUnlock = { doLearn() }) { onRequestAd { markUnlocked(context, key); doLearn() } }
                    }
                }
            }
        )
        val layoutManager = LinearLayoutManager(context)
        binding.rvSongs.layoutManager = layoutManager
        binding.rvSongs.adapter = adapter

        val allItems = mutableListOf<SongItem>()
        lifecycleScope?.launch {
            val localSongs = withContext(Dispatchers.IO) { localSongsProvider(context) }
            val processedLocal = withContext(Dispatchers.IO) {
                localSongs.map { song ->
                    SongItem.Local(song, HighScoreManager.getHighScore(context, song.name))
                }
            }
            allItems.addAll(processedLocal)
            adapter.updateItems(allItems)

            binding.rvSongs.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0 || isLoadingMore || isLastPage || binding.etSearch.text.isNotEmpty()) return
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                    if (visibleItemCount + firstVisibleItemPosition >= totalItemCount && firstVisibleItemPosition >= 0) {
                        loadMoreRemote(appId, instrumentType, binding, adapter, allItems)
                    }
                }
            })

            var searchJob: Job? = null
            binding.etSearch.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    searchJob?.cancel()
                    searchJob = lifecycleScope.launch {
                        delay(300)
                        val q = s.toString().lowercase().trim()
                        val filtered = withContext(Dispatchers.Default) {
                            if (q.isEmpty()) allItems
                            else allItems.filter { item ->
                                when (item) {
                                    is SongItem.Local -> item.song.name.lowercase().contains(q)
                                    is SongItem.Remote -> item.note.recordName.lowercase().contains(q) ||
                                            item.note.senderName.lowercase().contains(q)
                                }
                            }
                        }
                        adapter.updateItems(filtered)
                    }
                }
            })

            if (isCacheValid(context, instrumentType)) {
                binding.progressContainer.visibility = View.GONE
                val cachedNotes = cache[instrumentType]!!.notes
                allItems.addAll(cachedNotes.map { SongItem.Remote(it) })
                adapter.updateItems(allItems)
                isLastPage = cachedNotes.size < PAGE_SIZE
            } else {
                lastDocument = null
                isLoadingMore = false
                isLastPage = false
                fetchFirstPageRemote(appId, instrumentType, binding, adapter, allItems)
            }
        }
    }

    private fun fetchFirstPageRemote(appId: String, instrumentType: String, binding: DialogTutorialSongListBinding, adapter: SongListAdapter, allItems: MutableList<SongItem>) {
        val languageCode = Locale.getDefault().language
        var query = FirebaseFirestore.getInstance()
            .collection(appId)
            .whereEqualTo("category", instrumentType)
        if (!isAppDebuggable(mContext)) {
            query = query.whereEqualTo("status", "published")
                .whereArrayContainsAny("language", listOf("en", languageCode))
        }
        query
            .orderBy("submitted_at", Query.Direction.DESCENDING)
            .limit(PAGE_SIZE)
            .get()
            .addOnSuccessListener { snapshot ->
                binding.progressContainer.visibility = View.GONE
                if (snapshot.isEmpty) {
                    isLastPage = true
                    val remoteOnly = allItems.count { it is SongItem.Remote } == 0
                    if (remoteOnly && allItems.size > 0 && allItems.all { it is SongItem.Local }) {
                        // We have local songs, so don't show full empty state, but maybe just log
                    } else if (allItems.isEmpty()) {
                        binding.progressContainer.visibility = View.VISIBLE
                        binding.progressContainer.getChildAt(0).visibility = View.GONE
                        val tv = binding.progressContainer.findViewById<TextView>(R.id.tvSubtitle) // Reusing tvSubtitle if no specific loading text
                        tv?.text = mContext?.getString(R.string.data_empty)
                    }
                    return@addOnSuccessListener
                }
                lastDocument = snapshot.documents.lastOrNull()
                isLastPage = snapshot.size() < PAGE_SIZE
                val notes = snapshot.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val jsonNote = d["json_note"] as? String ?: ""
                    if (jsonNote.isBlank()) return@mapNotNull null
                    NoteItem(
                        recordName = d["record_name"] as? String ?: "-",
                        senderName = d["sender_name"] as? String ?: "-",
                        submittedAt = d["submitted_at"] as? Long ?: 0L,
                        status = d["status"] as? String ?: "-",
                        jsonNote = jsonNote,
                        isFree = d["is_free"] as? Boolean ?: false
                    )
                }
                cache[instrumentType] = CachedResult(notes, System.currentTimeMillis())
                allItems.addAll(notes.map { SongItem.Remote(it) })
                adapter.updateItems(allItems)
            }
            .addOnFailureListener {
                binding.progressContainer.visibility = View.GONE
            }
    }

    private fun loadMoreRemote(
        appId: String,
        instrumentType: String,
        binding: DialogTutorialSongListBinding,
        adapter: SongListAdapter,
        allItems: MutableList<SongItem>
    ) {
        val lastDoc = lastDocument ?: return
        if (isLoadingMore || isLastPage) return
        isLoadingMore = true
        binding.progressContainer.visibility = View.VISIBLE
        val languageCode = Locale.getDefault().language
        var query = FirebaseFirestore.getInstance()
            .collection(appId)
            .whereEqualTo("category", instrumentType)
        if (!isAppDebuggable(mContext)) {
            query = query.whereEqualTo("status", "published")
                .whereArrayContainsAny("language", listOf("en", languageCode))
        }
        query
            .orderBy("submitted_at", Query.Direction.DESCENDING)
            .startAfter(lastDoc)
            .limit(PAGE_SIZE)
            .get()
            .addOnSuccessListener { snapshot ->
                isLoadingMore = false
                binding.progressContainer.visibility = View.GONE
                if (snapshot.isEmpty) {
                    isLastPage = true
                    return@addOnSuccessListener
                }
                lastDocument = snapshot.documents.lastOrNull()
                isLastPage = snapshot.size() < PAGE_SIZE
                val newNotes = snapshot.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val jsonNote = d["json_note"] as? String ?: ""
                    if (jsonNote.isBlank()) return@mapNotNull null
                    NoteItem(
                        recordName = d["record_name"] as? String ?: "-",
                        senderName = d["sender_name"] as? String ?: "-",
                        submittedAt = d["submitted_at"] as? Long ?: 0L,
                        status = d["status"] as? String ?: "-",
                        jsonNote = jsonNote,
                        isFree = d["is_free"] as? Boolean ?: false
                    )
                }
                val currentCached = cache[instrumentType]?.notes ?: emptyList()
                cache[instrumentType] = CachedResult(currentCached + newNotes, System.currentTimeMillis())
                allItems.addAll(newNotes.map { SongItem.Remote(it) })
                adapter.updateItems(allItems)
            }
            .addOnFailureListener {
                isLoadingMore = false
                binding.progressContainer.visibility = View.GONE
            }
    }

    fun dismiss() {
        currentDialog?.dismiss()
        currentDialog = null
    }

    private fun createBottomSheet(context: Context): Pair<BottomSheetDialog, DialogTutorialSongListBinding> {
        val dialog = BottomSheetDialog(context)
        currentDialog = dialog
        val binding = DialogTutorialSongListBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)
            currentDialog = null
            if (dismissShouldStop) stopAll()
            else dismissShouldStop = true
            mContext = null
        }
        binding.btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
        dialog.window?.let { window ->
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN)
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
        val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            it.layoutParams = it.layoutParams?.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            behavior.isDraggable = false
            behavior.peekHeight = context.resources.displayMetrics.heightPixels
        }
        return Pair(dialog, binding)
    }

    private inner class SongListAdapter(
        private val items: MutableList<SongItem>,
        private val context: Context,
        private val showLearn: Boolean = true,
        private val isUnlocked: (SongItem) -> Boolean,
        private val onPlay: (SongItem) -> Unit,
        private val onLearn: (SongItem) -> Unit
    ) : RecyclerView.Adapter<SongListAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvSongName)
            val tvInfo: TextView = view.findViewById(R.id.tvSongInfo)
            val btnPlay: TextView = view.findViewById(R.id.btnPlay)
            val btnLearn: TextView = view.findViewById(R.id.btnLearn)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song_tutorial, parent, false)
            return ViewHolder(view)
        }

        @SuppressLint("SetTextI18n", "NotifyDataSetChanged")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val unlocked = isUnlocked(item)
            val lockSuffix = if (unlocked) "" else " 🔒"
            when (item) {
                is SongItem.Local -> {
                    holder.tvName.text = item.song.name
                    val best = item.bestScore
                    val total = item.song.notes.size
                    if (best > 0) {
                        holder.tvInfo.visibility = View.VISIBLE
                        holder.tvInfo.text = context.getString(R.string.best) + ": $best / $total"
                    } else {
                        holder.tvInfo.visibility = View.GONE
                    }
                    holder.btnPlay.text = "${context.getString(R.string.play).uppercase()}$lockSuffix"
                    holder.btnLearn.text = "${context.getString(R.string.learn).uppercase()}$lockSuffix"
                }
                is SongItem.Remote -> {
                    val sdf = SimpleDateFormat("dd/MM  HH:mm", Locale.getDefault())
                    if (!isAppDebuggable(context)) {
                        holder.tvName.text = item.note.recordName.uppercase()
                    } else {
                        holder.tvName.text = item.note.recordName.uppercase() + "---" + item.note.status
                    }
                    holder.tvInfo.visibility = View.VISIBLE
                    holder.tvInfo.text = "👤 ${item.note.senderName}  ·  🕐 ${sdf.format(Date(item.note.submittedAt))}"
                    holder.btnPlay.text = "${context.getString(R.string.play).uppercase()}$lockSuffix"
                    holder.btnLearn.text = "${context.getString(R.string.learn).uppercase()}$lockSuffix"
                }
            }
            holder.btnPlay.visibility = View.VISIBLE
            holder.btnLearn.visibility = if (showLearn) View.VISIBLE else View.GONE
            holder.btnPlay.setOnClickListener { onPlay(item) }
            holder.btnLearn.setOnClickListener { onLearn(item) }
        }

        override fun getItemCount() = items.size
        @SuppressLint("NotifyDataSetChanged")
        fun updateItems(newItems: List<SongItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }
    }

    private fun remoteNoteToSong(note: NoteItem): InstrumentSong? {
        try {
            val eventsResult = parseEvents(note.jsonNote)
            val allEvents = eventsResult.first
            if (allEvents.isEmpty()) return null

            val notes = mutableListOf<InstrumentNote>()
            val activeNotes = mutableMapOf<Int, Long>()

            allEvents.forEach { event ->
                val meta = event.metadata ?: ""
                if (meta == "OFF") {
                    val startTime = activeNotes.remove(event.padIndex)
                    if (startTime != null) {
                        val duration = event.timestamp - startTime
                        notes.add(InstrumentNote(event.padIndex, startTime, duration))
                    }
                } else {
                    activeNotes[event.padIndex] = event.timestamp
                }
            }

            activeNotes.forEach { (pad, start) ->
                notes.add(InstrumentNote(pad, start, 400L))
            }

            return InstrumentSong(note.recordName, notes.sortedBy { it.timeMs })
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseEvents(json: String): Pair<List<RecordedTap>, String> {
        val events = mutableListOf<RecordedTap>()
        try {
            val obj = JSONObject(json)
            val arr = obj.getJSONArray("events")
            val instrumentTypeFromNote = obj.optString("category", "")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val padIndex = if (o.has("padIndex")) o.getInt("padIndex") else o.optInt("a", -1)
                val timestamp = if (o.has("timestamp")) o.getLong("timestamp") else o.optLong("b", 0L)
                val metadata = when {
                    o.has("metadata") -> o.optString("metadata", "")
                    o.has("c") -> o.optString("c", "")
                    else -> ""
                }
                if (padIndex == -1) continue
                events.add(RecordedTap(padIndex, timestamp, metadata))
            }
            return Pair(events, instrumentTypeFromNote)
        } catch (e: Exception) {
            return Pair(emptyList(), "")
        }
    }

    private fun playUserNote(jsonNote: String) {
        if (lifecycleScope == null) return
        stopAll()
        playJob = lifecycleScope.launch {
            val eventsResult = withContext(Dispatchers.Default) { try { parseEvents(jsonNote) } catch (e: Exception) { null } }
            val events = eventsResult?.first?.sortedBy { it.timestamp }
            if (events.isNullOrEmpty()) {
                onToast(mContext?.getString(R.string.invalid_note_format) ?: "Invalid format")
                return@launch
            }
            onPlaybackStatusChanged(true)

            val playbackStartTime = System.currentTimeMillis()

            events.forEach { event ->
                val targetTime = playbackStartTime + event.timestamp
                val now = System.currentTimeMillis()
                val wait = targetTime - now
                if (wait > 0) delay(wait)

                if (event.metadata == "OFF") {
                    onStopNote(event.padIndex, event.metadata)
                    onUnhighlight(event.padIndex)
                } else {
                    onTriggerAnim(event.padIndex)
                    onPlayNote(event.padIndex, event.metadata ?: "")
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
            val eventsResult = withContext(Dispatchers.Default) { try { parseEvents(jsonNote) } catch (e: Exception) { null } }
            val allEvents = eventsResult?.first
            val typeKey = eventsResult?.second ?: instrumentType
            if (allEvents.isNullOrEmpty()) {
                onToast(mContext?.getString(R.string.invalid_note_format) ?: "Invalid format")
                return@launch
            }
            val events = allEvents.filter { it.metadata != "OFF" }
            if (events.isEmpty()) {
                onToast(mContext?.getString(R.string.invalid_note_format) ?: "Invalid format")
                return@launch
            }
            isLearning = true
            learnEvents = events
            learnStep = 0
            learnTypeKey = typeKey
            onLearnVisible(true)
            showLearnStep()
        }
    }

    private fun showLearnStep() {
        if (learnStep >= learnEvents.size) {
            onToast(mContext?.getString(R.string.learn_complete) ?: "Complete!")
            stopAll()
            return
        }
        val event = learnEvents[learnStep]
        val key = event.metadata?.takeIf { it.isNotBlank() } ?: learnTypeKey
        onTabSelect(key)
        onHighlight(event.padIndex)
        onLearnStepUpdate(learnStep + 1, learnEvents.size)
    }

    fun onBilahHit(index: Int) {
        if (!isLearning) return
        val expected = learnEvents.getOrNull(learnStep) ?: return
        if (index == expected.padIndex) {
            onUnhighlight(expected.padIndex)
            learnStep++
            showLearnStep()
        }
    }

    fun startLearnFromSong(song: InstrumentSong) {
        stopAll()
        val events = song.notes.map { RecordedTap(it.padIndex, it.timeMs, "") }
        if (events.isEmpty()) return
        isLearning = true
        learnEvents = events
        learnStep = 0
        learnTypeKey = instrumentType
        onLearnVisible(true)
        showLearnStep()
    }

    fun stopAll() {
        playHandler.removeCallbacksAndMessages(null)
        playJob?.cancel()
        playJob = null
        isLearning = false
        learnEvents = emptyList()
        learnStep = 0
        onClearHighlight()
        onLearnVisible(false)
        onPlaybackStatusChanged(false)
    }

    @SuppressLint("UseKtx", "SetTextI18n")
    private fun showUnlockDialog(context: Context, key: String, onCoinUnlock: () -> Unit, onAdConfirm: () -> Unit) {
        if (context is Activity && (context.isFinishing || context.isDestroyed)) return
        val d = AlertDialog.Builder(context).create()
        val balance = CoinManager.getBalance(context)
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

        if (zaifSDKConfig?.isCoin == true) {
            root.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.sdp(SdpR.dimen._8sdp))
            })
            root.addView(TextView(context).apply {
                text = "🪙 " + context.getString(R.string.your_coin) + ": $balance"
                setTextColor(Color.parseColor("#D2B48C"))
                textSize = 11f
                setLineSpacing(0f, 1.4f)
            })
        }

        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.sdp(SdpR.dimen._12sdp))
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
            onAdConfirm()
        })
        if (zaifSDKConfig?.isCoin == true && balance > 0) {
            btnRow.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(context.sdp(SdpR.dimen._8sdp), 1)
            })
            btnRow.addView(buildDialogBtn(context, "🪙 ${CoinManager.UNLOCK_COST} " + context.getString(R.string.coin), "4CAF50") {
                d.dismiss()
                if (CoinManager.spendCoin(context)) {
                    markUnlocked(context, key)
                    onCoinUnlock()
                } else {
                    Toast.makeText(context, context.getString(R.string.coin_not_enough), Toast.LENGTH_SHORT).show()
                }
            })
        }
        root.addView(btnRow)
        d.setView(root)
        d.show()
        d.window?.apply {
            setLayout((context.resources.displayMetrics.widthPixels * 0.85f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun buildDialogBtn(context: Context, label: String, colorHex: String, onClick: () -> Unit): TextView {
        val color = Color.parseColor("#$colorHex")
        return TextView(context).apply {
            text = label
            setTextColor(color)
            textSize = 12f
            setPadding(context.sdp(SdpR.dimen._12sdp), context.sdp(SdpR.dimen._6sdp), context.sdp(SdpR.dimen._12sdp), context.sdp(SdpR.dimen._6sdp))
            typeface = Typeface.DEFAULT_BOLD
            background = RippleDrawable(ColorStateList.valueOf(Color.argb(40, Color.red(color), Color.green(color), Color.blue(color))), null, null)
            setOnClickListener { onClick() }
        }
    }

    private fun Context.sdp(id: Int): Int = resources.getDimensionPixelSize(id)
    private fun Context.sdpF(id: Int): Float = resources.getDimension(id)
}
