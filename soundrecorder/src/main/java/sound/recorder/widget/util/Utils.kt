package sound.recorder.widget.util

import android.text.format.DateUtils
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

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
    }
}