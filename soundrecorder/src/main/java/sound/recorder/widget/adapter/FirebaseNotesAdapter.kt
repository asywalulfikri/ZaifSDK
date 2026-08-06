package sound.recorder.widget.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import sound.recorder.widget.R
import sound.recorder.widget.notes.Note

class FirebaseNotesAdapter(
    private var notesList: List<Note>,
    private val isDebug: Boolean = false,
    private val onStatusToggleClick: (Note) -> Unit = {},
    private val onDeleteClick: (Note) -> Unit = {},
    private val onItemClick: (Note) -> Unit
) : RecyclerView.Adapter<FirebaseNotesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvNoteTitle)
        val tvDesc: TextView  = view.findViewById(R.id.tvNoteDesc)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvTimestamp: TextView = view.findViewById(R.id.timestamp)
        val layoutAdmin: View = view.findViewById(R.id.layoutAdminActions)
        val btnStatus: TextView = view.findViewById(R.id.btnApprove)
        val btnDelete: TextView = view.findViewById(R.id.btnDeleteOnline)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.note_list_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val note = notesList[position]
        holder.tvTitle.text = note.title
        holder.tvDesc.text  = note.note
        
        // Hide local-only views
        holder.tvTimestamp.visibility = View.GONE

        if (isDebug) {
            holder.tvStatus.visibility = View.VISIBLE
            holder.tvStatus.text = note.status ?: "UNKNOWN"
            holder.layoutAdmin.visibility = View.VISIBLE
            
            holder.btnDelete.setOnClickListener { onDeleteClick(note) }

            val isPublished = note.status == "published"
            holder.btnStatus.text = if (isPublished) "DRAFT" else "APPROVE"
            holder.btnStatus.setTextColor(if (isPublished) android.graphics.Color.parseColor("#FFB347") else android.graphics.Color.parseColor("#4CAF50"))
            holder.btnStatus.setOnClickListener { onStatusToggleClick(note) }
        } else {
            holder.tvStatus.visibility = View.GONE
            holder.layoutAdmin.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onItemClick(note) }
    }

    override fun getItemCount(): Int = notesList.size

    fun updateData(newList: List<Note>) {
        this.notesList = newList
        notifyDataSetChanged()
    }
}
