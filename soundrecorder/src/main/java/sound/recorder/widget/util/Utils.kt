package sound.recorder.widget.util

import android.text.format.DateUtils
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by Susheel Kumar Karam
 * Website - SusheelKaram.com
 */
class Utils {

    companion object {
        fun buildFileName(prefix: String, extension: String): String {
            var formatter = SimpleDateFormat("dd_MM_yyyy_HH_mm_ss")
            val time = formatter.format(Calendar.getInstance().time)
            return prefix + time + extension
        }

        fun getFormattedDate(epoch: Int) : String{
            var date = Date(epoch.toLong() * 1000)
            var format = SimpleDateFormat("d MMM, yyyy hh:mm aaa")
            return format.format(date)
        }

        fun getFormattedSize(bytes: Int): String {
            var df =  DecimalFormat("#.##")
            df.roundingMode = RoundingMode.FLOOR
            return when(bytes) {
                in 0..1024*1024 -> "${df.format(bytes.toDouble()/1024)} KB"
                in 0..1024*1024*1024 -> "${df.format(bytes.toDouble()/(1024 * 1024))} MB"
                else  -> "${df.format(bytes.toDouble()/(1024 * 1024 * 1024))} GB"
            }
        }

        fun getFormattedDuration(millis: Int) : String{
            var secs = (millis / 1000);
            if(secs < 60) return "${secs} s"
            return DateUtils.formatElapsedTime(secs.toLong())
        }

        /**
         * Menggeser semua timestamp dalam JSON agar nada pertama dimulai pada targetStartMs.
         * Digunakan agar tutorial tidak memiliki jeda kosong yang terlalu lama di awal.
         */
        fun syncJsonNoteTimestamps(json: String, targetStartMs: Long): String {
            if (json.isBlank()) return json
            try {
                val arr = org.json.JSONArray(json)
                if (arr.length() == 0) return json

                // 1. Cari timestamp terkecil (minTs)
                var minTs = Long.MAX_VALUE
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val ts = if (item.has("timestamp")) item.getLong("timestamp")
                             else if (item.has("b")) item.getLong("b")
                             else Long.MAX_VALUE
                    if (ts < minTs) minTs = ts
                }

                if (minTs == Long.MAX_VALUE) return json

                // 2. Hitung offset agar minTs menjadi targetStartMs
                val offset = targetStartMs - minTs

                // 3. Terapkan offset ke semua item
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    if (item.has("timestamp")) {
                        item.put("timestamp", item.getLong("timestamp") + offset)
                    } else if (item.has("b")) {
                        item.put("b", item.getLong("b") + offset)
                    }
                }
                return arr.toString()
            } catch (e: Exception) {
                return json // Jika gagal parsing, kembalikan data asli (aman)
            }
        }
    }
}