package id.harpa.logger

import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import android.os.Environment
import android.util.Log

object LogXposed {
    private val logDir = File(Environment.getExternalStorageDirectory(), "logharpa")
    private val logFile = File(logDir, "log.html")

    init {
        try {
            if (!logDir.exists()) logDir.mkdirs()
            if (!logFile.exists()) logFile.createNewFile()
        } catch (e: Exception) {
            Log.e("LogXposed", "Failed to init log file: ${e.message}")
        }
    }

    fun append(html: String) {
        try {
            FileWriter(logFile, true).use { fw ->
                fw.append(html).append("\n")
            }
        } catch (e: Exception) {
            Log.e("LogXposed", "Error writing to log: ${e.message}")
        }
    }

    fun log(tag: String, msg: String) {
        val time = SimpleDateFormat("HH:mm:ss").format(Date())
        val html = "<p><b>[$time][$tag]</b> $msg</p>"
        append(html)
    }
}
