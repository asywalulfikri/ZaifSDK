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

class FragmentSheetListSong(
    private var showBtnStop: Boolean? = null,
    private var listener: OnClickListener? = null
) : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener, StopSDKMusicListener {

    private var weakContext: WeakReference<Context>? = null
    private var binding: BottomSheetSongBinding? = null
    private var sharedPreferences: SharedPreferences? = null
    private var listTitleSong = ArrayList<String>()
    private var listLocationSong = ArrayList<String>()
    private var listNoteSong = ArrayList<String>()
    private var adapter: ArrayAdapter<String>? = null
    private var mPanAnim: Animation? = null
    private var lisSong = ArrayList<Song>()

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = BottomSheetSongBinding.inflate(inflater, container, false)

        activity?.let {
            try {
                sharedPreferences = DataSession(it).getShared()
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

                if (!RecordingSDK.isHaveSong(it)) {
                    getSong(lisSong)
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
        activity?.let { activity ->
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val cursor = activity.contentResolver?.query(uri, null, null, null, null)

            if (cursor == null) {
                Toast.makeText(activity, "Something Went Wrong.", Toast.LENGTH_LONG).show()
                return
            }

            val titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val locationIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

            MainScope().launch {
                withContext(Dispatchers.Default) {
                    songList.forEach {
                        listTitleSong.add(it.title ?: "")
                        listLocationSong.add(it.pathRaw ?: "")
                        listNoteSong.add(it.note ?: "")
                    }
                }

                try {
                    if (cursor.moveToFirst()) {
                        withContext(Dispatchers.Default) {
                            do {
                                val songTitle = cursor.getString(titleIndex) ?: ""
                                val songLocation = cursor.getString(locationIndex) ?: ""

                                listTitleSong.add(songTitle)
                                listLocationSong.add(songLocation)
                                listNoteSong.add("")
                            } while (cursor.moveToNext())
                        }
                    }
                    updateView()
                } catch (e: Exception) {
                    setLog(e.message.toString())
                } finally {
                    cursor.close()
                }
            }
        }
    }

    private fun updateView() {
        activity?.let {
            try {
                adapter = ArrayAdapter(it, R.layout.item_simple_song, listTitleSong)
                binding?.listView?.adapter = adapter
                adapter?.notifyDataSetChanged()

                binding?.listView?.onItemClickListener = AdapterView.OnItemClickListener { _, _, i, _ ->
                    listener?.onPlaySong(listLocationSong[i])
                    listener?.onNoteSong(listNoteSong[i])
                }
            } catch (e: Exception) {
                setLog(e.message.toString())
            }
        }
    }

    private fun startAnimation() {
        binding?.ivStop?.apply {
            visibility = View.VISIBLE
            startAnimation(mPanAnim)
        }
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
            mPanAnim = AnimationUtils.loadAnimation(activity, R.anim.rotate).apply {
                interpolator = LinearInterpolator()
                startTime = 0
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
        weakContext = null
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        // Handle shared preference changes here
    }

    private fun setLog(message: String? = null) {
        Log.e("FragmentSheetListSong", message ?: "Unknown error.")
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
