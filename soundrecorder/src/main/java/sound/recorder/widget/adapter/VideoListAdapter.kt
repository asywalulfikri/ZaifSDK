package sound.recorder.widget.adapter

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import sound.recorder.widget.R
import sound.recorder.widget.model.Video

internal class VideoListAdapter(var context : FragmentActivity? , private var videoList: List<Video>, private val listener: OnItemClickListener) : RecyclerView.Adapter<VideoListAdapter.ViewHolder>() {


    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        var tvTitle : TextView = itemView.findViewById(R.id.tv_title)
        var tvDescription : TextView = itemView.findViewById(R.id.tv_description)
        var tvDatePublish : TextView = itemView.findViewById(R.id.tv_datepublish)
        var ivThumbnail : ImageView = itemView.findViewById(R.id.iv_thumbnail)

        init {
            itemView.setOnClickListener(this)
        }
        override fun onClick(p0: View?) {
            val position = adapterPosition // property of the recyclerview class
            if(position != RecyclerView.NO_POSITION)
                listener.onItemClick(position)
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_video, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return videoList.size
    }

    @SuppressLint("SimpleDateFormat", "SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if(position != RecyclerView.NO_POSITION){
            val video = videoList[position]

            holder.tvTitle.text = video.title
            holder.tvDescription.text = video.description
            holder.tvDatePublish.text = video.datepublish

            if(video.thumbnail!=""){
                Log.d("mmmm",video.thumbnail.toString())
                Picasso.get().load(video.thumbnail.toString()).into(holder.ivThumbnail)
            }
        }

    }

    @SuppressLint("NotifyDataSetChanged")
    fun setData(context : FragmentActivity?, videoList: ArrayList<Video>){
        this.videoList = videoList
        this.context = context
        notifyDataSetChanged()
    }


}