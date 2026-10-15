package com.likeflow.app.detect

import kotlin.math.abs

/** Reads one ARGB pixel. Lets the detectors run on a Bitmap in the app and an IntArray in tests. */
fun interface PixelSource {
    fun argb(x: Int, y: Int): Int
}

/** A tiny, blurred luma thumbnail of part of the screen, used to compare frames cheaply. */
class FrameSignature(private val cells: IntArray) {

    /** Mean absolute luma difference per cell, 0..255. */
    fun distanceTo(other: FrameSignature): Float {
        require(cells.size == other.cells.size) { "signature sizes differ" }
        var sum = 0L
        for (i in cells.indices) sum += abs(cells[i] - other.cells[i])
        return sum.toFloat() / cells.size
    }

    companion object {
        /**
         * Samples a [grid]x[grid] signature from the rectangle [left, top) - [right, bottom).
         * Each cell averages a 3x3 set of points, which smooths compression noise.
         */
        fun sample(
            source: PixelSource,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            grid: Int = 16,
        ): FrameSignature {
            val cells = IntArray(grid * grid)
            val cellW = (right - left).toFloat() / grid
            val cellH = (bottom - top).toFloat() / grid
            for (gy in 0 until grid) {
                for (gx in 0 until grid) {
                    var sum = 0
                    for (sy in 1..3) {
                        for (sx in 1..3) {
                            val x = (left + cellW * (gx + sx / 4f)).toInt()
                            val y = (top + cellH * (gy + sy / 4f)).toInt()
                            sum += ProgressBarDetector.luma(source.argb(x, y))
                        }
                    }
                    cells[gy * grid + gx] = sum / 9
                }
            }
            return FrameSignature(cells)
        }
    }
}

/**
 * Experimental fallback for videos that expose no progress at all.
 *
 * It remembers what the video looked like shortly after it started, waits until the picture has
 * clearly changed (so a still image can never trigger it), then reports a loop when the picture
 * suddenly snaps back to that starting frame.
 */
class VisualLoopDetector(private val config: Config = Config()) {

    data class Config(
        /** Ignore matches earlier than this after the reference frame. */
        val minLoopMs: Long = 4_000,
        /** The video must move at least this far from the reference before a loop can count. */
        val motionThreshold: Float = 18f,
        /** A frame this close to the reference counts as "back at the start". */
        val matchThreshold: Float = 7f,
    )

    private var reference: FrameSignature? = null
    private var referenceAt = 0L
    private var sawMotion = false
    private var lastDistance = 0f

    val isArmed: Boolean get() = reference != null && sawMotion

    fun reset() {
        reference = null
        sawMotion = false
        lastDistance = 0f
    }

    /** @return true when the frame looks like the video just restarted. */
    fun onFrame(frame: FrameSignature, nowMs: Long): Boolean {
        val ref = reference
        if (ref == null) {
            reference = frame
            referenceAt = nowMs
            return false
        }
        val d = frame.distanceTo(ref)
        if (d >= config.motionThreshold) sawMotion = true

        // Require a jump from "far" to "matching" so a slow fade can't sneak in.
        val looped = sawMotion &&
            nowMs - referenceAt >= config.minLoopMs &&
            d <= config.matchThreshold &&
            lastDistance >= config.matchThreshold * 2
        lastDistance = d
        if (looped) {
            referenceAt = nowMs
            sawMotion = false
        }
        return looped
    }
}
