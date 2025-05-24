package sound.recorder.widget.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import sound.recorder.widget.R
import sound.recorder.widget.model.Song

class SongAdapter(
    private val context: Context,
    private val songs: List<Song>
) : BaseAdapter() {

    override fun getCount(): Int = songs.size

    override fun getItem(position: Int): Any = songs[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_song, parent, false)
        val song = songs[position]

        val tvTitle = view.findViewById<TextView>(R.id.tvSongName)
       // val tvNote = view.findViewById<TextView>(R.id.tv_note)
      //  val ivIcon = view.findViewById<ImageView>(R.id.iv_icon)

        tvTitle.text = song.title ?: "Unknown"
     //   tvNote.text = song.note ?: ""

        return view
    }
}
