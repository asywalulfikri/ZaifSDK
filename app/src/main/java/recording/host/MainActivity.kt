package recording.host

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
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
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import recording.host.databinding.ActivityMainBinding
import sound.recorder.widget.RecordingSDK
import sound.recorder.widget.base.BaseActivityWidget
import sound.recorder.widget.listener.AdsListener
import sound.recorder.widget.listener.FragmentListener
import sound.recorder.widget.listener.MusicListener
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.listener.MyFragmentListener
import sound.recorder.widget.listener.MyMusicListener
import sound.recorder.widget.listener.PauseListener
import sound.recorder.widget.model.Song
import sound.recorder.widget.tools.showcase.GuideView
import sound.recorder.widget.tools.showcase.config.DismissType
import sound.recorder.widget.tools.showcase.config.Gravity
import sound.recorder.widget.ui.bottomSheet.BottomSheetNote
import sound.recorder.widget.ui.fragment.FragmentSettings
import sound.recorder.widget.ui.fragment.FragmentListSong
import sound.recorder.widget.ui.fragment.FragmentVideo
import sound.recorder.widget.ui.fragment.FragmentListRecord
import sound.recorder.widget.util.AppRatingHelper
import sound.recorder.widget.util.Constant
import sound.recorder.widget.util.DataSession
import sound.recorder.widget.util.SnowFlakesLayout
import sound.recorder.widget.util.Toastic
import java.io.IOException
import kotlin.math.ln


class MainActivity : BaseActivityWidget(),FragmentListener,AdsListener, SharedPreferences.OnSharedPreferenceChangeListener,PauseListener,FragmentListSong.OnClickListener,MusicListener {

    private lateinit var sp : SoundPool

    private var ss1 = 1
    private var ss2 = 2

    private lateinit var binding : ActivityMainBinding

    val listTitle = arrayOf(
        "Gundul Gundul Pacul",
        "Ampar Ampar Pisang"
    )


    var listNote = arrayOf(

        "Gundul gundul pacul cul\n" +
                "1   3       1    3    4  5    5\n" +
                "Gembelengan\n" +
                "7  1'  7  1'  7  5\n" +
                "Nyunggi nyunggi wakul kul\n" +
                "1         3    1    3     4    5    5\n" +
                "Gembelengan\n" +
                "7  1'  7  1'  7  5\n" +
                "Wakul ngglimpang segane dadi sak latar\n" +
                "  1   3     5     4         4   5     4   3  1 4 3 1\n" +
                "Wakul ngglimpang segane dadi sak latar\n" +
                "  1  3      5     4         4   5     4   3   1 4 3 1",


        "5 1   1 7   1 2\n" +
                "Ampar ampar pisang\n" +

                "\n" +
                "5 5   2  2 1   2 3\n" +
                "Pisangku belum masak\n" +
                "\n" +

                "4 2     2 3  1  1 2    2 1  7 1\n" +
                "Masak sabigi di hurung bari-bari\n" +
                "\n" +

                "4 2     2 3  1  1 2    2 1  7 1\n" +
                "Masak sabigi di hurung bari-bari\n" +
                "\n" +

                "5   5  5 1   1   7  1 2\n" +
                "Mangga lepak mangga lepok\n" +
                "\n" +

                "5 2   2 1  2   3\n" +
                "Patah kayu bengkok\n" +
                "\n" +

                "3   4   4 2 2   33\n" +
                "Bengkok dimakan api\n" +
                "\n" +

                "11 2   2    1 7 1\n" +
                "Apinya cang curupan\n" +
                "\n" +

                "3   4   4 2 2   33\n" +
                "Bengkok dimakan api\n" +
                "\n" +

                "11 2   2    1 7 1\n" +
                "Apinya cang curupan\n" +
                "\n" +

                "3    5 5  4 4   5 2\n" +
                "Nang mana batis kutung\n" +
                "\n" +

                "2 4 4 3  2 1\n" +
                "Dikitipi dawang\n" +
                "\n" +

                "3    5 5  4 4   5 2\n" +
                "Nang mana batis kutung\n" +
                "\n" +

                "2 4 4 3  2 1\n" +
                "Dikitipi dawang..."


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

    private var seekBarCallback: ((Int) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sharedPreferences = DataSession(this).getShared()
        sharedPreferences?.registerOnSharedPreferenceChangeListener(this)


        hideSystemNavigationBar()

       // binding.tvRunningText.startScroll()

        // binding.tvRunningText.startScroll()
        val progress = sharedPreferences?.getInt(Constant.KeyShared.volume,100)
        volumes = (1 - ln((ToneGenerator.MAX_VOLUME - progress!!).toDouble()) / ln(
            ToneGenerator.MAX_VOLUME.toDouble())).toFloat()

       /* setupBanner(binding.bannerView)
        setupBannerUnity(binding.bannerView)
       // setupBannerFacebook(binding.bannerFacebook)
        setupHideStatusBar(binding.root,false)*/
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
            itemSong.note = listNote[i]
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
           // showInterstitial()
            showInterstitialUnity()
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

        binding.btnChangePage.setOnClickListener {
            val intent = Intent(this@MainActivity, ActivityGame::class.java)
            startActivity(intent)
        }

        binding.btnInterstitialStarApp.setOnClickListener {
            try {
                // some code
                val fragment = FragmentSettings.newInstance()
                MyAdsListener.setBannerHome(false)
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
                    MyAdsListener.setBannerHome(false)
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

    fun setSeekBarUpdateCallback(callback: ((Int) -> Unit)?) {
        seekBarCallback = callback
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
            //MyFragmentListener.openFragment(FragmentListSong(showBtnStop,this))
            MyAdsListener.setBannerHome(false)

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

        if (fragment is FragmentListRecord) {
            val consumed = fragment.onBackPressed()
            if (consumed) {
                return
            }
        } else if (fragment is FragmentListSong) {
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

    override fun onViewBannerHome(boolean: Boolean) {
       if(boolean){
           binding.bannerView.visibility = View.VISIBLE
       }else{
           binding.bannerView.visibility = View.GONE
       }
    }

    override fun onViewBannerUnity(show: Boolean) {

    }

    override fun onHideAllBanner() {

    }

    override fun onShowInterstitial() {

    }



    override fun onPause(pause: Boolean) {

    }

    override fun showButtonStop(stop: Boolean) {
        showBtnStop = stop
    }

    override fun onPlaySong(filePath: String) {

    }

    private fun startSeekBarUpdates() {
        handler.post(object : Runnable {
            override fun run() {
                if (mp!!.isPlaying) {
                    seekBarCallback?.invoke(mp!!.currentPosition)
                    handler.postDelayed(this, 1000)
                }
            }
        })
    }

    override fun onStopSong() {

    }

    override fun onNoteSong(note: String) {

    }


    override fun onVolumeAudio(volume: Float?) {

    }
}