package sound.recorder.widget.listener

import android.media.MediaPlayer

interface MusicListener {

    fun onMusic(mediaPlayer: MediaPlayer?)
    fun onComplete()
    fun onNote(note : String?)

}