package sound.recorder.widget.ui.bottomSheet

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import sound.recorder.widget.databinding.BottomSheetNotesBinding
import sound.recorder.widget.listener.MyNoteListener
import sound.recorder.widget.notes.Note
import sound.recorder.widget.notes.NotesAdapter
import sound.recorder.widget.notes.utils.RecyclerTouchListener
import java.util.Locale

class BottomSheetNoteFirebase : BottomSheetDialogFragment() {

    private var _binding: BottomSheetNotesBinding? = null
    private val bindingOrNull get() = _binding

    private val notesList = ArrayList<Note>()
    private lateinit var adapter: NotesAdapter

    private val db = FirebaseFirestore.getInstance()
    private val collectionPath = "not"

    // Cache config
    private val PREF_NAME = "notes_cache"
    private val KEY_NOTES = "cached_notes"
    private val KEY_TIMESTAMP = "cache_timestamp"
    private val CACHE_DURATION_MS = 6 * 60 * 60 * 1000L // 6 jam

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetNotesBinding.inflate(inflater, container, false)
        return _binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBottomSheet()
        setupRecyclerView()
        setupActions()
        loadNotes()
    }

    private fun setupBottomSheet() {
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = STATE_EXPANDED
            isDraggable = false
        }
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun setupRecyclerView() {
        adapter = NotesAdapter(notesList)

        bindingOrNull?.recyclerView?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            itemAnimator = DefaultItemAnimator()
            adapter = this@BottomSheetNoteFirebase.adapter
        }

        bindingOrNull?.recyclerView?.addOnItemTouchListener(
            RecyclerTouchListener(
                requireContext(),
                bindingOrNull!!.recyclerView,
                object : RecyclerTouchListener.ClickListener {
                    override fun onClick(view: View?, position: Int) { onItemSelected(position) }
                    override fun onLongClick(view: View?, position: Int) { onItemSelected(position) }
                }
            )
        )
    }

    private fun setupActions() {
        bindingOrNull?.fab?.visibility = View.GONE
        bindingOrNull?.ivClose?.setOnClickListener { dismissAllowingStateLoss() }
    }

    // ─── Entry point: cache dulu, kalau expired baru fetch ───────────────────

    private fun loadNotes() {
        val cached = readCache()
        if (cached != null) {
            // Tampilkan data cache langsung — tanpa loading
            populateList(cached)
        } else {
            // Cache kosong / expired → fetch dari Firestore
            fetchDocumentsFromCollection()
        }
    }

    // ─── Firestore ────────────────────────────────────────────────────────────

    private fun fetchDocumentsFromCollection() {
        val languageCode = Locale.getDefault().language

        db.collection(collectionPath)
            .whereArrayContainsAny("language", listOf("en", languageCode))
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                val fetched = snapshot.documents.map { doc ->
                    Note().apply {
                        title = doc.getString("title").orEmpty()
                        note  = doc.getString("note").orEmpty()
                    }
                }

                saveCache(fetched)   // simpan ke cache
                populateList(fetched)
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                context?.let { ctx ->
                    Toast.makeText(ctx, it.message, Toast.LENGTH_SHORT).show()
                }
                // Kalau gagal fetch, coba tampilkan cache lama (walaupun expired)
                val stale = readCache(ignoreExpiry = true)
                if (stale != null) populateList(stale)
            }
    }

    // ─── List helper ─────────────────────────────────────────────────────────

    private fun populateList(notes: List<Note>) {
        notesList.clear()
        notesList.addAll(notes)
        adapter.notifyDataSetChanged()
        toggleEmptyView()
    }

    private fun toggleEmptyView() {
        val b = _binding ?: return
        b.emptyNotesView.visibility = if (notesList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onItemSelected(position: Int) {
        if (position in notesList.indices) {
            MyNoteListener.postNote(notesList[position].note)
            dismissAllowingStateLoss()
        }
    }

    // ─── Cache: baca ─────────────────────────────────────────────────────────

    /**
     * @param ignoreExpiry true → kembalikan cache walaupun sudah kadaluarsa (fallback)
     * @return List<Note> kalau cache valid, null kalau tidak ada / expired
     */
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
                    title = obj.optString("title")
                    note  = obj.optString("note")
                }
            }
        } catch (e: Exception) {
            null // JSON rusak → anggap tidak ada cache
        }
    }

    // ─── Cache: tulis ─────────────────────────────────────────────────────────

    private fun saveCache(notes: List<Note>) {
        val prefs = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) ?: return
        val array = JSONArray().apply {
            notes.forEach { note ->
                put(JSONObject().apply {
                    put("title", note.title)
                    put("note",  note.note)
                })
            }
        }
        prefs.edit()
            .putString(KEY_NOTES, array.toString())
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onDestroyView() {
        bindingOrNull?.recyclerView?.clearOnScrollListeners()
        _binding = null
        super.onDestroyView()
    }
}