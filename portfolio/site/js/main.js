/*
 * Renders js/content.js into the page and runs the interactions:
 * navigation, case-study routing, the LikeFlow figure, reveals and the form.
 * No dependencies. Works from file:// as well as a web server.
 */
(function () {
  "use strict";

  var C = window.PORTFOLIO;
  if (!C) return;

  var doc = document;
  var root = doc.documentElement;
  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

  /* ------------------------------------------------------------------ *
   * Helpers
   * ------------------------------------------------------------------ */

  var PH_RE = /\[[^\]\n<>]{1,160}\]/g;

  function esc(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  // Plain text, with [PLACEHOLDERS] marked.
  function t(value) {
    return esc(value).replace(PH_RE, function (m) { return '<span class="ph">' + m + "</span>"; });
  }
  // Owner-written inline HTML, with [PLACEHOLDERS] marked.
  function rich(value) {
    return String(value == null ? "" : value).replace(PH_RE, function (m) { return '<span class="ph">' + m + "</span>"; });
  }
  function isPh(value) { return typeof value === "string" && /\[[^\]]+\]/.test(value); }
  function usable(href) { return !!href && !isPh(href); }
  function isExternal(href) { return /^https?:\/\//.test(href); }
  function $(sel, ctx) { return (ctx || doc).querySelector(sel); }
  function $$(sel, ctx) { return Array.prototype.slice.call((ctx || doc).querySelectorAll(sel)); }
  function icon(name) { return '<svg class="icon" aria-hidden="true"><use href="#' + name + '"/></svg>'; }
  function pad2(n) { return (n < 10 ? "0" : "") + n; }
  function pct(f) { return Math.round(f * 100) + "%"; }

  function link(href, label, cls) {
    var ext = isExternal(href);
    return '<a class="' + (cls || "") + '" href="' + esc(href) + '"' +
      (ext ? ' target="_blank" rel="noopener"' : "") + ">" + t(label) +
      (ext ? '<span class="sr-only"> (opens in a new tab)</span>' + icon("arrow-out") : "") + "</a>";
  }

  var announcer = doc.createElement("p");
  announcer.className = "sr-only";
  announcer.setAttribute("aria-live", "polite");
  doc.body.appendChild(announcer);
  function announce(msg) {
    announcer.textContent = "";
    setTimeout(function () { announcer.textContent = msg; }, 50);
  }

  function fpo(ratio, note) {
    return '<div class="fpo" style="aspect-ratio:' + esc(ratio || "4/3") + '">' +
      '<svg class="fpo__x" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">' +
      '<path d="M0 0L100 100M100 0L0 100" stroke="currentColor" stroke-width="1" vector-effect="non-scaling-stroke"/></svg>' +
      '<p class="fpo__label"><strong>FPO</strong><span>' + t(note || "Image") + "</span></p></div>";
  }

  /* ------------------------------------------------------------------ *
   * LikeFlow's completion tracker, ported from CompletionTracker.kt
   * with the same thresholds. It drives both figures.
   * ------------------------------------------------------------------ */

  function Tracker() {
    this.cfg = { endThreshold: 0.985, loopFromAtLeast: 0.8, loopToAtMost: 0.2, samplesToTrust: 3, backwardTolerance: 0.02, maxForwardStep: 0.45 };
    this.reset();
  }
  Tracker.prototype.reset = function () { this.last = null; this.forward = 0; };
  Tracker.prototype.trusted = function () { return this.forward >= this.cfg.samplesToTrust; };
  Tracker.prototype.start = function (f) {
    this.last = f; this.forward = 0;
    return { kind: "learning", fraction: f, n: 0 };
  };
  Tracker.prototype.current = function (f) {
    return this.trusted() ? { kind: "playing", fraction: f } : { kind: "learning", fraction: f, n: this.forward };
  };
  Tracker.prototype.complete = function (reason, from, to) {
    this.start(to);
    return { kind: "completed", reason: reason, from: from, to: to };
  };
  Tracker.prototype.sample = function (fraction) {
    if (fraction == null) return this.last == null ? { kind: "none" } : this.current(this.last);
    var c = this.cfg;
    var f = Math.min(1, Math.max(0, fraction));
    var prev = this.last;
    if (prev == null) return this.start(f);
    var delta = f - prev;
    if (delta > c.maxForwardStep) return this.start(f);
    if (delta >= -c.backwardTolerance) {
      if (delta > 0.001) this.forward++;
      this.last = Math.max(prev, f);
      if (this.trusted() && f >= c.endThreshold) return this.complete("end", prev, f);
      return this.current(this.last);
    }
    if (this.trusted() && prev >= c.loopFromAtLeast && f <= c.loopToAtMost) return this.complete("loop", prev, f);
    return this.start(f); // scrubbed or changed video
  };

  /* ------------------------------------------------------------------ *
   * Figure: LikeFlow rebuilt in HTML, running the tracker live
   * ------------------------------------------------------------------ */

  var SOURCE = "on-screen progress bar";
  var VIDEOS = [24, 31, 17, 45, 22, 38]; // synthetic durations, seconds
  var SPEED = 3; // playback runs at 3× so a loop is visible in seconds

  function clock() {
    var d = new Date();
    return pad2(d.getHours()) + ":" + pad2(d.getMinutes()) + ":" + pad2(d.getSeconds());
  }
  function mmss(s) { s = Math.floor(s); return Math.floor(s / 60) + ":" + pad2(s % 60); }

  function videoMarkup(i, tone) {
    return '<div class="lf__video" data-tone="' + tone + '">' +
      '<p class="lf__frame-label" data-time>0:00 / ' + mmss(VIDEOS[i % VIDEOS.length]) + "</p>" +
      '<p class="lf__frame-label">Liked video ' + (i + 1) + "</p>" +
      '<div class="lf__bar"><span></span></div></div>';
  }

  function mountLikeflow(figure, opts) {
    var capId = opts.id + "-cap";
    figure.setAttribute("aria-labelledby", capId);
    figure.innerHTML =
      '<div class="lf marks">' +
        '<div class="lf__feed" aria-hidden="true">' +
          '<p class="lf__feed-note">TikTok · Liked (simulated)</p>' +
          '<div class="lf__track">' + videoMarkup(0, 0) + videoMarkup(1, 1) + "</div>" +
          '<div class="lf__detect"></div>' +
        "</div>" +
        '<div class="lf__app">' +
          '<div class="lf__head"><div><p class="lf__name">LikeFlow</p><p class="lf__tag">TikTok Liked Videos Auto Scroll</p></div><span class="lf__chip">Prototype</span></div>' +
          '<div class="lf__card">' +
            '<p class="lf__label">AUTO-SCROLL STATUS</p>' +
            '<p class="lf__title" data-title>Waiting for TikTok</p>' +
            '<p class="lf__detail" data-detail>Open TikTok, go to Profile → Liked, and tap the first video.</p>' +
            '<div class="lf__progress" data-progress><span></span></div>' +
            '<button class="lf__switch" type="button" role="switch" aria-checked="false" data-dry>Dry run (detect only, never swipe)<span class="lf__switch-track" aria-hidden="true"></span></button>' +
          "</div>" +
          '<div class="lf__card">' +
            '<p class="lf__label">DIAGNOSTICS</p>' +
            '<p class="lf__counts" data-counts>Completions detected: 0 · Swipes: 0</p>' +
            '<ol class="lf__log" data-log aria-label="Diagnostics log"></ol>' +
          "</div>" +
        "</div>" +
      "</div>" +
      '<figcaption class="caption" id="' + capId + '"><b>' + esc(opts.figNo) + "</b><span>" + esc(opts.caption) + "</span>" +
      '<span class="lf-controls"><button type="button" data-play>Pause demo</button></span></figcaption>';

    var feed = $(".lf__feed", figure);
    var track = $(".lf__track", figure);
    var titleEl = $("[data-title]", figure);
    var detailEl = $("[data-detail]", figure);
    var progEl = $("[data-progress]", figure);
    var countsEl = $("[data-counts]", figure);
    var logEl = $("[data-log]", figure);
    var dryBtn = $("[data-dry]", figure);
    var playBtn = $("[data-play]", figure);

    var tracker = new Tracker();
    var s = {
      video: 0, pos: 0, dry: false, cooldown: 0, completions: 0, swipes: 0,
      userPaused: false, visible: true, acc: 0, last: 0, raf: 0, swiping: false,
    };

    function log(msg) {
      var li = doc.createElement("li");
      li.innerHTML = "<time>" + clock() + "</time>" + esc(msg);
      logEl.insertBefore(li, logEl.firstChild);
      while (logEl.children.length > 4) logEl.removeChild(logEl.lastChild);
    }
    function counts() { countsEl.textContent = "Completions detected: " + s.completions + " · Swipes: " + s.swipes; }
    function dur() { return VIDEOS[s.video % VIDEOS.length]; }

    function paintFeed() {
      var cur = track.children[0];
      var f = s.pos / dur();
      $(".lf__bar span", cur).style.setProperty("--p", f.toFixed(4));
      $("[data-time]", cur).textContent = mmss(s.pos) + " / " + mmss(dur());
    }

    function status(title, detail, fraction, learning) {
      titleEl.textContent = title;
      detailEl.textContent = detail;
      progEl.style.visibility = fraction == null ? "hidden" : "visible";
      progEl.classList.toggle("is-learning", !!learning);
      if (fraction != null) $("span", progEl).style.setProperty("--p", fraction.toFixed(4));
    }

    function swipe() {
      s.swipes++;
      counts();
      s.swiping = true;
      var finish = function () {
        s.video++;
        s.pos = 0;
        track.classList.remove("is-swiping");
        track.innerHTML = videoMarkup(s.video, s.video % 4) + videoMarkup(s.video + 1, (s.video + 1) % 4);
        s.swiping = false;
        paintFeed();
      };
      if (reduceMotion.matches) { finish(); return; }
      track.classList.add("is-swiping");
      setTimeout(finish, 540);
    }

    function tick() {
      if (s.cooldown > 0) {
        s.cooldown -= 500;
        if (s.cooldown <= 0) tracker.reset();
        return;
      }
      var v = tracker.sample(s.pos / dur());
      feed.classList.toggle("is-locked", tracker.trusted());
      if (v.kind === "learning") {
        status("Checking progress signal (" + v.n + "/3)", "Found progress via " + SOURCE + ". Making sure it moves steadily before trusting it.", v.fraction, true);
      } else if (v.kind === "playing") {
        status("Watching: " + pct(v.fraction),
          s.dry ? "Tracking via " + SOURCE + ". Dry run: will log the finish without swiping."
                : "Tracking via " + SOURCE + ". Will swipe once when the video finishes.", v.fraction, false);
      } else if (v.kind === "completed") {
        s.completions++;
        var why = v.reason === "loop"
          ? "Video looped (" + pct(v.from) + " → " + pct(v.to) + ") via " + SOURCE
          : "Video reached the end (" + pct(v.to) + ") via " + SOURCE;
        if (s.dry) {
          log(why + ". Dry run: would swipe now");
          counts();
        } else {
          log(why + ". Swiping to next video");
          status("Moving to the next video", "Letting the next video settle before checking again.", null);
          s.cooldown = 1500;
          feed.classList.remove("is-locked");
          swipe();
        }
      }
    }

    function frame(now) {
      if (!s.last) s.last = now;
      var dt = Math.min(100, now - s.last);
      s.last = now;
      if (!s.swiping && s.cooldown <= 0) {
        s.pos += (dt / 1000) * SPEED;
        if (s.pos >= dur()) s.pos -= dur(); // TikTok loops instead of stopping
      }
      paintFeed();
      s.acc += dt;
      while (s.acc >= 500) { s.acc -= 500; tick(); }
      s.raf = requestAnimationFrame(frame);
    }

    function running() { return !s.userPaused && s.visible && !doc.hidden; }
    function sync() {
      if (running() && !s.raf) { s.last = 0; s.raf = requestAnimationFrame(frame); }
      if (!running() && s.raf) { cancelAnimationFrame(s.raf); s.raf = 0; }
      playBtn.textContent = s.userPaused ? "Play demo" : "Pause demo";
    }

    dryBtn.addEventListener("click", function () {
      s.dry = !s.dry;
      dryBtn.setAttribute("aria-checked", String(s.dry));
      log(s.dry ? "Dry run on: detect only" : "Dry run off: swiping enabled");
    });
    playBtn.addEventListener("click", function () {
      s.userPaused = !s.userPaused;
      sync();
    });
    doc.addEventListener("visibilitychange", sync);
    if ("IntersectionObserver" in window) {
      new IntersectionObserver(function (entries) {
        s.visible = entries[0].isIntersecting;
        sync();
      }).observe(figure);
    }

    // Start mid-story so the first frame already says something.
    log("Accessibility service connected");
    log("Auto-scroll on");
    s.pos = dur() * 0.18;
    for (var i = 0; i < 4; i++) { s.pos += 0.5 * SPEED; tick(); }
    paintFeed();
    counts();

    // Reduced motion: hold still until the visitor asks it to play.
    if (reduceMotion.matches) s.userPaused = true;
    sync();
  }

  /* ------------------------------------------------------------------ *
   * Figure: the tracker's verdicts over synthetic samples
   * ------------------------------------------------------------------ */

  function buildSamples() {
    // [time s, fraction or null]. 0.5 s apart, the service's sampling interval.
    var out = [];
    var t = 0;
    var i;
    for (i = 0; i <= 23; i++) { out.push([t, (i * 0.5) / 11.8]); t += 0.5; }    // video A: 0 → 97 %
    out.push([t, 0.2 / 11.8]); t += 0.5;                                           // loops back to 2 %
    for (i = 0; i < 3; i++) { out.push([t, null]); t += 0.5; }                    // 1.5 s cooldown
    var seg = [0, 0.06, 0.12, 0.18, 0.24, 0.3, 0.36, 0.42, 0.48, 0.54, 0.29, 0.35, 0.41, 0.47, 0.53, 0.59, 0.65, 0.71, 0.77, 0.83, 0.89, 0.95, 0.99];
    for (i = 0; i < seg.length; i++) { out.push([t, seg[i]]); t += 0.5; }         // video B, scrubbed back once, then ends
    return out;
  }

  function signalPlot(narrow) {
    var W = narrow ? 420 : 880, H = narrow ? 300 : 340;
    var m = { l: narrow ? 34 : 44, r: narrow ? 8 : 16, t: 34, b: 40 };
    var samples = buildSamples();
    var tracker = new Tracker();
    var tMax = samples[samples.length - 1][0];
    var x = function (tt) { return m.l + (tt / tMax) * (W - m.l - m.r); };
    var y = function (f) { return m.t + (1 - f) * (H - m.t - m.b); };
    var out = [];
    var add = function (s) { out.push(s); };

    add('<svg class="plot ' + (narrow ? "plot--narrow" : "plot--wide") + '" viewBox="0 0 ' + W + " " + H + '" role="img" aria-labelledby="plot-t-' + (narrow ? "n" : "w") + '">');
    add('<title id="plot-t-' + (narrow ? "n" : "w") + '">Line chart of playback progress over about 25 seconds. The tracker learns for three samples, trusts the signal, detects a loop from 98% to 2% and swipes, waits 1.5 seconds, learns the next video, drops trust when the video is scrubbed back, relearns, and swipes again when progress reaches 99%.</title>');

    [0, 0.25, 0.5, 0.75, 1].forEach(function (g) {
      add('<line class="plot__grid" x1="' + m.l + '" x2="' + (W - m.r) + '" y1="' + y(g) + '" y2="' + y(g) + '"/>');
      add('<text x="' + (m.l - 8) + '" y="' + (y(g) + 4) + '" text-anchor="end">' + Math.round(g * 100) + "</text>");
    });
    [[0.985, "end ≥ 98.5", -5], [0.8, "loop from ≥ 80", -5], [0.2, "loop to ≤ 20", 14]].forEach(function (th) {
      add('<line class="plot__threshold" x1="' + m.l + '" x2="' + (W - m.r) + '" y1="' + y(th[0]) + '" y2="' + y(th[0]) + '"/>');
      if (!narrow) add('<text x="' + (m.l + 8) + '" y="' + (y(th[0]) + th[2]) + '">' + th[1] + "</text>");
    });
    add('<line class="plot__axis" x1="' + m.l + '" x2="' + (W - m.r) + '" y1="' + y(0) + '" y2="' + y(0) + '"/>');
    for (var s = 0; s <= tMax; s += 5) {
      add('<text x="' + x(s) + '" y="' + (H - m.b + 18) + '" text-anchor="middle">' + s + "s</text>");
    }
    add('<text x="' + m.l + '" y="14" class="plot__label-strong">progress %</text>');

    // Run the tracker over the samples; break the line at every reset.
    var segments = [[]];
    var dots = [];
    var events = [];
    var coolStart = null;
    samples.forEach(function (smp) {
      var v = tracker.sample(smp[1]);
      if (smp[1] == null) {
        if (coolStart == null) coolStart = smp[0];
        if (segments[segments.length - 1].length) segments.push([]);
        tracker.reset();
        return;
      }
      if (v.kind === "learning" && v.n === 0 && segments[segments.length - 1].length) segments.push([]);
      if (v.kind === "completed") {
        segments[segments.length - 1].push([smp[0], v.from]);
        events.push({ t: smp[0], v: v });
        segments.push([[smp[0], smp[1]]]);
        dots.push({ t: smp[0], f: smp[1], trusted: false });
        return;
      }
      segments[segments.length - 1].push([smp[0], smp[1]]);
      dots.push({ t: smp[0], f: smp[1], trusted: v.kind === "playing" });
    });

    if (coolStart != null) {
      add('<rect class="plot__cool" x="' + x(coolStart - 0.25) + '" y="' + m.t + '" width="' + (x(1.5) - x(0)) + '" height="' + (H - m.t - m.b) + '"/>');
      add('<text x="' + (x(coolStart + 1.25) + 6) + '" y="' + y(narrow ? 0.88 : 0.62) + '" class="plot__label-event">' + (narrow ? "wait" : "1.5 s cooldown") + "</text>");
    }
    segments.forEach(function (seg) {
      if (seg.length < 2) return;
      add('<polyline class="plot__line" points="' + seg.map(function (p) { return x(p[0]).toFixed(1) + "," + y(p[1]).toFixed(1); }).join(" ") + '"/>');
    });
    dots.forEach(function (d) {
      add(d.trusted
        ? '<circle class="plot__trust" cx="' + x(d.t).toFixed(1) + '" cy="' + y(d.f).toFixed(1) + '" r="' + (narrow ? 2.4 : 2.8) + '"/>'
        : '<circle class="plot__learn" cx="' + x(d.t).toFixed(1) + '" cy="' + y(d.f).toFixed(1) + '" r="' + (narrow ? 2.6 : 3) + '"/>');
    });
    events.forEach(function (e) {
      var ex = x(e.t);
      add('<line class="plot__event" x1="' + ex + '" x2="' + ex + '" y1="' + m.t + '" y2="' + y(0) + '"/>');
      add('<circle class="plot__event-dot" cx="' + ex + '" cy="' + m.t + '" r="3.5"/>');
      var label = e.v.reason === "loop" ? "looped " + pct(e.v.from) + " → " + pct(e.v.to) : "reached " + pct(e.v.to);
      add('<text x="' + (ex - 6) + '" y="' + (m.t - 8) + '" text-anchor="end" class="plot__label-event">' + (narrow ? "swipe" : "swipe · " + label) + "</text>");
    });

    // Note where trust was dropped by the scrub.
    if (!narrow) {
      var scrub = samples.reduce(function (acc, smp, i) {
        if (acc || i === 0 || smp[1] == null || samples[i - 1][1] == null) return acc;
        var d = smp[1] - samples[i - 1][1];
        return d < -0.1 && smp[1] > 0.2 ? smp : null;
      }, null);
      if (scrub) {
        add('<text x="' + (x(scrub[0]) + 10) + '" y="' + (y(scrub[1]) + 18) + '" class="plot__label-strong">scrubbed back:</text>');
        add('<text x="' + (x(scrub[0]) + 10) + '" y="' + (y(scrub[1]) + 32) + '" class="plot__label-strong">trust dropped</text>');
      }
    }

    // Legend
    var ly = H - 8;
    add('<circle class="plot__learn" cx="' + (m.l + 4) + '" cy="' + (ly - 4) + '" r="3"/><text x="' + (m.l + 12) + '" y="' + ly + '">learning</text>');
    add('<circle class="plot__trust" cx="' + (m.l + 84) + '" cy="' + (ly - 4) + '" r="3"/><text x="' + (m.l + 92) + '" y="' + ly + '">trusted</text>');
    add("</svg>");
    return out.join("");
  }

  function plotFigure() {
    return '<div class="plot-frame"><div class="plot-wide">' + signalPlot(false) + '</div><div class="plot-narrow">' + signalPlot(true) + "</div></div>";
  }

  /* ------------------------------------------------------------------ *
   * Home
   * ------------------------------------------------------------------ */

  function renderHero() {
    var p = C.person, h = C.hero;
    $("[data-hero-label]").innerHTML = t(p.label) + "<span>" + t(p.location) + "</span>";
    var title = $("[data-hero-title]");
    title.innerHTML = '<span class="register"><span class="register__cyan" aria-hidden="true">' + t(h.headline) +
      '</span><span class="register__ink">' + t(h.headline) + "</span></span>";
    $("[data-hero-statement]").innerHTML = t(h.statement);
    $("[data-hero-actions]").innerHTML =
      '<a class="btn btn--ink" href="' + esc(h.primaryCta.href) + '">' + t(h.primaryCta.label) + icon("arrow") + "</a>" +
      '<a class="link-arrow" href="' + esc(h.secondaryCta.href) + '">' + t(h.secondaryCta.label) + icon("arrow") + "</a>";

    var plate = $("[data-hero-plate]");
    plate.setAttribute("data-reveal", "");
    mountLikeflow(plate, {
      id: "hero-plate",
      figNo: "Fig. 1",
      caption: "LikeFlow, rebuilt in HTML from the app's layout and strings, running its real completion logic. The TikTok feed is simulated at 3× speed.",
    });

    var register = function () {
      requestAnimationFrame(function () { requestAnimationFrame(function () { title.classList.add("is-registered"); }); });
    };
    if (doc.fonts && doc.fonts.ready) doc.fonts.ready.then(register); else register();
  }

  function renderAvailability() {
    var a = C.availability;
    $$("[data-availability]").forEach(function (el) {
      if (!a || !a.show) { el.hidden = true; return; }
      el.hidden = false;
      el.classList.toggle("availability--closed", !a.open);
      el.innerHTML = '<span class="availability__dot" aria-hidden="true"></span>' + t(a.label);
    });
  }

  function mediaMarkup(p) {
    var m = p.media || {};
    if (m.type === "likeflow") return plotFigure();
    if (m.type === "image" && m.src) {
      return '<div class="fpo" style="aspect-ratio:' + esc(m.ratio || "16/10") + '"><img src="' + esc(m.src) + '" alt="' + esc(m.alt || "") +
        '" loading="lazy" decoding="async"></div>';
    }
    return fpo(m.ratio, m.note || "Project image");
  }

  function renderWork() {
    var list = $("[data-work-list]");
    list.innerHTML = C.projects.map(function (p, i) {
      var num = "Plate " + pad2(i + 1);
      var caption = p.media && p.media.type === "likeflow"
        ? "Progress samples through LikeFlow's tracker. Synthetic data, real thresholds."
        : p.placeholder ? "Placeholder: replace with a real image." : (p.media && p.media.caption) || "";
      return '<li class="work-item work-item--' + esc(p.layout || "lead") + '">' +
        '<figure class="work-item__media plate">' +
          '<div class="work-item__frame marks" data-reveal><div class="work-item__clip">' + mediaMarkup(p) + "</div></div>" +
          '<figcaption class="caption"><b>' + num + "</b><span>" + t(caption) + "</span></figcaption>" +
        "</figure>" +
        '<div class="work-item__text">' +
          '<p class="work-item__meta">' + t(p.category) + " · " + t(p.year) + "</p>" +
          '<h3 class="work-item__title"><a href="#/work/' + esc(p.slug) + '">' + t(p.title) + "</a></h3>" +
          '<p class="work-item__summary">' + t(p.summary) + "</p>" +
          '<p class="work-item__role"><b>Role</b> ' + t(p.role) + "</p>" +
          (p.tools && p.tools.length ? '<ul class="tags" aria-label="Tools">' + p.tools.map(function (x) { return "<li>" + t(x) + "</li>"; }).join("") + "</ul>" : "") +
          '<span class="link-arrow" aria-hidden="true">' + (p.placeholder ? "Open the case-study template" : "Read the case study") + icon("arrow") + "</span>" +
        "</div></li>";
    }).join("");
  }

  function renderAbout() {
    var a = C.about;
    var portrait = $("[data-portrait]");
    portrait.setAttribute("data-reveal", "");
    portrait.innerHTML = a.portrait
      ? '<div class="fpo" style="aspect-ratio:4/5"><img src="' + esc(a.portrait) + '" alt="' + esc(a.portraitAlt) + '" loading="lazy" decoding="async"></div>'
      : fpo("4/5", "[PORTRAIT] 4:5");
    $("[data-about-intro]").innerHTML = a.intro.map(function (p) { return "<p>" + t(p) + "</p>"; }).join("");

    var links = [];
    if (usable(C.cv)) links.push('<a class="btn btn--line" href="' + esc(C.cv) + '" download>Download CV (PDF)' + icon("arrow") + "</a>");
    else links.push('<span class="about__cv-note"><span class="ph">[CV: add a PDF and set `cv` in js/content.js]</span></span>');
    C.socials.forEach(function (s) { if (usable(s.href)) links.push(link(s.href, s.label, "text-link")); });
    $("[data-about-links]").innerHTML = links.join("");

    $("[data-principles]").innerHTML = a.philosophy.map(function (p) {
      return "<div><dt>" + t(p.title) + "</dt><dd>" + t(p.body) + "</dd></div>";
    }).join("");
    $("[data-experience]").innerHTML = a.experience.map(function (e) {
      return '<li><span class="ledger__period">' + t(e.period) + '</span><span><span class="ledger__role">' + t(e.role) + "</span>, " + t(e.org) + "</span>" +
        '<span class="ledger__note">' + t(e.note) + "</span></li>";
    }).join("");
  }

  function renderCapabilities() {
    $("[data-capabilities] tbody").innerHTML = C.capabilities.map(function (c) {
      var ev = c.evidence || {};
      var evHtml = usable(ev.href) ? link(ev.href, ev.label, "text-link") : t(ev.label || "");
      return '<tr><th scope="row">' + t(c.area) + '</th><td class="spec__items">' +
        c.items.map(function (x) { return "<span>" + t(x) + "</span>"; }).join("") +
        "</td><td>" + evHtml + "</td></tr>";
    }).join("");
  }

  function renderProcess() {
    $("[data-process]").innerHTML = C.process.map(function (s, i) {
      return '<li class="stage">' +
        '<span class="stage__num">' + pad2(i + 1) + "</span>" +
        '<h3 class="stage__name">' + t(s.name) + "</h3>" +
        '<p class="stage__body">' + t(s.body) + "</p>" +
        '<dl class="stage__out"><dt>Output</dt><dd>' + t(s.output) + '</dd><dt>Example</dt><dd class="stage__eg">' + t(s.example) + "</dd></dl>" +
        "</li>";
    }).join("");
  }

  function renderContact() {
    var c = C.contact;
    $("[data-contact-title]").innerHTML = t(c.headline);
    $("[data-contact-invitation]").innerHTML = t(c.invitation);
    var emailEl = $("[data-contact-email]");
    if (usable(c.email)) {
      emailEl.innerHTML = '<a href="mailto:' + esc(c.email) + '">' + esc(c.email) + "</a>" +
        '<button class="copy-btn" type="button" data-copy>Copy</button>';
      $("[data-copy]", emailEl).addEventListener("click", function (e) {
        var btn = e.currentTarget;
        var done = function (ok) {
          btn.textContent = ok ? "Copied" : "Copy failed";
          announce(ok ? "Email address copied" : "Couldn't copy. Select the address instead.");
          setTimeout(function () { btn.textContent = "Copy"; }, 2000);
        };
        if (navigator.clipboard) navigator.clipboard.writeText(c.email).then(function () { done(true); }, function () { done(false); });
        else done(false);
      });
    } else {
      emailEl.innerHTML = '<span class="contact__email-missing">' + t(c.email) + "</span>";
    }
    var socials = C.socials.map(function (s) {
      return "<li>" + (usable(s.href) ? link(s.href, s.label) : "<span>" + t(s.label) + " " + t(s.href) + "</span>") + "</li>";
    }).join("");
    $("[data-socials]").innerHTML = socials;
    $("[data-footer-socials]").innerHTML = C.socials.filter(function (s) { return usable(s.href); }).map(function (s) {
      return "<li>" + link(s.href, s.label) + "</li>";
    }).join("");
    $("[data-year]").textContent = new Date().getFullYear();
  }

  /* ------------------------------------------------------------------ *
   * Case studies
   * ------------------------------------------------------------------ */

  function chapter(num, title, body, wide) {
    return '<section class="page-grid case__chapter" aria-labelledby="ch-' + num + '">' +
      '<header><span class="case__chapter-num">' + pad2(num) + '</span><h2 id="ch-' + num + '">' + esc(title) + "</h2></header>" +
      '<div class="' + (wide ? "case__wide case__body" : "case__body") + '">' + body + "</div></section>";
  }
  function paras(list) { return (list || []).map(function (p) { return "<p>" + rich(p) + "</p>"; }).join(""); }
  function checklist(list) { return '<ul class="checklist">' + list.map(function (x) { return "<li>" + rich(x) + "</li>"; }).join("") + "</ul>"; }

  function pipelineFigure() {
    return '<figure class="plate">' +
      '<ol class="pipeline">' +
        '<li><span class="pipeline__tier">1</span><span><strong>Accessibility tree</strong> A wide seek bar with range info, or a “0:12 / 0:45” label.</span></li>' +
        '<li><span class="pipeline__tier">2</span><span><strong>Screenshot, Android 11+</strong> TikTok\'s thin progress bar in the bottom 40 % of the screen.</span></li>' +
        '<li><span class="pipeline__tier">3</span><span><strong>Visual loop check</strong> Experimental and off by default: the picture snaps back to the first frame.</span></li>' +
        '<li class="pipeline__out"><span class="pipeline__tier">→</span><span><strong>CompletionTracker</strong> Trust after 3 forward samples, then one swipe and a 1.5 s cooldown.</span></li>' +
      "</ol>" +
      '<figcaption class="caption"><b>Fig. 2</b><span>The detection pipeline, polled every ~500 ms. Each tier runs only when the one above it finds nothing.</span></figcaption>' +
      "</figure>";
  }

  function codeFigure(code) {
    var KW = /\b(data|class|val|Float|Int|Long)\b/g;
    var body = code.lines.map(function (l) {
      return esc(l).replace(KW, '<span class="k">$1</span>');
    }).join("\n");
    return '<figure class="plate"><div class="codeblock"><pre><code>' + body + "</code></pre></div>" +
      '<figcaption class="caption"><b>Fig. 4</b><span>' + esc(code.caption) + "</span></figcaption></figure>";
  }

  function realCase(p) {
    var cs = p.caseStudy;
    var n = 0;
    var html = [];
    html.push(chapter(++n, "Overview", paras(cs.overview)));
    html.push(chapter(++n, "The problem", paras(cs.problem)));
    html.push(chapter(++n, "Goals and constraints",
      '<div class="twocol"><div><h3>Goals</h3>' + checklist(cs.goals.goals) + "</div><div><h3>Constraints</h3>" + checklist(cs.goals.constraints) + "</div></div>"));
    html.push(chapter(++n, "Research and design decisions",
      pipelineFigure() +
      '<ol class="decisions">' + cs.decisions.map(function (d) { return "<li><h3>" + rich(d.title) + "</h3><p>" + rich(d.body) + "</p></li>"; }).join("") + "</ol>" +
      '<h3>Safeguards against swiping at the wrong time</h3>' +
      '<div class="table-scroll"><table class="safeguards"><thead><tr><th scope="col">Situation</th><th scope="col">What LikeFlow does</th></tr></thead><tbody>' +
      cs.safeguards.map(function (r) { return '<tr><th scope="row">' + rich(r[0]) + "</th><td>" + rich(r[1]) + "</td></tr>"; }).join("") +
      "</tbody></table></div>"));
    html.push(chapter(++n, "Explorations and implementation",
      paras(cs.implementation) +
      '<figure class="plate case__live" data-case-live></figure>' +
      codeFigure(cs.code)));
    html.push(chapter(++n, "Outcome", paras(cs.outcome)));
    html.push(chapter(++n, "Lessons learned",
      '<dl class="principles">' + cs.lessons.map(function (l) { return "<div><dt>" + rich(l.title) + "</dt><dd>" + rich(l.body) + "</dd></div>"; }).join("") + "</dl>"));
    return {
      facts: cs.facts,
      figure: '<figure class="plate marks"><div class="plate-inner">' + plotFigure() + "</div>" +
        '<figcaption class="caption"><b>Fig. 1</b><span>One stretch of playback as the tracker sees it: three samples to earn trust, a loop and a swipe, a 1.5 s cooldown, a hand scrub that drops trust, then a finish. Synthetic samples, real thresholds.</span></figcaption></figure>',
      body: html.join(""),
    };
  }

  function templateCase(p) {
    var n = 0;
    var html = [];
    html.push(chapter(++n, "Overview", paras(["[Project context: who it was for, what existed before, and what you were responsible for.]"])));
    html.push(chapter(++n, "The problem", paras(["[The problem in the user's words. What was slow, confusing, broken or missing, and how you know.]"])));
    html.push(chapter(++n, "Goals and constraints",
      '<div class="twocol"><div><h3>Goals</h3>' + checklist(["[Goal]", "[Goal]"]) + "</div><div><h3>Constraints</h3>" + checklist(["[Constraint: time, platform, budget, legacy]", "[Constraint]"]) + "</div></div>"));
    html.push(chapter(++n, "Research and design decisions", paras(["[What you learned, and the two or three decisions it led to. Name the alternatives you rejected.]"])));
    html.push(chapter(++n, "Explorations and implementation",
      paras(["[Sketches, prototypes and how it was built. Show the work in progress, not just the final screens.]"]) +
      '<figure class="plate marks">' + fpo("16/9", "[PROCESS IMAGE] 16:9") + '<figcaption class="caption"><b>Fig. 2</b><span class="ph">[Caption]</span></figcaption></figure>'));
    html.push(chapter(++n, "Outcome", paras(["[Results. Only real, measurable outcomes: usage, time saved, errors reduced. If you don't have numbers, describe what shipped.]"])));
    html.push(chapter(++n, "Lessons learned", paras(["[What you'd do differently next time.]"])));
    return {
      facts: [["Platform", "[PLATFORM]"], ["Role", p.role], ["Year", p.year], ["Status", "[STATUS]"]],
      figure: '<figure class="plate marks">' + mediaMarkup(p) + '<figcaption class="caption"><b>Fig. 1</b><span class="ph">[Caption for the lead image]</span></figcaption></figure>',
      body: '<div class="page-grid"><p class="case__notice">This is a placeholder. Replace this project in <code>js/content.js</code> with real work, or delete it.</p></div>' + html.join(""),
    };
  }

  function renderCase(p) {
    var idx = C.projects.indexOf(p);
    var next = C.projects[(idx + 1) % C.projects.length];
    var data = p.caseStudy ? realCase(p) : templateCase(p);
    var links = (p.links || []).filter(function (l) { return usable(l.href); });
    return '<div class="page-grid"><a class="case__back" href="#work">' + icon("arrow") + "All work</a></div>" +
      '<header class="page-grid case__head">' +
        '<p class="case__meta">' + t(p.category) + " · " + t(p.year) + "</p>" +
        '<h1 class="case__title" id="case-title" tabindex="-1">' + t(p.title) + "</h1>" +
        '<p class="lead case__summary">' + t(p.summary) + "</p>" +
        '<dl class="case__facts">' +
          [["Role", p.role]].concat(data.facts.filter(function (f) { return f[0] !== "Role"; })).map(function (f) {
            return "<div><dt>" + t(f[0]) + "</dt><dd>" + t(f[1]) + "</dd></div>";
          }).join("") +
        "</dl>" +
        (links.length ? '<p class="case__links">' + links.map(function (l) { return '<a class="btn btn--line" href="' + esc(l.href) + '" target="_blank" rel="noopener">' + t(l.label) + '<span class="sr-only"> (opens in a new tab)</span>' + icon("arrow-out") + "</a>"; }).join("") + "</p>" : "") +
      "</header>" +
      '<div class="page-grid case__figure">' + data.figure + "</div>" +
      data.body +
      '<nav class="page-grid" aria-label="Next project"><a class="case__next" href="#/work/' + esc(next.slug) + '">' +
        '<span class="case__next-label">Next project</span>' +
        '<span class="case__next-title">' + t(next.title) + icon("arrow") + "</span></a></nav>";
  }

  /* ------------------------------------------------------------------ *
   * Routing: #/work/<slug> opens a case study, anything else is home
   * ------------------------------------------------------------------ */

  var homeView = $('[data-view="home"]');
  var caseView = $('[data-view="case"]');
  var baseTitle = doc.title;
  var lastCase = null;

  function swap(update) {
    if (doc.startViewTransition && !reduceMotion.matches) doc.startViewTransition(update);
    else update();
  }

  function route(initial) {
    var m = location.hash.match(/^#\/work\/([\w-]+)$/);
    if (m) {
      var p = C.projects.filter(function (x) { return x.slug === m[1]; })[0];
      if (!p) { location.replace("#work"); return; }
      lastCase = p.slug;
      var open = function () {
        caseView.innerHTML = renderCase(p);
        caseView.hidden = false;
        homeView.hidden = true;
        var live = $("[data-case-live]", caseView);
        if (live) mountLikeflow(live, { id: "case-live", figNo: "Fig. 3", caption: "The status and diagnostics cards with dry run available. Turn on dry run to see finishes logged without a swipe. Simulated feed, real completion logic." });
        window.scrollTo(0, 0);
        observeReveals(caseView);
        $("#case-title", caseView).focus({ preventScroll: true });
      };
      if (initial) open(); else swap(open);
      doc.title = (isPh(p.title) ? "Placeholder project" : p.title) + " · Case study · " + C.person.name;
      setActive("work");
      return;
    }

    var wasCase = !caseView.hidden;
    var target = location.hash && location.hash.length > 1 ? doc.getElementById(decodeURIComponent(location.hash.slice(1))) : null;
    if (wasCase) {
      var show = function () {
        caseView.hidden = true;
        caseView.innerHTML = "";
        homeView.hidden = false;
        if (target && target.id === "work" && lastCase) {
          var a = $('a[href="#/work/' + lastCase + '"]', homeView);
          if (a) {
            a.closest(".work-item").scrollIntoView({ block: "center" });
            a.focus({ preventScroll: true });
            return;
          }
        }
        if (target) target.scrollIntoView(); else window.scrollTo(0, 0);
      };
      swap(show);
      doc.title = baseTitle;
    } else if (initial && target) {
      // Content was rendered after the browser's own jump; go there again.
      target.scrollIntoView();
    }
  }

  /* ------------------------------------------------------------------ *
   * Navigation: scroll-aware slug line, active section, mobile menu
   * ------------------------------------------------------------------ */

  var slug = $("[data-slug]");
  var menu = $("[data-menu]");
  var toggle = $("[data-menu-toggle]");

  function setActive(id) {
    $$("[data-nav-link]").forEach(function (a) {
      if (a.getAttribute("data-nav-link") === id) a.setAttribute("aria-current", "true");
      else a.removeAttribute("aria-current");
    });
  }

  function initSlug() {
    var lastY = window.scrollY;
    var ticking = false;
    window.addEventListener("scroll", function () {
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(function () {
        var y = window.scrollY;
        slug.classList.toggle("is-scrolled", y > 8);
        if (!menu.hidden) { /* keep visible while the menu is open */ }
        else if (y > 160 && y > lastY + 4) slug.classList.add("is-hidden");
        else if (y < lastY - 4 || y <= 160) slug.classList.remove("is-hidden");
        lastY = y;
        ticking = false;
      });
    }, { passive: true });

    if ("IntersectionObserver" in window) {
      var io = new IntersectionObserver(function (entries) {
        if (!caseView.hidden) return;
        entries.forEach(function (e) {
          if (e.isIntersecting) setActive(e.target.getAttribute("data-section"));
        });
      }, { rootMargin: "-40% 0px -55% 0px" });
      $$("[data-section]").forEach(function (s) { io.observe(s); });
      new IntersectionObserver(function (entries) {
        if (entries[0].isIntersecting && caseView.hidden) setActive(null);
      }, { rootMargin: "0px 0px -70% 0px" }).observe($(".hero"));
    }
  }

  function focusables(container) {
    return $$('a[href], button:not([disabled]), input, textarea, [tabindex]:not([tabindex="-1"])', container)
      .filter(function (el) { return el.offsetParent !== null; });
  }

  function openMenu() {
    menu.hidden = false;
    toggle.setAttribute("aria-expanded", "true");
    $(".menu-toggle__label", toggle).textContent = "Close";
    slug.classList.remove("is-hidden");
    doc.body.style.overflow = "hidden";
    var first = focusables(menu)[0];
    if (first) first.focus();
  }
  function closeMenu(returnFocus) {
    if (menu.hidden) return;
    menu.hidden = true;
    toggle.setAttribute("aria-expanded", "false");
    $(".menu-toggle__label", toggle).textContent = "Menu";
    doc.body.style.overflow = "";
    if (returnFocus) toggle.focus();
  }

  function initMenu() {
    toggle.addEventListener("click", function () { if (menu.hidden) openMenu(); else closeMenu(true); });
    menu.addEventListener("click", function (e) { if (e.target.closest("a")) closeMenu(false); });
    doc.addEventListener("keydown", function (e) {
      if (menu.hidden) return;
      if (e.key === "Escape") { e.preventDefault(); closeMenu(true); return; }
      if (e.key !== "Tab") return;
      var items = [toggle].concat(focusables(menu));
      var i = items.indexOf(doc.activeElement);
      if (e.shiftKey && i <= 0) { e.preventDefault(); items[items.length - 1].focus(); }
      else if (!e.shiftKey && i === items.length - 1) { e.preventDefault(); items[0].focus(); }
    });
    window.matchMedia("(min-width: 56rem)").addEventListener("change", function (mq) { if (mq.matches) closeMenu(false); });
  }

  /* ------------------------------------------------------------------ *
   * Reveals: plates settle into register once, as they arrive
   * ------------------------------------------------------------------ */

  var revealIO = null;
  function initReveals() {
    if (reduceMotion.matches || !("IntersectionObserver" in window)) return;
    root.classList.add("js-reveal");
    revealIO = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) { e.target.classList.add("is-in"); revealIO.unobserve(e.target); }
      });
    }, { rootMargin: "0px 0px -8% 0px", threshold: 0.12 });
    observeReveals(doc);
  }
  function observeReveals(ctx) {
    if (!revealIO) return;
    $$("[data-reveal], .stage", ctx).forEach(function (el) {
      if (!el.classList.contains("is-in")) revealIO.observe(el);
    });
  }

  /* ------------------------------------------------------------------ *
   * Contact form
   * ------------------------------------------------------------------ */

  function initForm() {
    var form = $("[data-contact-form]");
    var status = $("[data-form-status]");
    var submit = $("[data-submit]");
    var submitLabel = $("[data-submit-label]");
    var touched = {};
    var EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

    var rules = {
      name: function (v) { return v.trim() ? "" : "Enter your name."; },
      email: function (v) {
        if (!v.trim()) return "Enter your email address so I can reply.";
        return EMAIL_RE.test(v.trim()) ? "" : "That address looks incomplete. Check for a missing @ or domain.";
      },
      message: function (v) {
        var n = v.trim().length;
        if (!n) return "Write a short message about your project.";
        return n < 20 ? "Add a little more detail (at least 20 characters, " + (20 - n) + " to go)." : "";
      },
    };

    function check(name) {
      var field = form.elements[name];
      var msg = rules[name](field.value);
      field.setAttribute("aria-invalid", msg ? "true" : "false");
      $('[data-error-for="' + name + '"]', form).textContent = msg;
      return !msg;
    }
    function setStatus(html, tone) {
      status.innerHTML = html;
      if (tone) status.setAttribute("data-tone", tone); else status.removeAttribute("data-tone");
    }

    Object.keys(rules).forEach(function (name) {
      var field = form.elements[name];
      field.addEventListener("blur", function () { if (field.value) touched[name] = true; if (touched[name]) check(name); });
      field.addEventListener("input", function () { if (touched[name]) check(name); });
    });

    form.addEventListener("submit", function (e) {
      e.preventDefault();
      var bad = Object.keys(rules).filter(function (name) { touched[name] = true; return !check(name); });
      if (bad.length) {
        setStatus(bad.length === 1 ? "One field needs fixing before this can send." : bad.length + " fields need fixing before this can send.", "error");
        form.elements[bad[0]].focus();
        return;
      }

      var data = {
        name: form.elements.name.value.trim(),
        email: form.elements.email.value.trim(),
        message: form.elements.message.value.trim(),
      };
      var c = C.contact;
      var github = C.socials.filter(function (s) { return /github/i.test(s.label) && usable(s.href); })[0];

      if (usable(c.formEndpoint)) {
        submit.setAttribute("aria-busy", "true");
        submit.disabled = true;
        submitLabel.textContent = "Sending…";
        setStatus("", null);
        fetch(c.formEndpoint, {
          method: "POST",
          headers: { "Content-Type": "application/json", Accept: "application/json" },
          body: JSON.stringify(data),
        }).then(function (res) {
          if (!res.ok) throw new Error(String(res.status));
          setStatus("Thanks, " + esc(data.name) + ". Your message is on its way, and I'll reply to " + esc(data.email) + ".", "success");
          form.reset();
          touched = {};
          $$("[aria-invalid]", form).forEach(function (f) { f.removeAttribute("aria-invalid"); });
        }).catch(function () {
          setStatus("Your message didn't send. That's a network or server problem, not something you did. Try again" +
            (usable(c.email) ? ', or email <a href="mailto:' + esc(c.email) + '">' + esc(c.email) + "</a>." : "."), "error");
        }).then(function () {
          submit.removeAttribute("aria-busy");
          submit.disabled = false;
          submitLabel.textContent = "Send message";
        });
        return;
      }

      if (usable(c.email)) {
        var href = "mailto:" + encodeURIComponent(c.email) +
          "?subject=" + encodeURIComponent("Project enquiry from " + data.name) +
          "&body=" + encodeURIComponent(data.message + "\n\n— " + data.name + " (" + data.email + ")");
        window.location.href = href;
        setStatus("Your email app should open with the message filled in. If nothing happened, write to " +
          '<a href="mailto:' + esc(c.email) + '">' + esc(c.email) + "</a> directly.", "success");
        return;
      }

      setStatus("This form isn't connected yet because no contact email has been set." +
        (github ? ' You can reach Jay Marc on <a href="' + esc(github.href) + '" target="_blank" rel="noopener">GitHub</a> in the meantime.' : ""), "error");
    });
  }

  /* ------------------------------------------------------------------ *
   * Boot
   * ------------------------------------------------------------------ */

  renderHero();
  renderAvailability();
  renderWork();
  renderAbout();
  renderCapabilities();
  renderProcess();
  renderContact();
  initReveals();
  initSlug();
  initMenu();
  initForm();
  route(true);
  window.addEventListener("hashchange", function () { closeMenu(false); route(false); });
})();
