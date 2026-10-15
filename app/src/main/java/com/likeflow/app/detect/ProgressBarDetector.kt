package com.likeflow.app.detect

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Finds thin horizontal "progress bar" shapes in a strip of screen pixels.
 *
 * TikTok draws playback progress as a thin bar near the bottom of the video: a bright (white)
 * filled part that starts at the left edge, followed by a dimmer translucent track. A row is a
 * candidate when:
 *  - a bright, low-saturation run starts close to the left edge,
 *  - that run is clearly brighter than the pixels a few rows above and below it (it is thin),
 *  - the rest of the bar's span is clearly dimmer than the run (filled vs. unfilled).
 *
 * Static UI such as caption text can also pass these checks, so this class only reports
 * candidates. [ProgressBarLock] decides which candidate is the real bar by watching it move.
 *
 * Pixels are ARGB ints in row-major order, as returned by `Bitmap.getPixels`.
 */
class ProgressBarDetector(private val config: Config = Config()) {

    data class Config(
        /** Minimum luma (0..255) for a pixel to count as part of the filled bar. */
        val brightLuma: Int = 145,
        /** Maximum max(r,g,b) - min(r,g,b) for a pixel to count as white-ish. */
        val maxChroma: Int = 60,
        /** The filled part must start within this fraction of the width from the left edge. */
        val maxStartInsetFraction: Float = 0.10f,
        /** Non-bright pixels tolerated inside the filled run (anti-aliasing, compression). */
        val maxGapPx: Int = 2,
        /** Rows this far above/below are sampled to confirm the bar is thin. */
        val neighbourOffsetPx: Int = 6,
        /** The filled run must be at least this much brighter than its neighbour rows. */
        val minThinContrast: Int = 20,
        /** The filled run must be at least this much brighter than the unfilled track. */
        val minTrackContrast: Int = 25,
        /** Maximum luma standard deviation inside the filled run. */
        val maxRunStdDev: Float = 34f,
        /** Shortest filled run worth reporting. */
        val minRunPx: Int = 6,
        /** A real bar is only a few pixels tall; taller bright bands are something else. */
        val maxThicknessPx: Int = 14,
    )

    data class Candidate(
        /** Played fraction, 0f..1f. */
        val fraction: Float,
        /** Centre row of the bar within the analysed strip. */
        val row: Int,
        /** Rows the bar spans. */
        val thickness: Int,
        /** Higher is more bar-like. Only meaningful for comparing candidates in one frame. */
        val score: Float,
    )

    private class RowHit(val y: Int, val start: Int, val end: Int, val spanEnd: Int, val contrast: Int)

    fun detect(pixels: IntArray, width: Int, height: Int): List<Candidate> {
        require(pixels.size >= width * height) { "pixel buffer too small" }
        val off = config.neighbourOffsetPx
        val hits = ArrayList<RowHit>()
        for (y in off until height - off) {
            scanRow(pixels, width, y)?.let(hits::add)
        }
        return groupRows(hits)
    }

    private fun scanRow(px: IntArray, width: Int, y: Int): RowHit? {
        val rowStart = y * width
        val maxInset = (width * config.maxStartInsetFraction).toInt()

        var x = 0
        while (x <= maxInset && !isBright(px[rowStart + x])) x++
        if (x > maxInset) return null
        val start = x

        var end = start
        var gap = 0
        var n = 0
        var sum = 0L
        var sumSq = 0L
        while (x < width) {
            val p = px[rowStart + x]
            if (isBright(p)) {
                val l = luma(p)
                end = x
                gap = 0
                n++
                sum += l
                sumSq += l.toLong() * l
            } else if (++gap > config.maxGapPx) {
                break
            }
            x++
        }
        if (end - start + 1 < config.minRunPx || n == 0) return null

        val mean = sum.toFloat() / n
        val variance = sumSq.toFloat() / n - mean * mean
        if (sqrt(max(variance, 0f)) > config.maxRunStdDev) return null

        val off = config.neighbourOffsetPx
        val above = meanLuma(px, width, y - off, start, end)
        val below = meanLuma(px, width, y + off, start, end)
        val contrast = (mean - max(above, below)).toInt()
        if (contrast < config.minThinContrast) return null

        // Bars are laid out symmetrically, so the unfilled track ends as far from the right edge
        // as the filled part starts from the left one.
        val spanEnd = width - 1 - start
        if (spanEnd <= start) return null
        val trackStart = end + config.maxGapPx + 1
        if (spanEnd - trackStart > width * 0.03f) {
            val track = meanLuma(px, width, y, trackStart, spanEnd)
            if (mean - track < config.minTrackContrast) return null
        }
        return RowHit(y, start, min(end, spanEnd), spanEnd, contrast)
    }

    private fun groupRows(hits: List<RowHit>): List<Candidate> {
        if (hits.isEmpty()) return emptyList()
        val out = ArrayList<Candidate>()
        var group = ArrayList<RowHit>()
        fun flush() {
            if (group.isEmpty() || group.size > config.maxThicknessPx) return
            val ends = group.map { it.end - it.start + 1 }.sorted()
            val spans = group.map { it.spanEnd - it.start + 1 }.sorted()
            val fraction = ends[ends.size / 2].toFloat() / spans[spans.size / 2]
            // Rows of a real bar agree on where the fill ends; text rows usually don't.
            val spread = (ends.last() - ends.first()).toFloat() / spans.last()
            val contrast = group.maxOf { it.contrast }.toFloat()
            out += Candidate(
                fraction = fraction.coerceIn(0f, 1f),
                row = group[group.size / 2].y,
                thickness = group.size,
                score = contrast * (1f - min(spread * 10f, 0.9f)),
            )
        }
        for (hit in hits) {
            val prev = group.lastOrNull()
            if (prev != null && hit.y != prev.y + 1) {
                flush()
                group = ArrayList()
            }
            group += hit
        }
        flush()
        return out.sortedByDescending { it.score }
    }

    private fun isBright(p: Int): Boolean {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return luma(r, g, b) >= config.brightLuma && max(r, max(g, b)) - min(r, min(g, b)) <= config.maxChroma
    }

    private fun meanLuma(px: IntArray, width: Int, y: Int, from: Int, to: Int): Float {
        val rowStart = y * width
        var sum = 0L
        var n = 0
        for (x in from..to) {
            sum += luma(px[rowStart + x])
            n++
        }
        return if (n == 0) 0f else sum.toFloat() / n
    }

    companion object {
        fun luma(p: Int): Int = luma((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
        fun luma(r: Int, g: Int, b: Int): Int = (r * 299 + g * 587 + b * 114) / 1000
    }
}

/**
 * Picks the real progress bar out of [ProgressBarDetector] candidates across frames.
 *
 * A candidate is only adopted once it is seen *moving forward* at the same position between
 * two frames; static white UI never qualifies. Once adopted, the bar stays locked while it
 * keeps appearing at that position (including when it jumps back to the start on a loop).
 */
class ProgressBarLock(
    private val rowTolerancePx: Int = 4,
    private val missesBeforeUnlock: Int = 6,
) {
    private var previous: List<ProgressBarDetector.Candidate> = emptyList()
    private var lockedRow: Int? = null
    private var misses = 0

    val isLocked: Boolean get() = lockedRow != null

    fun reset() {
        previous = emptyList()
        lockedRow = null
        misses = 0
    }

    fun update(candidates: List<ProgressBarDetector.Candidate>): ProgressBarDetector.Candidate? {
        val prev = previous
        previous = candidates

        lockedRow?.let { row ->
            val match = candidates.nearestTo(row)
            if (match != null) {
                misses = 0
                lockedRow = match.row
                return match
            }
            if (++misses >= missesBeforeUnlock) {
                lockedRow = null
                misses = 0
            }
            return null
        }

        val moving = candidates.filter { c ->
            val before = prev.nearestTo(c.row) ?: return@filter false
            c.fraction > before.fraction + MIN_MOVE && c.fraction - before.fraction < MAX_MOVE
        }
        val chosen = moving.maxByOrNull { it.score } ?: return null
        lockedRow = chosen.row
        misses = 0
        return chosen
    }

    private fun List<ProgressBarDetector.Candidate>.nearestTo(row: Int) =
        filter { kotlin.math.abs(it.row - row) <= rowTolerancePx }
            .minByOrNull { kotlin.math.abs(it.row - row) }

    private companion object {
        const val MIN_MOVE = 0.002f
        const val MAX_MOVE = 0.45f
    }
}
