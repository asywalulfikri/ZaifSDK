package recording.host.cons

import android.content.Context
import org.json.JSONObject
import sound.recorder.widget.tutorial.InstrumentNote
import sound.recorder.widget.tutorial.InstrumentSong

object SongRepository {

    private fun loadFromAssets(context: Context, fileName: String): InstrumentSong? {
        return try {
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)
            
            val songName = root.optString("name", "Unknown Song")
            val eventsArr = root.getJSONArray("events")

            val notes = mutableListOf<InstrumentNote>()
            
            // Check if it has "OFF" events (Sustain instruments like Pianika)
            var hasOff = false
            for (i in 0 until eventsArr.length()) {
                val event = eventsArr.getJSONObject(i)
                val metadata = if (event.has("c")) event.optString("c", "") else event.optString("metadata", "")
                if (metadata == "OFF") {
                    hasOff = true
                    break
                }
            }

            if (hasOff) {
                val activeNotes = mutableMapOf<Int, Long>()
                for (i in 0 until eventsArr.length()) {
                    val event = eventsArr.getJSONObject(i)
                    val padIndex = if (event.has("a")) event.getInt("a") else event.getInt("padIndex")
                    val timestamp = if (event.has("b")) event.getLong("b") else event.getLong("timestamp")
                    val metadata = if (event.has("c")) event.getString("c") else event.getString("metadata")

                    if (metadata != "OFF") {
                        activeNotes[padIndex] = timestamp
                    } else {
                        val startTime = activeNotes.remove(padIndex)
                        if (startTime != null) {
                            notes.add(InstrumentNote(padIndex, startTime, timestamp - startTime))
                        }
                    }
                }
                activeNotes.forEach { (pad, start) ->
                    notes.add(InstrumentNote(pad, start, 400L))
                }
            } else {
                // Tap instruments like Guzheng
                for (i in 0 until eventsArr.length()) {
                    val event = eventsArr.getJSONObject(i)
                    val padIndex = if (event.has("a")) event.getInt("a") else event.getInt("padIndex")
                    val timestamp = if (event.has("b")) event.getLong("b") else event.getLong("timestamp")
                    notes.add(InstrumentNote(padIndex, timestamp, 400L))
                }
            }

            InstrumentSong(songName.uppercase(), notes.sortedBy { it.timeMs })
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getAllSongsGuzheng(context: Context): List<InstrumentSong> {
        val list = mutableListOf<InstrumentSong>()
        val files = listOf("test.json")
        for (file in files) {
            loadFromAssets(context, file)?.let { list.add(it) }
        }
        return list
    }

    fun getAllSongsPianika(context: Context): List<InstrumentSong> = getAllSongsGuzheng(context)
}
