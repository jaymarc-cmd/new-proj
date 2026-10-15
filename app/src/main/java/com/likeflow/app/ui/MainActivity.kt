package com.likeflow.app.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.likeflow.app.R
import com.likeflow.app.core.LikeFlowSettings
import com.likeflow.app.core.LikeFlowStatus
import com.likeflow.app.core.LikeFlowStatus.Phase
import com.likeflow.app.core.TikTok
import com.likeflow.app.service.LikeFlowAccessibilityService

class MainActivity : Activity() {

    private lateinit var settings: LikeFlowSettings

    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var statusProgress: ProgressBar
    private lateinit var enableService: Button
    private lateinit var restrictedHint: TextView
    private lateinit var openAppInfo: Button
    private lateinit var autoScroll: Switch
    private lateinit var dryRun: Switch
    private lateinit var screenshots: Switch
    private lateinit var visualLoop: Switch
    private lateinit var diagnosticsCounts: TextView
    private lateinit var diagnosticsLog: TextView
    private lateinit var diagnosticsDetail: TextView
    private lateinit var previewLabel: TextView
    private lateinit var preview: ImageView

    private val statusListener: (LikeFlowStatus.Snapshot) -> Unit = { render(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = LikeFlowSettings(this)

        statusTitle = view(R.id.status_title)
        statusDetail = view(R.id.status_detail)
        statusProgress = view(R.id.status_progress)
        enableService = view(R.id.enable_service)
        restrictedHint = view(R.id.restricted_hint)
        openAppInfo = view(R.id.open_app_info)
        autoScroll = view(R.id.auto_scroll_switch)
        dryRun = view(R.id.dry_run_switch)
        screenshots = view(R.id.screenshot_switch)
        visualLoop = view(R.id.visual_loop_switch)
        diagnosticsCounts = view(R.id.diagnostics_counts)
        diagnosticsLog = view(R.id.diagnostics_log)
        diagnosticsDetail = view(R.id.diagnostics_detail)
        previewLabel = view(R.id.diagnostics_preview_label)
        preview = view(R.id.diagnostics_preview)

        applySystemBarInsets(view(R.id.scroll))

        enableService.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        openAppInfo.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
        }
        view<Button>(R.id.open_tiktok).setOnClickListener { openTikTok() }

        autoScroll.setOnCheckedChangeListener { _, checked -> settings.autoScrollEnabled = checked }
        dryRun.setOnCheckedChangeListener { _, checked -> settings.dryRun = checked }
        screenshots.setOnCheckedChangeListener { _, checked -> settings.screenshotDetection = checked }
        visualLoop.setOnCheckedChangeListener { _, checked -> settings.visualLoopDetection = checked }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            screenshots.isEnabled = false
            visualLoop.isEnabled = false
            view<TextView>(R.id.screenshot_hint).setText(R.string.screenshot_unsupported)
        }
    }

    override fun onResume() {
        super.onResume()
        autoScroll.isChecked = settings.autoScrollEnabled
        dryRun.isChecked = settings.dryRun
        screenshots.isChecked = settings.screenshotDetection
        visualLoop.isChecked = settings.visualLoopDetection
        LikeFlowStatus.addListener(statusListener)
    }

    override fun onPause() {
        LikeFlowStatus.removeListener(statusListener)
        super.onPause()
    }

    private fun render(status: LikeFlowStatus.Snapshot) {
        val serviceOn = isServiceEnabled()
        enableService.visibility = if (serviceOn) View.GONE else View.VISIBLE
        // Android 13+ blocks accessibility for sideloaded apps until "restricted settings" are allowed.
        val showRestricted = !serviceOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        restrictedHint.visibility = if (showRestricted) View.VISIBLE else View.GONE
        openAppInfo.visibility = restrictedHint.visibility
        autoScroll.isEnabled = serviceOn

        // The service may be enabled in settings but not yet connected, or vice versa for a moment.
        val phase = if (!serviceOn) Phase.SERVICE_OFF else status.phase
        val source = status.source?.label ?: ""
        val (title, detail) = when (phase) {
            Phase.SERVICE_OFF ->
                getString(R.string.status_service_off_title) to getString(R.string.status_service_off_detail)
            Phase.PAUSED ->
                getString(R.string.status_paused_title) to getString(R.string.status_paused_detail)
            Phase.WAITING_FOR_TIKTOK ->
                getString(R.string.status_waiting_title) to getString(R.string.status_waiting_detail)
            Phase.NO_SIGNAL ->
                getString(R.string.status_no_signal_title) to getString(R.string.status_no_signal_detail)
            Phase.LEARNING ->
                getString(R.string.status_learning_title, status.forwardSamples.coerceAtMost(3)) to
                    getString(R.string.status_learning_detail, source)
            Phase.WATCHING ->
                getString(R.string.status_watching_title, ((status.progress ?: 0f) * 100).toInt()) to
                    getString(
                        if (status.dryRun) R.string.status_watching_detail_dry else R.string.status_watching_detail,
                        source,
                    )
            Phase.COOLDOWN ->
                getString(R.string.status_cooldown_title) to getString(R.string.status_cooldown_detail)
        }
        val active = phase != Phase.SERVICE_OFF && phase != Phase.PAUSED
        statusTitle.text = if (status.dryRun && active) {
            "$title · ${getString(R.string.status_dry_run_suffix)}"
        } else {
            title
        }
        statusDetail.text = detail

        val progress = status.progress
        if (progress != null && (phase == Phase.WATCHING || phase == Phase.LEARNING)) {
            statusProgress.visibility = View.VISIBLE
            statusProgress.progress = (progress * statusProgress.max).toInt()
        } else {
            statusProgress.visibility = View.GONE
        }

        diagnosticsCounts.text = getString(R.string.diagnostics_counts, status.completionsDetected, status.swipes)
        diagnosticsDetail.text = status.debug
        diagnosticsDetail.visibility = if (serviceOn && status.debug.isNotEmpty()) View.VISIBLE else View.GONE
        val image = status.preview
        preview.setImageBitmap(image)
        preview.visibility = if (serviceOn && image != null) View.VISIBLE else View.GONE
        previewLabel.visibility = preview.visibility
        diagnosticsLog.text = if (status.log.isEmpty()) {
            getString(R.string.diagnostics_empty)
        } else {
            status.log.joinToString("\n")
        }
    }

    private fun <T : View> view(id: Int): T = checkNotNull(findViewById<T>(id))

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val ours = ComponentName(this, LikeFlowAccessibilityService::class.java)
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
        return splitter.any { ComponentName.unflattenFromString(it) == ours }
    }

    private fun openTikTok() {
        val launch = TikTok.PACKAGES.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (launch == null) {
            Toast.makeText(this, R.string.tiktok_not_installed, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(launch)
    }

    /** Android 15 draws apps edge to edge; keep content clear of the status and navigation bars. */
    private fun applySystemBarInsets(scroll: ScrollView) {
        val base = scroll.paddingTop to scroll.paddingBottom
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val (top, bottom) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                bars.top to bars.bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetTop to insets.systemWindowInsetBottom
            }
            view.setPadding(view.paddingLeft, base.first + top, view.paddingRight, base.second + bottom)
            insets
        }
    }
}
