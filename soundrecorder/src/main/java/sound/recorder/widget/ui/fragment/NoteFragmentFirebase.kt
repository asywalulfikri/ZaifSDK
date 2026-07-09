package sound.recorder.widget.ui.fragment

import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import sound.recorder.widget.databinding.ListNoteBinding
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.notes.Note

import sound.recorder.widget.notes.NotesAdapter
import sound.recorder.widget.notes.utils.MyDividerItemDecoration
import sound.recorder.widget.notes.utils.RecyclerTouchListener


open class NoteFragmentFirebase : BottomSheetDialogFragment() {

    private var _binding: ListNoteBinding? = null
    private val binding get() = _binding!!
    private val notesList: ArrayList<Note> = ArrayList()


    private var db: FirebaseFirestore? = null
    private val collectionPath = "not"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Safety check for Firebase initialization
        val ctx = requireContext()
        if (FirebaseApp.getApps(ctx).isEmpty()) {
            try { FirebaseApp.initializeApp(ctx) } catch (e: Exception) {}
        }
        db = FirebaseFirestore.getInstance()

        _binding = ListNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    companion object {
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 1 hari
        private var noteCache: List<Note>? = null
        private var lastFetchedAt = 0L

        fun newInstance(): NoteFragmentFirebase {
            return NoteFragmentFirebase()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        newInstance()
        val b = Bundle()
        super.onCreate(b)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Record to the external cache directory for visibility
        if(activity!=null&&requireActivity()!=null){
            val displayMetrics = DisplayMetrics()
            val screenWidth = displayMetrics.widthPixels

            (dialog as? BottomSheetDialog)?.behavior?.state = STATE_EXPANDED
            (dialog as? BottomSheetDialog)?.behavior?.isDraggable = false

            val layoutParams =  (dialog as? BottomSheetDialog)?.window?.attributes
            layoutParams?.width = screenWidth
            dialog?.window?.attributes = layoutParams

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dialog?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
            } else {
                @Suppress("DEPRECATION")
                dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }

            fetchDocumentsFromCollection()
        }
    }


    private fun fetchDocumentsFromCollection() {
        // Check Memory Cache first
        val now = System.currentTimeMillis()
        if (noteCache != null && (now - lastFetchedAt < CACHE_TTL_MS)) {
            notesList.clear()
            notesList.addAll(noteCache!!)
            songNote()
            return
        }

        db?.collection(collectionPath)
            ?.get()
            ?.addOnSuccessListener { querySnapshot ->
                // Process the list of documents here
                val newNotes = ArrayList<Note>()
                for (document in querySnapshot) {
                    if (document.exists()) {
                        val data = document.data

                        val note = Note()
                        note.title = data["title"] as? String ?: "-"
                        note.note = data["note"] as? String ?: ""
                        // Add more fields as needed
                        newNotes.add(note)
                    }
                }

                // Update Cache
                noteCache = newNotes
                lastFetchedAt = System.currentTimeMillis()

                notesList.clear()
                notesList.addAll(newNotes)
                songNote()
            }
            ?.addOnFailureListener { exception ->
                // Fallback to cache if failed (offline)
                if (noteCache != null) {
                    notesList.clear()
                    notesList.addAll(noteCache!!)
                    songNote()
                } else {
                    Toast.makeText(requireActivity(), exception.message, Toast.LENGTH_SHORT).show()
                }
            }
    }


    private fun songNote() {
        val mAdapter = NotesAdapter(notesList)
        val mLayoutManager: RecyclerView.LayoutManager = LinearLayoutManager(requireActivity())
        binding.recyclerView.layoutManager = mLayoutManager
        binding.recyclerView.itemAnimator = DefaultItemAnimator()
        binding.recyclerView.addItemDecoration(
            MyDividerItemDecoration(
                requireActivity(),
                LinearLayoutManager.VERTICAL,
                16
            )
        )
        binding.recyclerView.adapter = mAdapter
        toggleEmptyNotes()
        binding.recyclerView.addOnItemTouchListener(
            RecyclerTouchListener(requireActivity(),
                binding.recyclerView, object : RecyclerTouchListener.ClickListener {
                    override fun onClick(view: View?, position: Int) {

                    }

                    override fun onLongClick(view: View?, position: Int) {

                    }
                })
        )
    }

    private fun toggleEmptyNotes() {
        // you can check notesList.size() > 0
        if (notesList.size > 0) {
            binding.emptyNotesView.visibility = View.GONE
        } else {
            binding.emptyNotesView.visibility = View.VISIBLE
        }
    }

    fun onBackPressed(): Boolean {
        MyAdsListener.setBanner(true)
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)?.commit()
        return false
    }

    private fun applyImmersiveMode() {
        val window = dialog?.window ?: return
        val decorView = window.decorView

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // ANDROID 11+
            WindowCompat.setDecorFitsSystemWindows(window, false)

            val controller = WindowCompat.getInsetsController(window, decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            controller.hide(WindowInsetsCompat.Type.systemBars())

        } else {
            // ANDROID 10 DAN DI BAWAH
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    override fun onStart() {
        super.onStart()
        applyImmersiveMode()
    }

}