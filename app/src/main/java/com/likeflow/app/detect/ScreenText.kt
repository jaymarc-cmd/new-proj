package com.likeflow.app.detect

/** Pure helpers for text read from TikTok's accessibility tree. */
object ScreenText {

    private val TIME = """(\d{1,2}(?::\d{2}){1,2})"""
    private val TIME_PAIR = Regex("""$TIME\s*/\s*$TIME""")

    /**
     * Parses an elapsed/total label such as "0:12 / 0:45" or "01:02:03/01:10:00".
     * @return played fraction, or null if [text] doesn't contain a usable pair.
     */
    fun parseTimeProgress(text: CharSequence?): Float? {
        if (text.isNullOrEmpty()) return null
        val match = TIME_PAIR.find(text) ?: return null
        val elapsed = seconds(match.groupValues[1]) ?: return null
        val total = seconds(match.groupValues[2]) ?: return null
        if (total <= 0 || elapsed > total) return null
        return elapsed.toFloat() / total
    }

    private fun seconds(clock: String): Int? {
        val parts = clock.split(':').map { it.toIntOrNull() ?: return null }
        if (parts.drop(1).any { it >= 60 }) return null
        return parts.fold(0) { acc, part -> acc * 60 + part }
    }

    private val COUNT = Regex("""^[\d.,\s]+[KkMmBb]?$""")

    /**
     * Builds a fingerprint of the video currently on screen from the caption/author labels.
     * Pure counts ("12.4K") are dropped because they can tick up while a video plays.
     * When a video loops this stays the same; after a swipe it changes.
     */
    fun videoIdentity(labels: List<CharSequence>): String? {
        val kept = labels.asSequence()
            .map { it.toString().trim() }
            .filter { it.length >= 2 && !COUNT.matches(it) }
            .map { it.take(80) }
            .distinct()
            .sorted()
            .toList()
        return if (kept.isEmpty()) null else kept.joinToString("|")
    }
}
