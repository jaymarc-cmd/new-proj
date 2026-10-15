package com.likeflow.app.core

/** TikTok's Android package names. LikeFlow only ever looks at these apps. */
object TikTok {
    val PACKAGES = listOf(
        "com.zhiliaoapp.musically", // TikTok (most regions)
        "com.ss.android.ugc.trill", // TikTok (some Asian regions)
        "com.zhiliaoapp.musically.go", // TikTok Lite
    )

    fun isTikTok(packageName: CharSequence?): Boolean =
        packageName != null && PACKAGES.any { it.contentEquals(packageName) }
}
