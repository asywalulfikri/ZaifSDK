package sound.recorder.widget.ui.bottomSheet

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import sound.recorder.widget.adapter.VideoListAdapter
import sound.recorder.widget.databinding.ActivityListVideoBinding
import sound.recorder.widget.model.Video
import sound.recorder.widget.model.VideoWrapper
import sound.recorder.widget.util.Toastic


class BottomSheetVideo(private var firestore: FirebaseFirestore? = FirebaseFirestore.getInstance()) : BottomSheetDialogFragment(),VideoListAdapter.OnItemClickListener {

    private var mAdapter: VideoListAdapter? = null
    private var mPage = 1
    private var mVideoList = ArrayList<Video>()
    private lateinit var binding : ActivityListVideoBinding
    private var isFragmentAdded = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = ActivityListVideoBinding.inflate(layoutInflater)

        if(activity!==null){
            (dialog as? BottomSheetDialog)?.behavior?.state = STATE_EXPANDED
            (dialog as? BottomSheetDialog)?.behavior?.isDraggable = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dialog?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
            } else {
                @Suppress("DEPRECATION")
                dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }

            binding.ivClose.setOnClickListener {
                dismiss()
            }


            setupRecyclerView()
            load(false)
        }

        return binding.root

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        isFragmentAdded = true
    }



    private fun setupRecyclerView(){
        val mainMenuLayoutManager = GridLayoutManager(activity, 3)
        binding.recyclerView.layoutManager = mainMenuLayoutManager
        binding.recyclerView.setHasFixedSize(true)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun load(loadMore: Boolean) {

        firestore?.collection("videos")
            ?.get()
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val wrapper = VideoWrapper()
                    wrapper.list = ArrayList()
                    var rowList = 1
                    for (doc in task.result!!) {
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
                    if (wrapper.list.size != 0) {
                        result(wrapper, loadMore)
                        mAdapter?.notifyDataSetChanged()
                    } else if (task.result!!.size() == 0) {
                        setToastInfo(activity,"No Data")
                    }
                } else {
                    setToastError(activity,"Failed get data")

                }
            }
    }

    private fun result(wrapper: VideoWrapper?, loadMore: Boolean) {
        if (wrapper != null) {
            Log.e("gg2", "mm")
            if (wrapper.list.size == 0) {
                Log.e("gg3", "mm")
                setToastInfo(activity,"Tidak ada data")
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
        } else {
            Log.e("gg4", "mm")
            setToastInfo(activity,"No Data Found")
        }
    }

    private fun showList() {
        binding.recyclerView.visibility = View.VISIBLE
    }


    @SuppressLint("NotifyDataSetChanged")
    private fun updateList(wrapper: VideoWrapper) {
        showList()
        if(activity!=null){
            mAdapter = VideoListAdapter(activity,wrapper.list,this)
            mAdapter?.setData(activity,wrapper.list)
            binding.recyclerView.adapter = mAdapter
            mAdapter?.notifyDataSetChanged()
        }
    }

    override fun onItemClick(position: Int) {
        val video = mVideoList[position]
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + video.url))
        //val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.youtube.com/watch?v=" + video.url))
        try {
            startActivity(appIntent)
        } catch (e: ActivityNotFoundException) {
            setToastError(activity,e.message.toString())
        }catch (e : Exception){
            setToastError(activity,e.message.toString())
        }
    }




    fun setToastError(activity: Activity?, message : String){
        if(activity!=null){
            Toastic.toastic(activity,
                message = message,
                duration = Toastic.LENGTH_SHORT,
                type = Toastic.SUCCESS,
                isIconAnimated = true
            ).show()
        }
    }

    fun setToastWarning(activity: Activity?, message : String){
        if(activity!=null){
            Toastic.toastic(activity,
                message = message,
                duration = Toastic.LENGTH_SHORT,
                type = Toastic.WARNING,
                isIconAnimated = true
            ).show()
        }

    }

    fun setToastSuccess(activity: Activity?, message : String){
        if(activity!=null){
            Toastic.toastic(
                activity,
                message = message,
                duration = Toastic.LENGTH_SHORT,
                type = Toastic.SUCCESS,
                isIconAnimated = true
            ).show()
        }
    }

    fun setToastInfo(activity: Activity?, message : String){
        if(activity!=null){
            Toastic.toastic(activity,
                message = message,
                duration = Toastic.LENGTH_SHORT,
                type = Toastic.INFO,
                isIconAnimated = true
            ).show()
        }
    }



}