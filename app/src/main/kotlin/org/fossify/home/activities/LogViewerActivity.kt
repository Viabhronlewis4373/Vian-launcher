package org.fossify.home.activities

import android.content.Intent
import android.os.Bundle
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.home.R
import org.fossify.home.databinding.ActivityLogViewerBinding
import org.fossify.home.helpers.LogKeeperHelper

class LogViewerActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityLogViewerBinding::inflate)
    private lateinit var logKeeperHelper: LogKeeperHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        logKeeperHelper = LogKeeperHelper(applicationContext)

        setupEdgeToEdge(padBottomSystem = listOf(binding.logViewerScrollview))
        setupMaterialScrollListener(binding.logViewerScrollview, binding.logViewerAppbar)
        setupOptionsMenu()
        loadLogs()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.logViewerAppbar, NavigationIcon.Arrow)
        loadLogs()
    }

    private fun setupOptionsMenu() {
        binding.logViewerToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.share_logs -> shareLogs()
                R.id.clear_logs -> clearLogs()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun loadLogs() {
        ensureBackgroundThread {
            val logs = logKeeperHelper.getLogs()
            runOnUiThread {
                val isEmpty = logs.isBlank()
                binding.logViewerPlaceholder.beVisibleIf(isEmpty)
                binding.logViewerText.beVisibleIf(!isEmpty)
                binding.logViewerText.text = logs
            }
        }
    }

    private fun shareLogs() {
        val logs = logKeeperHelper.getLogs()
        if (logs.isBlank()) {
            toast(R.string.no_logs_yet)
            return
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, logs)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_logs)))
    }

    private fun clearLogs() {
        logKeeperHelper.clearLogs()
        loadLogs()
        toast(R.string.logs_cleared)
    }
}
