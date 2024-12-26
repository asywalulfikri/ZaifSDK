package recording.host

import android.Manifest
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import recording.host.databinding.ActivityMainBinding
import sound.recorder.widget.RecordingSDK
import sound.recorder.widget.base.BaseActivityWidget
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.listener.AdsListener
import sound.recorder.widget.listener.FragmentListener
import sound.recorder.widget.listener.MusicListener
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.listener.MyFragmentListener
import sound.recorder.widget.listener.MyMusicListener
import sound.recorder.widget.listener.MyPauseListener
import sound.recorder.widget.listener.MyStopMusicListener
import sound.recorder.widget.listener.MyStopSDKMusicListener
import sound.recorder.widget.listener.PauseListener
import sound.recorder.widget.model.Song
import sound.recorder.widget.tools.showcase.GuideView
import sound.recorder.widget.tools.showcase.config.DismissType
import sound.recorder.widget.tools.showcase.config.Gravity
import sound.recorder.widget.tools.showcase.config.PointerType
import sound.recorder.widget.ui.bottomSheet.BottomSheetNote
import sound.recorder.widget.ui.fragment.FragmentSettings
import sound.recorder.widget.ui.fragment.FragmentSheetListSong
import sound.recorder.widget.ui.fragment.FragmentVideo
import sound.recorder.widget.ui.fragment.ListRecordFragment
import sound.recorder.widget.util.AppRatingHelper
import sound.recorder.widget.util.Constant
import sound.recorder.widget.util.DataSession
import sound.recorder.widget.util.SnowFlakesLayout
import sound.recorder.widget.util.Toastic
import java.io.IOException
import kotlin.math.ln


class MainActivity : BaseActivityWidget(),FragmentListener,AdsListener, SharedPreferences.OnSharedPreferenceChangeListener,PauseListener,FragmentSheetListSong.OnClickListener,MusicListener {

    private lateinit var sp : SoundPool

    private var ss1 = 1
    private var ss2 = 2

    private lateinit var binding : ActivityMainBinding

    private val listTitle = arrayOf(
        "Gundul Gundul Pacul",
        "Ampar Ampar Pisang"
    )

    private var song = ArrayList<Song>()

    private val pathRaw = arrayOf(
        "android.resource://"+BuildConfig.APPLICATION_ID+"/raw/gundul_gundul_pacul",
        "android.resource://"+BuildConfig.APPLICATION_ID+"/raw/ampar_ampar_pisang"
    )

    private lateinit var salju : SnowFlakesLayout

    private var showBtnStop = false
    private var mp :  MediaPlayer? =null
    private var songIsPlaying = false
    private var volumes : Float? =null

    private var mGuideView: GuideView? = null
    private var builder: GuideView.Builder? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sharedPreferences = DataSession(this).getShared()
        sharedPreferences?.registerOnSharedPreferenceChangeListener(this)



       // binding.tvRunningText.startScroll()

        // binding.tvRunningText.startScroll()
        val progress = sharedPreferences?.getInt(Constant.KeyShared.volume,100)
        volumes = (1 - ln((ToneGenerator.MAX_VOLUME - progress!!).toDouble()) / ln(
            ToneGenerator.MAX_VOLUME.toDouble())).toFloat()

        setupBanner(binding.bannerView)
        setupBannerFacebook(binding.bannerFacebook)
        setupHideStatusBar(binding.root,true)
       // setupAppOpenAd()

        val xx = DataSession(this).getBackgroundColor()
        if (xx != -1) {
            binding.layoutBackground.setBackgroundColor(
                getSharedPreferenceUpdate().getInt(
                    Constant.KeyShared.backgroundColor,
                    -1
                )
            )
        }

        try {
            setupInterstitial()
        }catch (e : Exception){
            setLog("asywalul xx : "+ e.message.toString())
        }

        setupGDPR()


        permissionNotification()

        salju = SnowFlakesLayout(this)
        salju.init()

        for (i in listTitle.indices) {
            val itemSong = Song()
            itemSong.title = listTitle[i]
            itemSong.pathRaw = pathRaw[i]
            song.add(itemSong)
        }
        RecordingSDK.addSong(this,song)

        MyFragmentListener.setMyListener(this)
        MyAdsListener.setMyListener(this)

        RecordingSDK.run()

        //salju.startSnowing()
        //binding.layoutBackground.addView(salju)


        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        sp = SoundPool.Builder()
            .setMaxStreams(5) // jumlah maksimum stream yang bisa diputar
            .setAudioAttributes(audioAttributes)
            .build()


       // showOpenAd() bikin error

        ss1 = sp.load(this,R.raw.dum,1)
        ss2 = sp.load(this,R.raw.dek,1)


        binding.btn1.setOnClickListener {
           // showInterstitial()

            sp.play(ss1, 1f, 1f, 0, 0, 1f)
        }

        val appRatingHelper = AppRatingHelper(this)


        binding.openPlayStore.setOnClickListener {
           appRatingHelper.openRating()
        }


        binding.btnInterstitialAdmob.setOnClickListener {
            showInterstitial()
        }

        binding.btnOpenMusic.setOnClickListener {
            startPermissionSong()
        }

        binding.btnOpenId.setOnClickListener {
            showOpenAd()
        }

        //showCase(binding.btnOpenId)

        binding.btnNote.setOnClickListener {
            try {
                val bottomSheetNote = BottomSheetNote()
                bottomSheetNote.show(supportFragmentManager,"")
            }catch (e : Exception){
                Log.d("error","error")
            }
        }

        binding.btnInterstitialStarApp.setOnClickListener {
            try {
                // some code
                val fragment = FragmentSettings.newInstance()
                MyAdsListener.setAds(false)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentFileViewer, fragment)
                    .commit()

            } catch (e: Exception) {
                setToastTic(Toastic.ERROR,e.message.toString())
            } catch (e : IllegalStateException){
                setToastTic(Toastic.ERROR,e.message.toString())
            } catch (e : IllegalAccessException){
                setToastTic(Toastic.ERROR,e.message.toString())
            }catch (e : NoSuchFieldException){
                setToastTic(Toastic.ERROR,e.message.toString())
            }
        }

        binding.btnVideo.setOnClickListener {
            try {
                // some code
                if (savedInstanceState == null) {
                    val fragment = FragmentVideo.newInstance()
                    MyAdsListener.setAds(false)
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentFileViewer, fragment)
                        .commit()
                }
            } catch (e: Exception) {
                setToastTic(Toastic.ERROR,e.message.toString())
            } catch (e : IllegalStateException){
                setToastTic(Toastic.ERROR,e.message.toString())
            } catch (e : IllegalAccessException){
                setToastTic(Toastic.ERROR,e.message.toString())
            }catch (e : NoSuchFieldException){
                setToastTic(Toastic.ERROR,e.message.toString())
            }
        }


       // checkForUpdates()


       /* builder = GuideView.Builder(this)
            .setTitle("Guide Title Text")
            .setContentText("Guide Description Text\n .....Guide Description Text\n .....Guide Description Text .....")
            .setGravity(Gravity.center)
            .setDismissType(DismissType.anywhere)
            .setPointerType(PointerType.circle)
            .setTargetView(binding.btn1)
            .setGuideListener { view: View ->
                when (view.id) {
                    R.id.btn1 -> builder?.setTargetView(binding.btn2)?.build()
                    R.id.btn2 -> builder?.setTargetView(binding.btnVideo)?.build()
                    R.id.btnVideo -> return@setGuideListener
                }
                mGuideView = builder!!.build()
                mGuideView?.show()
            }

        mGuideView = builder?.build()
        mGuideView?.show()*/



    }

    private fun updatingForDynamicLocationViews() {
        //view4.setOnFocusChangeListener { view, b -> mGuideView!!.updateGuideViewLocation() }
    }

    fun showCase(view : View){


        GuideView.Builder(this@MainActivity)
            .setTitle("Guide Title Text")
            .setContentText("Ini Berfungsi untuk Membesarkan suara")
            .setGravity(Gravity.center)
            .setDismissType(DismissType.outside) //optional - default dismissible by TargetView
            .setGuideListener {
                //TODO ...
            }
            .build()
            .show()

      /*  MaterialShowcaseView.Builder(this)
            .setTarget(binding.btn2)
            .setDismissText("GOT IT")
            .setContentText("This is some amazing feature you should know about")
            .setDelay(500) // optional but starting animations immediately in onCreate can make them choppy
            .singleUse("apaaja") // provide a unique ID used to ensure it is only shown once
            .show()*/

       /* val config = ShowcaseConfig()
        config.delay = 500 // half second between each showcase view

        val sequence: MaterialShowcaseSequence = MaterialShowcaseSequence(this, "apaaja")

        sequence.setConfig(config)

        sequence.addSequenceItem(
            binding.btn1,
            "This is button one", "GOT IT"
        )

        sequence.addSequenceItem(
            binding.btn2,
            "This is button two", "GOT IT"
        )

        sequence.addSequenceItem(
            binding.btnOpenMusic,
            "This is button three", "GOT IT"
        )*/
    }

    private val requestPermissionSong =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            // do something
            if(isGranted){
                showBottomSheetSong()
            }else{
                showAllowPermission()
            }
        }

    private fun showAllowPermission(){
        try {
            setToastTic(Toastic.INFO,getString(sound.recorder.widget.R.string.allow_permission))
            openSettings()
        }catch (e : Exception){
            setLog(e.message.toString())
        }

    }

    private fun startPermissionSong(){
        if(Build.VERSION.SDK_INT >=Build.VERSION_CODES.TIRAMISU){
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                showBottomSheetSong()
            } else {
                requestPermissionSong.launch(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionSong.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }else{
                showBottomSheetSong()
            }
        }else{
            showBottomSheetSong()
        }

    }


    private fun showBottomSheetSong(){
        try {
            MyFragmentListener.openFragment(FragmentSheetListSong(showBtnStop,this))
            MyAdsListener.setAds(false)

        }catch (e : Exception){
            setLog(e.message.toString())
        }
    }

    private fun checkForUpdates() {
        try {
            checkUpdate()
        } catch (e: Exception) {
            Log.d("Update App home", e.message.toString())
        }
    }


    override fun onPause() {
        super.onPause()
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        //binding.bannerID.pause()
    }

    override fun onResume() {
        super.onResume()
        sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    private fun getSharedPreferenceUpdate() : SharedPreferences{
        return DataSession(this).getSharedUpdate()
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if(key== Constant.KeyShared.backgroundColor){
            binding.layoutBackground.setBackgroundColor(getSharedPreferenceUpdate().getInt(Constant.KeyShared.backgroundColor,-1))
        }
    }


    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragmentFileViewer)

        if (fragment is ListRecordFragment) {
            val consumed = fragment.onBackPressed()
            if (consumed) {
                return
            }
        } else if (fragment is FragmentSheetListSong) {
            val consumed = fragment.onBackPressed()
            if (consumed) {
                return
            }
        }
        else if (fragment is FragmentVideo) {
            val consumed = fragment.onBackPressed()
            if (consumed) {
                return
            }
        }else{
            finish()
        }
    }

    override fun openFragment(fragment: Fragment?) {
        setupFragment(binding.fragmentFileViewer.id,fragment)
    }

    override fun onViewAds(boolean: Boolean) {
       if(boolean){
           binding.bannerView.visibility = View.VISIBLE
       }else{
           binding.bannerView.visibility = View.GONE
       }
    }

    override fun onPause(pause: Boolean) {

    }

    override fun showButtonStop(stop: Boolean) {
        showBtnStop = stop
    }

    override fun onPlaySong(filePath: String) {
        try {
            if(mp!=null){
                mp.apply {
                    mp?.release()
                    mp = null
                    MyMusicListener.postAction(null)
                }
            }
        }catch (e : Exception){
            setToastTic(Toastic.ERROR,e.message.toString())
        }
        Handler().postDelayed({
            try {
                mp = MediaPlayer()
                mp?.apply {
                    setDataSource(this@MainActivity, Uri.parse(filePath))
                    volumes?.let { setVolume(it, volumes!!) }
                    setOnPreparedListener{
                        mp?.start()
                        MyMusicListener.postAction(mp)
                        MyStopSDKMusicListener.onStartAnimation()
                    }
                    mp?.prepareAsync()
                    setOnCompletionListener {
                        MyStopSDKMusicListener.postAction(true)
                        MyStopMusicListener.postAction(true)
                        MyPauseListener.showButtonStop(false)
                        showBtnStop = false
                    }
                    MyPauseListener.showButtonStop(true)
                    showBtnStop = true
                    songIsPlaying = true

                }
            } catch (e: Exception) {
                try {
                    MyStopSDKMusicListener.postAction(true)
                    MyStopMusicListener.postAction(true)
                    MyPauseListener.showButtonStop(false)
                    showBtnStop = false
                    setToastTic(Toastic.ERROR,e.message.toString())
                }catch (e : Exception){
                    setLog(e.message.toString())
                }
            }
        }, 100)
    }

    override fun onStopSong() {
        try {
            mp?.apply {
                stop()
                reset()
                release()
                mp = null
                songIsPlaying = false
                showBtnStop = false
                MyPauseListener.showButtonStop(false)
                MyMusicListener.postAction(null)
                MyStopMusicListener.postAction(true)
            }
        } catch (e: IOException) {
            setToastTic(Toastic.ERROR,e.message.toString())
        } catch (e: IllegalStateException) {
            setToastTic(Toastic.ERROR,e.message.toString())
        }catch (e : Exception){
            setToastTic(Toastic.ERROR,e.message.toString())
        }
    }

    override fun onNoteSong(note: String) {
        MyMusicListener.postNote(note)
    }

    override fun onMusic(mediaPlayer: MediaPlayer?) {
        TODO("Not yet implemented")
    }

    override fun onComplete() {
        TODO("Not yet implemented")
    }

    override fun onNote(note: String?) {
        TODO("Not yet implemented")
    }

    override fun onVolumeAudio(volume: Float?) {

    }
}