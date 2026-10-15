package com.likeflow.app.detect

import com.likeflow.app.detect.CompletionTracker.Reason
import com.likeflow.app.detect.CompletionTracker.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionTrackerTest {

    private val tracker = CompletionTracker()
    private var now = 0L

    private fun feed(vararg fractions: Float?): List<Verdict> = fractions.map {
        now += 500
        tracker.onSample(it, now)
    }

    @Test
    fun `no readings means no signal`() {
        assertEquals(Verdict.NoSignal, feed(null).single())
    }

    @Test
    fun `detects a loop after steady progress`() {
        val verdicts = feed(0.50f, 0.60f, 0.70f, 0.80f, 0.90f, 0.05f)
        assertTrue(verdicts[4] is Verdict.Playing)
        assertEquals(Reason.LOOPED, (verdicts.last() as Verdict.Completed).reason)
    }

    @Test
    fun `detects reaching the end`() {
        val last = feed(0.90f, 0.93f, 0.96f, 0.98f, 0.99f).last()
        assertEquals(Reason.REACHED_END, (last as Verdict.Completed).reason)
    }

    @Test
    fun `does not trust a signal it has not seen move`() {
        // Only two forward steps before the wrap: not enough to be confident.
        val verdicts = feed(0.85f, 0.90f, 0.95f, 0.02f)
        assertTrue(verdicts.none { it is Verdict.Completed })
    }

    @Test
    fun `a stalled reading never becomes trusted`() {
        val verdicts = feed(0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.02f)
        assertTrue(verdicts.none { it is Verdict.Completed })
    }

    @Test
    fun `jumping back to the middle is a new video, not a loop`() {
        val verdicts = feed(0.50f, 0.60f, 0.70f, 0.85f, 0.40f)
        assertTrue(verdicts.last() is Verdict.Learning)
    }

    @Test
    fun `large forward jump drops trust`() {
        val verdicts = feed(0.10f, 0.20f, 0.30f, 0.40f, 0.99f)
        assertTrue(verdicts.last() is Verdict.Learning)
    }

    @Test
    fun `fires once then has to relearn`() {
        val verdicts = feed(0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 0.05f, 0.10f, 0.15f, 0.9f)
        assertEquals(1, verdicts.count { it is Verdict.Completed })
    }

    @Test
    fun `small backwards jitter is tolerated`() {
        val verdicts = feed(0.50f, 0.60f, 0.59f, 0.70f, 0.80f, 0.90f, 0.03f)
        assertTrue(verdicts.last() is Verdict.Completed)
    }

    @Test
    fun `progress bar moving to a different row resets trust`() {
        listOf(0.5f, 0.6f, 0.7f, 0.85f).forEach { now += 500; tracker.onSample(it, now, anchor = 2000) }
        now += 500
        val verdict = tracker.onSample(0.05f, now, anchor = 1500)
        assertTrue(verdict is Verdict.Learning)
    }

    @Test
    fun `signal times out after a gap`() {
        feed(0.5f, 0.6f, 0.7f, 0.8f)
        now += 10_000
        assertEquals(Verdict.NoSignal, tracker.onSample(null, now))
        assertTrue(!tracker.hasSignal)
    }

    @Test
    fun `brief missing readings keep the signal`() {
        val verdicts = feed(0.5f, 0.6f, 0.7f, 0.8f, null, 0.9f, 0.04f)
        assertTrue(verdicts[4] is Verdict.Playing)
        assertTrue(verdicts.last() is Verdict.Completed)
    }
}
