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
 * drag-drop system — it's a fixed overlay, not a grid item.
 * Trigger build: 2026-09-12
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
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(tickReceiver, filter)
        } catch (e: Exception) {
            logKeeperHelper.log("ClockWidgetView", "Failed to register time tick receiver", e)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            context.unregisterReceiver(tickReceiver)
        } catch (ignored: Exception) {
        }
    }

    private fun updateTime() {
        val now = Calendar.getInstance().time
        binding.clockWidgetTime.text = timeFormat.format(now)
        binding.clockWidgetAmpm.text = amPmFormat.format(now)
        binding.clockWidgetDate.text = dateFormat.format(now)
    }

    private fun launchClockApp() {
        val attempts = listOf(
            Intent(AlarmClock.ACTION_SHOW_ALARMS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(AlarmClock.ACTION_SET_ALARM)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            context.packageManager.getLaunchIntentForPackage("com.android.deskclock")
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            context.packageManager.getLaunchIntentForPackage("com.miui.clock")
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        for (intent in attempts) {
            if (intent == null) continue
            try {
                context.startActivity(intent)
                return
            } catch (ignored: Exception) {
            }
        }

        logKeeperHelper.log("ClockWidgetView", "All clock launch attempts failed", null)
    }
}
