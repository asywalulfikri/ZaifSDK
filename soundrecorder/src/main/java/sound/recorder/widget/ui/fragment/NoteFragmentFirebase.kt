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
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
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


    private val db = FirebaseFirestore.getInstance()
    private val collectionPath = "not"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ListNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    companion object {
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
        db.collection(collectionPath)
            .get()
            .addOnSuccessListener { querySnapshot ->
                // Process the list of documents here

                for (document in querySnapshot) {
                    if (document.exists()) {
                        val data = document.data

                        val note = Note()
                        note.title = data["title"] as String
                        note.note = data["note"] as String
                        // Add more fields as needed
                        notesList.add(note)

                    }
                }

                songNote()
                // Here, you have the list of documents in 'documentList'
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireActivity(),exception.message,Toast.LENGTH_SHORT).show()
                // Handle any errors that occurred while retrieving data
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
        MyAdsListener.setAds(true)
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)?.commit()
        return false
    }

}