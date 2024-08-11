package recording.host

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.SoundPool
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.fragment.app.Fragment
import recording.host.databinding.ActivityMainBinding
import sound.recorder.widget.RecordingSDK
import sound.recorder.widget.base.BaseActivityWidget
import sound.recorder.widget.listener.AdsListener
import sound.recorder.widget.listener.FragmentListener
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.listener.MyFragmentListener
import sound.recorder.widget.model.Song
import sound.recorder.widget.ui.fragment.FragmentSettings
import sound.recorder.widget.ui.fragment.FragmentSheetListSong
import sound.recorder.widget.ui.fragment.FragmentVideo
import sound.recorder.widget.ui.fragment.ListRecordFragment
import sound.recorder.widget.ui.fragment.VoiceRecordFragmentHorizontalZaif
import sound.recorder.widget.ui.fragment.VoiceRecordFragmentVertical
import sound.recorder.widget.util.Constant
import sound.recorder.widget.util.DataSession
import sound.recorder.widget.util.SnowFlakesLayout

class MainActivity : BaseActivityWidget(),FragmentListener,AdsListener, SharedPreferences.OnSharedPreferenceChangeListener {

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
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sharedPreferences = DataSession(this).getShared()
        sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        setupBannerNew(binding.bannerView)
        setupAppOpenAd()

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

        //setupAppOpenAd()


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

        salju.startSnowing()
        binding.layoutBackground.addView(salju)


        sp = SoundPool(
            5,
            AudioManager.STREAM_MUSIC, 5
        )


        showOpenAd()

        ss1 = sp.load(this,R.raw.dum,1)
        ss2 = sp.load(this,R.raw.dek,1)

        // btn1 = findViewById(R.id.btn1)
        // btn2 = findViewById(R.id.btn2)

        binding.btn1.setOnClickListener {
           // showInterstitial()

            sp.play(ss1, 1f, 1f, 0, 0, 1f)
        }

        binding.btn2.setOnClickListener {
            sp.play(ss2, 1f, 1f, 0, 0, 1f)
        }


        binding.btnInterstitialAdmob.setOnClickListener {
            showInterstitial()
        }

        binding.btnOpenId.setOnClickListener {
            showOpenAd()
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
                setToastError(e.message.toString())
            } catch (e : IllegalStateException){
                setToastError(e.message.toString())
            } catch (e : IllegalAccessException){
                setToastError(e.message.toString())
            }catch (e : NoSuchFieldException){
                setToastError(e.message.toString())
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
                setToastError(e.message.toString())
            } catch (e : IllegalStateException){
                setToastError(e.message.toString())
            } catch (e : IllegalAccessException){
                setToastError(e.message.toString())
            }catch (e : NoSuchFieldException){
                setToastError(e.message.toString())
            }
        }


        checkForUpdates()

        setupFragment(binding.recordingView.id,VoiceRecordFragmentHorizontalZaif())


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
}