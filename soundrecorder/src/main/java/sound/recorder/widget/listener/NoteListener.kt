package sound.recorder.widget.listener

import sound.recorder.widget.notes.Note

interface NoteListener {
    fun onCallback(result: Note?)
}