# LikeFlow

**TikTok Liked Videos Auto Scroll** (prototype)

LikeFlow is a native Android app that moves you through your TikTok **Liked** videos hands-free. It watches for the current video to finish, then performs one upward swipe to go to the next one.

It does this with Android's `AccessibilityService`, not a TikTok API. There's no TikTok login, no account connection and no server: everything runs on the phone.

> **Design rule:** TikTok usually loops a video instead of announcing that it finished. LikeFlow only swipes when it is *confident* the video ended. When it can't read playback progress, it **waits**. It never swipes on a timer or at random.

## How it works

```
TikTok in foreground + auto-scroll on
        │
        ▼  every ~500 ms
1. Accessibility tree ──► wide progress/seek bar with range info, or a "0:12 / 0:45" label
        │ (none found)
        ▼
2. Screenshot (Android 11+) ──► TikTok's thin on-screen progress bar
        │ (none found, optional)
        ▼
3. Visual loop check (experimental) ──► picture snaps back to the video's first frame
        │
        ▼
CompletionTracker: trust a signal only after it has moved forward steadily for 3 samples,
then fire on "reached ≥ 98.5 %" or "jumped from ≥ 80 % back to ≤ 20 %" (a loop).
        │
        ▼
One upward swipe → 1.5 s cooldown → start learning the next video from scratch
```

Safeguards against swiping at the wrong time:

| Situation | What LikeFlow does |
|---|---|
| No progress signal on this video | Waits. Status shows *Waiting: no progress signal*. |
| Signal found but not yet seen moving | Waits until 3 forward samples confirm it. |
| White UI that looks like a bar (caption text, buttons) | Ignored. A bar is only adopted once it's seen **moving forward** between frames (`ProgressBarLock`). |
| You swipe or scrub by hand | Detected through the pager's scroll event and a change in the on-screen author/caption labels. The new video is learned from scratch, so this isn't mistaken for a loop. |
| Paused video | Progress stops moving, so nothing fires. |
| You leave TikTok, pull down the shade, or the screen turns off | Pauses and resets. |
| A swipe doesn't take effect | The tracker has to re-earn trust before it can fire again. No rapid repeat swipes. |

### Project layout

```
app/src/main/java/com/likeflow/app/
├── detect/                    Pure Kotlin, unit-tested on the JVM
│   ├── CompletionTracker.kt     progress samples → "finished?" decision
│   ├── ProgressBarDetector.kt   finds thin progress bars in screenshot pixels (+ ProgressBarLock)
│   ├── VisualLoopDetector.kt    experimental frame-similarity loop check
│   └── ScreenText.kt            "0:12 / 0:45" parsing, video identity from caption labels
├── service/
│   ├── LikeFlowAccessibilityService.kt   main loop, screenshots, swipe gesture
│   └── NodeScanner.kt                    bounded walk of TikTok's accessibility tree
├── core/                      settings, live status shared with the UI, TikTok package names
└── ui/MainActivity.kt         status card, switches, diagnostics log
```

The app uses only Android framework APIs (no AndroidX), targets Android 15 (API 35) and runs on Android 8.0+ (API 26). Screenshot-based detection needs Android 11+ (API 30).

## Build

Requirements: JDK 17 and the Android SDK (Android Studio Ladybug or newer works).

```bash
./gradlew testDebugUnitTest   # detection logic tests
./gradlew assembleDebug       # → app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and press **Run**.

Every push also runs the **Android build** GitHub Actions workflow. It runs the tests and uploads `likeflow-debug-apk` as a downloadable artifact, so you can get an APK without a local SDK.

## Install & set up

1. Install the APK (`adb install app-debug.apk`, or open it on the phone).
2. Open **LikeFlow** → **Enable in Accessibility settings** → **LikeFlow auto-scroll** → turn it on.
   - **Android 13+, sideloaded APK:** if the switch is greyed out as a *restricted setting*, go to App info → ⋮ → **Allow restricted settings**, then try again. The app has a shortcut button for this.
3. Back in LikeFlow, turn on **Dry run** first, then switch on **Auto-scroll**.
4. Tap **Open TikTok** → Profile → **Liked** tab → tap the first video.

## Testing whether your phone exposes progress

This is a prototype. The first job is to find out what your phone and TikTok version expose:

1. Keep **Dry run** on. LikeFlow detects and logs finishes, but never swipes.
2. Watch a few liked videos, ideally including some longer than 30 s. TikTok shows its progress bar more reliably on those.
3. Return to LikeFlow and read **Diagnostics**:
   - `Video looped (93% → 4%) via on-screen progress bar`: screenshot detection works.
   - `… via TikTok accessibility data`: TikTok exposes progress directly. This is the most reliable source.
   - Status stuck on **Waiting: no progress signal**: neither is available for that video. Try enabling **Visual loop check (experimental)**.
4. Once the log matches what you saw, turn **Dry run** off.

Things likely to need tuning on real devices: the screen strip searched for the bar (bottom 40 %, `analyse()` in the service), the brightness/contrast thresholds in `ProgressBarDetector.Config`, and the swipe path in `swipeUp()`.

## Privacy

- Accessibility events are restricted to TikTok's package names (`com.zhiliaoapp.musically`, `com.ss.android.ugc.trill`, `com.zhiliaoapp.musically.go`) in `res/xml/likeflow_accessibility_service.xml`.
- Screenshots are analysed in memory for the progress bar and discarded immediately. Nothing is saved, logged or transmitted. The app has **no INTERNET permission**.
- It never reads your TikTok credentials or account data.

## Limitations

- TikTok's UI changes between versions and regions. The progress bar may be hidden on short videos or drawn differently, and then LikeFlow waits.
- The accessibility-tree path only works if TikTok exposes its seek bar or a time label to accessibility services.
- Visual loop detection is a heuristic. It can miss loops in fast-cut videos and could misfire on very repetitive ones, so it is off by default.
- Google Play restricts non-accessibility uses of `AccessibilityService`. This prototype is meant for sideloading and personal use.
- Not affiliated with or endorsed by TikTok or ByteDance. Automating the TikTok app may conflict with TikTok's terms of service, so use it at your own discretion.
