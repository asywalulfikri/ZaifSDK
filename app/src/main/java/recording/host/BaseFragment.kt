package recording.host

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.Animation
import sound.recorder.widget.base.BaseFragmentWidget
import sound.recorder.widget.music.MusicListDialogHelper
import sound.recorder.widget.music.MusicPlayerManager
import sound.recorder.widget.ui.viewmodel.MusicViewModel

@SuppressLint("Registered")
open class BaseFragment : BaseFragmentWidget() {

    var myAnim: Animation? = null
    private var musicPlayerListener: MusicPlayerManager.PlayerListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    fun setupMusicObserver(ivStop: View?) {
        // 1. Bersihkan listener lama jika ada (mencegah leak/duplikasi)
        musicPlayerListener?.let { MusicPlayerManager.removeListener(it) }

        // 2. Buat listener baru yang terikat ke lifecycle Fragment ini
        Log.d("BaseFragment", "setupMusicObserver: Creating new listener")
        musicPlayerListener = object : MusicPlayerManager.PlayerListener {
            override fun onPlay(track: MusicPlayerManager.MusicTrack) {
                Log.d("BaseFragment", "onMusicPlay: track=${track.title}")
                setToast("wwlwlwlwl")
                activity?.runOnUiThread { updateStopButton(ivStop, true) }
            }

            override fun onPause() {
                Log.d("BaseFragment", "onMusicPause")
                activity?.runOnUiThread { updateStopButton(ivStop, false) }
            }

            override fun onStop() {
                Log.d("BaseFragment", "onMusicStop")
                activity?.runOnUiThread { updateStopButton(ivStop, false) }
            }

            override fun onComplete() {
                Log.d("BaseFragment", "onMusicComplete")
                activity?.runOnUiThread { updateStopButton(ivStop, false) }
            }

            override fun onProgress(current: Int, max: Int) {
                // Bisa update progress UI di sini jika butuh
            }
        }

        // 3. Daftarkan ke MusicPlayerManager
        musicPlayerListener?.let { MusicPlayerManager.addListener(it) }

        // Initial state check - jika musik sudah jalan sebelum observer dipasang
        if (MusicPlayerManager.isPlaying) {
            val track = MusicPlayerManager.getCurrentTrack()
            if (track != null) {
                Log.d("BaseFragment", "setupMusicObserver: Music already playing, updating UI")
                updateStopButton(ivStop, true)
            }
        } else {
            updateStopButton(ivStop, false)
        }

        ivStop?.setOnClickListener {
            try {
                MusicPlayerManager.stop()
                updateStopButton(ivStop, false)
            } catch (e: Exception) {
                setToast(e.message.toString())
            }
        }
    }

    override fun onDestroyView() {
        // 4. Hapus listener saat Fragment hancur (Mencegah Memory Leak)
        musicPlayerListener?.let {
            Log.d("BaseFragment", "onDestroyView: Removing music listener")
            MusicPlayerManager.removeListener(it)
        }
        musicPlayerListener = null
        super.onDestroyView()
    }

    private fun updateStopButton(ivStop: View?, isPlaying: Boolean) {
        if (isPlaying) {
            ivStop?.visibility = View.VISIBLE
            ivStop?.startAnimation(mPanAnim)
        } else {
            ivStop?.clearAnimation()
            ivStop?.visibility = View.GONE
        }
    }
}
