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

    private lateinit var binding : BottomSheetNotesBinding

    private var notesList: ArrayList<Note> = ArrayList()
    private var db: DatabaseHelper? = null
    private var mAdapter: NotesAdapter? = null
    private var activity : Activity? =null

    companion object {
        fun newInstance(): BottomSheetDialogFragment {
            return BottomSheetDialogFragment()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = BottomSheetNotesBinding.inflate(layoutInflater)
        activity = getActivity()
        if(activity!=null&&context!=null){
            try {
                (dialog as? BottomSheetDialog)?.behavior?.state = STATE_EXPANDED
                (dialog as? BottomSheetDialog)?.behavior?.isDraggable = false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    dialog?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
                } else {
                    @Suppress("DEPRECATION")
                    dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                }

                try {
                    songNote()
                }catch (e : Exception){
                    showToast(e.message.toString())
                }

                binding.fab.setOnClickListener {
                    try {
                        showNoteDialog(false, null, -1)
                    }catch (e : Exception){
                        Toast.makeText(activity,e.message.toString(),Toast.LENGTH_SHORT).show()
                    }
                }

                binding.ivClose.setOnClickListener {
                    dismiss()
                }
            }catch (e : Exception){
                Toast.makeText(activity,e.message.toString(),Toast.LENGTH_SHORT).show()
            }
        }
        return binding.root

    }

    private fun showToast(message : String){
        Toast.makeText(activity, "$message.",Toast.LENGTH_SHORT).show()
    }


    private fun toggleEmptyNotes() {
        if (db!!.notesCount > 0) {
            binding.emptyNotesView.visibility = View.GONE
        } else {
            binding.emptyNotesView.visibility = View.VISIBLE
        }
    }

    private fun songNote() {
        db = DatabaseHelper(activity)
        notesList.addAll(db!!.allNotes)

        mAdapter = NotesAdapter(notesList)
        val mLayoutManager: RecyclerView.LayoutManager = LinearLayoutManager(activity)
        binding.recyclerView.layoutManager = mLayoutManager
        binding.recyclerView.itemAnimator = DefaultItemAnimator()
        binding.recyclerView.addItemDecoration(
            MyDividerItemDecoration(
                activity as Context,
                LinearLayoutManager.VERTICAL,
                16
            )
        )
        binding.recyclerView.adapter = mAdapter
        toggleEmptyNotes()
        binding.recyclerView.addOnItemTouchListener(
            RecyclerTouchListener(activity,
                binding.recyclerView, object : RecyclerTouchListener.ClickListener {
                    override fun onClick(view: View?, position: Int) {
                        try {
                            showActionsDialog(position)
                        }catch (e : Exception){
                            Toast.makeText(activity,e.message.toString(),Toast.LENGTH_SHORT).show()
                        }

                    }

                    override fun onLongClick(view: View?, position: Int) {
                        try {
                            showActionsDialog(position)
                        }catch (e : Exception){
                            Toast.makeText(activity,e.message.toString(),Toast.LENGTH_SHORT).show()
                        }
                    }
                })
        )
    }


    private fun showActionsDialog(position: Int) {
        try {
            activity?.let { activity ->
                val useNote = activity.getString(R.string.use_note)
                val editNote = activity.getString(R.string.edit_note)
                val deleteNote = activity.getString(R.string.delete_not)
                val colors = arrayOf<CharSequence>(useNote, editNote, deleteNote)
                val builder = AlertDialog.Builder(activity)
                builder.setTitle(activity.getString(R.string.choose))
                builder.setItems(colors) { _, which ->
                    when (which) {
                        0 -> useNote(notesList[position])
                        1 -> showNoteDialog(true, notesList[position], position)
                        2 ->  deleteNote(position)
                    }
                }
                builder.show()
            }
        } catch (e: Exception) {
            showToast(e.message.toString())
        }
    }

    private fun deleteNote(position: Int) {
        try {
            // deleting the note from db
            db?.deleteNote(notesList[position])

            // removing the note from the list
            notesList.removeAt(position)
            mAdapter?.notifyItemRemoved(position)
            toggleEmptyNotes()
        }catch (e : Exception){
            showToast(e.message.toString())
        }

    }

    private fun useNote(note: Note) {
        MyNoteListener.postActionCompleted(note)
        dismiss()
    }


    private fun showNoteDialog(shouldUpdate: Boolean, note: Note?, position: Int) {
        val layoutInflaterAndroid = LayoutInflater.from(activity)
        @SuppressLint("InflateParams") val view =
            layoutInflaterAndroid.inflate(R.layout.note_dialog, null)
        val alertDialogBuilderUserInput = AlertDialog.Builder(activity)
        alertDialogBuilderUserInput.setView(view)
        val inputNote = view.findViewById<EditText>(R.id.note)
        val inputTitle = view.findViewById<EditText>(R.id.title)
        val dialogTitle = view.findViewById<TextView>(R.id.dialog_title)
        dialogTitle.text = if (!shouldUpdate) activity?.getString(R.string.lbl_new_note_title) else activity?.getString(R.string.lbl_edit_note_title)

        if (shouldUpdate && note != null) {

            try {
                JSONObject(note.note.toString())
                val value = Gson().fromJson(note.note,Note::class.java)
                // The JSON string is valid
                inputNote.setText(value.note)
                inputTitle.setText(value.title)

            } catch (e: Exception) {
                // The JSON string is not valid
                inputNote.setText(note.note)
                inputTitle.setText(note.title)
            }

        }
        alertDialogBuilderUserInput
            .setCancelable(false)
            .setPositiveButton(
                if (shouldUpdate) activity?.getString(R.string.update) else activity?.getString(R.string.save)
            ) { _, _ -> }
            .setNegativeButton(
                activity?.getString(R.string.cancel)
            ) { dialogBox, _ -> dialogBox.cancel() }
        val alertDialog = alertDialogBuilderUserInput.create()
        alertDialog.show()
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
            View.OnClickListener {
                // Show toast message when no text is entered
                if (TextUtils.isEmpty(inputNote.text.toString())) {
                    setToastWarning(activity,activity?.getString(R.string.enter_note).toString())
                    return@OnClickListener
                }else if (TextUtils.isEmpty(inputNote.text.toString())) {
                    setToastWarning(activity,activity?.getString(R.string.enter_note_title).toString())
                    return@OnClickListener
                }else {
                    alertDialog.dismiss()
                }

                val note1 = Note()
                note1.title = inputTitle.text.toString()
                note1.note = inputNote.text.toString()
                val input = Gson().toJson(note1)

                // check if user updating note
                if (shouldUpdate && note != null) {
                    // update note by it's id
                    updateNote(input,inputTitle.text.toString(), position)
                } else {
                    // create new note
                    createNote(input,inputTitle.text.toString())
                }
            })
    }


    @SuppressLint("NotifyDataSetChanged")
    private fun createNote(note: String,title : String) {
        // inserting note in db and getting
        // newly inserted note id
        val id = db?.insertNote(note,title)

        // get the newly inserted note from db
        val n = db?.getNote(id!!)
        if (n != null) {
            // adding new note to array list at 0 position
            notesList.add(0, n)
            setToastSuccess(activity,"Note Success Add")
            // refreshing the list
            mAdapter?.notifyDataSetChanged()
            toggleEmptyNotes()


        }
    }

    fun setToastSuccess(activity: Activity?, message : String){
        if(activity!=null){
            Toastic.toastic(
                activity,
                message = message,
                duration = Toastic.LENGTH_SHORT,
                type = Toastic.SUCCESS,
                isIconAnimated = true
            ).show()
        }
    }

    private fun setToastWarning(activity: Activity?, message : String){
        if(activity!=null){
            Toastic.toastic(activity,
                message = message,
                duration = Toastic.LENGTH_SHORT,
                type = Toastic.WARNING,
                isIconAnimated = true
            ).show()
        }

    }

    private fun updateNote(note: String,title : String, position: Int) {
        val n = notesList[position]
        // updating note text
        n.note = note
        n.title = title

        // updating note in db
        db!!.updateNote(n)

        // refreshing the list
        notesList[position] = n
        mAdapter!!.notifyItemChanged(position)
        toggleEmptyNotes()
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