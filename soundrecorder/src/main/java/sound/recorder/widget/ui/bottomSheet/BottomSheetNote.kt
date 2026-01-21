package sound.recorder.widget.ui.bottomSheet

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.*
import android.widget.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import org.json.JSONObject
import sound.recorder.widget.R
import sound.recorder.widget.databinding.BottomSheetNotesBinding
import sound.recorder.widget.listener.MyNoteListener
import sound.recorder.widget.notes.DatabaseHelper
import sound.recorder.widget.notes.Note
import sound.recorder.widget.notes.NotesAdapter
import sound.recorder.widget.notes.utils.MyDividerItemDecoration
import sound.recorder.widget.notes.utils.RecyclerTouchListener
import sound.recorder.widget.util.Toastic


class BottomSheetNote : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetNotesBinding

    private val notesList = ArrayList<Note>()
    private var db: DatabaseHelper? = null
    private var mAdapter: NotesAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetNotesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sheetDialog = dialog as? BottomSheetDialog ?: return
        sheetDialog.behavior.state = STATE_EXPANDED
        sheetDialog.behavior.isDraggable = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sheetDialog.window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
            }
        } else {
            @Suppress("DEPRECATION")
            sheetDialog.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }

        songNote()

        binding.fab.setOnClickListener {
            if (!isAdded) return@setOnClickListener
            showNoteDialog(false, null, -1)
        }

        binding.ivClose.setOnClickListener {
            dismissAllowingStateLoss()
        }
    }

    private fun songNote() {
        if (!isAdded) return

        db = DatabaseHelper(requireContext())
        notesList.clear()
        notesList.addAll(db?.allNotes.orEmpty())

        mAdapter = NotesAdapter(notesList)

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
            adapter = mAdapter

            addOnItemTouchListener(
                RecyclerTouchListener(
                    requireContext(),
                    this,
                    object : RecyclerTouchListener.ClickListener {
                        override fun onClick(view: View?, position: Int) {
                            if (!isAdded) return
                            showActionsDialog(position)
                        }

                        override fun onLongClick(view: View?, position: Int) {
                            if (!isAdded) return
                            showActionsDialog(position)
                        }
                    }
                )
            )
        }

        toggleEmptyNotes()
    }

    private fun toggleEmptyNotes() {
        if (!isAdded) return
        binding.emptyNotesView.visibility =
            if ((db?.notesCount ?: 0) > 0) View.GONE else View.VISIBLE
    }

    private fun showActionsDialog(position: Int) {
        if (!isAdded || position !in notesList.indices) return

        val ctx = requireContext()
        val items = arrayOf(
            ctx.getString(R.string.use_note),
            ctx.getString(R.string.edit_note),
            ctx.getString(R.string.delete_not)
        )

        AlertDialog.Builder(ctx)
            .setTitle(ctx.getString(R.string.choose))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> useNote(notesList[position])
                    1 -> showNoteDialog(true, notesList[position], position)
                    2 -> deleteNote(position)
                }
            }
            .setOnDismissListener {
                // prevent focus crash
                view?.clearFocus()
            }
            .show()
    }

    private fun deleteNote(position: Int) {
        if (position !in notesList.indices) return

        db?.deleteNote(notesList[position])
        notesList.removeAt(position)
        mAdapter?.notifyItemRemoved(position)
        toggleEmptyNotes()
    }

    private fun useNote(note: Note) {
        MyNoteListener.postActionCompleted(note)
        dismissAllowingStateLoss()
    }

    private fun showNoteDialog(
        shouldUpdate: Boolean,
        note: Note?,
        position: Int
    ) {
        if (!isAdded) return

        val ctx = requireContext()
        val view = LayoutInflater.from(ctx)
            .inflate(R.layout.note_dialog, null, false)

        val inputNote = view.findViewById<EditText>(R.id.note)
        val inputTitle = view.findViewById<EditText>(R.id.title)
        val dialogTitle = view.findViewById<TextView>(R.id.dialog_title)

        dialogTitle.text =
            if (shouldUpdate) ctx.getString(R.string.lbl_edit_note_title)
            else ctx.getString(R.string.lbl_new_note_title)

        if (shouldUpdate && note != null) {
            inputNote.setText(note.note)
            inputTitle.setText(note.title)
        }

        val dialog = AlertDialog.Builder(ctx)
            .setView(view)
            .setCancelable(false)
            .setPositiveButton(
                if (shouldUpdate) ctx.getString(R.string.update)
                else ctx.getString(R.string.save),
                null
            )
            .setNegativeButton(ctx.getString(R.string.cancel)) { d, _ ->
                d.dismiss()
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (inputNote.text.isNullOrBlank()) {
                    setToastWarning(ctx, ctx.getString(R.string.enter_note))
                    return@setOnClickListener
                }
                if (inputTitle.text.isNullOrBlank()) {
                    setToastWarning(ctx, ctx.getString(R.string.enter_note_title))
                    return@setOnClickListener
                }

                dialog.dismiss()

                if (shouldUpdate && note != null) {
                    updateNote(
                        inputNote.text.toString(),
                        inputTitle.text.toString(),
                        position
                    )
                } else {
                    createNote(
                        inputNote.text.toString(),
                        inputTitle.text.toString()
                    )
                }
            }
        }

        dialog.show()
    }

    private fun createNote(note: String, title: String) {
        val id = db?.insertNote(note, title) ?: return
        val n = db?.getNote(id) ?: return

        notesList.add(0, n)
        mAdapter?.notifyItemInserted(0)
        toggleEmptyNotes()
    }

    private fun updateNote(
        noteText: String,
        noteTitle: String,
        position: Int
    ) {
        if (position !in notesList.indices) return

        val note = notesList[position]
        note.note = noteText
        note.title = noteTitle

        db?.updateNote(note)
        mAdapter?.notifyItemChanged(position)
    }

    private fun setToastWarning(context: Context, message: String) {
        Toastic.toastic(
            context,
            message,
            duration = Toastic.LENGTH_SHORT,
            type = Toastic.WARNING,
            isIconAnimated = true
        ).show()
    }


    override fun onDestroyView() {
        binding.recyclerView.clearOnScrollListeners()
        binding.recyclerView.adapter = null
        super.onDestroyView()
    }
}
