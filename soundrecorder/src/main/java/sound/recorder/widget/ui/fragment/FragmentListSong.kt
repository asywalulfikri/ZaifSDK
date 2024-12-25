package sound.recorder.widget.ui.fragment

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import sound.recorder.widget.RecordingSDK
import sound.recorder.widget.databinding.BottomSheetSongBinding
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.model.Song
import sound.recorder.widget.util.DataSession


class FragmentListSong(private var showBtnStop: Boolean, private var listener: OnClickListener) : Fragment(),SharedPreferences.OnSharedPreferenceChangeListener {


    //Load Song
    private var listTitleSong: ArrayList<String>? = null
    private var listLocationSong: ArrayList<String>? = null
    private var adapter: ArrayAdapter<String>? = null



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

            sharedPreferences = DataSession(requireContext()).getShared()
            sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            if(showBtnStop){
                binding.ivStop.visibility = View.VISIBLE
            }else{
                binding.ivStop.visibility = View.GONE
            }

            binding.ivStop.setOnClickListener {
                listener.onStopSong()
                binding.ivStop.visibility = View.GONE
            }

            binding.btnCLose.setOnClickListener {

            }

            listTitleSong = ArrayList()
            listLocationSong = ArrayList()

            if(!RecordingSDK.isHaveSong(requireContext())){
                getSong(lisSong)
            }
        }

        return binding.root

    }


    private fun getSong(list : ArrayList<Song>){
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
    private fun getAllMediaMp3Files(songList: ArrayList<Song>) {
        if (activity != null && requireActivity() != null) {
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

            // Jalankan query di background thread
            lifecycleScope.launch(Dispatchers.IO) {
                val cursor = requireActivity().contentResolver?.query(
                    uri, null, null, null, null
                )

                if (cursor == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireActivity(), "Something Went Wrong.", Toast.LENGTH_LONG).show()
                    }
                } else if (!cursor.moveToFirst()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireActivity(), "No Music Found on SD Card.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val title = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    val location = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                    while (cursor.moveToNext()) {
                        val songTitle = cursor.getString(title) ?: ""
                        val songLocation = cursor.getString(location) ?: ""

                        listLocationSong?.add(songLocation)
                        listTitleSong?.add(songTitle)
                    }
                    cursor.close()
                }

                // Setelah data selesai diproses, perbarui UI di thread utama
                withContext(Dispatchers.Main) {
                    updateView()
                }
            }
        }
    }


    private fun updateView(){
        if(activity!=null){
            val listSong = listTitleSong!!.toTypedArray()

            adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, listSong)
            binding.listView.adapter = adapter
            adapter?.notifyDataSetChanged()
            binding.listView.onItemClickListener =
                AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, i: Int, _: Long ->
                    //dismiss()
                    //(dialog as? BottomSheetDialog)?.behavior?.state = STATE_HIDDEN
                    listener.onPlaySong(listLocationSong?.get(i).toString())
                    binding.ivStop.visibility = View.VISIBLE

                }
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

    fun onBackPressed(): Boolean {
        MyAdsListener.setAds(true)
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)?.commit()
        return false
    }
}