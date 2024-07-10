package sound.recorder.widget.adapter

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import sound.recorder.widget.R
import sound.recorder.widget.db.AudioRecord
import java.text.SimpleDateFormat
import java.util.*

internal class AudioRecorderAdapter(private var audioRecords: List<AudioRecord>, private val listener: OnItemClickListener) : RecyclerView.Adapter<AudioRecorderAdapter.ViewHolder>() {

    private var editMode = false

    interface OnItemClickListener {
        fun onItemClick(position: Int)
        fun onItemLongClick(position: Int)
        fun onShareClick(audioRecord: AudioRecord)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener, View.OnLongClickListener {
        var filename : TextView = itemView.findViewById(R.id.filename)
        var fileMeta : TextView = itemView.findViewById(R.id.file_meta)
        var checkBox : CheckBox = itemView.findViewById(R.id.checkbox)
        var ivShare : ImageView = itemView.findViewById(R.id.ivShare)

        init {
            itemView.setOnClickListener(this)
            itemView.setOnLongClickListener(this)
        }
        override fun onClick(p0: View?) {
            val position = adapterPosition // property of the recyclerview class
            if(position != RecyclerView.NO_POSITION)
                listener.onItemClick(position)
        }

        override fun onLongClick(p0: View?): Boolean {
            val position = adapterPosition // property of the recyclerview class
            if(position != RecyclerView.NO_POSITION)
                listener.onItemLongClick(position)
            return true
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.itemview_layout, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return audioRecords.size
    }

    @SuppressLint("SimpleDateFormat", "SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if(position != RecyclerView.NO_POSITION){
            val audioRecord = audioRecords[position]
            holder.filename.text = audioRecord.filename
            val sdf = SimpleDateFormat("dd/MM/yy")
            val netDate = Date(audioRecord.date)
            val date =sdf.format(netDate)

            holder.fileMeta.text = "${audioRecord.duration}  $date"

            Log.d("ListingTag", audioRecord.isChecked.toString())

            if(editMode) {
                holder.checkBox.visibility = View.VISIBLE
                if (audioRecord.isChecked){
                    holder.checkBox.isChecked = audioRecord.isChecked
                }else{
                    holder.checkBox.isChecked = false
                }
            }else {
                holder.checkBox.visibility = View.GONE
                audioRecord.isChecked = false
                holder.checkBox.isChecked = false
            }

            holder.ivShare.setOnClickListener {
                listener.onShareClick(audioRecord)
            }
        }

    }

    @SuppressLint("NotifyDataSetChanged")
    fun setData(audioRecords: List<AudioRecord>){
        this.audioRecords = audioRecords
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setEditMode(mode: Boolean){
        editMode = mode
        notifyDataSetChanged()
    }

    fun isEditMode():Boolean{
        return editMode
    }

}