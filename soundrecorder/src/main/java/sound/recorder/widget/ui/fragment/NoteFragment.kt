package sound.recorder.widget.ui.fragment

import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import sound.recorder.widget.databinding.ListNoteBinding
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.notes.DatabaseHelper
import sound.recorder.widget.notes.Note

import sound.recorder.widget.notes.NotesAdapter
import sound.recorder.widget.notes.utils.MyDividerItemDecoration
import sound.recorder.widget.notes.utils.RecyclerTouchListener


internal class NoteFragment : BottomSheetDialogFragment() {

    private var _binding: ListNoteBinding? = null
    private val binding get() = _binding!!
    private var db: DatabaseHelper? = null
    private val notesList: ArrayList<Note> = ArrayList()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ListNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    companion object {
        fun newInstance(): NoteFragment{
            return NoteFragment()
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
            songNote()
        }
    }


    private fun songNote() {
        db = DatabaseHelper(activity)
        notesList.addAll(db!!.allNotes)
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
        if (db!!.notesCount > 0) {
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