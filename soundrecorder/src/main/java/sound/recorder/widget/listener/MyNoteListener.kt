package sound.recorder.widget.listener

import sound.recorder.widget.notes.Note

object MyNoteListener {
    private var myListener: NoteListener? = null

    fun setMyListener(listener: NoteListener? =null) {
        myListener = listener
    }
    fun postActionCompleted(note: Note?) {
        myListener?.onCallback(note)
    }
}