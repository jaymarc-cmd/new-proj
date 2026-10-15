package com.likeflow.app.core

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Live state published by the accessibility service and shown on the app screen.
 * Both run in the same process, so an in-memory holder is enough.
 */
object LikeFlowStatus {

    enum class Phase {
        /** The accessibility service isn't running. */
        SERVICE_OFF,

        /** Service running, auto-scroll switched off. */
        PAUSED,

        /** Auto-scroll on, but TikTok isn't the foreground app. */
        WAITING_FOR_TIKTOK,

        /** In TikTok, but no playback progress can be read. Waiting, not swiping. */
        NO_SIGNAL,

        /** A progress signal was found and is being checked for consistency. */
        LEARNING,

        /** Trusted progress signal; waiting for the video to finish. */
        WATCHING,

        /** Just swiped (or would have, in dry run); letting the next video settle. */
        COOLDOWN,
    }

    enum class Source(val label: String) {
        ACCESSIBILITY("TikTok accessibility data"),
        SCREEN_BAR("on-screen progress bar"),
        VISUAL_LOOP("visual loop check"),
    }

    data class Snapshot(
        val phase: Phase = Phase.SERVICE_OFF,
        val source: Source? = null,
        val progress: Float? = null,
        val forwardSamples: Int = 0,
        val swipes: Int = 0,
        val completionsDetected: Int = 0,
        val dryRun: Boolean = false,
        val log: List<String> = emptyList(),
        /** Technical detail on what each detection method currently sees. */
        val debug: String = "",
        /** Latest analysed screen strip with bar candidates marked. */
        val preview: Bitmap? = null,
    )

    @Volatile
    var snapshot: Snapshot = Snapshot()
        private set

    private val listeners = CopyOnWriteArraySet<(Snapshot) -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun addListener(listener: (Snapshot) -> Unit) {
        listeners += listener
        listener(snapshot)
    }

    fun removeListener(listener: (Snapshot) -> Unit) {
        listeners -= listener
    }

    /** Must be called on the main thread. */
    fun update(transform: (Snapshot) -> Snapshot) {
        val next = transform(snapshot)
        if (next == snapshot) return
        snapshot = next
        main.post { listeners.forEach { it(next) } }
    }

    /** Must be called on the main thread. */
    fun log(message: String) = update {
        val line = "${timeFormat.format(Date())}  $message"
        it.copy(log = (listOf(line) + it.log).take(MAX_LOG_LINES))
    }

    private const val MAX_LOG_LINES = 30
}
