package recording.host

import android.content.Context
import com.google.gson.Gson
import sound.recorder.widget.tutorial.InstrumentNote
import sound.recorder.widget.tutorial.InstrumentSong
import sound.recorder.widget.tutorial.SongJson

object SongRepository {

    // metadataPrefix: prefix yang menandai event NOTE-ON, misal "PIANIKA", "SULING", "GAMELAN"
    fun loadFromAssets(
        context: Context,
        fileName: String,
        metadataPrefix: String = "PIANIKA"
    ): InstrumentSong? {
        return try {
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val songJson = Gson().fromJson(jsonString, SongJson::class.java)

            val notes = mutableListOf<InstrumentNote>()
            val activeNotes = mutableMapOf<Int, Long>()

            for (event in songJson.events) {
                if (event.metadata.startsWith(metadataPrefix)) {
                    activeNotes[event.padIndex] = event.timestamp
                } else if (event.metadata == "OFF") {
                    val startTime = activeNotes.remove(event.padIndex)
                    if (startTime != null) {
                        val duration = event.timestamp - startTime
                        notes.add(InstrumentNote(event.padIndex, startTime, duration))
                    }
                }
            }

            // Not tanpa pasangan OFF diberi durasi default
            activeNotes.forEach { (pad, start) ->
                notes.add(InstrumentNote(pad, start, 400L))
            }

            InstrumentSong(songJson.name.uppercase(), notes.sortedBy { it.timeMs })

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Generic: host app menyediakan daftar file asset dan prefix metadata instrumennya
    fun getAllSongs(
        context: Context,
        files: List<String>,
        metadataPrefix: String = "PIANIKA"
    ): List<InstrumentSong> {
        return files.mapNotNull { loadFromAssets(context, it, metadataPrefix) }
    }

    // Backward-compat wrapper untuk pianika
    fun getAllSongsPianika(context: Context): List<InstrumentSong> = getAllSongs(
        context,
        listOf(
            "doraemon.json",
            "ibu_kita_kartini.json",
            "indonesia_raya.json",
            "happy_birthday.json",
            "mbg.json"
        ),
        metadataPrefix = "PIANIKA"
    )
}