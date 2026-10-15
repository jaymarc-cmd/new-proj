package com.likeflow.app.detect

import kotlin.math.abs
import kotlin.math.max

/**
 * Turns a stream of playback-progress samples (0f..1f) into a "this video is finished" decision.
 *
 * The tracker never acts on a single reading. A signal must first show steady forward movement
 * over [Config.samplesToTrust] samples before an end-of-video or loop transition counts.
 * Anything ambiguous (a backwards jump that isn't a loop, a large forward jump, a moved
 * progress bar, a signal that disappears) drops trust, so the caller waits instead of swiping.
 */
class CompletionTracker(private val config: Config = Config()) {

    data class Config(
        /** Progress at or above this is treated as "reached the end". */
        val endThreshold: Float = 0.985f,
        /** A loop is a jump from at least this far into the video... */
        val loopFromAtLeast: Float = 0.80f,
        /** ...back to at most this far into the video. */
        val loopToAtMost: Float = 0.20f,
        /** Forward-moving samples needed before the signal is trusted. */
        val samplesToTrust: Int = 3,
        /** Small backwards wobble that is treated as measurement noise. */
        val backwardTolerance: Float = 0.02f,
        /** A forward jump larger than this is a seek or a different video, not playback. */
        val maxForwardStep: Float = 0.45f,
        /** If the bar's on-screen position moves by more than this, it's a different bar. */
        val anchorTolerancePx: Int = 6,
        /** Forget the signal if no sample arrives for this long. */
        val signalTimeoutMs: Long = 3_000,
    )

    enum class Reason { REACHED_END, LOOPED }

    sealed interface Verdict {
        /** Nothing to go on. The caller must wait. */
        data object NoSignal : Verdict

        /** A signal exists but hasn't earned trust yet. */
        data class Learning(val fraction: Float, val forwardSamples: Int) : Verdict

        /** Trusted signal, video still playing. */
        data class Playing(val fraction: Float) : Verdict

        /** Trusted signal says the video finished. Fired once; the tracker re-learns afterwards. */
        data class Completed(val reason: Reason, val from: Float, val to: Float) : Verdict
    }

    private var last: Float? = null
    private var lastAnchor: Int? = null
    private var lastSampleAt = 0L
    private var forwardSamples = 0

    val isTrusted: Boolean get() = forwardSamples >= config.samplesToTrust
    val hasSignal: Boolean get() = last != null

    fun reset() {
        last = null
        lastAnchor = null
        lastSampleAt = 0L
        forwardSamples = 0
    }

    /**
     * @param fraction current playback progress, or null if no reading was available this tick.
     * @param anchor optional on-screen position of the progress source (e.g. pixel row of a bar).
     */
    fun onSample(fraction: Float?, nowMs: Long, anchor: Int? = null): Verdict {
        if (fraction == null || fraction.isNaN()) {
            val prev = last ?: return Verdict.NoSignal
            if (nowMs - lastSampleAt > config.signalTimeoutMs) {
                reset()
                return Verdict.NoSignal
            }
            return current(prev)
        }

        val f = fraction.coerceIn(0f, 1f)
        val prev = last
        if (prev == null || anchorMoved(anchor)) return start(f, nowMs, anchor)

        val delta = f - prev
        return when {
            delta > config.maxForwardStep -> start(f, nowMs, anchor)

            delta >= -config.backwardTolerance -> {
                if (delta > MIN_STEP) forwardSamples++
                last = max(prev, f)
                lastAnchor = anchor ?: lastAnchor
                lastSampleAt = nowMs
                if (isTrusted && f >= config.endThreshold) {
                    complete(Reason.REACHED_END, prev, f, nowMs, anchor)
                } else {
                    current(last!!)
                }
            }

            isTrusted && prev >= config.loopFromAtLeast && f <= config.loopToAtMost ->
                complete(Reason.LOOPED, prev, f, nowMs, anchor)

            // Jumped backwards without looking like a loop: the user scrubbed or changed video.
            else -> start(f, nowMs, anchor)
        }
    }

    private fun anchorMoved(anchor: Int?): Boolean {
        val previous = lastAnchor ?: return false
        return anchor != null && abs(anchor - previous) > config.anchorTolerancePx
    }

    private fun start(f: Float, nowMs: Long, anchor: Int?): Verdict {
        last = f
        lastAnchor = anchor
        lastSampleAt = nowMs
        forwardSamples = 0
        return Verdict.Learning(f, forwardSamples)
    }

    private fun complete(reason: Reason, from: Float, to: Float, nowMs: Long, anchor: Int?): Verdict {
        // Start over untrusted so a failed swipe can't make us fire again on the very next sample.
        start(to, nowMs, anchor)
        return Verdict.Completed(reason, from, to)
    }

    private fun current(f: Float): Verdict =
        if (isTrusted) Verdict.Playing(f) else Verdict.Learning(f, forwardSamples)

    private companion object {
        const val MIN_STEP = 0.001f
    }
}
