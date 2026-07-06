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
import android.os.Build
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
        val docId: String,
        val recordName: String,
        val senderName: String,
        val submittedAt: Long,
        val status: String,
        val jsonNote: String,
        val isFree: Boolean = false,
        val language: List<String> = emptyList()
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
    private var currentAdapter: SongListAdapter? = null
    private val allItems = mutableListOf<SongItem>()
    private var lastDocument: DocumentSnapshot? = null
    private var isLoadingMore = false
    private var isLastPage = false
    private val PAGE_SIZE = 100L

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

        val appId = config?.applicationId?.takeIf { it.isNotEmpty() } ?: zaifSDKConfig?.applicationId
        if (appId.isNullOrEmpty()) {
            binding.progressContainer.visibility = View.GONE
            // If appId is null, we can still show local songs
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
            items = allItems,
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
                                if (song != null) {
                                    val endMs = song.notes.maxOfOrNull { it.timeMs + it.durationMs } ?: 0L
                                    onPlaybackStatusChanged(true)
                                    onPlay(song)
                                    playHandler.postDelayed({ onPlaybackStatusChanged(false) }, endMs + 300L)
                                }
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
        this.currentAdapter = adapter

        allItems.clear()
        lifecycleScope?.launch {
            val localSongs = withContext(Dispatchers.IO) { localSongsProvider(context) }
            val processedLocal = withContext(Dispatchers.IO) {
                localSongs.map { song ->
                    SongItem.Local(song, HighScoreManager.getHighScore(context, song.name))
                }
            }
            
            withContext(Dispatchers.Main) {
                allItems.addAll(processedLocal)
                adapter.notifyDataSetChanged()
            }

            binding.rvSongs.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0 || isLoadingMore || isLastPage || binding.etSearch.text.isNotEmpty()) return
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                    if (visibleItemCount + firstVisibleItemPosition >= totalItemCount && firstVisibleItemPosition >= 0) {
                        appId?.let { loadMoreRemote(it, instrumentType, binding, adapter, allItems) }
                    }
                }
            })

            var searchJob: Job? = null
            binding.etSearch.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    searchJob?.cancel()
                    searchJob = lifecycleScope?.launch {
                        delay(300)
                        val q = s.toString().lowercase().trim()
                        val filtered = withContext(Dispatchers.Default) {
                            if (q.isEmpty()) allItems.toList()
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
                adapter.notifyDataSetChanged()
                isLastPage = cachedNotes.size < PAGE_SIZE
            } else if (!appId.isNullOrEmpty()) {
                lastDocument = null
                isLoadingMore = false
                isLastPage = false
                fetchFirstPageRemote(appId, instrumentType, binding, adapter, allItems)
            } else {
                binding.progressContainer.visibility = View.GONE
            }
        }
    }

    private fun isNetworkAvailable(context: Context?): Boolean {
        if (context == null) return false
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            return connectivityManager.activeNetworkInfo?.isConnected ?: false
        }
    }

    private fun fetchFirstPageRemote(appId: String, instrumentType: String, binding: DialogTutorialSongListBinding, adapter: SongListAdapter, allItems: MutableList<SongItem>) {
        if (!isNetworkAvailable(mContext)) {
            binding.progressContainer.visibility = View.VISIBLE
            binding.progressBar.visibility = View.GONE
            val tvLoading = binding.progressContainer.findViewById<TextView>(R.id.tvLoading)
            tvLoading?.visibility = View.VISIBLE
            tvLoading?.text = (mContext?.getString(R.string.no_internet_connection) ?: "") + "\n" + (mContext?.getString(R.string.turn_on_internet_for_more_tutorial) ?: "Turn on the internet to view more tutorials!")
            return
        }

        val languageCode = Locale.getDefault().language
        binding.progressContainer.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE
        binding.progressContainer.findViewById<TextView>(R.id.tvLoading)?.visibility = View.GONE
        
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
                        binding.progressBar.visibility = View.GONE
                        val tvLoading = binding.progressContainer.findViewById<TextView>(R.id.tvLoading)
                        tvLoading?.visibility = View.VISIBLE
                        tvLoading?.text = mContext?.getString(R.string.data_empty)
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
                        docId = doc.id,
                        recordName = d["record_name"] as? String ?: "-",
                        senderName = d["sender_name"] as? String ?: "-",
                        submittedAt = d["submitted_at"] as? Long ?: 0L,
                        status = d["status"] as? String ?: "-",
                        jsonNote = jsonNote,
                        isFree = d["is_free"] as? Boolean ?: false,
                        language = (d["language"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    )
                }
                cache[instrumentType] = CachedResult(notes, System.currentTimeMillis())
                allItems.addAll(notes.map { SongItem.Remote(it) })
                adapter.notifyDataSetChanged()
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
        
        if (!isNetworkAvailable(mContext)) {
            onToast(mContext?.getString(R.string.no_internet_connection) ?: "No Internet")
            return
        }

        isLoadingMore = true
        binding.progressContainer.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE
        binding.progressContainer.findViewById<TextView>(R.id.tvLoading)?.visibility = View.GONE

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
                        docId = doc.id,
                        recordName = d["record_name"] as? String ?: "-",
                        senderName = d["sender_name"] as? String ?: "-",
                        submittedAt = d["submitted_at"] as? Long ?: 0L,
                        status = d["status"] as? String ?: "-",
                        jsonNote = jsonNote,
                        isFree = d["is_free"] as? Boolean ?: false,
                        language = (d["language"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    )
                }
                val currentCached = cache[instrumentType]?.notes ?: emptyList()
                cache[instrumentType] = CachedResult(currentCached + newNotes, System.currentTimeMillis())
                allItems.addAll(newNotes.map { SongItem.Remote(it) })
                adapter.notifyDataSetChanged()
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

        // 1. Setup window flags BEFORE show() to minimize layout cycles
        dialog.window?.let { window ->
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or 
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or 
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or 
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or 
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or 
                View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }

        // 2. Setup BottomSheet layout and behavior BEFORE show() if possible
        val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            it.layoutParams = it.layoutParams?.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            behavior.isDraggable = false
            behavior.peekHeight = context.resources.displayMetrics.heightPixels
        }

        dialog.setOnDismissListener {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)
            currentDialog = null
            if (dismissShouldStop) stopAll()
            else dismissShouldStop = true
            mContext = null
        }
        
        binding.btnClose.setOnClickListener { dialog.dismiss() }
        
        // 3. Finally show the dialog
        dialog.show()

        // 4. Clear NOT_FOCUSABLE after show to allow interaction
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

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
            val layoutAdmin: View = view.findViewById(R.id.layoutAdmin)
            val btnPublish: TextView = view.findViewById(R.id.btnPublish)
            val btnEdit: TextView = view.findViewById(R.id.btnEdit)
            val btnDelete: TextView = view.findViewById(R.id.btnDelete)
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
            val isDebug = isAppDebuggable(context)

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
                    holder.layoutAdmin.visibility = View.GONE
                }
                is SongItem.Remote -> {
                    val sdf = SimpleDateFormat("dd/MM  HH:mm", Locale.getDefault())
                    if (!isDebug) {
                        holder.tvName.text = item.note.recordName.uppercase()
                    } else {
                        holder.tvName.text = item.note.recordName.uppercase() + "---" + item.note.status
                    }
                    holder.tvInfo.visibility = View.VISIBLE
                    holder.tvInfo.text = "👤 ${item.note.senderName}  ·  🕐 ${sdf.format(Date(item.note.submittedAt))}"
                    holder.btnPlay.text = "${context.getString(R.string.play).uppercase()}$lockSuffix"
                    holder.btnLearn.text = "${context.getString(R.string.learn).uppercase()}$lockSuffix"

                    if (isDebug) {
                        holder.layoutAdmin.visibility = View.VISIBLE
                        holder.btnEdit.setOnClickListener { showEditChoiceDialog(holder.itemView.context, item.note) }
                        holder.btnPublish.visibility = if (item.note.status == "published") View.GONE else View.VISIBLE
                        holder.btnPublish.setOnClickListener { publishNote(holder.itemView.context, item.note) }
                        holder.btnDelete.setOnClickListener { deleteNote(holder.itemView.context, item.note) }
                    } else {
                        holder.layoutAdmin.visibility = View.GONE
                    }
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
        return try {
            val arr = JSONObject(note.jsonNote).getJSONArray("events")
            val allEvents = (0 until arr.length()).map { arr.getJSONObject(it) }
            val notes = mutableListOf<InstrumentNote>()

            // Dynamic sustain detection: check if any event has "OFF" metadata
            val hasSustain = allEvents.any {
                val meta = if (it.has("metadata")) it.optString("metadata", "") else it.optString("c", "")
                meta == "OFF"
            }

            if (hasSustain) {
                val activeNotes = mutableMapOf<Int, Long>()
                for (o in allEvents) {
                    val padIndex = if (o.has("padIndex")) o.getInt("padIndex") else o.optInt("a", -1)
                    val timestamp = if (o.has("timestamp")) o.getLong("timestamp") else o.optLong("b", 0L)
                    val metadata = if (o.has("metadata")) o.optString("metadata", "") else o.optString("c", "")

                    if (metadata == "OFF") {
                        val startTime = activeNotes.remove(padIndex)
                        if (startTime != null) {
                            notes.add(InstrumentNote(padIndex, startTime, timestamp - startTime))
                        }
                    } else {
                        // Filter by prefix if provided and metadata starts with something else
                        if (instrumentPrefix.isEmpty() || metadata.isEmpty() || metadata.startsWith(instrumentPrefix) || !metadata.contains("_")) {
                            activeNotes[padIndex] = timestamp
                        }
                    }
                }
                // Sustain notes without a matching OFF get a default duration
                activeNotes.forEach { (pad, start) ->
                    notes.add(InstrumentNote(pad, start, 400L))
                }
            } else {
                // Tap/percussion: each event is an independent note
                for (o in allEvents) {
                    val padIndex = if (o.has("padIndex")) o.getInt("padIndex") else o.optInt("a", -1)
                    val timestamp = if (o.has("timestamp")) o.getLong("timestamp") else o.optLong("b", 0L)
                    val metadata = if (o.has("metadata")) o.optString("metadata", "") else o.optString("c", "")
                    
                    if (instrumentPrefix.isEmpty() || metadata.isEmpty() || metadata.startsWith(instrumentPrefix) || !metadata.contains("_")) {
                        notes.add(InstrumentNote(padIndex, timestamp, 400L))
                    }
                }
            }

            InstrumentSong(name = note.recordName, notes = notes.sortedBy { it.timeMs })
        } catch (e: Exception) {
            null
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
            val eventsResult = withContext(Dispatchers.Default) {
                try {
                    parseEvents(jsonNote)
                } catch (e: Exception) {
                    null
                }
            }
            val events = eventsResult?.first
            if (events.isNullOrEmpty()) {
                onToast(mContext?.getString(R.string.invalid_note_format) ?: "Invalid format")
                return@launch
            }
            onPlaybackStatusChanged(true)

            var lastTimestamp = 0L
            events.forEach { event ->
                val wait = event.timestamp - lastTimestamp
                if (wait > 0) delay(wait)
                lastTimestamp = event.timestamp

                val metadata = event.metadata.orEmpty()
                val isOff = metadata == "OFF"

                // Filter by prefix if provided
                val isCurrentInstrument = instrumentPrefix.isEmpty() || metadata.isEmpty() || metadata.startsWith(instrumentPrefix) || !metadata.contains("_")

                if (isOff) {
                    onStopNote(event.padIndex, metadata)
                    onUnhighlight(event.padIndex)
                } else if (isCurrentInstrument) {
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

    private fun publishNote(ctx: Context, note: NoteItem) {
        val appId = zaifSDKConfig?.applicationId ?: return
        AlertDialog.Builder(ctx).apply {
            setTitle("Publish Data?")
            setMessage("Data ini akan ditampilkan ke publik (semua user).")
            setPositiveButton("Publish") { _, _ ->
                FirebaseFirestore.getInstance().collection(appId).document(note.docId)
                    .update("status", "published")
                    .addOnSuccessListener {
                        onToast("Berhasil di-publish!")
                        clearCache(instrumentType)

                        // Update local list & notify adapter
                        val index = allItems.indexOfFirst { it is SongItem.Remote && it.note.docId == note.docId }
                        if (index != -1) {
                            val currentRemote = allItems[index] as SongItem.Remote
                            allItems[index] = currentRemote.copy(note = currentRemote.note.copy(status = "published"))
                            currentAdapter?.notifyItemChanged(index)
                        }
                    }
                    .addOnFailureListener { e -> onToast("Gagal publish: ${e.message}") }
            }
            setNegativeButton("Batal", null)
            show()
        }
    }

    private fun showEditChoiceDialog(ctx: Context, note: NoteItem) {
        val dialog = AlertDialog.Builder(ctx).create()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A0F09"))
                cornerRadius = ctx.sdpF(SdpR.dimen._14sdp)
                setStroke(ctx.sdp(SdpR.dimen._1sdp), Color.parseColor("#22D2B48C"))
            }
            val pad = ctx.sdp(SdpR.dimen._16sdp)
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(ctx).apply {
            text = "Pilih yang ingin diedit"
            setTextColor(Color.parseColor("#F5F5DC"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = ctx.sdp(SdpR.dimen._12sdp)
            }
        })

        val options = listOf(
            "Nama Rekaman" to "7BAFD4",
            "JSON Note"    to "D2B48C",
            "Language"     to "A8D8A8",
            "Sender Name"  to "FFB347",
            "isFree"       to "C792EA"
        )

        options.forEach { (label, color) ->
            root.addView(TextView(ctx).apply {
                text = label
                setTextColor(Color.parseColor("#$color"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
                val padH = ctx.sdp(SdpR.dimen._12sdp)
                val padV = ctx.sdp(SdpR.dimen._10sdp)
                setPadding(padH, padV, padH, padV)
                background = RippleDrawable(
                    ColorStateList.valueOf(Color.parseColor("#40FFFFFF")),
                    GradientDrawable().apply {
                        setColor(Color.parseColor("#20$color"))
                        cornerRadius = ctx.sdpF(SdpR.dimen._8sdp)
                        setStroke(ctx.sdp(SdpR.dimen._1sdp), Color.parseColor("#$color"))
                    }, null
                )
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = ctx.sdp(SdpR.dimen._8sdp)
                }
                setOnClickListener {
                    dialog.dismiss()
                    when (label) {
                        "Nama Rekaman" -> showEditNameDialog(ctx, note)
                        "JSON Note"    -> showEditJsonNoteDialog(ctx, note)
                        "Language"     -> showEditLanguageDialog(ctx, note)
                        "Sender Name"  -> showEditSenderNameDialog(ctx, note)
                        "isFree"       -> showEditIsFreeDialog(ctx, note)
                    }
                }
            })
        }
        dialog.setView(root)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((ctx.resources.displayMetrics.widthPixels * 0.85).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun showEditNameDialog(ctx: Context, note: NoteItem) {
        val dialog = AlertDialog.Builder(ctx).create()
        val root = buildEditRoot(ctx, "Edit Nama Rekaman")
        val input = buildEditText(ctx, note.recordName, "Nama rekaman")
        root.addView(input)
        root.addView(buildEditActionRow(ctx, dialog, "Simpan", "D2B48C") {
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty()) {
                updateNoteField(note, "record_name", newName)
                dialog.dismiss()
            }
        })
        dialog.setView(root)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun showEditJsonNoteDialog(ctx: Context, note: NoteItem) {
        val dialog = AlertDialog.Builder(ctx).create()
        val root = buildEditRoot(ctx, "Edit JSON Note")
        val input = buildEditText(ctx, note.jsonNote, "JSON...", isMultiLine = true)
        root.addView(input)
        root.addView(buildEditActionRow(ctx, dialog, "Simpan", "D2B48C") {
            updateNoteField(note, "json_note", input.text.toString().trim())
            dialog.dismiss()
        })
        dialog.setView(root)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun showEditLanguageDialog(ctx: Context, note: NoteItem) {
        val dialog = AlertDialog.Builder(ctx).create()
        val root = buildEditRoot(ctx, "Edit Language (comma separated)")
        val input = buildEditText(ctx, note.language.joinToString(","), "id,en...")
        root.addView(input)
        root.addView(buildEditActionRow(ctx, dialog, "Simpan", "A8D8A8") {
            val list = input.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
            updateNoteField(note, "language", list)
            dialog.dismiss()
        })
        dialog.setView(root)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun showEditSenderNameDialog(ctx: Context, note: NoteItem) {
        val dialog = AlertDialog.Builder(ctx).create()
        val root = buildEditRoot(ctx, "Edit Sender Name")
        val input = buildEditText(ctx, note.senderName, "Nama pengirim")
        root.addView(input)
        root.addView(buildEditActionRow(ctx, dialog, "Simpan", "FFB347") {
            updateNoteField(note, "sender_name", input.text.toString().trim())
            dialog.dismiss()
        })
        dialog.setView(root)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun showEditIsFreeDialog(ctx: Context, note: NoteItem) {
        val dialog = AlertDialog.Builder(ctx).create()
        val root = buildEditRoot(ctx, "Edit isFree")
        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, ctx.sdp(SdpR.dimen._12sdp), 0, 0)
        }
        btnRow.addView(buildDialogBtn(ctx, "TRUE", "4CAF50") { updateNoteField(note, "is_free", true); dialog.dismiss() })
        btnRow.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(ctx.sdp(SdpR.dimen._8sdp), 1) })
        btnRow.addView(buildDialogBtn(ctx, "FALSE", "FF5252") { updateNoteField(note, "is_free", false); dialog.dismiss() })
        root.addView(btnRow)
        dialog.setView(root)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun updateNoteField(note: NoteItem, field: String, value: Any) {
        val appId = zaifSDKConfig?.applicationId ?: return
        FirebaseFirestore.getInstance().collection(appId).document(note.docId)
            .update(field, value)
            .addOnSuccessListener {
                onToast("Update $field berhasil!")
                clearCache(instrumentType)
                // Update local model
                val index = allItems.indexOfFirst { it is SongItem.Remote && it.note.docId == note.docId }
                if (index != -1) {
                    val remote = allItems[index] as SongItem.Remote
                    val updatedNote = when(field) {
                        "record_name" -> remote.note.copy(recordName = value as String)
                        "sender_name" -> remote.note.copy(senderName = value as String)
                        "json_note"   -> remote.note.copy(jsonNote = value as String)
                        "is_free"     -> remote.note.copy(isFree = value as Boolean)
                        else -> remote.note
                    }
                    allItems[index] = remote.copy(note = updatedNote)
                    currentAdapter?.notifyItemChanged(index)
                }
            }
    }

    private fun buildEditRoot(ctx: Context, title: String) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#1A0F09"))
            cornerRadius = ctx.sdpF(SdpR.dimen._14sdp)
        }
        setPadding(ctx.sdp(SdpR.dimen._16sdp), ctx.sdp(SdpR.dimen._16sdp), ctx.sdp(SdpR.dimen._16sdp), ctx.sdp(SdpR.dimen._16sdp))
        addView(TextView(ctx).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })
    }

    private fun buildEditText(ctx: Context, initial: String, hintText: String, isMultiLine: Boolean = false) = android.widget.EditText(ctx).apply {
        setText(initial)
        hint = hintText
        setTextColor(Color.WHITE)
        setHintTextColor(Color.GRAY)
        textSize = 13f
        if (isMultiLine) {
            minLines = 3
            gravity = Gravity.TOP
        }
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#2D1B10"))
            cornerRadius = ctx.sdpF(SdpR.dimen._8sdp)
        }
        setPadding(ctx.sdp(SdpR.dimen._10sdp), ctx.sdp(SdpR.dimen._10sdp), ctx.sdp(SdpR.dimen._10sdp), ctx.sdp(SdpR.dimen._10sdp))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.sdp(SdpR.dimen._12sdp) }
    }

    private fun buildEditActionRow(ctx: Context, d: AlertDialog, btnLabel: String, btnColor: String, onSave: () -> Unit) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        setPadding(0, ctx.sdp(SdpR.dimen._12sdp), 0, 0)
        addView(buildDialogBtn(ctx, "Batal", "777777") { d.dismiss() })
        addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(ctx.sdp(SdpR.dimen._8sdp), 1) })
        addView(buildDialogBtn(ctx, btnLabel, btnColor) { onSave() })
    }

    private fun deleteNote(ctx: Context, note: NoteItem) {
        val appId = zaifSDKConfig?.applicationId ?: return
        AlertDialog.Builder(ctx).apply {
            setTitle("Hapus Data?")
            setMessage("Data ini akan dihapus permanen dari server.")
            setPositiveButton("Hapus") { _, _ ->
                FirebaseFirestore.getInstance().collection(appId).document(note.docId)
                    .delete()
                    .addOnSuccessListener {
                        onToast("Data berhasil dihapus.")
                        clearCache(instrumentType)
                        
                        // Update local list & notify adapter
                        val index = allItems.indexOfFirst { it is SongItem.Remote && it.note.docId == note.docId }
                        if (index != -1) {
                            allItems.removeAt(index)
                            currentAdapter?.notifyItemRemoved(index)
                        }
                    }
                    .addOnFailureListener { e -> onToast("Gagal hapus: ${e.message}") }
            }
            setNegativeButton("Batal", null)
            show()
        }
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
