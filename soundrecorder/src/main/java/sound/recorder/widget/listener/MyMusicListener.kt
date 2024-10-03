package sound.recorder.widget.listener

import android.media.MediaPlayer

object MyMusicListener {
    private var myListener: MusicListener? = null

    fun setMyListener(listener: MusicListener?=null) {
        myListener = listener
    }
    fun postAction(mediaPlayer: MediaPlayer?) {
        myListener?.onMusic(mediaPlayer)
    }
    fun postNote(note : String?){
        myListener?.onNote(note)
    }

    fun postVolumeAudio(volume : Float?){
        myListener?.onVolumeAudio(volume)
    }
}