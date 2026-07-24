package org.fossify.home.helpers

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight in-app logger. Writes timestamped entries to a rotating file in
 * app-private storage, so on-device issues (crashes, silently-caught errors)
 * can be inspected and exported without ADB/root access.
 */
class LogKeeperHelper(context: Context) {
    private val appContext = context.applicationContext
    private val logFile = File(appContext.filesDir, LOG_FILE_NAME)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(tag: String, message: String, throwable: Throwable? = null) {
        try {
            rotateIfNeeded()
            val timestamp = dateFormat.format(Date())
            val builder = StringBuilder()
            builder.append(timestamp).append(" [").append(tag).append("] ").append(message).append('\n')
            if (throwable != null) {
                builder.append(throwable.stackTraceToString()).append('\n')
            }
            logFile.appendText(builder.toString())
        } catch (ignored: Exception) {
            // Logging must never itself crash the app.
        }
    }

    fun logCrash(throwable: Throwable) {
        log(tag = "CRASH", message = throwable.message ?: "Uncaught exception", throwable = throwable)
    }

    @Synchronized
    fun getLogs(): String {
        return try {
            if (logFile.exists()) logFile.readText() else ""
        } catch (ignored: Exception) {
            ""
        }
    }

    @Synchronized
    fun clearLogs() {
        try {
            if (logFile.exists()) {
                logFile.writeText("")
            }
        } catch (ignored: Exception) {
        }
    }

    private fun rotateIfNeeded() {
        if (logFile.exists() && logFile.length() > MAX_LOG_FILE_SIZE_BYTES) {
            // Keep only the newer half of the log rather than the whole history.
            val lines = logFile.readLines()
            val keepFrom = lines.size / 2
            logFile.writeText(lines.subList(keepFrom, lines.size).joinToString("\n", postfix = "\n"))
        }
    }

    companion object {
        private const val LOG_FILE_NAME = "vian_app_log.txt"
        private const val MAX_LOG_FILE_SIZE_BYTES = 1 * 1024 * 1024 // 1 MB cap before trimming
    }
}
