package id.harpa.logger

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date

object LogXposed {
    private val logDir = File("/data/data/id.harpa.logger/files/logharpa")
    private val logFile = File(logDir, "log.html")

    init {
        try {
            if (!logDir.exists()) logDir.mkdirs()
            if (!logFile.exists()) logFile.createNewFile()
        } catch (e: Exception) {
            Log.e("LogXposed", "Gagal init log: ${e.message}")
        }
    }

    fun append(html: String) {
        try {
            FileWriter(logFile, true).use { it.append(html).append("\n") }
        } catch (e: Exception) {
            Log.e("LogXposed", "Error tulis log: ${e.message}")
        }
    }

    fun log(tag: String, msg: String) {
        val time = SimpleDateFormat("HH:mm:ss").format(Date())
        append("<p><b>[$time][$tag]</b> $msg</p>")
    }
}
