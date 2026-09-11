package org.fossify.home.helpers

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Keeps a short, in-memory trail of recent user/app actions (taps, drags,
 * settings changes, etc.) — NOT written to disk on its own. Purely a rolling
 * buffer that exists in process memory for as long as the app is running.
 *
 * When LogKeeperHelper actually writes a log entry (an error or a crash), it
 * pulls the current trail and includes it as context, so the log shows what
 * led up to the problem instead of just the failure itself.
 *
 * This buffer is intentionally never persisted, and never grows without bound,
 * so it does not affect the log-file rotation/size concerns that logging every
 * routine tap directly to disk would cause.
 */
object ActionTrail {
    private const val MAX_ENTRIES = 50
    private val entries = ArrayDeque<String>(MAX_ENTRIES)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun record(action: String) {
        if (entries.size >= MAX_ENTRIES) {
            entries.removeFirst()
        }
        entries.addLast("${timeFormat.format(Date())} $action")
    }

    @Synchronized
    fun snapshot(): String {
        return if (entries.isEmpty()) {
            "(no recent actions recorded)"
        } else {
            entries.joinToString("\n")
        }
    }
}
