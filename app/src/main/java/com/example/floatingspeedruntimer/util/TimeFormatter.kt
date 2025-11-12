package com.example.floatingspeedruntimer.util
import android.widget.EditText
import java.util.Locale
import kotlin.math.abs
data class TimeParts(val hours: Long, val minutes: Long, val seconds: Long, val milliseconds: Long)
object TimeFormatter {
    fun formatTime(millis: Long, showMillis: Boolean): String {
        return format(abs(millis), showMillis)
    }
    fun formatCountdownTime(millis: Long, showMillis: Boolean): String {
        return format(millis, showMillis)
    }
    private fun format(millis: Long, showMillis: Boolean): String {
        if (millis <= 0L) {
             return "0:00" + if(showMillis) ".00" else ""
        }
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (millis % 1000) / 10
        return if (minutes > 0) {
            if (showMillis) String.format(Locale.US, "%d:%02d.%02d", minutes, seconds, hundredths)
            else String.format(Locale.US, "%d:%02d", minutes, seconds)
        } else {
            if (showMillis) String.format(Locale.US, "%d.%02d", seconds, hundredths)
            else String.format(Locale.US, "%d", seconds)
        }
    }
    fun toHMS(millis: Long): TimeParts {
        if (millis <= 0) return TimeParts(0,0,0,0)
        val hours = millis / 3600000
        val minutes = (millis % 3600000) / 60000
        val seconds = (millis % 60000) / 1000
        val milliseconds = millis % 1000
        return TimeParts(hours, minutes, seconds, milliseconds)
    }
    fun parseTime(hrs: EditText, min: EditText, sec: EditText, ms: EditText): Long {
        val h = hrs.text.toString().toLongOrNull() ?: 0
        val m = min.text.toString().toLongOrNull() ?: 0
        val s = sec.text.toString().toLongOrNull() ?: 0
        val mss = ms.text.toString().toLongOrNull() ?: 0
        return h * 3600000 + m * 60000 + s * 1000 + mss
    }
}
