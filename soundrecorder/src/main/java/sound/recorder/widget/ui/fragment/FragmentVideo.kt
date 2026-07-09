package sound.recorder.widget.ui.fragment

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
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
    private var firestore: FirebaseFirestore? = null

    companion object {
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 1 hari
        private var videoCache: List<Video>? = null
        private var lastFetchedAt = 0L

        fun newInstance(): FragmentVideo {
            return FragmentVideo()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Safety check for Firebase initialization
        val ctx = requireContext()
        if (FirebaseApp.getApps(ctx).isEmpty()) {
            try { FirebaseApp.initializeApp(ctx) } catch (e: Exception) {}
        }
        firestore = FirebaseFirestore.getInstance()

        binding = ActivityListVideoBinding.inflate(inflater, container, false)
        setupRecyclerView()
        load(false)
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
                    MyAdsListener.setBanner(false)
                    findNavController().navigateUp()
                }catch (e : Exception){
                    setToast(e.message.toString())
                }
            }
        })

        binding.ivClose.setOnClickListener {
            try {
                MyAdsListener.setBanner(false)
                findNavController().navigateUp()
            }catch (e : Exception){
                setToast(e.message.toString())
            }
        }
    }


    @SuppressLint("NotifyDataSetChanged")
    private fun load(loadMore: Boolean) {
        // Check Memory Cache first
        val now = System.currentTimeMillis()
        if (!loadMore && videoCache != null && (now - lastFetchedAt < CACHE_TTL_MS)) {
            val wrapper = VideoWrapper()
            wrapper.list = ArrayList(videoCache!!)
            binding.progressBar.visibility = View.GONE
            result(wrapper, loadMore)
            mAdapter?.notifyDataSetChanged()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Handle offline fallback if cache exists
                if (!isInternetConnected() && videoCache != null) {
                    val wrapper = VideoWrapper()
                    wrapper.list = ArrayList(videoCache!!)
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        result(wrapper, loadMore)
                        mAdapter?.notifyDataSetChanged()
                    }
                    return@launch
                }

                val querySnapshot = firestore?.collection("videos")?.get()?.await()
                if (querySnapshot != null) {
                    val allVideos = ArrayList<Video>()
                    for (doc in querySnapshot.documents) {
                        val video = Video()
                        video.datepublish = doc.getString("datepublish")
                        video.description = doc.getString("description")
                        video.thumbnail = doc.getString("thumbnail")
                        video.url = doc.getString("url")
                        video.title = doc.getString("title")
                        allVideos.add(video)
                    }

                    // Update Cache
                    if (!loadMore) {
                        videoCache = allVideos
                        lastFetchedAt = System.currentTimeMillis()
                    }

                    val wrapper = VideoWrapper()
                    wrapper.list = ArrayList()
                    var rowList = 1
                    // Apply current pagination logic
                    for (video in allVideos) {
                        if (rowList <= mPage * 50 && rowList > (mPage - 1) * 50) {
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
                        // If failed and have cache, fallback to cache
                        if (videoCache != null) {
                            val wrapper = VideoWrapper()
                            wrapper.list = ArrayList(videoCache!!)
                            binding.progressBar.visibility = View.GONE
                            result(wrapper, loadMore)
                        } else {
                            setToast("Failed Get Data")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (videoCache != null) {
                        val wrapper = VideoWrapper()
                        wrapper.list = ArrayList(videoCache!!)
                        binding.progressBar.visibility = View.GONE
                        result(wrapper, loadMore)
                    } else {
                        setToast("Error: ${e.message}")
                    }
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