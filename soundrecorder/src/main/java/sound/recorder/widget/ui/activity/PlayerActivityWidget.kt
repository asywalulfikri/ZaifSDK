package sound.recorder.widget.ui.activity

import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.SeekBar
import androidx.core.content.res.ResourcesCompat
import sound.recorder.widget.R
import sound.recorder.widget.base.BaseActivityWidget
import sound.recorder.widget.databinding.ActivityPlayerBinding
import sound.recorder.widget.util.Toastic
import java.io.IOException

internal class PlayerActivityWidget : BaseActivityWidget() {

    private val delay = 100L
    private lateinit var runnable : Runnable
    private lateinit var handler : Handler
    private lateinit var mediaPlayer : MediaPlayer
    private var playbackSpeed :Float = 1.0f
    private lateinit var binding: ActivityPlayerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        val filePath = intent.getStringExtra("filepath")
        val filename = intent.getStringExtra("filename")


        if (!filePath.isNullOrBlank()) {
            binding.tvFilename.text = filename


            try {
                mediaPlayer = MediaPlayer()
                mediaPlayer.apply {
                    setDataSource(this@PlayerActivityWidget, Uri.parse(filePath))
                    setOnPreparedListener { player ->
                        binding.seekBar.max = player.duration
                        playPausePlayer()
                    }
                    setOnCompletionListener { stopPlayer() }
                    prepareAsync()
                }
            } catch (e: IOException) {
                setToastTic(Toastic.ERROR,e.message.toString())
            } catch (e: IllegalStateException) {
                setToastTic(Toastic.ERROR,e.message.toString())
            }catch (e : Exception){
                setToastTic(Toastic.ERROR,e.message.toString())
            }

            if (!::mediaPlayer.isInitialized) {
                finish()
                return
            }

            /*mediaPlayer = MediaPlayer()
            mediaPlayer.apply {
                setDataSource(filePath)
                prepare()
            }*/
            handler = Handler(Looper.getMainLooper())

            binding.btnPlay.setOnClickListener {
                playPausePlayer()
            }

            binding.btnForward.setOnClickListener {

                if (::mediaPlayer.isInitialized) {
                    try {
                        mediaPlayer.apply {
                            seekTo(mediaPlayer.currentPosition + 1000)
                            binding.seekBar.progress += 1000
                        }
                    } catch (e: IOException) {
                        setToastTic(Toastic.ERROR,e.message.toString())
                    } catch (e: IllegalStateException) {
                        setToastTic(Toastic.ERROR,e.message.toString())
                    }catch (e : Exception){
                        setToastTic(Toastic.ERROR,e.message.toString())
                    }
                }
            }

            binding.btnBackward.setOnClickListener {

                if (::mediaPlayer.isInitialized) {
                    try {
                        mediaPlayer.apply {
                            seekTo(mediaPlayer.currentPosition - 1000)
                            binding.seekBar.progress += 1000
                        }
                    } catch (e: IOException) {
                        setToastTic(Toastic.ERROR,e.message.toString())
                    } catch (e: IllegalStateException) {
                        setToastTic(Toastic.ERROR,e.message.toString())
                    }catch (e : Exception){
                        setToastTic(Toastic.ERROR,e.message.toString())
                    }
                }
            }

            binding.seekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                    try {
                        if(p2) mediaPlayer.seekTo(p1)
                    }catch (e : Exception){
                       setToast(e.message.toString())
                    }
                }
                override fun onStartTrackingTouch(p0: SeekBar?) {}

                override fun onStopTrackingTouch(p0: SeekBar?) {}

            })

            binding.chip.setOnClickListener {
                when(playbackSpeed){
                    0.5f -> playbackSpeed += 0.5f
                    1.0f -> playbackSpeed += 0.5f
                    1.5f -> playbackSpeed += 0.5f
                    2.0f -> playbackSpeed = 0.5f
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        mediaPlayer.playbackParams = PlaybackParams().setSpeed(playbackSpeed)
                    }catch (e : Exception){
                        setToast(getString(R.string.device_not_support))
                    }
                }
                binding.chip.text = "x $playbackSpeed"
            }
        }else{
            setToastTic(Toastic.ERROR,"Audio not found")
            finish()
        }

    }

    private fun playPausePlayer(){
        try {
            if(!mediaPlayer.isPlaying){
                mediaPlayer.start()
                binding.btnPlay.background = ResourcesCompat.getDrawable(resources,
                    R.drawable.ic_pause_circle, theme)

                runnable = Runnable {
                    val progress = mediaPlayer.currentPosition
                    Log.d("progress", progress.toString())
                    binding.seekBar.progress = progress

                    val amp = 80 + Math.random()*300
                    //binding.playerView.updateAmps(amp.toInt())

                    handler.postDelayed(runnable, delay)
                }
                handler.postDelayed(runnable, delay)
            }else{
                mediaPlayer.pause()
                binding.btnPlay.background = ResourcesCompat.getDrawable(resources,
                    R.drawable.ic_play_circle, theme)

                handler.removeCallbacks(runnable)
            }
        }catch (e : Exception){
            setToastTic(Toastic.ERROR,e.message.toString())
        }
    }

    private fun stopPlayer(){
        try {

        }catch (e : Exception){

        }
        binding.btnPlay.background = ResourcesCompat.getDrawable(resources,
            R.drawable.ic_play_circle, theme)
        handler.removeCallbacks(runnable)
    }

    override fun onBackPressed() {
        if (::mediaPlayer.isInitialized) {
            try {
                mediaPlayer.apply {
                    stop()
                    release()
                    handler.removeCallbacks(runnable)
                }
            } catch (e: IOException) {
                setToastTic(Toastic.ERROR,e.message.toString())
            } catch (e: IllegalStateException) {
                setToastTic(Toastic.ERROR,e.message.toString())
            }catch (e : Exception){
                setToastTic(Toastic.ERROR,e.message.toString())
            }
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        if (::handler.isInitialized) handler.removeCallbacksAndMessages(null)
        if (::mediaPlayer.isInitialized) {
            try { mediaPlayer.release() } catch (_: Exception) { }
        }
        super.onDestroy()
    }
}
