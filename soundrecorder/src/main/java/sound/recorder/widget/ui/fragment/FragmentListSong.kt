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
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import sound.recorder.widget.R
import sound.recorder.widget.RecordingSDK
import sound.recorder.widget.databinding.BottomSheetSongBinding
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.listener.MyMusicListener
import sound.recorder.widget.model.Song
import sound.recorder.widget.ui.viewmodel.MusicViewModel
import sound.recorder.widget.util.DataSession
import sound.recorder.widget.util.DialogUtils
import java.lang.ref.WeakReference

class FragmentListSong(
) : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var weakContext: WeakReference<Context>? = null
    private var binding: BottomSheetSongBinding? = null
    private var sharedPreferences: SharedPreferences? = null
    private var listTitleSong = ArrayList<String>()
    private var listLocationSong = ArrayList<String>()
    private var listNoteSong = ArrayList<String>()
    private var adapter: ArrayAdapter<String>? = null
    private var mPanAnim: Animation? = null
    private var lisSong = ArrayList<Song>()
    private val musicViewModel: MusicViewModel by activityViewModels()
    private var volumeMusic: Float = 1.0f // Volume default 100% for MediaPlayer
    private var volumeAudio: Float = 1.0f // Volume default 100% for SoundPool
    lateinit var dataSession : DataSession

    override fun onAttach(context: Context) {
        super.onAttach(context)
        weakContext = WeakReference(context)
    }

    companion object {
        fun newInstance(): FragmentListSong {
            return FragmentListSong()
        }
    }

    interface OnClickListener {
        fun onPlaySong(filePath: String)
        fun onStopSong()
        fun onNoteSong(note: String)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetSongBinding.inflate(inflater, container, false)

        activity?.let {
            try {
                requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        try {
                            MyAdsListener.setBannerHome(true)
                            MyAdsListener.setBannerUnity(true)
                            findNavController().navigateUp()
                        }catch (e : Exception){
                            setToast(e.message.toString())
                        }
                    }
                })

                MyAdsListener.setBannerUnity(false)
                sharedPreferences = DataSession(it).getShared()
                sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
                initAnim()

                dataSession = DataSession(requireContext())
                volumeMusic = dataSession.getVolumeMusic()
                volumeAudio = dataSession.getVolumeAudio()

                binding?.btnCLose?.setOnClickListener {
                    MyAdsListener.setBannerHome(true)
                    MyAdsListener.setBannerUnity(true)
                    findNavController().navigateUp()
                }

                binding?.btnStop?.setOnClickListener {
                    binding?.llMusic?.visibility = View.GONE
                    musicViewModel.stopMusic()
                    stopAnimation()
                }

                binding?.btnPlay?.setOnClickListener {
                    musicViewModel.resumeMusic()
                }

                binding?.btnPause?.setOnClickListener {
                    musicViewModel.pauseMusic()
                }

                binding?.btnVolume?.setOnClickListener {
                    showVolumeDialog()
                }

                lifecycleScope.launch {
                    delay(2000) // delay 2 detik (2000 ms)

                    if (!RecordingSDK.isHaveSong(it)) {
                        getSong(lisSong)
                    }
                }

                musicViewModel.duration.observe(viewLifecycleOwner) { dur ->
                    binding?.seekBar?.max = dur
                    //durationTextView.text = formatTime(dur)
                }

                musicViewModel.currentPosition.observe(viewLifecycleOwner) { pos ->
                    binding?.seekBar?.progress = pos
                    binding?.tvTime?.text = formatTime(pos)
                }

                musicViewModel.completeRequest.observe(viewLifecycleOwner) { isComplete ->
                    binding?.llMusic?.visibility = View.GONE
                    binding?.ivStop?.visibility = View.GONE
                }

                musicViewModel.pauseRequest.observe(viewLifecycleOwner) { isPause ->
                    isPause?.let {
                       binding?.btnPlay?.text = requireContext().getString(R.string.text_continue)
                    }
                }

                musicViewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying->
                    if(isPlaying){
                        binding?.btnPause?.visibility = View.VISIBLE
                        binding?.btnStop?.visibility = View.VISIBLE
                        binding?.btnPlay?.visibility = View.GONE
                        binding?.ivStop?.visibility = View.VISIBLE
                        startAnimation()
                    }else{

                        binding?.btnPause?.visibility = View.GONE
                        binding?.btnStop?.visibility = View.GONE
                        binding?.btnPlay?.visibility = View.VISIBLE
                        binding?.ivStop?.visibility = View.GONE
                        stopAnimation()
                    }
                }

                musicViewModel.isActive.observe(viewLifecycleOwner) { active ->
                    if (active) {
                        binding?.llMusic?.visibility = View.VISIBLE
                    } else {
                        binding?.llMusic?.visibility = View.GONE
                    }
                }

                binding?.seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            binding?.tvTime?.text = formatTime(progress)
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        val pos = seekBar?.progress ?: 0
                        musicViewModel.seekbarMusic(pos)
                    }
                })

            } catch (e: Exception) {
                setLog(e.message)
            }
        }

        return binding?.root ?: View(context)
    }

    private fun showVolumeDialog(){
        try {
            DialogUtils().showVolumeDialog(
                context = requireContext(),
                initialVolumeMusic = volumeMusic, // Volume musik awal
                initialVolumeAudio = volumeAudio, // Volume audio awal
                onVolumeMusicChanged = { newVolumeMusic ->
                    volumeMusic = newVolumeMusic
                    musicViewModel.setVolume(newVolumeMusic)
                },
                onVolumeAudioChanged = { newVolumeAudio ->
                    volumeAudio = newVolumeAudio
                    MyMusicListener.postVolumeAudio(newVolumeAudio) // Update volume pada SoundPool
                }
            )
        }catch (e : Exception){
            setToast(e.message.toString())
        }
    }

    fun setToast(message : String?){
        try {
            Toast.makeText(requireContext().applicationContext, "$message.", Toast.LENGTH_SHORT).show()
        }catch (e : Exception){
            setLog(e.message)
        }
    }

    private fun formatTime(millis: Int): String {
        val seconds = millis / 1000 % 60
        val minutes = millis / 1000 / 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun getSong(list: ArrayList<Song>) {
        try {
            getAllMediaMp3Files(list)
        } catch (e: Exception) {
            setLog(e.message.toString())
        }
    }

    @SuppressLint("Recycle")
    private fun getAllMediaMp3FilesAA(songList: ArrayList<Song>) {
        activity?.let { activity ->
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val cursor = activity.contentResolver?.query(uri, null, null, null, null)

            if (cursor == null) {
                Toast.makeText(activity, "Something Went Wrong.", Toast.LENGTH_LONG).show()
                return
            }

            val titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val locationIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val song1 = Song()
            MainScope().launch {
                withContext(Dispatchers.Default) {
                    songList.forEach {
                        listTitleSong.add(it.title ?: "")
                        listLocationSong.add(it.pathRaw ?: "")
                        listNoteSong.add(it.note ?: "")

                        //Toast.makeText(requireContext(),"cept", Toast.LENGTH_LONG).show()
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

                                song1.title = songTitle
                                song1.pathRaw = songLocation
                                song1.note = ""
                                lisSong.add(song1)
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

                            listLocationSong.add(songLocation1)
                            listTitleSong.add(songTitle1)
                            listNoteSong.add(songNote1)
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

                                        listLocationSong.add(songLocation)
                                        listTitleSong.add(songTitle)
                                        listNoteSong.add(songNote)

                                    } while (cursor.moveToNext())
                                }
                                updateView()
                            } else {
                                updateView()
                            }

                            musicViewModel.songIsLoaded = true
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
                val listSong = listTitleSong.toTypedArray()

                adapter = ArrayAdapter(requireContext(), R.layout.item_simple_song, listSong)
                binding?.listView?.adapter = adapter
                adapter?.notifyDataSetChanged()
                binding?.listView?.onItemClickListener =
                    AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, i: Int, _: Long ->
                        run {
                            try {
                                binding?.tvTitle?.text = listTitleSong[i].toString()
                                musicViewModel.playMusic(requireContext(),listLocationSong[i].toString())
                                musicViewModel.setNote(listNoteSong[i].toString())
                            }catch (e : Exception){
                                setToast(e.message)
                            }
                        }
                    }
            } catch (e: Exception) {
                setLog(e.message.toString())
            }
        }
    }


    private fun startAnimation() {
        binding?.ivStop?.apply {
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
        if (isAdded && isVisible) {
            if(!musicViewModel.songIsLoaded){
                songListResponse?.let { getSong(it) }
            }
        }
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
        weakContext = null
        musicViewModel.songIsLoaded = false
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        // Handle shared preference changes here
    }

    private fun setLog(message: String? = null) {
        try {
            Log.e("FragmentSheetListSong", message ?: "Unknown error.")
        }catch (e : Exception){
           //
        }
    }

    fun onStartAnimation() {
        startAnimation()
    }

    fun onBackPressed(): Boolean {
        MyAdsListener.setBannerHome(true)
        MyAdsListener.setBannerUnity(true)
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)?.commit()
        return false
    }
}
