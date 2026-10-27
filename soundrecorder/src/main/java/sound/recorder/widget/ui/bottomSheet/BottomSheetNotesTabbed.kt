package sound.recorder.widget.ui.bottomSheet

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.intuit.sdp.R as SdpR
import sound.recorder.widget.R
import sound.recorder.widget.adapter.FirebaseNotesAdapter
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.builder.ZaifSDKConfig
import sound.recorder.widget.databinding.BottomSheetNotesTabbedBinding
import sound.recorder.widget.listener.MyNoteListener
import sound.recorder.widget.notes.DatabaseHelper
import sound.recorder.widget.notes.Note
import sound.recorder.widget.notes.NotesAdapter
import sound.recorder.widget.notes.utils.RecyclerTouchListener
import sound.recorder.widget.recording.RecordingDialogPalette
import sound.recorder.widget.recording.RecordingDialogStyle
import sound.recorder.widget.recording.RecordingDialogViews
import sound.recorder.widget.recording.RecordingDialogViews.SheetItem
import sound.recorder.widget.recording.RecordingDialogViews.sdp
import sound.recorder.widget.tutorial.InstrumentTutorialDialog
import sound.recorder.widget.util.Toastic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BottomSheetNotesTabbed : BottomSheetDialogFragment() {

    private var _binding: BottomSheetNotesTabbedBinding? = null
    private val binding get() = _binding!!

    // Local state
    private var localNotesList = ArrayList<Note>()
    private var dbHelper: DatabaseHelper? = null
    private var localAdapter: NotesAdapter? = null

    // Online state
    private var onlineFullList = listOf<Note>()
    private var onlineAdapter: FirebaseNotesAdapter? = null
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var collectionPath = "not"

    private var isOnlineTab = false
    private var zaifSDKConfig: ZaifSDKConfig? = null
    private var searchJob: Job? = null

    // Cache config
    private val PREF_NAME = "notes_cache"
    private val KEY_NOTES = "cached_notes"
    private val KEY_TIMESTAMP = "cache_timestamp"
    private val CACHE_DURATION_MS = 6 * 60 * 60 * 1000L // 6 jam

    // Warna tampilan. Pakai context Activity agar colorPrimary diambil dari tema aplikasi,
    // bukan dari tema dialog (FullScreenDialogTheme).
    private val palette by lazy { RecordingDialogPalette(requireActivity(), RecordingDialogStyle.default) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Gunakan FullScreenDialogTheme agar bisa mentok sampai status bar
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetNotesTabbedBinding.inflate(inflater, container, false)
        return binding.root
    }

    companion object {
        private const val ARG_START_ONLINE = "arg_start_online"

        fun newInstance(startOnline: Boolean = false): BottomSheetNotesTabbed {
            val fragment = BottomSheetNotesTabbed()
            val args = Bundle()
            args.putBoolean(ARG_START_ONLINE, startOnline)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        zaifSDKConfig = ZaifSDKBuilder.load(requireContext())
        collectionPath = collectionPath+"_"+zaifSDKConfig?.applicationId
        dbHelper = DatabaseHelper.getInstance(requireContext())

        applySheetStyle()
        setupBottomSheet()
        setupCommonActions()
        setupTabs()
        setupRecyclerViews()

        val isPromoteEnabled = zaifSDKConfig?.isPromotNote ?: false
        if (!isPromoteEnabled) {
            binding.tabStrip.visibility = View.GONE
            switchToLocalTab()
        } else {
            binding.tabStrip.visibility = View.VISIBLE
            val startOnline = arguments?.getBoolean(ARG_START_ONLINE, false) ?: false
            if (startOnline) {
                switchToOnlineTab()
            } else {
                switchToLocalTab()
            }
        }
    }

    private fun setupBottomSheet() {
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = STATE_EXPANDED
            isDraggable = false
            skipCollapsed = true
        }
        dialog?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
            } else {
                @Suppress("DEPRECATION")
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
        }
    }

    private fun setupCommonActions() {
        binding.ivClose.setOnClickListener { dismissAllowingStateLoss() }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300) // Debounce search
                    filterNotes(s.toString())
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.fab.setOnClickListener {
            showNoteDialog(false, null, -1)
        }
    }

    private fun setupTabs() {
        binding.tabLocal.setOnClickListener { switchToLocalTab() }
        binding.tabOnline.setOnClickListener { switchToOnlineTab() }
    }

    private fun switchToLocalTab() {
        isOnlineTab = false
        updateTabUI()
        binding.fab.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE

        binding.recyclerView.adapter = localAdapter
        loadLocalNotes()
    }

    private fun switchToOnlineTab() {
        isOnlineTab = true
        updateTabUI()
        binding.fab.visibility = View.GONE

        binding.recyclerView.adapter = onlineAdapter
        loadOnlineNotes()
    }

    private fun updateTabUI() {
        val context = requireContext()
        val c = palette
        val radius = SdpR.dimen._8sdp
        val active = if (!isOnlineTab) binding.tabLocal else binding.tabOnline
        val inactive = if (!isOnlineTab) binding.tabOnline else binding.tabLocal
        active.setTextColor(c.onAccent)
        active.background = RecordingDialogViews.rounded(context, c.accent, radius)
        inactive.setTextColor(c.textSecondary)
        inactive.background = null
    }

    /** Warna sheet, header, tab, pencarian, tampilan kosong & tombol tambah (hanya tampilan). */
    private fun applySheetStyle() {
        val context = requireContext()
        val c = palette
        binding.sheetRoot.background = GradientDrawable().apply {
            setColor(c.surface)
            val r = context.resources.getDimension(SdpR.dimen._16sdp)
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
        binding.sheetHandle.background = RecordingDialogViews.rounded(context, c.outline, SdpR.dimen._2sdp)
        binding.tvSheetTitle.setTextColor(c.text)
        binding.ivClose.imageTintList = android.content.res.ColorStateList.valueOf(c.textSecondary)
        binding.progressBar.indeterminateTintList = android.content.res.ColorStateList.valueOf(c.accent)
        binding.tabStrip.background = RecordingDialogViews.rounded(context, c.surfaceAlt, SdpR.dimen._10sdp)
        binding.searchContainer.background = RecordingDialogViews.rounded(context, c.surfaceAlt, SdpR.dimen._10sdp, c.outline)
        binding.ivSearch.imageTintList = android.content.res.ColorStateList.valueOf(c.textSecondary)
        binding.etSearch.setTextColor(c.text)
        binding.etSearch.setHintTextColor(c.textSecondary)
        binding.emptyIconBg.background = RecordingDialogViews.circle(c.accentSoft)
        binding.ivEmpty.imageTintList = android.content.res.ColorStateList.valueOf(c.accent)
        binding.tvEmpty.setTextColor(c.textSecondary)
        binding.fab.backgroundTintList = android.content.res.ColorStateList.valueOf(c.accent)
        binding.fab.imageTintList = android.content.res.ColorStateList.valueOf(c.onAccent)
    }

    // ─── Local Notes Logic ────────────────────────────────────────────────────

    override fun onStart() {
        super.onStart()

        val dialog = dialog
        if (dialog != null) {
            // Paksa BottomSheet agar tingginya maksimal (mentok atas)
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
                behavior.state = STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = false
            }

            // Pastikan window menempati seluruh layar termasuk area status bar
            dialog.window?.let { window ->
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.setDecorFitsSystemWindows(false)
                }
            }
        }

        // Optimasi Lebar Dialog di Mode Landscape
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            dialog?.window?.let { window ->
                val params = window.attributes
                val displayMetrics = resources.displayMetrics
                // Batasi lebar ke 85% layar atau max 640dp (mana yang lebih kecil)
                val maxWidthPx = (640 * displayMetrics.density).toInt()
                val targetWidth = (displayMetrics.widthPixels * 0.85).toInt()
                params.width = if (targetWidth > maxWidthPx) maxWidthPx else targetWidth
                window.attributes = params
            }
        }
    }

    private fun setupRecyclerViews() {
        localAdapter = NotesAdapter(localNotesList) { position ->
            showActionsDialog(position)
        }
        onlineAdapter = FirebaseNotesAdapter(
            notesList = onlineFullList,
            isDebug = isAppDebuggable(requireContext()),
            onViewClick = { note -> showViewNoteDialog(note) },
            onStatusToggleClick = { note -> toggleOnlineNoteStatus(note) },
            onDeleteClick = { note -> deleteOnlineNote(note) },
            onItemClick = { note -> showViewNoteDialog(note) }
        )

        localAdapter?.palette = palette
        onlineAdapter?.palette = palette

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            itemAnimator = DefaultItemAnimator()
            // Adapter will be set in switchToLocalTab / switchToOnlineTab
        }
    }

    private fun loadLocalNotes() {
        lifecycleScope.launch(Dispatchers.IO) {
            val notes = dbHelper?.allNotes.orEmpty()
            withContext(Dispatchers.Main) {
                if (_binding == null || !isAdded) return@withContext
                
                localNotesList.clear()
                localNotesList.addAll(notes)
                localAdapter?.notifyDataSetChanged()
                toggleEmptyView(localNotesList.isEmpty())
            }
        }
    }

    private fun showActionsDialog(position: Int) {
        val activity = activity ?: return
        
        // Pre-fetch all strings to avoid IllegalStateException if fragment is detached during callback
        val strUseNote = getString(R.string.use_note)
        val strEditNote = getString(R.string.edit_note)
        val strDeleteNote = getString(R.string.delete_not)
        val strPromote = getString(R.string.promosikan)
        val strChoose = getString(R.string.choose)

        val optionsList = mutableListOf<CharSequence>(strUseNote, strEditNote, strDeleteNote)

        val isPromoteEnabled = zaifSDKConfig?.isPromotNote ?: false
        if (isPromoteEnabled) {
            optionsList.add(strPromote)
        }

        val options = optionsList.toTypedArray()

        fun onOptionSelected(which: Int) {
                if (!isAdded || position !in localNotesList.indices) return

                val selectedOption = options[which]
                when (selectedOption) {
                    strUseNote -> {
                        try {
                            val rawNote = localNotesList[position].note
                            val noteText = try {
                                val value = Gson().fromJson(rawNote, Note::class.java)
                                value.note
                            } catch (e: Exception) {
                                rawNote
                            }
                            MyNoteListener.postNote(noteText.toString())
                            dismissAllowingStateLoss()
                        } catch (e: Exception) {}
                    }
                    strEditNote -> showNoteDialog(true, localNotesList[position], position)
                    strDeleteNote -> deleteLocalNote(position)
                    strPromote -> showPromoteConfirmation(localNotesList[position])
                }
        }

        val icons = mapOf<CharSequence, Int>(
            strUseNote to R.drawable.zaif_rec_ic_play,
            strEditNote to R.drawable.zaif_rec_ic_edit,
            strDeleteNote to R.drawable.zaif_rec_ic_delete,
            strPromote to R.drawable.zaif_rec_ic_star
        )
        RecordingDialogViews.actionSheet(activity, palette, strChoose, options.mapIndexed { which, option ->
            SheetItem(
                icon = icons[option] ?: R.drawable.zaif_rec_ic_queue_music,
                label = option.toString(),
                color = if (option == strDeleteNote) palette.error else null
            ) { onOptionSelected(which) }
        })
    }

    private fun showPromoteConfirmation(note: Note) {
        val context = context ?: return
        
        // Pre-fetch strings
        val strAlreadyPromoted = getString(R.string.already_promoted)
        val strLimitPromoted = getString(R.string.limit_promot)
        val strPromote = getString(R.string.promosikan)
        val strPromoteInfo = getString(R.string.promot_info)
        val strSend = getString(R.string.send)
        val strCancel = getString(R.string.cancel)

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val prefs = context.getSharedPreferences("note_promo_prefs", Context.MODE_PRIVATE)

        // 1. Check if already promoted (Signature check)
        val (cleanTitle, cleanNote) = getCleanNoteData(note)
        val currentSignature = "$cleanTitle|$cleanNote"
        val savedSignature = prefs.getString("sig_${note.id}", "")

        if (currentSignature == savedSignature) {
            Toastic.toastic(context, strAlreadyPromoted, Toastic.LENGTH_SHORT, Toastic.WARNING, null, true).show()
            return
        }

        // 2. Check Daily Limit
        val lastDate = prefs.getString("last_promo_date", "")
        val count = if (lastDate == today) prefs.getInt("promo_count", 0) else 0

        if (count >= 3) {
            Toastic.toastic(context, strLimitPromoted, Toastic.LENGTH_SHORT, Toastic.WARNING, null, true).show()
            return
        }

        RecordingDialogViews.confirmDialog(
            context, palette,
            title = strPromote,
            message = strPromoteInfo,
            positiveLabel = strSend,
            negativeLabel = strCancel
        ) {
            promoteNoteToOnline(note, today, count, currentSignature)
        }
    }

    private fun getCleanNoteData(note: Note): Pair<String, String> {
        var cleanTitle: String = "Untitled"
        var cleanNote: String = ""
        try {
            val value = Gson().fromJson(note.note, Note::class.java)
            cleanTitle = value.title ?: note.title ?: "Untitled"
            cleanNote = value.note ?: ""
        } catch (e: Exception) {
            cleanTitle = note.title ?: "Untitled"
            cleanNote = note.note ?: ""
        }
        return Pair(cleanTitle, cleanNote)
    }

    private fun promoteNoteToOnline(note: Note, today: String, currentCount: Int, signature: String) {
        val context = context ?: return
        if (_binding == null) return
        
        _binding?.progressBar?.visibility = View.VISIBLE

        val (cleanTitle, cleanNote) = getCleanNoteData(note)
        val languageCode = Locale.getDefault().language
        val languageList = if (languageCode == "id" || languageCode == "in") listOf("id", "in") else listOf(languageCode)

        val data = hashMapOf(
            "title" to cleanTitle,
            "note" to cleanNote,
            "status" to "DRAFT",
            "language" to languageList,
            "submitted_at" to System.currentTimeMillis()
        )

        // Pre-fetch success string
        val strSuccess = getString(R.string.send_note_success)

        firestore.collection(collectionPath)
            .add(data)
            .addOnSuccessListener {
                if (_binding == null || !isAdded) return@addOnSuccessListener
                
                _binding?.progressBar?.visibility = View.GONE
                // Update limit AND save signature
                context.getSharedPreferences("note_promo_prefs", Context.MODE_PRIVATE).edit()
                    .putString("last_promo_date", today)
                    .putInt("promo_count", currentCount + 1)
                    .putString("sig_${note.id}", signature)
                    .apply()

                Toastic.toastic(context, strSuccess, Toastic.LENGTH_SHORT, Toastic.SUCCESS, null, true).show()
            }
            .addOnFailureListener {
                if (_binding == null || !isAdded) return@addOnFailureListener
                _binding?.progressBar?.visibility = View.GONE
                Toast.makeText(context, "Gagal: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteLocalNote(position: Int) {
        if (position !in localNotesList.indices) return
        val note = localNotesList[position]
        lifecycleScope.launch(Dispatchers.IO) {
            dbHelper?.deleteNote(note)
            withContext(Dispatchers.Main) {
                if (_binding == null || !isAdded) return@withContext
                localNotesList.removeAt(position)
                localAdapter?.notifyItemRemoved(position)
                toggleEmptyView(localNotesList.isEmpty())
            }
        }
    }

    private fun showNoteDialog(shouldUpdate: Boolean, existingNote: Note?, position: Int) {
        val activity = activity ?: return
        val view = LayoutInflater.from(activity).inflate(R.layout.note_dialog, null)

        val inputNote = view.findViewById<EditText>(R.id.note)
        val inputTitle = view.findViewById<EditText>(R.id.title)
        val dialogTitle = view.findViewById<TextView>(R.id.dialog_title)

        // Pre-fetch strings
        val strNewNote = getString(R.string.lbl_new_note_title)
        val strEditNote = getString(R.string.lbl_edit_note_title)
        val strEnterNote = getString(R.string.enter_note)

        dialogTitle.text = if (!shouldUpdate) strNewNote else strEditNote

        if (shouldUpdate && existingNote != null) {
            try {
                val value = Gson().fromJson(existingNote.note, Note::class.java)
                inputNote.setText(value.note)
                inputTitle.setText(value.title)
            } catch (e: Exception) {
                inputNote.setText(existingNote.note)
                inputTitle.setText(existingNote.title)
            }
        }

        val btnCancel = view.findViewById<TextView>(R.id.btnNoteCancel)
        val btnSave = view.findViewById<TextView>(R.id.btnNoteSave)
        styleNoteDialog(view, dialogTitle, inputTitle, inputNote, btnCancel, btnSave)
        btnSave.setText(if (shouldUpdate) R.string.update else R.string.save)

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(false)
            .create()

        btnCancel.setOnClickListener { dialog.cancel() }

        dialog.show()
        RecordingDialogViews.applyWindow(dialog, activity, 0.90, 0.60)

        btnSave.setOnClickListener {
            if (!isAdded) return@setOnClickListener
            
            val titleText = inputTitle.text.toString().trim()
            val noteText = inputNote.text.toString().trim()

            if (TextUtils.isEmpty(noteText)) {
                Toastic.toastic(activity, strEnterNote, Toastic.LENGTH_SHORT, Toastic.WARNING, null, true).show()
                return@setOnClickListener
            }

            val savedNote = Note().apply {
                this.title = titleText
                this.note = noteText
            }
            val jsonInput = Gson().toJson(savedNote)

            if (shouldUpdate && existingNote != null) {
                updateLocalNote(jsonInput, titleText, position)
            } else {
                createLocalNote(jsonInput, titleText)
            }
            dialog.dismiss()
        }
    }

    /** Warna dialog tambah/edit not (hanya tampilan). */
    private fun styleNoteDialog(
        root: View,
        dialogTitle: TextView,
        inputTitle: EditText,
        inputNote: EditText,
        btnCancel: TextView,
        btnSave: TextView
    ) {
        val context = root.context
        val c = palette
        root.background = RecordingDialogViews.rounded(context, c.surface, SdpR.dimen._12sdp)
        dialogTitle.setTextColor(c.text)
        RecordingDialogViews.styleInput(context, c, inputTitle)
        RecordingDialogViews.styleInput(context, c, inputNote)
        listOf(btnCancel to c.textSecondary, btnSave to c.accent).forEach { (btn, color) ->
            btn.setTextColor(color)
            btn.background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(c.ripple), null, null
            )
        }
    }

    private fun createLocalNote(noteContent: String, titleText: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val id = dbHelper?.insertNote(noteContent, titleText) ?: return@launch
            val newNote = dbHelper?.getNote(id) ?: return@launch
            withContext(Dispatchers.Main) {
                if (_binding == null || !isAdded) return@withContext
                
                localNotesList.add(0, newNote)
                localAdapter?.notifyItemInserted(0)
                _binding?.recyclerView?.scrollToPosition(0)
                toggleEmptyView(localNotesList.isEmpty())
            }
        }
    }

    private fun updateLocalNote(noteContent: String, titleText: String, position: Int) {
        if (position in localNotesList.indices) {
            val note = localNotesList[position]
            note.note = noteContent
            note.title = titleText
            lifecycleScope.launch(Dispatchers.IO) {
                dbHelper?.updateNote(note)
                withContext(Dispatchers.Main) {
                    if (_binding == null || !isAdded) return@withContext
                    localAdapter?.notifyItemChanged(position)
                }
            }
        }
    }

    // ─── Online Notes Logic ───────────────────────────────────────────────────

    private fun showViewNoteDialog(note: Note) {
        val activity = activity ?: return
        val context = context ?: return
        if (_binding == null || !isAdded) return

        val dialog = androidx.appcompat.app.AlertDialog.Builder(context).create()
        val c = palette

        val root = RecordingDialogViews.dialogRoot(context, c)

        // Title
        root.addView(RecordingDialogViews.title(context, c, "👁️ Preview Catatan"))
        root.addView(RecordingDialogViews.vSpace(context, com.intuit.sdp.R.dimen._6sdp))

        // Note Info (Title & Status)
        val tvTitle = TextView(context).apply {
            text = "Judul: ${note.title ?: "-"}"
            setTextColor(c.text)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(tvTitle)

        val isPublished = note.status == "published"
        val tvStatus = TextView(context).apply {
            text = "Status: ${note.status ?: "UNKNOWN"}"
            setTextColor(if (isPublished) android.graphics.Color.parseColor("#4CAF50") else android.graphics.Color.parseColor("#FFB347"))
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, context.sdp(com.intuit.sdp.R.dimen._2sdp), 0, context.sdp(com.intuit.sdp.R.dimen._6sdp))
        }
        root.addView(tvStatus)

        // Scrollable Note Content
        val scrollView = android.widget.ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, context.sdp(com.intuit.sdp.R.dimen._120sdp))
            background = RecordingDialogViews.rounded(context, c.surfaceAlt, com.intuit.sdp.R.dimen._8sdp, c.outline)
            val p = context.sdp(com.intuit.sdp.R.dimen._8sdp)
            setPadding(p, p, p, p)
        }

        val tvContent = TextView(context).apply {
            text = note.note ?: ""
            setTextColor(c.text)
            textSize = 12f
            setLineSpacing(0f, 1.2f)
        }
        scrollView.addView(tvContent)
        root.addView(scrollView)
        root.addView(RecordingDialogViews.vSpace(context, com.intuit.sdp.R.dimen._8sdp))

        // Action Buttons
        val btnUse = RecordingDialogViews.textButton(context, c, "Gunakan", c.accent).apply {
            setOnClickListener {
                dialog.dismiss()
                MyNoteListener.postNote(note.note)
                dismissAllowingStateLoss()
            }
        }

        val btnCopy = RecordingDialogViews.textButton(context, c, "Salin", c.textSecondary).apply {
            setOnClickListener {
                dialog.dismiss()
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Catatan", note.note)
                clipboard.setPrimaryClip(clip)
                Toastic.toastic(activity, "Berhasil disalin!", Toastic.LENGTH_SHORT, Toastic.SUCCESS, null, true).show()
            }
        }

        val isDebug = isAppDebuggable(context)
        val btnStatus = if (isDebug) {
            RecordingDialogViews.textButton(
                context, c,
                if (isPublished) "Set DRAFT" else "Set APPROVE",
                if (isPublished) android.graphics.Color.parseColor("#FFB347") else android.graphics.Color.parseColor("#4CAF50")
            ).apply {
                setOnClickListener {
                    dialog.dismiss()
                    toggleOnlineNoteStatus(note)
                }
            }
        } else null

        val btnClose = RecordingDialogViews.textButton(context, c, "Tutup", c.textSecondary).apply {
            setOnClickListener { dialog.dismiss() }
        }

        val footerButtons = mutableListOf<View>()
        btnStatus?.let { footerButtons.add(it) }
        footerButtons.add(btnCopy)
        footerButtons.add(btnUse)
        footerButtons.add(btnClose)

        val footer = RecordingDialogViews.footer(context, *footerButtons.toTypedArray())
        root.addView(footer, LinearLayout.LayoutParams(-1, -2))

        dialog.setView(root)
        dialog.show()
        RecordingDialogViews.applyWindow(dialog, context, 0.90, 0.60)
    }

    private fun loadOnlineNotes() {
        val isDebug = isAppDebuggable(context)
        if (isDebug) {
            fetchOnlineNotes()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val cached = readCache()
            withContext(Dispatchers.Main) {
                if (cached != null) {
                    populateOnlineList(cached)
                } else {
                    fetchOnlineNotes()
                }
            }
        }
    }

    fun setToast(message : String){
        Toast.makeText(context,message, Toast.LENGTH_SHORT).show()
    }


    private fun isAppDebuggable(context: Context?): Boolean {
        return context?.applicationInfo?.let {
            (it.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } ?: false
    }

    private fun fetchOnlineNotes() {
        if (_binding == null) return
        _binding?.progressBar?.visibility = View.VISIBLE
        val languageCode = Locale.getDefault().language

        val isDebug = isAppDebuggable(context)
        var query: Query = firestore.collection(collectionPath)

        if(!isDebug) {
            query = query.whereEqualTo("status", "published")
                .whereArrayContainsAny("language", listOf("en", languageCode))
        }

        query.get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null || !isAdded) return@addOnSuccessListener
                _binding?.progressBar?.visibility = View.GONE

                val fetched = snapshot.documents.map { doc ->
                    Note().apply {
                        docId = doc.id
                        title = doc.getString("title").orEmpty()
                        note  = doc.getString("note").orEmpty()
                        status = doc.getString("status") ?: "DRAFT"
                    }
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    if (!isDebug) {
                        saveCache(fetched)
                    }
                }
                populateOnlineList(fetched)
            }
            .addOnFailureListener {
                if (_binding == null || !isAdded) return@addOnFailureListener
                _binding?.progressBar?.visibility = View.GONE
                
                context?.let { ctx ->
                    Toast.makeText(ctx, it.message, Toast.LENGTH_SHORT).show()
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    val stale = readCache(ignoreExpiry = true)
                    withContext(Dispatchers.Main) {
                        if (_binding != null && isAdded && stale != null) {
                            populateOnlineList(stale)
                        }
                    }
                }
            }
    }

    private fun toggleOnlineNoteStatus(note: Note) {
        val docId = note.docId ?: return
        val context = context ?: return
        if (_binding == null) return
        
        val currentStatus = note.status ?: "DRAFT"
        val newStatus = if (currentStatus == "published") "DRAFT" else "published"

        _binding?.progressBar?.visibility = View.VISIBLE

        firestore.collection(collectionPath).document(docId)
            .update("status", newStatus)
            .addOnSuccessListener {
                if (_binding == null || !isAdded) return@addOnSuccessListener
                _binding?.progressBar?.visibility = View.GONE
                note.status = newStatus
                onlineAdapter?.notifyDataSetChanged()
                val msg = if (newStatus == "published") "Berhasil dipublish!" else "Dikembalikan ke Draft"
                Toastic.toastic(context, msg, Toastic.LENGTH_SHORT, Toastic.SUCCESS, null, true).show()
            }
            .addOnFailureListener {
                if (_binding == null || !isAdded) return@addOnFailureListener
                _binding?.progressBar?.visibility = View.GONE
                Toast.makeText(context, "Gagal: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteOnlineNote(note: Note) {
        val docId = note.docId ?: return
        val context = context ?: return
        if (_binding == null) return

 RecordingDialogViews.confirmDialog(
            context, palette,
            title = "Hapus Catatan Online?",
            message = "Data ini akan dihapus permanen dari Firestore.",
            positiveLabel = "Hapus",
            negativeLabel = "Batal",
            destructive = true
        ) {
                if (_binding == null || !isAdded) return@confirmDialog
                
                _binding?.progressBar?.visibility = View.VISIBLE
                firestore.collection(collectionPath).document(docId)
                    .delete()
                    .addOnSuccessListener {
                        if (_binding == null || !isAdded) return@addOnSuccessListener
                        _binding?.progressBar?.visibility = View.GONE
                        onlineFullList = onlineFullList.filter { it.docId != docId }
                        onlineAdapter?.updateData(onlineFullList)
                        toggleEmptyView(onlineFullList.isEmpty())
                        Toastic.toastic(context, "Berhasil dihapus!", Toastic.LENGTH_SHORT, Toastic.SUCCESS, null, true).show()
                    }
                    .addOnFailureListener {
                        if (_binding == null || !isAdded) return@addOnFailureListener
                        _binding?.progressBar?.visibility = View.GONE
                        Toast.makeText(context, "Gagal: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
        }
    }




    private fun populateOnlineList(notes: List<Note>) {
        onlineFullList = notes
        onlineAdapter?.updateData(notes)
        toggleEmptyView(notes.isEmpty())
    }

    // ─── Search & UI Helpers ──────────────────────────────────────────────────

    private fun filterNotes(query: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            if (!isOnlineTab) {
                val filtered = if (query.isBlank()) {
                    dbHelper?.allNotes.orEmpty()
                } else {
                    dbHelper?.allNotes.orEmpty().filter {
                        it.title?.contains(query, ignoreCase = true) == true ||
                                it.note?.contains(query, ignoreCase = true) == true
                    }
                }
                withContext(Dispatchers.Main) {
                    if (_binding != null && isAdded) {
                        localNotesList.clear()
                        localNotesList.addAll(filtered)
                        localAdapter?.notifyDataSetChanged()
                        toggleEmptyView(localNotesList.isEmpty())
                    }
                }
            } else {
                val filtered = if (query.isBlank()) {
                    onlineFullList
                } else {
                    onlineFullList.filter {
                        it.title?.contains(query, ignoreCase = true) == true ||
                                it.note?.contains(query, ignoreCase = true) == true
                    }
                }
                withContext(Dispatchers.Main) {
                    if (_binding != null && isAdded) {
                        onlineAdapter?.updateData(filtered)
                        toggleEmptyView(filtered.isEmpty())
                    }
                }
            }
        }
    }

    private fun toggleEmptyView(isEmpty: Boolean) {
        if (_binding == null) return
        _binding?.emptyNotesView?.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun readCache(ignoreExpiry: Boolean = false): List<Note>? {
        val prefs = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) ?: return null
        val json      = prefs.getString(KEY_NOTES, null) ?: return null
        val timestamp = prefs.getLong(KEY_TIMESTAMP, 0L)

        val isExpired = System.currentTimeMillis() - timestamp > CACHE_DURATION_MS
        if (isExpired && !ignoreExpiry) return null

        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Note().apply {
                    docId = obj.optString("docId")
                    title = obj.optString("title")
                    note  = obj.optString("note")
                    status = obj.optString("status")
                }
            }
        } catch (e: Exception) { null }
    }

    private fun saveCache(notes: List<Note>) {
        val prefs = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) ?: return
        val array = JSONArray().apply {
            notes.forEach { note ->
                put(JSONObject().apply {
                    put("docId", note.docId)
                    put("title", note.title)
                    put("note",  note.note)
                    put("status", note.status)
                })
            }
        }
        prefs.edit()
            .putString(KEY_NOTES, array.toString())
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
