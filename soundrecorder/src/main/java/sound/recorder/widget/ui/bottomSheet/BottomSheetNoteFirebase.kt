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
    private val bindingOrNull get() = _binding

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
        return _binding!!.root
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

        bindingOrNull?.recyclerView?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            itemAnimator = DefaultItemAnimator()
            /*addItemDecoration(
                MyDividerItemDecoration(
                    requireContext(),
                    LinearLayoutManager.VERTICAL,
                    16
                )
            )*/
            adapter = this@BottomSheetNoteFirebase.adapter
        }

        bindingOrNull?.recyclerView?.addOnItemTouchListener(
            RecyclerTouchListener(
                requireContext(),
                bindingOrNull!!.recyclerView,
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
        bindingOrNull?.fab?.visibility = View.GONE
        bindingOrNull?.ivClose?.setOnClickListener {
            dismissAllowingStateLoss()
        }
    }

    private fun fetchDocumentsFromCollection() {
        val languageCode = Locale.getDefault().language

        db.collection(collectionPath)
            .whereArrayContainsAny("language", listOf("en", languageCode))
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                notesList.clear()
                for (document in snapshot) {
                    val data = document.data
                    val note = Note().apply {
                        title = data["title"]?.toString().orEmpty()
                        note = data["note"]?.toString().orEmpty()
                    }
                    notesList.add(note)
                }

                adapter.notifyDataSetChanged()
                toggleEmptyView()
            }
            .addOnFailureListener {
                context?.let { ctx ->
                    Toast.makeText(ctx, it.message, Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun toggleEmptyView() {
        val b = _binding ?: return
        b.emptyNotesView.visibility =
            if (notesList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onItemSelected(position: Int) {
        if (position in notesList.indices) {
            MyNoteListener.postNote(notesList[position].note)
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        bindingOrNull?.recyclerView?.clearOnScrollListeners()
        _binding = null
        super.onDestroyView()
    }
}
