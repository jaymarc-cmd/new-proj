# Jay Marc Merilo · Portfolio

Personal portfolio for Jay Marc Merilo, Android & Frontend Developer. It's plain static HTML, CSS and JavaScript, with no build step and no dependencies.

The design is laid out like a printer's **press proof**, the stage where work gets checked before it goes to press: crop marks around each project, a slug line as navigation, one process-cyan accent, and a headline whose cyan plate slides into register when the page loads. The hero runs a live rebuild of LikeFlow's status card using the app's real completion logic.

## Structure

```
site/                  ← everything that gets deployed
├── index.html         page shell and metadata
├── 404.html
├── js/content.js      ← ALL personal content: edit this file
├── js/main.js         rendering, routing, interactions
├── css/tokens.css     design tokens (colour, type, space, motion)
├── css/site.css       components and layout
├── fonts/             self-hosted Schibsted Grotesk + Fragment Mono (OFL)
└── img/               favicon, social share image
PRODUCT.md             who the site is for and what it must never claim
DESIGN.md              the design system, for future changes
.impeccable/           design-direction notes (not deployed)
```

## Personalising it

Open `site/js/content.js`. Anything in `[SQUARE BRACKETS]` is a placeholder. On the page, placeholders show with a pale cyan "proof correction" highlight so nobody mistakes them for real content.

Things to fill in:

| Field | What it does |
|---|---|
| `contact.email` | Email link, copy button, and the contact form's destination |
| `contact.formEndpoint` | Optional. A Formspree/Basin/Getform URL that accepts a JSON POST. Leave empty and the form opens the visitor's email app instead |
| `socials` | LinkedIn and any other profiles. Entries left as placeholders aren't shown as links |
| `person.location`, `contact.invitation` | Your city and typical reply time |
| `about.intro[1]`, `about.experience` | Your current situation and real roles |
| `about.portrait`, `cv` | Paths to a portrait image and a CV PDF (put the files in `site/img/` and `site/cv/`) |
| `availability` | The nav indicator. Set `show: false` to hide it |
| `projects` | LikeFlow is real. The other three are placeholder projects: replace them or delete them |

Adding a project: copy a placeholder entry, give it a unique `slug`, and set `media` to `{ type: "image", src: "img/your-shot.jpg", alt: "…", ratio: "16/10" }`. Add a `caseStudy` object (see LikeFlow's for the shape) to get a full case-study page. Without one, the project opens a marked template.

**Don't add claims you can't back up.** There are deliberately no invented clients, metrics or testimonials. The Capabilities table asks each line to point at where it's shown.

Also update in `site/index.html`: `og:url` and `og:image` (use absolute URLs once the site has an address), plus the description if you change the headline.

## Previewing locally

Open `site/index.html` directly in a browser, or serve the folder:

```bash
cd site && python3 -m http.server 8000   # → http://localhost:8000
```

## Deploying

- **GitHub Pages:** the included workflow (`.github/workflows/pages.yml`) publishes `site/` on every push to `main`. Enable it once under *Settings → Pages → Source: GitHub Actions*.
- **Netlify / Vercel / Cloudflare Pages:** set the publish (output) directory to `site` with no build command.

## Accessibility and performance notes

- Keyboard: skip link, visible focus everywhere, an Esc-closable focus-trapped mobile menu, and focus moved to the heading when a case study opens.
- `prefers-reduced-motion`: no register slide, no reveals, and the LikeFlow demo waits for *Play demo* instead of autoplaying. Content is always visible without animation, and without JavaScript a `<noscript>` note shows instead.
- The demo pauses itself when it's off-screen or the tab is hidden, and it always has a pause button.
- Total page weight is under 200 KB uncompressed, both fonts included, with no third-party requests.
