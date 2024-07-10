package sound.recorder.widget.ui.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.fragment.app.Fragment
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
import java.lang.ref.WeakReference

class FragmentSheetListSong(private var showBtnStop: Boolean? = null, private var listener: OnClickListener? = null) :
    Fragment(), SharedPreferences.OnSharedPreferenceChangeListener, StopSDKMusicListener {

    private var weakContext: WeakReference<Context>? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        weakContext = WeakReference(context)
    }

    companion object {
        fun newInstance(): FragmentSheetListSong {
            return FragmentSheetListSong()
        }
    }

    interface OnClickListener {
        fun onPlaySong(filePath: String)
        fun onStopSong()
        fun onNoteSong(note: String)
    }

    private var binding: BottomSheetSongBinding? = null
    private var sharedPreferences: SharedPreferences? = null
    private var listTitleSong: ArrayList<String>? = null
    private var listLocationSong: ArrayList<String>? = null
    private var listNoteSong: ArrayList<String>? = null
    private var adapter: ArrayAdapter<String>? = null
    private var mPanAnim: Animation? = null
    private var lisSong = ArrayList<Song>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = BottomSheetSongBinding.inflate(layoutInflater, container, false)

        if (activity != null) {
            try {
                sharedPreferences = DataSession(requireContext()).getShared()
                sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

                MyStopSDKMusicListener.setMyListener(this)

                initAnim()

                if (showBtnStop == true) {
                    binding?.ivStop?.visibility = View.VISIBLE
                    startAnimation()
                } else {
                    binding?.ivStop?.visibility = View.GONE
                }

                binding?.ivStop?.setOnClickListener {
                    listener?.onStopSong()
                    stopAnimation()
                }

                binding?.btnCLose?.setOnClickListener {
                    onBackPressed()
                }

                listTitleSong = ArrayList()
                listLocationSong = ArrayList()
                listNoteSong = ArrayList()

                try {
                    if (!RecordingSDK.isHaveSong(requireActivity())) {
                        getSong(lisSong)
                    }
                } catch (e: Exception) {
                    setLog(e.message)
                }
            } catch (e: Exception) {
                setLog(e.message)
            }
        }

        return binding!!.root
    }

    private fun getSong(list: ArrayList<Song>) {
        try {
            getAllMediaMp3Files(list)
        } catch (e: Exception) {
            setLog(e.message.toString())
        }
    }

    @SuppressLint("Recycle")
    private fun getAllMediaMp3Files(songList: ArrayList<Song>) {
        if (activity != null) {
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val cursor = requireContext().contentResolver?.query(
                uri,
                null,
                null,
                null,
                null
            )
            if (cursor == null) {
                Toast.makeText(requireContext(), "Something Went Wrong.", Toast.LENGTH_LONG).show()
            } else {
                val title = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val location = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                MainScope().launch {
                    var songTitle1: String
                    var songLocation1: String
                    var songNote1: String

                    withContext(Dispatchers.Default) {
                        for (i in songList.indices) {
                            songTitle1 = songList[i].title.toString()
                            songLocation1 = songList[i].pathRaw.toString()
                            songNote1 = songList[i].note.toString()

                            listLocationSong?.add(songLocation1)
                            listTitleSong?.add(songTitle1)
                            listNoteSong?.add(songNote1)
                        }
                    }

                    try {
                        MainScope().launch {

                            if (cursor.moveToFirst()) {
                                withContext(Dispatchers.Default) {
                                    do {
                                        var songTitle = ""
                                        var songLocation = ""
                                        val songNote = ""

                                        if (cursor.getString(title) != null) {
                                            songTitle = cursor.getString(title)
                                        }

                                        if (cursor.getString(location) != null) {
                                            songLocation = cursor.getString(location)
                                        }

                                        listLocationSong?.add(songLocation)
                                        listTitleSong?.add(songTitle)
                                        listNoteSong?.add(songNote)

                                    } while (cursor.moveToNext())
                                }
                                updateView()
                            } else {
                                updateView()
                            }
                        }

                    } catch (e: Exception) {
                        setLog(e.message.toString())
                    }
                }
            }
        }
    }

    private fun updateView() {
        if (activity != null) {
            try {
                val listSong = listTitleSong!!.toTypedArray()

                adapter = ArrayAdapter(requireContext(), R.layout.item_simple_song, listSong)
                binding?.listView?.adapter = adapter
                adapter?.notifyDataSetChanged()
                binding?.listView?.onItemClickListener =
                    AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, i: Int, _: Long ->
                        run {
                            listener?.onPlaySong(listLocationSong?.get(i).toString())
                            listener?.onNoteSong(listNoteSong?.get(i).toString())
                        }
                    }
            } catch (e: Exception) {
                setLog(e.message.toString())
            }
        }
    }

    private fun startAnimation() {
        binding?.ivStop?.visibility = View.VISIBLE
        binding?.ivStop?.startAnimation(mPanAnim)
    }

    private fun stopAnimation() {
        try {
            binding?.ivStop?.clearAnimation()
            binding?.ivStop?.visibility = View.GONE
        } catch (e: Exception) {
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
                anim
            }
        } catch (e: Exception) {
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

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAnimation()
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        MyStopSDKMusicListener.setMyListener(null)
        weakContext = null // Nullify the weak context reference
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        // Handle shared preference changes here
    }

    private fun setLog(message: String? = null) {
        Log.e("message", message.toString() + ".")
    }

    override fun onStop(stop: Boolean) {
        if (stop) {
            stopAnimation()
        }
    }

    override fun onStartAnimation() {
        startAnimation()
    }

    fun onBackPressed(): Boolean {
        MyAdsListener.setAds(true)
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)?.commit()
        return false
    }
}
