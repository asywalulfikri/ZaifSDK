package sound.recorder.widget.ui.bottomSheet

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.core.view.WindowCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import sound.recorder.widget.R
import sound.recorder.widget.RecordingSDK
import sound.recorder.widget.databinding.BottomSheetSongBinding
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.listener.MyStopSDKMusicListener
import sound.recorder.widget.listener.StopSDKMusicListener
import sound.recorder.widget.model.Song
import sound.recorder.widget.util.DataSession


class BottomSheetListSong(private var showBtnStop: Boolean, private var listener: OnClickListener) : BottomSheetDialogFragment(),SharedPreferences.OnSharedPreferenceChangeListener,StopSDKMusicListener{

    //Load Song
    private var listTitleSong: ArrayList<String>? = null
    private var listLocationSong: ArrayList<String>? = null
    private var adapter: ArrayAdapter<String>? = null
    private var mPanAnim: Animation? = null

    // Step 1 - This interface defines the type of messages I want to communicate to my owner
    interface OnClickListener {
        fun onPlaySong(filePath: String)
        fun onStopSong()
    }

    private lateinit var binding : BottomSheetSongBinding
    private var sharedPreferences : SharedPreferences? =null
    private var lisSong = ArrayList<Song>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = BottomSheetSongBinding.inflate(layoutInflater)

        if(activity!=null){
            try {
                MyAdsListener.setHideAllBanner()
                (dialog as? BottomSheetDialog)?.behavior?.state = STATE_EXPANDED
                (dialog as? BottomSheetDialog)?.behavior?.isDraggable = false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    dialog?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
                } else {
                    @Suppress("DEPRECATION")
                    dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                }


                sharedPreferences = DataSession(requireActivity()).getShared()
                sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

                MyStopSDKMusicListener.setMyListener(this)

                initAnim()




                if(showBtnStop){
                    binding.ivStop.visibility = View.VISIBLE
                    startAnimation()

                }else{
                    binding.ivStop.visibility = View.GONE
                }

                binding.ivStop.setOnClickListener {
                    listener.onStopSong()
                    stopAnimation()
                }

                binding.btnCLose.setOnClickListener {
                    dismiss()
                }

                listTitleSong = ArrayList()
                listLocationSong = ArrayList()

                try {
                    if(!RecordingSDK.isHaveSong(requireActivity())){
                        getSong(lisSong)
                    }
                }catch (e : Exception){
                    setLog(e.message)
                }
            }catch (e : Exception){
                setLog(e.message)
            }

        }

        return binding.root

    }

    private fun getSong(list : ArrayList<Song>){
       /* if(Build.VERSION.SDK_INT>32){
            getTiramisu(list)
        }else{
            getAllMediaMp3Files(list)
        }*/
        getAllMediaMp3Files(list)
    }


    private fun getTiramisu(songList : ArrayList<Song>){
        MainScope().launch {

            var songTitle1: String
            var songLocation1: String

            withContext(Dispatchers.Default) {

                //Process Background 2
                for (i in songList.indices) {
                    songTitle1 = songList[i].title.toString()
                    songLocation1 = songList[i].pathRaw.toString()
                    listLocationSong?.add(songLocation1)
                    listTitleSong?.add(songTitle1)
                }
            }

            updateView()
        }
    }

    @SuppressLint("Recycle")
    private fun getAllMediaMp3Files(songList : ArrayList<Song>) {
        if(activity!=null){
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val cursor = requireActivity().contentResolver?.query(uri,
                null,
                null,
                null,
                null
            )
            if (cursor == null) {
                Toast.makeText(requireActivity(), "Something Went Wrong.", Toast.LENGTH_LONG).show()
            } else if (!cursor.moveToFirst()) {
                Toast.makeText(requireActivity(), "No Music Found on SD Card.", Toast.LENGTH_LONG).show()
            } else {
                val title    = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val location = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)


                MainScope().launch {

                    var songTitle1: String
                    var songLocation1: String

                    withContext(Dispatchers.Default) {

                        //Process Background 2
                        for (i in songList.indices) {
                            songTitle1 = songList[i].title.toString()
                            songLocation1  = songList[i].pathRaw.toString()
                            listLocationSong?.add(songLocation1)
                            listTitleSong?.add(songTitle1)
                        }
                    }

                    //Result Process Background 2

                    MainScope().launch {

                        //Background Process 1
                        withContext(Dispatchers.Default) {

                            do {
                                var songTitle = ""
                                var songLocation = ""
                                if(cursor.getString(title)!=null){
                                    songTitle = cursor.getString(title)
                                }

                                if(cursor.getString(location)!=null){
                                    songLocation = cursor.getString(location)
                                }

                                listLocationSong?.add(songLocation)
                                listTitleSong?.add(songTitle)

                            } while (cursor.moveToNext())
                        }
                        updateView()
                    }

                }

            }

        }
    }

    private fun updateView(){
        if(activity!=null){
            val listSong = listTitleSong!!.toTypedArray()

            adapter = ArrayAdapter(requireActivity(), R.layout.item_simple_song, listSong)
            binding.listView.adapter = adapter
            adapter?.notifyDataSetChanged()
            binding.listView.onItemClickListener =
                AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, i: Int, _: Long ->
                    //dismiss()
                    (dialog as? BottomSheetDialog)?.behavior?.state = STATE_HIDDEN
                    listener.onPlaySong(listLocationSong?.get(i).toString())
                }
        }

    }

    fun setToast(message : String){
        Toast.makeText(activity,message,Toast.LENGTH_SHORT).show()
    }

    private fun startAnimation() {
        binding.ivStop.visibility = View.VISIBLE
        binding.ivStop.startAnimation(mPanAnim)
    }

    private fun stopAnimation(){
        try {
            binding.ivStop.clearAnimation()
            binding.ivStop.visibility = View.GONE
        }catch (e : Exception){
           setLog(e.message)
        }
    }

    private fun initAnim() {
        try {
            mPanAnim = AnimationUtils.loadAnimation(activity, R.anim.rotate)
            val mPanLin = LinearInterpolator()
            mPanAnim?.interpolator = mPanLin
            mPanAnim?.startTime = 0
            mPanAnim?.let { anim ->
                anim.interpolator = mPanLin
                anim.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation) {

                    }

                    override fun onAnimationEnd(animation: Animation) {
                        binding.ivStop.visibility = View.GONE

                    }

                    override fun onAnimationRepeat(animation: Animation) {

                    }
                })
            } ?: run {
                // Handle the case where mPanAnim is null
                println("Error: mPanAnim is null")
            }
        }catch (e : Exception){
           setLog(e.message)
        }
    }


    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.ASYNC)
    fun onMessageEvent(songListResponse: ArrayList<Song>?) {
        songListResponse?.let { getSong(it) }
    }

    override fun onResume() {
        super.onResume()
        sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {

    }

    private fun setLog(message : String? =null){
        Log.e("message",message.toString()+".")
    }

    override fun onStop(stop: Boolean) {
        if(stop){
            stopAnimation()
        }
    }

    override fun onStartAnimation() {
        startAnimation()
    }

}