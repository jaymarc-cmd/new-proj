# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Static HTML, CSS and vanilla JavaScript: no framework, no build step, no runtime dependencies. User's choice. All editable copy lives in one content module so the owner can personalise the site without touching layout code. Intended to live in its own public GitHub repository (`portfolio`) and deploy to any static host (GitHub Pages, Netlify, Vercel).

## Users

- **Primary:** hiring managers, engineering leads, and prospective clients looking at Jay Marc Merilo as an Android & frontend developer. They usually arrive from a link (CV, GitHub, a message) and have minutes, not hours. They want to answer "can this person build careful, real software, and how do they think?"
- **Secondary:** peers and recruiters scanning for stack, role, and contact details.

## Product Purpose

A personal portfolio that presents Jay Marc Merilo's work and way of thinking, and turns a qualified visitor into a conversation (an email, or a message through the contact form). It succeeds when a visitor can quickly say what Jay builds, see one real project explained in depth, and know how to get in touch.

## Positioning

The owner's real, inspectable work: LikeFlow, a native Android app built on Android's AccessibilityService with a deliberately conservative detection pipeline ("it waits when it isn't sure"). The portfolio shows that engineering judgment (careful states, honest limits, privacy by construction) instead of claiming it.

## Operating Context

- Visitors read on laptops during hiring reviews and on phones from links in messages.
- The owner edits content by hand in a single data file and deploys by pushing to GitHub.
- No backend. The contact form has to work without a server (mailto or a static form service the owner configures later).

## Capabilities and Constraints

- Sections: navigation, hero, selected work with case-study views, about, capabilities, process, contact, footer.
- Case studies are rendered client-side from the content module (a hash route per project), so the site stays static.
- Placeholder content is always marked visibly as a placeholder (for example `[PROJECT TITLE]`) and is never presented as real.
- Undecided: email address, social profiles, CV file, portrait, other real projects, location/time zone. All stay as labeled placeholders until the owner supplies them.

## Brand Commitments

- Name: **Jay Marc Merilo**.
- Title: **Android & Frontend Developer**.
- The brief asks for a minimalist look with an editorial, Swiss-typographic sensibility, restrained color (warm off-white, near-black, gray, one accent), and no gimmicks: no generic gradients, glassmorphism, glow, floating shapes, oversized rounded cards, or decorative icon tiles.

## Evidence on Hand

- **LikeFlow** (real), source in the `new-proj` repository: an Android app (Kotlin, framework APIs only, no AndroidX, API 26–35) that advances through TikTok Liked videos hands-free using an AccessibilityService. Detection runs in three tiers: accessibility-tree progress, then screenshot progress-bar detection on Android 11+, then an experimental visual loop check. A `CompletionTracker` trusts a signal only after 3 forward samples and fires at ≥ 98.5 % or on an 80 % → 20 % loop jump. It has a dry-run mode, an on-screen diagnostics card, no INTERNET permission, JVM unit tests for the detection logic, and a GitHub Actions build that publishes the APK. It is a prototype for sideloading and is not affiliated with TikTok.
- **Absent, must not be fabricated:** clients, employers, testimonials, metrics (downloads, users, performance numbers), awards, years of experience, education, other projects, email, socials, CV, portrait.

## Product Principles

1. Show the work, not adjectives. Every claim on the page can be traced to an artifact.
2. Honest by construction. Placeholders look like placeholders, and limits are stated plainly, the way LikeFlow states its own.
3. Fast and calm. The site loads instantly, reads well on a phone, and never makes the visitor wait for decoration.
4. Easy for the owner to edit. Personal content changes in one place.

## Accessibility & Inclusion

WCAG 2.2 AA: full keyboard operation, visible focus, a reduced-motion path with all content visible, semantic headings and landmarks, and form errors announced to assistive technology.
