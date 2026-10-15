package com.likeflow.app.detect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualLoopDetectorTest {

    /** A 64x64 synthetic frame whose content is a gradient shifted by [phase]. */
    private fun frame(phase: Int): FrameSignature {
        val source = PixelSource { x, y ->
            val l = ((x * 3 + y * 2 + phase * 37) % 256)
            (0xFF shl 24) or (l shl 16) or (l shl 8) or l
        }
        return FrameSignature.sample(source, 0, 0, 64, 64)
    }

    @Test
    fun `detects the picture snapping back to the start`() {
        val detector = VisualLoopDetector()
        var now = 0L
        val phases = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
        val results = phases.map { now += 500; detector.onFrame(frame(it), now) }
        assertFalse(results.dropLast(1).any { it })
        assertTrue(results.last())
    }

    @Test
    fun `a still picture never counts as a loop`() {
        val detector = VisualLoopDetector()
        var now = 0L
        repeat(30) { now += 500; assertFalse(detector.onFrame(frame(0), now)) }
    }

    @Test
    fun `too early to be a loop`() {
        val detector = VisualLoopDetector()
        var now = 0L
        val results = listOf(0, 3, 6, 0).map { now += 500; detector.onFrame(frame(it), now) }
        assertFalse(results.any { it })
    }
}
