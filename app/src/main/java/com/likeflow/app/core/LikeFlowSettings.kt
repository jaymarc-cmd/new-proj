package com.likeflow.app.core

import android.content.Context
import android.content.SharedPreferences

/** User preferences shared by the app screen and the accessibility service. */
class LikeFlowSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("likeflow", Context.MODE_PRIVATE)

    /** Master switch. The service does nothing while this is off. */
    var autoScrollEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SCROLL, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SCROLL, value).apply()

    /** Detect completion and report it, but never swipe. For testing a phone's signals. */
    var dryRun: Boolean
        get() = prefs.getBoolean(KEY_DRY_RUN, false)
        set(value) = prefs.edit().putBoolean(KEY_DRY_RUN, value).apply()

    /** Read TikTok's on-screen progress bar from screenshots (Android 11+). */
    var screenshotDetection: Boolean
        get() = prefs.getBoolean(KEY_SCREENSHOTS, true)
        set(value) = prefs.edit().putBoolean(KEY_SCREENSHOTS, value).apply()

    /** Experimental: detect loops by comparing frames when no progress is exposed at all. */
    var visualLoopDetection: Boolean
        get() = prefs.getBoolean(KEY_VISUAL_LOOP, false)
        set(value) = prefs.edit().putBoolean(KEY_VISUAL_LOOP, value).apply()

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val KEY_AUTO_SCROLL = "auto_scroll_enabled"
        const val KEY_DRY_RUN = "dry_run"
        const val KEY_SCREENSHOTS = "screenshot_detection"
        const val KEY_VISUAL_LOOP = "visual_loop_detection"
    }
}
