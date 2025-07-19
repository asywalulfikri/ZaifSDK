package sound.recorder.widget.ui.fragment

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import sound.recorder.widget.adapter.VideoListAdapter
import sound.recorder.widget.base.BaseFragmentWidget
import sound.recorder.widget.databinding.ActivityListVideoBinding
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.model.Video
import sound.recorder.widget.model.VideoWrapper
import sound.recorder.widget.util.Toastic

class FragmentVideo : BaseFragmentWidget(), VideoListAdapter.OnItemClickListener {

    private var mAdapter: VideoListAdapter? = null
    private var mPage = 1
    private var mVideoList = ArrayList<Video>()
    private lateinit var binding: ActivityListVideoBinding
    private var firestore: FirebaseFirestore? = FirebaseFirestore.getInstance()

    companion object {
        fun newInstance(): FragmentVideo {
            return FragmentVideo()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = ActivityListVideoBinding.inflate(inflater, container, false)
        setupRecyclerView()
        load(false)
        binding.ivClose.visibility = View.GONE
        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
    }

    private fun setupRecyclerView() {
        val mainMenuLayoutManager = GridLayoutManager(activity, 3)
        binding.recyclerView.layoutManager = mainMenuLayoutManager
        binding.recyclerView.setHasFixedSize(true)

        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                try {
                    findNavController().navigateUp()
                }catch (e : Exception){
                    setToast(e.message.toString())
                }
            }
        })
    }


    @SuppressLint("NotifyDataSetChanged")
    private fun load(loadMore: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val querySnapshot = firestore?.collection("videos")?.get()?.await()
                if (querySnapshot != null) {
                    val wrapper = VideoWrapper()
                    wrapper.list = ArrayList()
                    var rowList = 1
                    for (doc in querySnapshot.documents) { // Menggunakan documents untuk mendapatkan List<DocumentSnapshot>
                        if (rowList <= mPage * 50 && rowList > (mPage - 1) * 50) {
                            val video = Video()
                            video.datepublish = doc.getString("datepublish")
                            video.description = doc.getString("description")
                            video.thumbnail = doc.getString("thumbnail")
                            video.url = doc.getString("url")
                            video.title = doc.getString("title")
                            Log.d("title", video.url + "-")
                            wrapper.list.add(video)
                        }
                        rowList++
                    }
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        result(wrapper, loadMore)
                        mAdapter?.notifyDataSetChanged()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        setToast("Failed Get Data")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setToast("Error: ${e.message}")
                }
            }
        }
    }


    private fun result(wrapper: VideoWrapper?, loadMore: Boolean) {
        if (wrapper != null) {
            try {
                if (wrapper.list.isEmpty()) {
                    setToastTic(Toastic.INFO,"No Data")
                } else {
                    mVideoList = ArrayList()
                    updateList(wrapper)
                    for (i in wrapper.list.indices) {
                        mVideoList.add(wrapper.list[i])
                    }
                    if (loadMore) {
                        mPage += 1
                    }
                    showList()
                }
            }catch (e : Exception){
                setLog(e.message.toString())
            }
        } else {
            setToast("No Data")
        }
    }

    private fun showList() {
        binding.recyclerView.visibility = View.VISIBLE
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateList(wrapper: VideoWrapper) {
        try {
            showList()
            if (isAdded) {
                mAdapter = VideoListAdapter(activity, wrapper.list, this)
                mAdapter?.setData(activity, wrapper.list)
                binding.recyclerView.adapter = mAdapter
                mAdapter?.notifyDataSetChanged()
            }
        }catch (e : Exception){
            setLog(e.message.toString())
        }
    }

    fun onBackPressed(): Boolean {
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)?.commit()
        return false
    }

    override fun onItemClick(position: Int) {
        val video = mVideoList[position]
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + video.url))
        try {
            startActivity(appIntent)
        } catch (e: ActivityNotFoundException) {
            setToastError(activity, e.message.toString())
        } catch (e: Exception) {
            setToastError(activity, e.message.toString())
        }
    }
}
