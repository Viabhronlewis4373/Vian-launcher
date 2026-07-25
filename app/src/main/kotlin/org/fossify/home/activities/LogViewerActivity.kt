package org.fossify.home.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyTextView
import org.fossify.home.R
import org.fossify.home.databinding.ActivityLogViewerBinding
import org.fossify.home.extensions.config
import org.fossify.home.helpers.LogKeeperHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class LogViewerActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityLogViewerBinding::inflate)
    private lateinit var logKeeperHelper: LogKeeperHelper

    private var fullLogText = ""
    private var selectedRangeHours = 24 // -1 means "All"
    private val rangePillViews = LinkedHashMap<Int, MyTextView>()
    private var pendingDownloadText: String? = null

    private val entryStartPattern =
        Pattern.compile("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) \\[")
    private val entryDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        logKeeperHelper = LogKeeperHelper(applicationContext)

        setupEdgeToEdge(padBottomSystem = listOf(binding.logViewerScrollview))
        setupMaterialScrollListener(binding.logViewerScrollview, binding.logViewerAppbar)
        setupTimeRangePills()
        setupMasterSwitch()
        setupActionButtons()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.logViewerAppbar, NavigationIcon.Arrow)
        binding.logViewerMasterSwitch.isChecked = config.logKeeperEnabled
        updateDisabledHint()
        loadLogs()
    }

    private fun setupMasterSwitch() {
        binding.logViewerMasterSwitch.setOnCheckedChangeListener { _, isChecked ->
            config.logKeeperEnabled = isChecked
            updateDisabledHint()
        }
    }

    private fun updateDisabledHint() {
        val isEnabled = config.logKeeperEnabled
        binding.logViewerDisabledHint.text = getString(R.string.log_keeper_disabled_hint)
        binding.logViewerDisabledHint.beVisibleIf(!isEnabled)
    }

    private data class TimeRange(val labelRes: Int, val hours: Int)

    private fun setupTimeRangePills() {
        val ranges = listOf(
            TimeRange(R.string.time_range_1h, 1),
            TimeRange(R.string.time_range_6h, 6),
            TimeRange(R.string.time_range_12h, 12),
            TimeRange(R.string.time_range_24h, 24),
            TimeRange(R.string.time_range_48h, 48),
            TimeRange(R.string.time_range_all, -1)
        )

        val marginPx = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.normal_margin)
        val paddingHPx = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.big_margin)
        val paddingVPx = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.normal_margin)

        ranges.forEach { range ->
            val pill = MyTextView(this).apply {
                text = getString(range.labelRes)
                background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_pill)?.mutate()
                setPadding(paddingHPx, paddingVPx, paddingHPx, paddingVPx)
                gravity = Gravity.CENTER
                isAllCaps = false
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = marginPx }
                setOnClickListener {
                    selectedRangeHours = range.hours
                    refreshPillSelection()
                    renderFilteredLogs()
                }
            }
            rangePillViews[range.hours] = pill
            binding.logViewerTimeRangeHolder.addView(pill)
        }

        refreshPillSelection()
    }

    private fun refreshPillSelection() {
        val selectedColor = getProperPrimaryColor()
        val unselectedColor = getProperBackgroundColor().adjustAlpha(0.6f)

        rangePillViews.forEach { (hours, pill) ->
            val isSelected = hours == selectedRangeHours
            pill.background?.setTint(if (isSelected) selectedColor else unselectedColor)
        }
    }

    private fun setupActionButtons() {
        binding.logViewerCopy.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_pill)?.mutate()
        binding.logViewerDownload.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_pill)?.mutate()
        binding.logViewerCopy.background?.setTint(getProperPrimaryColor())
        binding.logViewerDownload.background?.setTint(getProperPrimaryColor())

        binding.logViewerCopy.setOnClickListener {
            copyVisibleLogs()
        }

        binding.logViewerDownload.setOnClickListener {
            downloadVisibleLogs()
        }
    }

    private fun loadLogs() {
        ensureBackgroundThread {
            fullLogText = logKeeperHelper.getLogs()
            runOnUiThread {
                renderFilteredLogs()
            }
        }
    }

    /**
     * Splits the raw log file into individual entries. A new entry starts at any
     * line matching the "yyyy-MM-dd HH:mm:ss.SSS [tag] ..." pattern written by
     * LogKeeperHelper; any following lines (e.g. a stack trace) are treated as
     * part of that same entry until the next timestamped line begins.
     */
    private fun parseEntries(logText: String): List<Pair<Date?, String>> {
        if (logText.isBlank()) return emptyList()

        val entries = mutableListOf<Pair<Date?, String>>()
        val currentLines = StringBuilder()
        var currentDate: Date? = null

        logText.lines().forEach { line ->
            val matcher = entryStartPattern.matcher(line)
            if (matcher.find()) {
                if (currentLines.isNotEmpty()) {
                    entries.add(currentDate to currentLines.toString().trimEnd('\n'))
                    currentLines.clear()
                }
                currentDate = try {
                    entryDateFormat.parse(matcher.group(1) ?: "")
                } catch (ignored: Exception) {
                    null
                }
            }
            currentLines.append(line).append('\n')
        }
        if (currentLines.isNotEmpty()) {
            entries.add(currentDate to currentLines.toString().trimEnd('\n'))
        }

        return entries
    }

    private fun renderFilteredLogs() {
        val entries = parseEntries(fullLogText)
        val filtered = if (selectedRangeHours < 0) {
            entries
        } else {
            val cutoff = Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, -selectedRangeHours)
            }.time
            entries.filter { (date, _) -> date == null || !date.before(cutoff) }
        }

        val displayText = filtered.joinToString("\n") { it.second }
        val isEmpty = displayText.isBlank()
        binding.logViewerPlaceholder.beVisibleIf(isEmpty)
        binding.logViewerText.beVisibleIf(!isEmpty)
        binding.logViewerText.text = displayText
    }

    private fun getCurrentlyVisibleLogText(): String = binding.logViewerText.text?.toString().orEmpty()

    private fun copyVisibleLogs() {
        val text = getCurrentlyVisibleLogText()
        if (text.isBlank()) {
            toast(R.string.no_logs_yet)
            return
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Vian Launcher log", text))
        toast(R.string.logs_copied)
    }

    private fun downloadVisibleLogs() {
        val text = getCurrentlyVisibleLogText()
        if (text.isBlank()) {
            toast(R.string.no_logs_yet)
            return
        }

        val fileName = "vian_log_${System.currentTimeMillis()}.txt"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }

        pendingDownloadText = text
        startActivityForResult(intent, CREATE_LOG_FILE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == CREATE_LOG_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
            val uri: Uri? = resultData?.data
            val text = pendingDownloadText
            pendingDownloadText = null
            if (uri == null || text == null) {
                toast(R.string.log_save_failed)
                return
            }

            ensureBackgroundThread {
                try {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(text.toByteArray())
                    }
                    runOnUiThread {
                        toast(R.string.log_saved_to)
                    }
                } catch (e: Exception) {
                    logKeeperHelper.log("LogViewerActivity", "Failed to save log export", e)
                    runOnUiThread {
                        toast(R.string.log_save_failed)
                    }
                }
            }
        }
    }

    companion object {
        private const val CREATE_LOG_FILE_REQUEST_CODE = 91
    }
}
