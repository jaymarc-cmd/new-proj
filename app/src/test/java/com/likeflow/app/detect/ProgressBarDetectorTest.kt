package com.likeflow.app.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ProgressBarDetectorTest {

    private val width = 1080
    private val height = 300
    private val barRow = 240
    private val detector = ProgressBarDetector()

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun grey(l: Int) = rgb(l, l, l)

    /** A noisy, dark-ish "video" with optional TikTok-style bar drawn on top. */
    private fun frame(fraction: Float?, seed: Int = 1, barTop: Int = barRow - 1, thickness: Int = 3): IntArray {
        val random = Random(seed)
        val px = IntArray(width * height) {
            rgb(30 + random.nextInt(60), 20 + random.nextInt(50), 40 + random.nextInt(70))
        }
        if (fraction != null) {
            val filled = (width * fraction).toInt()
            for (y in barTop until barTop + thickness) {
                for (x in 0 until width) {
                    val i = y * width + x
                    // Filled: near-white. Track: video blended with 25% white.
                    px[i] = if (x < filled) grey(235 + random.nextInt(15)) else blendWhite(px[i], 0.25f)
                }
            }
        }
        return px
    }

    private fun blendWhite(p: Int, alpha: Float): Int {
        fun ch(c: Int) = (c * (1 - alpha) + 255 * alpha).toInt()
        return rgb(ch((p shr 16) and 0xFF), ch((p shr 8) and 0xFF), ch(p and 0xFF))
    }

    @Test
    fun `finds a bar and measures its fill`() {
        for (fraction in listOf(0.05f, 0.33f, 0.5f, 0.9f)) {
            val best = detector.detect(frame(fraction), width, height).firstOrNull()
            assertNotNull("no bar at $fraction", best)
            assertEquals(fraction, best!!.fraction, 0.01f)
            assertEquals(barRow, best.row)
        }
    }

    @Test
    fun `plain video has no bar`() {
        assertTrue(detector.detect(frame(null), width, height).isEmpty())
    }

    @Test
    fun `thick white band is not a bar`() {
        val px = frame(fraction = 0.4f, barTop = 100, thickness = 60)
        assertTrue(detector.detect(px, width, height).none { it.row in 100..160 })
    }

    @Test
    fun `bright run that doesn't start at the left edge is ignored`() {
        val px = frame(null)
        for (y in barRow - 1..barRow + 1) for (x in 300 until 600) px[y * width + x] = grey(245)
        assertTrue(detector.detect(px, width, height).isEmpty())
    }

    @Test
    fun `lock adopts only a moving candidate`() {
        val lock = ProgressBarLock()
        val staticText = ProgressBarDetector.Candidate(fraction = 0.2f, row = 100, thickness = 3, score = 90f)
        val bar1 = ProgressBarDetector.Candidate(fraction = 0.30f, row = 240, thickness = 3, score = 40f)
        val bar2 = bar1.copy(fraction = 0.31f)

        assertNull(lock.update(listOf(staticText, bar1)))
        assertEquals(240, lock.update(listOf(staticText, bar2))?.row)
        // Once locked, it follows the bar even when it jumps back to the start (a loop).
        assertEquals(0.01f, lock.update(listOf(staticText, bar1.copy(fraction = 0.01f)))!!.fraction, 0f)
    }

    @Test
    fun `lock never adopts static ui`() {
        val lock = ProgressBarLock()
        val text = ProgressBarDetector.Candidate(fraction = 0.2f, row = 100, thickness = 3, score = 90f)
        repeat(10) { assertNull(lock.update(listOf(text))) }
    }

    @Test
    fun `detector and lock together follow playback across frames`() {
        val lock = ProgressBarLock()
        val tracker = CompletionTracker()
        var now = 0L
        val fractions = listOf(0.60f, 0.66f, 0.72f, 0.78f, 0.84f, 0.90f, 0.03f)
        val verdicts = fractions.mapIndexed { i, f ->
            now += 500
            val bar = lock.update(detector.detect(frame(f, seed = i), width, height))
            tracker.onSample(bar?.fraction, now, bar?.row)
        }
        assertEquals(CompletionTracker.Reason.LOOPED, (verdicts.last() as CompletionTracker.Verdict.Completed).reason)
    }
}
