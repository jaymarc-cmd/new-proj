package com.likeflow.app.service

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.likeflow.app.detect.ScreenText
import java.util.ArrayDeque

/**
 * One bounded walk over TikTok's accessibility tree. It collects:
 *  - playback progress, if TikTok exposes a wide progress/seek bar with range info, or an
 *    "elapsed / total" time label;
 *  - an identity for the video on screen (author + caption labels), so a loop can be told
 *    apart from the user swiping to a different video.
 */
internal object NodeScanner {

    data class Result(val progress: Float?, val identity: String?)

    private const val MAX_NODES = 800

    fun scan(root: AccessibilityNodeInfo, screenWidth: Int, screenHeight: Int): Result {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val bounds = Rect()
        val labels = ArrayList<CharSequence>()
        var rangeProgress: Float? = null
        var rangeWidth = 0
        var timeProgress: Float? = null
        var visited = 0

        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            visited++
            // Off-screen pages (TikTok preloads neighbouring videos) are skipped with their subtree.
            if (node.isVisibleToUser && visited <= MAX_NODES) {
                node.getBoundsInScreen(bounds)

                val range = node.rangeInfo
                if (range != null && range.max > range.min &&
                    bounds.width() >= screenWidth / 2 && bounds.width() > rangeWidth
                ) {
                    rangeProgress = ((range.current - range.min) / (range.max - range.min)).coerceIn(0f, 1f)
                    rangeWidth = bounds.width()
                }

                val text = node.text
                val description = node.contentDescription
                if (timeProgress == null) {
                    timeProgress = ScreenText.parseTimeProgress(text) ?: ScreenText.parseTimeProgress(description)
                }
                // Author and caption sit in the lower-left of each video; counts are on the right.
                if (bounds.top >= screenHeight * 0.55f && bounds.centerX() < screenWidth / 2) {
                    if (!text.isNullOrBlank()) labels += text
                    else if (!description.isNullOrBlank()) labels += description
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let(queue::add)
                }
            }
            if (node !== root) node.recycleCompat()
        }
        return Result(rangeProgress ?: timeProgress, ScreenText.videoIdentity(labels))
    }
}

/** Node recycling is required before Android 13 and a deprecated no-op after it. */
@Suppress("DEPRECATION")
internal fun AccessibilityNodeInfo.recycleCompat() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) recycle()
}
