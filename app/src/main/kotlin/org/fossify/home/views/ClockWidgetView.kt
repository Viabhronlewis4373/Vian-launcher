package org.fossify.home.views

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.AlarmClock
import android.util.AttributeSet
import android.widget.LinearLayout
import org.fossify.home.databinding.ClockWidgetBinding
import org.fossify.home.helpers.LogKeeperHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Self-contained clock widget. Deliberately isolated from HomeScreenGrid's
 * WorkspaceItem/grid-cell system — it's a fixed overlay on the home screen,
 * not a draggable/resizable grid item, so it doesn't touch any of the
 * drag-drop/rendering logic there. Manages its own tick-update lifecycle via
 * onAttachedToWindow/onDetachedFromWindow, so nothing needs to be wired into
 * MainActivity's onResume/onPause.
 */
class ClockWidgetView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
    private lateinit var binding: ClockWidgetBinding
    private val logKeeperHelper by lazy { LogKeeperHelper(context.applicationContext) }

    private val timeFormat = SimpleDateFormat("h:mm", Locale.getDefault())
    private val amPmFormat = SimpleDateFormat("a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateTime()
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = ClockWidgetBinding.bind(this)
        setOnClickListener {
            launchClockApp()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateTime()
        try {
            val filter = IntentFilter(Intent.ACTION_TIME_TICK).apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            }
            context.registerReceiver(tickReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            logKeeperHelper.log("ClockWidgetView", "Failed to register time tick receiver", e)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            context.unregisterReceiver(tickReceiver)
        } catch (ignored: Exception) {
            // Receiver may already be unregistered — not an error worth logging.
        }
    }

    private fun updateTime() {
        val now = Calendar.getInstance().time
        binding.clockWidgetTime.text = timeFormat.format(now)
        binding.clockWidgetAmpm.text = amPmFormat.format(now)
        binding.clockWidgetDate.text = dateFormat.format(now)
    }

    private fun launchClockApp() {
        try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
            context.startActivity(intent)
        } catch (e: Exception) {
            logKeeperHelper.log("ClockWidgetView", "Failed to launch clock app", e)
        }
    }
}
