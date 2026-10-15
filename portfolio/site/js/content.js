/*
 * Everything personal on the site lives in this file.
 *
 * Edit the values below, save, and reload. Nothing else needs to change.
 * Anything in [SQUARE BRACKETS] is a placeholder. The site shows placeholders
 * in a marked style so they can't be mistaken for real content. Replace them
 * or delete the entry.
 *
 * Text fields are plain text. Fields named `html` accept simple inline HTML
 * (<em>, <strong>, <a>, <code>).
 */
window.PORTFOLIO = {
  person: {
    name: "Jay Marc Merilo",
    shortName: "Jay Marc",
    title: "Android & Frontend Developer",
    // Shown beside the headline, not above it.
    label: "Independent Android & Frontend Developer",
    location: "[YOUR CITY, COUNTRY]",
  },

  // Nav availability indicator. Set `show: false` to hide it.
  availability: {
    show: true,
    open: true,
    label: "Available for QA work",
  },

  hero: {
    // `register` is the part of the headline whose cyan plate slides into place.
    headline: "Software that checks its work before it acts.",
    statement:
      "I build Android apps and web interfaces with a QA mindset: careful about state, honest about their limits, and tested before they ship.",
    primaryCta: { label: "Explore selected work", href: "#work" },
    secondaryCta: { label: "Get in touch", href: "#contact" },
  },

  contact: {
    // Your public email. Until it's set, the form and email link explain that
    // the address is missing instead of failing silently.
    email: "[YOUR EMAIL]",
    // Optional: a form backend (Formspree, Basin, Getform…) that accepts a JSON POST.
    // Leave empty to have the form open the visitor's email app instead.
    formEndpoint: "",
    headline: "Have something that has to work every time?",
    invitation:
      "Tell me what you're building and where it keeps breaking. I usually reply within [RESPONSE TIME].",
  },

  socials: [
    { label: "GitHub", href: "https://github.com/jaymarc-cmd" },
    { label: "LinkedIn", href: "[LINKEDIN URL]" },
    // { label: "Read.cv", href: "https://read.cv/…" },
  ],

  // Set to a real file path (e.g. "cv/jay-marc-merilo.pdf") to enable the link.
  cv: "",

  about: {
    // Short paragraphs. The first is set larger.
    intro: [
      "I'm Jay Marc Merilo, an Android and frontend developer who treats testing as part of the design, not a step after it.",
      "[One or two sentences about where you are now: what you're working on, the kind of team or client you want to work with, and where you're based.]",
    ],
    philosophy: [
      {
        title: "Wait when unsure",
        body: "If software can't tell what's happening, it should hold still and say so. A visible, honest waiting state beats a confident wrong action.",
      },
      {
        title: "Make the machine legible",
        body: "Build the diagnostics into the product. If I can see what the code saw, so can the person tuning it, and bugs stop being guesswork.",
      },
      {
        title: "Small surface, tested core",
        body: "Keep the logic that makes decisions pure and unit-tested, and keep the parts that touch the platform thin.",
      },
    ],
    // Add real roles here. Each entry renders as one row.
    experience: [
      { period: "[YEAR – YEAR]", role: "[ROLE]", org: "[COMPANY OR CONTEXT]", note: "[One line on what you owned.]" },
      { period: "[YEAR – YEAR]", role: "[ROLE]", org: "[COMPANY OR CONTEXT]", note: "[One line on what you owned.]" },
    ],
    // Set to an image path (e.g. "img/portrait.jpg") to replace the FPO plate.
    portrait: "",
    portraitAlt: "Portrait of Jay Marc Merilo",
  },

  // Each capability cites where it's demonstrated. Only list what you can show.
  capabilities: [
    {
      area: "Android development",
      items: ["Kotlin", "Android framework APIs (no AndroidX)", "AccessibilityService", "Gesture dispatch", "Accessibility screenshots (API 30+)", "Gradle Kotlin DSL"],
      evidence: { label: "LikeFlow", href: "#/work/likeflow" },
    },
    {
      area: "Testing & quality assurance",
      items: ["JVM unit tests for pure logic", "Dry-run modes", "On-device diagnostics", "Edge-case tables", "CI builds on every push"],
      evidence: { label: "LikeFlow, sections 4–5", href: "#/work/likeflow" },
    },
    {
      area: "Frontend development",
      items: ["Semantic HTML", "Modern CSS (grid, custom properties, clamp)", "Vanilla JavaScript", "View Transitions", "Performance budgets"],
      evidence: { label: "This site", href: "https://github.com/jaymarc-cmd/portfolio" },
    },
    {
      area: "UI & interaction design",
      items: ["State-driven UI copy", "Status and empty states", "Design tokens", "Typography systems"],
      evidence: { label: "LikeFlow + this site", href: "#/work/likeflow" },
    },
    {
      area: "Accessibility & responsive design",
      items: ["WCAG 2.2 AA", "Keyboard and focus management", "Reduced-motion paths", "Fluid type and layout"],
      evidence: { label: "This site", href: "https://github.com/jaymarc-cmd/portfolio" },
    },
    {
      area: "[ANOTHER AREA]",
      items: ["[SKILL]", "[SKILL]", "[SKILL]"],
      evidence: { label: "[WHERE IT'S SHOWN]", href: "" },
    },
  ],

  process: [
    {
      name: "Discover",
      body: "Find out what actually happens today: the platform, the edge cases, and what the user is trying to get done.",
      output: "Constraints list",
      example: "LikeFlow: TikTok loops videos instead of reporting an end.",
    },
    {
      name: "Define",
      body: "Write down what must never happen, then what success looks like. The negative rules shape the design most.",
      output: "Rules + acceptance checks",
      example: "LikeFlow: never swipe on a timer or a guess.",
    },
    {
      name: "Design",
      body: "Design the states before the screens: waiting, learning, working, failing. Each gets words a person can act on.",
      output: "State map + UI copy",
      example: "LikeFlow: “Waiting: no progress signal”.",
    },
    {
      name: "Build",
      body: "Pure, testable logic in the middle; thin platform code at the edges. Ship a build anyone can install from CI.",
      output: "Working build",
      example: "LikeFlow: detect/ package runs on the plain JVM.",
    },
    {
      name: "Test & refine",
      body: "Run it for real in a mode that can't do damage, read what it saw, and tune from evidence rather than hunches.",
      output: "Tuned thresholds",
      example: "LikeFlow: dry run + diagnostics card.",
    },
  ],

  /*
   * Projects. `slug` becomes the case-study URL (#/work/<slug>).
   * `media` is one of:
   *   { type: "likeflow" }                          – built-in live LikeFlow figure
   *   { type: "image", src, alt, ratio: "16/10" }   – your screenshot or mockup
   *   { type: "fpo", ratio: "4/3", note: "…" }      – marked placeholder
   * `placeholder: true` marks the whole project as unpublished sample content.
   */
  projects: [
    {
      slug: "likeflow",
      title: "LikeFlow",
      category: "Android app · Prototype",
      role: "Design, Android development, testing",
      year: "2026",
      summary:
        "TikTok loops a video instead of saying it finished, so a hands-free scroller can't just use a timer. LikeFlow reads playback progress through Android's accessibility APIs and swipes only when it's confident the video ended.",
      tools: ["Kotlin", "AccessibilityService", "Android 8–15", "JUnit", "GitHub Actions"],
      media: { type: "likeflow" },
      layout: "lead",
      links: [{ label: "Source on GitHub", href: "https://github.com/jaymarc-cmd/new-proj" }],
      caseStudy: {
        overview: [
          "LikeFlow is a native Android app that moves through your TikTok <em>Liked</em> videos hands-free. It watches the current video, decides when it has finished, and performs one upward swipe to reach the next one.",
          "It uses Android's <code>AccessibilityService</code>, not a TikTok API. There's no login, no account connection and no server. Everything runs on the phone, and the app has no <code>INTERNET</code> permission.",
        ],
        facts: [
          ["Platform", "Android 8.0+ (API 26), targets API 35"],
          ["Language", "Kotlin, framework APIs only"],
          ["Status", "Prototype, for sideloading"],
          ["Year", "2026"],
        ],
        problem: [
          "TikTok usually loops a video rather than announcing that it ended. No public signal says “this video is done”, and TikTok's interface changes between versions and regions.",
          "The obvious shortcuts fail. A timer swipes in the middle of long videos and lingers on short ones, and a random swipe skips things. Both break the one promise the app makes.",
        ],
        goals: {
          goals: [
            "Swipe exactly once per finished video.",
            "Never swipe on a timer or a guess. When there's no signal, wait.",
            "Pause the moment the user leaves TikTok, pulls down the shade, or turns off the screen.",
          ],
          constraints: [
            "No TikTok API, so everything has to come from what's on screen.",
            "Process screenshots in memory and discard them. Nothing stored or sent.",
            "Screenshot detection needs Android 11+, and older phones fall back to accessibility data.",
            "Google Play restricts this use of accessibility, so the app is for personal sideloading.",
          ],
        },
        decisions: [
          {
            title: "Three tiers of evidence, most reliable first",
            body: "Every ~500 ms LikeFlow looks for a progress signal: first TikTok's accessibility tree (a seek bar with range info, or a “0:12 / 0:45” label), then a screenshot of the bottom 40 % of the screen for TikTok's thin progress bar, and last an experimental check that the picture snapped back to its first frame.",
          },
          {
            title: "Trust has to be earned",
            body: "A <code>CompletionTracker</code> only trusts a signal after it has moved forward steadily for three samples. It fires when progress reaches ≥ 98.5 %, or when it jumps from ≥ 80 % back to ≤ 20 %, which is a loop.",
          },
          {
            title: "Anything ambiguous drops trust",
            body: "A backwards jump that isn't a loop, a forward jump over 45 %, a bar that moved more than 6 px, or a signal silent for 3 s all reset the tracker. Once reset, the app waits again rather than acting.",
          },
          {
            title: "A bar is only a bar if it moves",
            body: "White UI like captions and buttons can look like a progress bar. <code>ProgressBarLock</code> adopts a candidate only after it is seen moving forward between frames.",
          },
        ],
        safeguards: [
          ["No progress signal on this video", "Waits. Status shows “Waiting: no progress signal”."],
          ["Signal found but not yet seen moving", "Waits until 3 forward samples confirm it."],
          ["You swipe or scrub by hand", "Detected from the pager's scroll event and changed caption labels, then relearned from scratch."],
          ["Paused video", "Progress stops moving, so nothing fires."],
          ["A swipe doesn't take effect", "The tracker re-earns trust before it can fire again, so there are no rapid repeat swipes."],
        ],
        implementation: [
          "The decision logic lives in a pure Kotlin <code>detect/</code> package, unit-tested on the plain JVM: the completion tracker, the progress-bar detector, the visual loop check, and the “0:12 / 0:45” parser. The service around it stays thin: a bounded walk of TikTok's accessibility tree, screenshots, and the swipe gesture.",
          "Two features exist only to make the app testable on real phones. <strong>Dry run</strong> detects and logs every finish but never swipes. The <strong>Diagnostics</strong> card shows what each detection method currently sees, plus a small image of the screen strip LikeFlow last analysed, with red lines marking anything that looked like a progress bar.",
          "Every push runs a GitHub Actions workflow that runs the tests and publishes a debug APK, so the app can be installed without a local Android SDK.",
        ],
        code: {
          caption: "The tracker's thresholds, from CompletionTracker.kt",
          lines: [
            "data class Config(",
            "    val endThreshold: Float = 0.985f,",
            "    val loopFromAtLeast: Float = 0.80f,",
            "    val loopToAtMost: Float = 0.20f,",
            "    val samplesToTrust: Int = 3,",
            "    val backwardTolerance: Float = 0.02f,",
            "    val maxForwardStep: Float = 0.45f,",
            "    val anchorTolerancePx: Int = 6,",
            "    val signalTimeoutMs: Long = 3_000,",
            ")",
          ],
        },
        outcome: [
          "A working prototype: an installable APK built by CI, a detection core covered by unit tests, and an in-app diagnostics view for tuning on new devices.",
          "No usage or accuracy numbers are reported here. The app collects no telemetry by design. <span class=\"ph\">[Add results from your own device testing, e.g. how many finishes the dry run caught on which phone and TikTok version.]</span>",
        ],
        lessons: [
          {
            title: "Waiting is a feature",
            body: "The most important state in the app is the one where it does nothing. Designing that state's wording mattered as much as the detector.",
          },
          {
            title: "Ship the instruments with the product",
            body: "Thresholds tuned on one phone won't hold on the next. A diagnostics card the user can screenshot turns “it doesn't work” into data.",
          },
          {
            title: "Keep decisions out of the platform layer",
            body: "Because the tracker is plain Kotlin with no Android dependencies, every edge case in the table above can be tested without a phone.",
          },
        ],
      },
    },
    {
      slug: "project-two",
      placeholder: true,
      title: "[PROJECT TITLE]",
      category: "[CATEGORY]",
      role: "[YOUR ROLE]",
      year: "[YEAR]",
      summary: "[Two sentences: the problem, and what you built to solve it.]",
      tools: ["[TOOL]", "[TOOL]", "[TOOL]"],
      media: { type: "fpo", ratio: "4/3", note: "Screenshot or mockup, 4:3" },
      layout: "offset",
    },
    {
      slug: "project-three",
      placeholder: true,
      title: "[PROJECT TITLE]",
      category: "[CATEGORY]",
      role: "[YOUR ROLE]",
      year: "[YEAR]",
      summary: "[Two sentences: the problem, and what you built to solve it.]",
      tools: ["[TOOL]", "[TOOL]"],
      media: { type: "fpo", ratio: "3/4", note: "Phone screen, 3:4" },
      layout: "tall",
    },
    {
      slug: "project-four",
      placeholder: true,
      title: "[PROJECT TITLE]",
      category: "[CATEGORY]",
      role: "[YOUR ROLE]",
      year: "[YEAR]",
      summary: "[Two sentences: the problem, and what you built to solve it.]",
      tools: ["[TOOL]", "[TOOL]", "[TOOL]"],
      media: { type: "fpo", ratio: "16/9", note: "Wide screenshot, 16:9" },
      layout: "wide",
    },
  ],
};
