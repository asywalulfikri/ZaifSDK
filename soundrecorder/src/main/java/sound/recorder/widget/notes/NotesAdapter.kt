package sound.recorder.widget.notes

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import org.json.JSONObject
import sound.recorder.widget.R
import sound.recorder.widget.notes.NotesAdapter.MyViewHolder
import java.text.ParseException
import java.text.SimpleDateFormat

class NotesAdapter(
    private val notesList: ArrayList<Note>,
    private val onItemClick: (Int) -> Unit
) :
    RecyclerView.Adapter<MyViewHolder>() {
    class MyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var tvNoteDesc: TextView
        var timestamp: TextView
        var tvNoteTitle : TextView
        var tvStatus: TextView
        var layoutAdmin: View

        init {
            tvNoteDesc = view.findViewById(R.id.tvNoteDesc)
            timestamp = view.findViewById(R.id.timestamp)
            tvNoteTitle = view.findViewById(R.id.tvNoteTitle)
            tvStatus = view.findViewById(R.id.tvStatus)
            layoutAdmin = view.findViewById(R.id.layoutAdminActions)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.note_list_row, parent, false)
        return MyViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val note = notesList[position]

        // Hide admin views for local notes
        holder.tvStatus.visibility = View.GONE
        holder.layoutAdmin.visibility = View.GONE

        try {
            val jsonObject = JSONObject(note.note.toString())
            val value = Gson().fromJson(note.note, Note::class.java)
            // The JSON string is valid
            if(note.timestamp!=null){
                holder.timestamp.visibility = View.VISIBLE
                holder.timestamp.text = formatDate(note.timestamp)
            }else{
                holder.timestamp.visibility =View.GONE
            }
            holder.tvNoteTitle.text = value.title.toString()
            holder.tvNoteTitle.visibility = View.VISIBLE
            holder.tvNoteDesc.text = value.note.toString()
            holder.tvNoteDesc.visibility = View.VISIBLE

        } catch (e: Exception) {
            // The JSON string is not valid
            holder.tvNoteDesc.text = note.note
            if(note.title!=null){
                holder.tvNoteTitle.text = note.title
            }
        }

        // Formatting and displaying timestamp
        if(note.timestamp!=null){
            holder.timestamp.visibility = View.VISIBLE
            holder.timestamp.text = formatDate(note.timestamp)
        }else{
            holder.timestamp.visibility =View.GONE
        }

        holder.itemView.setOnClickListener { onItemClick(position) }
    }

    override fun getItemCount(): Int {
        return notesList.size
    }

    /**
     * Formatting timestamp to `MMM d` format
     * Input: 2018-02-21 00:15:42
     * Output: Feb 21
     */
    private fun formatDate(dateStr: String): String {
        try {
            @SuppressLint("SimpleDateFormat") val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            val date = fmt.parse(dateStr)
            @SuppressLint("SimpleDateFormat") val fmtOut = SimpleDateFormat("MMM d")
            if (date != null) {
                return fmtOut.format(date)
            }
        } catch (ignored: ParseException) {
        }
        return ""
    }
}