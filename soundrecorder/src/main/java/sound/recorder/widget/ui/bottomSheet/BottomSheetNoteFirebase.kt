package sound.recorder.widget.ui.bottomSheet


import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import sound.recorder.widget.databinding.BottomSheetNotesBinding
import sound.recorder.widget.listener.MyNoteListener
import sound.recorder.widget.notes.Note
import sound.recorder.widget.notes.NotesAdapter
import sound.recorder.widget.notes.utils.MyDividerItemDecoration
import sound.recorder.widget.notes.utils.RecyclerTouchListener
import java.util.Locale


class BottomSheetNoteFirebase : BottomSheetDialogFragment() {

    private var _binding: BottomSheetNotesBinding? = null
    private val binding get() = _binding!!

    private val notesList = ArrayList<Note>()
    private lateinit var adapter: NotesAdapter

    private val db = FirebaseFirestore.getInstance()
    private val collectionPath = "not"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetNotesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBottomSheet()
        setupRecyclerView()
        setupActions()

        fetchDocumentsFromCollection()
    }

    private fun setupBottomSheet() {
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = STATE_EXPANDED
            isDraggable = false
        }

        dialog?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
    }

    private fun setupRecyclerView() {
        adapter = NotesAdapter(notesList)

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            itemAnimator = DefaultItemAnimator()
            addItemDecoration(
                MyDividerItemDecoration(
                    requireContext(),
                    LinearLayoutManager.VERTICAL,
                    16
                )
            )
            adapter = this@BottomSheetNoteFirebase.adapter
        }

        binding.recyclerView.addOnItemTouchListener(
            RecyclerTouchListener(
                requireContext(),
                binding.recyclerView,
                object : RecyclerTouchListener.ClickListener {
                    override fun onClick(view: View?, position: Int) {
                        onItemSelected(position)
                    }

                    override fun onLongClick(view: View?, position: Int) {
                        onItemSelected(position)
                    }
                }
            )
        )
    }

    private fun setupActions() {
        binding.fab.visibility = View.GONE

        binding.ivClose.setOnClickListener {
            dismissAllowingStateLoss()
        }
    }

    private fun fetchDocumentsFromCollection() {
        val languageCode = Locale.getDefault().language

        db.collection(collectionPath)
            .whereArrayContainsAny("language", listOf("en", languageCode))
            .get()
            .addOnSuccessListener { snapshot ->
                notesList.clear()

                for (document in snapshot) {
                    val data = document.data
                    val note = Note().apply {
                        title = data["title"].toString()
                        note = data["note"].toString()
                    }
                    notesList.add(note)
                }

                adapter.notifyDataSetChanged()
                toggleEmptyView()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show()
            }
    }

    private fun toggleEmptyView() {
        binding.emptyNotesView.visibility =
            if (notesList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onItemSelected(position: Int) {
        if (position in notesList.indices) {
            MyNoteListener.postNote(notesList[position].note)
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        binding.recyclerView.clearOnScrollListeners()
        _binding = null
        super.onDestroyView()
    }
}
