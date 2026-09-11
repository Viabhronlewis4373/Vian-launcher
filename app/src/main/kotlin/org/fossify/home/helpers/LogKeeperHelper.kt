package org.fossify.home.helpers

import android.content.Context
import org.fossify.home.extensions.config
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight in-app logger. Writes timestamped entries to a rotating file in
 * app-private storage, so on-device issues (crashes, silently-caught errors)
 * can be inspected and exported without ADB/root access.
 *
 * Gated by the Log Keeper master switch (Config.logKeeperEnabled) — when off,
 * log() and logCrash() are no-ops app-wide. Designed to be reused by future
 * components (sidebar, net speed, call recorder, etc.) as they're added, so
 * they all respect the same on/off switch and write to the same file.
 */
class LogKeeperHelper(context: Context) {
    private val appContext = context.applicationContext
    private val logFile = File(appContext.filesDir, LOG_FILE_NAME)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(tag: String, message: String, throwable: Throwable? = null, forceLog: Boolean = false) {
        if (!forceLog && !appContext.config.logKeeperEnabled) {
            return
        }
        try {
            rotateIfNeeded()
            val timestamp = dateFormat.format(Date())
            val builder = StringBuilder()
            builder.append(timestamp).append(" [").append(tag).append("] ").append(message).append('\n')
            if (throwable != null) {
                builder.append(throwable.stackTraceToString()).append('\n')
            }
            builder.append("Recent actions leading up to this:\n")
            builder.append(ActionTrail.snapshot()).append('\n')
            logFile.appendText(builder.toString())
        } catch (ignored: Exception) {
            // Logging must never itself crash the app.
        }
    }

    fun logCrash(throwable: Throwable) {
        // Crashes are always logged regardless of the master switch — a crash
        // is exactly the moment you most need the record, and the switch is
        // meant to control routine tracing noise, not this.
        log(tag = "CRASH", message = throwable.message ?: "Uncaught exception", throwable = throwable, forceLog = true)
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
