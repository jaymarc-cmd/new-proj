package com.likeflow.app.service

import android.accessibilityservice.AccessibilityService
import android.annotation.TargetApi
import android.accessibilityservice.GestureDescription
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.TypedValue
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.likeflow.app.core.LikeFlowSettings
import com.likeflow.app.core.LikeFlowStatus
import com.likeflow.app.core.LikeFlowStatus.Phase
import com.likeflow.app.core.LikeFlowStatus.Source
import com.likeflow.app.core.TikTok
import com.likeflow.app.detect.CompletionTracker
import com.likeflow.app.detect.FrameSignature
import com.likeflow.app.detect.ProgressBarDetector
import com.likeflow.app.detect.ProgressBarLock
import com.likeflow.app.detect.VisualLoopDetector
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Watches TikTok while it is in the foreground and auto-scroll is on, and swipes up once when
 * the current video has finished. When completion can't be detected confidently it waits.
 *
 * Each tick (about twice a second) it tries, in order:
 *  1. TikTok's accessibility tree: a progress/seek bar with range info, or a time label.
 *  2. A screenshot (Android 11+): TikTok's thin on-screen progress bar.
 *  3. Optionally, an experimental visual loop check on the same screenshot.
 */
class LikeFlowAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: LikeFlowSettings
    private lateinit var screenshotExecutor: ExecutorService
    private lateinit var barDetector: ProgressBarDetector
    private lateinit var powerManager: PowerManager

    private val tracker = CompletionTracker()
    private val barLock = ProgressBarLock()
    private val loopDetector = VisualLoopDetector()

    private var tickScheduled = false
    private var screenshotInFlight = false
    private var cooldownUntil = 0L
    private var loopWarmupUntil = 0L
    private var lastIdentity: String? = null
    private var currentSource: Source? = null
    private var lastTikTokEventAt = 0L
    private var screenshotsTaken = 0

    /** Reused between screenshots. Only touched on [screenshotExecutor]. */
    private var stripBuffer = IntArray(0)

    private val tick = Runnable {
        tickScheduled = false
        runTick()
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            LikeFlowSettings.KEY_AUTO_SCROLL -> {
                resetVideoState()
                LikeFlowStatus.log(if (settings.autoScrollEnabled) "Auto-scroll on" else "Auto-scroll off")
            }
            LikeFlowSettings.KEY_DRY_RUN -> {
                LikeFlowStatus.update { it.copy(dryRun = settings.dryRun) }
                LikeFlowStatus.log(if (settings.dryRun) "Dry run on: detect only" else "Dry run off: swiping enabled")
            }
        }
        rescheduleNow()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = LikeFlowSettings(this)
        powerManager = checkNotNull(getSystemService(PowerManager::class.java))
        screenshotExecutor = Executors.newSingleThreadExecutor()
        barDetector = ProgressBarDetector(
            ProgressBarDetector.Config(
                neighbourOffsetPx = dp(4f),
                maxThicknessPx = dp(6f),
                minRunPx = dp(2f),
            ),
        )
        settings.registerListener(prefListener)
        LikeFlowStatus.update { it.copy(phase = Phase.PAUSED, dryRun = settings.dryRun) }
        LikeFlowStatus.log("Accessibility service connected")
        rescheduleNow()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // The service config already limits events to TikTok; this is a second guard.
        if (!TikTok.isTikTok(event.packageName)) return
        lastTikTokEventAt = SystemClock.uptimeMillis()
        if (!settings.autoScrollEnabled) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && isFeedScroll(event)) {
            if (SystemClock.uptimeMillis() >= cooldownUntil) {
                LikeFlowStatus.log("Feed moved by hand, starting over on this video")
            }
            resetVideoState()
        }
        if (!tickScheduled && !screenshotInFlight) schedule(0)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::settings.isInitialized) settings.unregisterListener(prefListener)
        if (::screenshotExecutor.isInitialized) screenshotExecutor.shutdownNow()
        LikeFlowStatus.update { it.copy(phase = Phase.SERVICE_OFF, source = null, progress = null) }
        super.onDestroy()
    }

    // ---- Main loop ------------------------------------------------------------------------

    private fun runTick() {
        if (!settings.autoScrollEnabled) {
            publish(Phase.PAUSED)
            return // Restarted by the preference listener.
        }

        val now = SystemClock.uptimeMillis()
        val root = if (powerManager.isInteractive) activeRoot() else null
        // Some phones don't hand TikTok's window to the service. Recent TikTok events still prove
        // it is on screen; another app's window in front proves it isn't.
        val inTikTok = if (root != null) {
            TikTok.isTikTok(root.packageName)
        } else {
            powerManager.isInteractive && now - lastTikTokEventAt < RECENT_EVENT_MS
        }
        if (!inTikTok) {
            nodeDebug = if (root == null) "No active window readable" else "Foreground app: ${root.packageName}"
            root?.recycleCompat()
            resetVideoState()
            lastIdentity = null
            publish(Phase.WAITING_FOR_TIKTOK)
            debug("")
            // TikTok events restart the loop sooner; this slow poll covers e.g. the notification
            // shade closing over a video that fires no events.
            schedule(AWAY_POLL_MS)
            return
        }

        if (now < cooldownUntil) {
            root?.recycleCompat()
            publish(Phase.COOLDOWN)
            schedule(cooldownUntil - now)
            return
        }

        val metrics = resources.displayMetrics
        val scan = if (root != null) {
            NodeScanner.scan(root, metrics.widthPixels, metrics.heightPixels).also { root.recycleCompat() }
        } else {
            NodeScanner.Result(progress = null, identity = null)
        }
        nodeDebug = when {
            root == null -> "TikTok window not readable; using screenshots only"
            scan.progress != null -> "TikTok exposes progress: ${pct(scan.progress)}"
            else -> "TikTok exposes no progress to accessibility"
        }

        val previousIdentity = lastIdentity
        if (scan.identity != null) {
            if (previousIdentity != null && scan.identity != previousIdentity) {
                resetVideoState()
            }
            lastIdentity = scan.identity
        }

        if (scan.progress != null) {
            handleProgress(scan.progress, Source.ACCESSIBILITY, anchor = null, now = now)
            schedule(TICK_MS)
        } else if (settings.screenshotDetection && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestScreenshot() // Schedules the next tick when it returns.
        } else {
            debug(
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) "Screen reading needs Android 11+"
                else "Screen reading is switched off",
            )
            handleProgress(null, null, anchor = null, now = now)
            schedule(TICK_MS)
        }
    }

    private fun handleProgress(fraction: Float?, source: Source?, anchor: Int?, now: Long) {
        if (fraction != null) currentSource = source
        when (val verdict = tracker.onSample(fraction, now, anchor)) {
            CompletionTracker.Verdict.NoSignal -> {
                currentSource = null
                publish(Phase.NO_SIGNAL)
            }
            is CompletionTracker.Verdict.Learning ->
                publish(Phase.LEARNING, currentSource, verdict.fraction, verdict.forwardSamples)
            is CompletionTracker.Verdict.Playing ->
                publish(Phase.WATCHING, currentSource, verdict.fraction)
            is CompletionTracker.Verdict.Completed -> {
                val what = if (verdict.reason == CompletionTracker.Reason.LOOPED) {
                    "Video looped (${pct(verdict.from)} → ${pct(verdict.to)})"
                } else {
                    "Video reached the end (${pct(verdict.to)})"
                }
                onVideoFinished("$what via ${currentSource?.label}")
            }
        }
    }

    private fun onVideoFinished(why: String) {
        LikeFlowStatus.update { it.copy(completionsDetected = it.completionsDetected + 1) }
        resetVideoState()
        if (settings.dryRun) {
            LikeFlowStatus.log("$why. Dry run: would swipe now")
            startCooldown(DRY_RUN_COOLDOWN_MS)
            return
        }
        LikeFlowStatus.log("$why. Swiping to next video")
        startCooldown(SWIPE_COOLDOWN_MS)
        swipeUp()
    }

    // ---- Screenshot analysis (Android 11+) ------------------------------------------------

    @TargetApi(Build.VERSION_CODES.R)
    private fun requestScreenshot() {
        screenshotInFlight = true
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            screenshotExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val analysis = try {
                        val hardware = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        val bitmap = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                        hardware?.recycle()
                        bitmap?.let { analyse(it).also { _ -> it.recycle() } }
                    } catch (e: RuntimeException) {
                        null
                    } finally {
                        result.hardwareBuffer.close()
                    }
                    handler.post { onScreenAnalysed(analysis) }
                }

                override fun onFailure(errorCode: Int) {
                    handler.post { onScreenAnalysed(null) }
                }
            },
        )
    }

    private class ScreenAnalysis(
        val bars: List<ProgressBarDetector.Candidate>,
        val frame: FrameSignature?,
        val preview: Bitmap?,
    )

    /** Runs on [screenshotExecutor]. */
    private fun analyse(bitmap: Bitmap): ScreenAnalysis {
        val w = bitmap.width
        val h = bitmap.height
        // TikTok's progress bar sits at the bottom of the video, above the comment bar.
        val top = (h * 0.60f).toInt()
        val stripHeight = (h * 0.99f).toInt() - top
        if (stripBuffer.size < w * stripHeight) stripBuffer = IntArray(w * stripHeight)
        bitmap.getPixels(stripBuffer, 0, w, 0, top, w, stripHeight)
        val bars = barDetector.detect(stripBuffer, w, stripHeight).map { it.copy(row = it.row + top) }
        val preview = if (screenshotsTaken % PREVIEW_EVERY == 0) preview(bitmap, top, stripHeight, bars) else null

        // The middle of the video, away from TikTok's buttons and captions.
        val frame = if (settings.visualLoopDetection) {
            FrameSignature.sample(
                { x, y -> bitmap.getPixel(x, y) },
                left = (w * 0.08f).toInt(),
                top = (h * 0.15f).toInt(),
                right = (w * 0.78f).toInt(),
                bottom = (h * 0.62f).toInt(),
            )
        } else {
            null
        }
        return ScreenAnalysis(bars, frame, preview)
    }

    /** A small copy of the analysed strip with every bar candidate marked, for the app screen. */
    private fun preview(bitmap: Bitmap, top: Int, stripHeight: Int, bars: List<ProgressBarDetector.Candidate>): Bitmap {
        val scale = PREVIEW_WIDTH.toFloat() / bitmap.width
        val strip = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, stripHeight)
        val out = Bitmap.createScaledBitmap(strip, PREVIEW_WIDTH, (stripHeight * scale).toInt().coerceAtLeast(1), true)
            .copy(Bitmap.Config.ARGB_8888, true)
        strip.recycle()
        val canvas = Canvas(out)
        val paint = Paint().apply { color = 0xFFFF2050.toInt(); strokeWidth = 2f }
        for (bar in bars) {
            val y = (bar.row - top) * scale
            canvas.drawLine(0f, y, bar.fraction * out.width, y, paint)
        }
        return out
    }

    private fun onScreenAnalysed(analysis: ScreenAnalysis?) {
        screenshotInFlight = false
        screenshotsTaken++
        analysis?.preview?.let { preview -> LikeFlowStatus.update { it.copy(preview = preview) } }
        if (!settings.autoScrollEnabled) {
            schedule(0) // Publishes the paused state.
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now < cooldownUntil) {
            schedule(cooldownUntil - now)
            return
        }

        val bar = analysis?.let { barLock.update(it.bars) }
        debug(
            when {
                analysis == null -> "Screenshot failed"
                bar != null -> "Progress bar locked at ${pct(bar.fraction)}"
                analysis.bars.isEmpty() -> "No progress bar visible on screen"
                else -> "${analysis.bars.size} bar-like line(s) seen, waiting to see one move " +
                    "(best ${pct(analysis.bars.first().fraction)})"
            },
        )
        if (bar != null || tracker.hasSignal) {
            handleProgress(bar?.fraction, Source.SCREEN_BAR, bar?.row, now)
        } else if (analysis?.frame != null && now >= loopWarmupUntil) {
            if (loopDetector.onFrame(analysis.frame, now)) {
                currentSource = Source.VISUAL_LOOP
                onVideoFinished("Picture returned to the start of the video via ${Source.VISUAL_LOOP.label}")
            } else {
                publish(Phase.NO_SIGNAL, if (loopDetector.isArmed) Source.VISUAL_LOOP else null)
            }
        } else {
            handleProgress(null, null, null, now)
        }
        schedule(TICK_MS)
    }

    // ---- Gestures ---------------------------------------------------------------------------

    private fun swipeUp() {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * 0.5f
        val path = Path().apply {
            moveTo(x, metrics.heightPixels * 0.70f)
            lineTo(x, metrics.heightPixels * 0.25f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, SWIPE_DURATION_MS))
            .build()
        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    LikeFlowStatus.update { it.copy(swipes = it.swipes + 1) }
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    LikeFlowStatus.log("Swipe was cancelled by the system")
                }
            },
            handler,
        )
        if (!dispatched) LikeFlowStatus.log("Android refused the swipe gesture")
    }

    // ---- Helpers ----------------------------------------------------------------------------

    private var nodeDebug = ""

    private fun debug(screen: String) {
        val text = listOf(nodeDebug, screen, "Screenshots analysed: $screenshotsTaken")
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        LikeFlowStatus.update { it.copy(debug = text) }
    }

    /** The focused window's content, also looking through the window list for phones where
     *  rootInActiveWindow comes back empty. */
    private fun activeRoot(): AccessibilityNodeInfo? =
        rootInActiveWindow ?: try {
            windows.firstOrNull { it.isActive }?.root
        } catch (e: RuntimeException) {
            null
        }

    private fun resetVideoState() {
        tracker.reset()
        barLock.reset()
        loopDetector.reset()
        currentSource = null
        loopWarmupUntil = maxOf(loopWarmupUntil, SystemClock.uptimeMillis() + LOOP_WARMUP_MS)
    }

    private fun startCooldown(ms: Long) {
        cooldownUntil = SystemClock.uptimeMillis() + ms
        loopWarmupUntil = cooldownUntil + LOOP_WARMUP_MS
        lastIdentity = null
        publish(Phase.COOLDOWN)
    }

    /** True for scrolls of the full-screen video pager, not comment lists or captions. */
    private fun isFeedScroll(event: AccessibilityEvent): Boolean {
        val source = event.source ?: return false
        val bounds = Rect()
        source.getBoundsInScreen(bounds)
        source.recycleCompat()
        return bounds.height() >= resources.displayMetrics.heightPixels * 0.6f
    }

    private fun schedule(delayMs: Long) {
        if (tickScheduled) return
        tickScheduled = true
        handler.postDelayed(tick, delayMs)
    }

    private fun rescheduleNow() {
        handler.removeCallbacks(tick)
        tickScheduled = false
        if (!screenshotInFlight) schedule(0)
    }

    private fun publish(phase: Phase, source: Source? = null, progress: Float? = null, forwardSamples: Int = 0) {
        LikeFlowStatus.update {
            it.copy(phase = phase, source = source, progress = progress, forwardSamples = forwardSamples)
        }
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()

    private fun pct(f: Float) = "${(f * 100).toInt()}%"

    private companion object {
        const val TICK_MS = 500L
        const val AWAY_POLL_MS = 1_500L
        const val RECENT_EVENT_MS = 4_000L
        const val PREVIEW_EVERY = 4
        const val PREVIEW_WIDTH = 360
        const val SWIPE_DURATION_MS = 280L
        const val SWIPE_COOLDOWN_MS = 1_500L
        const val DRY_RUN_COOLDOWN_MS = 2_500L
        const val LOOP_WARMUP_MS = 900L
    }
}
