package id.harpa.logger

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object LogXposed {
    private var logFile: File? = null

    fun init(context: Context) {
        try {
            val dir = context.getExternalFilesDir(null)
            logFile = File(dir, "log.html")
            if (!logFile!!.exists()) logFile!!.createNewFile()
        } catch (e: Exception) {
            Log.e("LogXposed", "init failed: ${e.message}")
        }
    }

    fun log(tag: String, msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "<p><b>[$time][$tag]</b> $msg</p>"
        try {
            logFile?.let {
                FileWriter(it, true).use { writer -> writer.append(line).append("\n") }
            }
        } catch (e: Exception) {
            Log.e("LogXposed", "log failed: ${e.message}")
        }
    }
}
