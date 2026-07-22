# Day 7 — Student Guide

> **Trainer-facing equivalent:** [TrainersGuide/day7/README.md](../../TrainersGuide/day7/README.md)
> **Module:** HTML/CSS Module 2 + JS Advanced + React Module 1 — Frontend Foundations

## What you'll build today

Day 7 is the pivot from backend to browser. The morning is two
back-to-back theory blocks — Advanced CSS first (Flexbox, custom
properties, animations, responsive) and JavaScript Advanced second
(ES6+ classes, advanced DOM, Promises / async-await / Web Workers).
After lunch you sit down for React Module 1 theory (Vite/CRA project
setup, JSX + props, the `useMemo` / `useCallback` / `useReducer` trio,
and composition primitives), then spend the rest of the afternoon
inside Workshop 7. By 16:45 your static dashboard should have a
Flexbox shell, design-token theming with a dark-mode toggle, animated
real-time trade cards driven by a Server-Sent Events feed, a
responsive layout that survives a 375px phone, and a sortable +
resizable + frozen-header trades table. **Flexbox is the chosen layout
primitive today** — CSS Grid is a stretch goal only, ignore it on your
first pass. The SSE live feed is the headline deliverable, and the
React M1 scaffold is your launchpad into tomorrow's React M2+M3 work
where the static dashboard gets reborn as a real component tree.

## Day at a glance

| #    | Block                                                          | Exercises       |
|---------------|----------------------------------------------------------------|-----------------|
| 1 | Standup (confirm `/api/v1/trades/stream` is reachable)         | —               |
| 2 | AM — HTML/CSS Module 2 (Advanced CSS) theory + Percipio        | —               |
| 3 | Break                                                          | —               |
| 4 | AM — JavaScript Advanced theory + Percipio                     | —               |
| 5 | Lunch                                                          | —               |
| 6 | PM theory — React Module 1: Advanced Foundations               | —               |
| 7 | Workshop 7 — Frontend Foundations + SSE Feed                   | TICKET-ADV098 – TICKET-ADV106 |
| 8 | End-of-day debrief + Day-8 preview                             | —               |

## Exercises

Workshop 7 is **9 hands-on exercises** in a single 2-hour-15-minute
block. Pace yourself at roughly 15 minutes per exercise — TICKET-ADV104
through TICKET-ADV106 (SSE feed and the advanced table) are the longest, so
do not bleed time on the early CSS exercises. Each exercise below has
three hints: Hint 1 nudges you in the right direction, Hint 2 points
at a specific file or API, and Hint 3 sketches the shape of the
answer. Open hints in order. If you find yourself opening Hint 3 on
every exercise, slow down and ask a neighbour — the hint ladder is
designed so most people finish on Hint 1.

### Workshop 7 — Frontend Foundations + SSE Feed

### TICKET-ADV098 — Flexbox layout: sidebar, header, 3-col main, footer

**Goal:** Build the static-dashboard page shell using nested Flexbox containers — header on top, sidebar + main row in the middle, footer at the bottom, with a 3-card grid inside the main area.

**What**
- `static-dashboard/dashboard.html` plus `css/style.css` carrying a nested-Flexbox `.app-shell` (column) wrapping `.app-header`, `.app-body` (row containing `.app-sidebar` + `.app-main`), `.app-footer`, with `.dashboard-grid` wrapping three `.stat-card` children inside main.

**Why**
- This shell is the canvas every later Day 7 ticket paints on — design tokens (ADV099), the theme toggle (ADV100), the SSE feed area (ADV101), responsive breakpoints (ADV103), and tomorrow's React component tree (Day 8) all hang off these landmarks.

**Observe**
- DevTools → Elements → click `<body>` → the Flexbox badge shows `column` main-axis; the footer pins to the viewport bottom even when `.app-main` is empty.

**Done when:**
- `dashboard.html` renders with a fixed-width sidebar that does not shrink at narrow viewports.
- The header sits at the top, the footer at the bottom of the viewport even when content is short.
- The main content area shows three cards across at desktop width using a single Flexbox row that wraps.

<details>
<summary>Hint 1 — gentle direction</summary>

Think of the shell as two nested 1-D flows: an outer vertical stack and an inner horizontal band. Open DevTools, click the `<body>` element, and look for the Flexbox overlay badge — does the main axis run the direction you expect?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Work in `static-dashboard/dashboard.html` and `css/style.css`. The outer container needs `flex-direction: column`, the middle band is its own flex row, and the 3-card area is a third nested flex container with `flex-wrap` turned on. Don't forget `html, body { height: 100% }` or the footer will float.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

The sidebar's lock is `flex: 0 0 240px` — `flex-shrink: 0` is the bit students miss because the default is `1`. The middle band needs `flex: 1 1 auto` so it claims remaining vertical space. The card grid children use `flex-basis: calc((100% - gap * 2) / 3)` with a `min-width` floor so they collapse cleanly.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `static-dashboard/dashboard.html` with `<header class="app-header">`, `<div class="app-body">` containing `<aside class="app-sidebar">` + `<main class="app-main">`, and `<footer class="app-footer">` — wrap all four inside `<body class="app-shell">`.
2. Add `<section class="dashboard-grid">` inside `.app-main` with three card children.
3. In `static-dashboard/css/style.css`, set `html, body { height: 100%; margin: 0 }` and make `.app-shell` a `flex-direction: column` container with `min-height: 100vh`.
4. Give `.app-header` `flex: 0 0 64px` and `.app-footer` `flex: 0 0 48px` — fixed sizes that won't grow or shrink.
5. Make `.app-body` `display: flex; flex: 1 1 auto; min-height: 0` so the middle band fills remaining vertical space.
6. Lock `.app-sidebar` with `flex: 0 0 240px` (no-grow, no-shrink, 240px basis) and let `.app-main` take `flex: 1 1 auto`.
7. Style `.dashboard-grid` as `display: flex; flex-wrap: wrap; gap: var(--space-4)` and give children `flex: 1 1 calc((100% - var(--space-4) * 2) / 3); min-width: 240px`.
8. Open DevTools, click `<body>`, confirm the Flex overlay shows a column main-axis, and verify the footer pins to the viewport bottom on a short page.

**Reference solution** (`static-dashboard/dashboard.html`):

```html
<!-- File: static-dashboard/dashboard.html -->
<!doctype html>
<!--
============================================================================
CSS Grid page shell (sidebar + header + main + footer)
Semantic HTML5 (header/nav/main/section/article/aside/footer)
ARIA roles + aria-live for the trade feed
============================================================================
-->
<html lang="en" data-theme="light">
<head>
  <meta charset="utf-8" />
  <title>ReconX (static) — Dashboard</title>
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <link rel="stylesheet" href="css/style.css" />
</head>
<body>
  <header class="layout__header" role="banner">
    <h1 class="layout__title">ReconX</h1>
    <nav class="layout__nav" aria-label="Main navigation">
      <a href="dashboard.html" aria-current="page">Dashboard</a>
      <a href="trades.html">Trades</a>
      <a href="recon.html">Recon</a>
    </nav>
    <button id="theme-toggle" aria-label="Toggle light/dark theme">🌗</button>
  </header>

  <aside class="layout__sidebar" role="complementary">
    <h2>Quick filters</h2>
    <ul>
      <li><a href="#">All trades</a></li>
      <li><a href="#">Open breaks</a></li>
      <li><a href="#">Today</a></li>
    </ul>
  </aside>

  <main class="layout__main" role="main">
    <section aria-labelledby="kpi">
      <h2 id="kpi">Today at a glance</h2>
      <div class="stat-grid">
        <article class="stat-card"><h3>Trades</h3><p>120</p></article>
        <article class="stat-card"><h3>Matched</h3><p>105</p></article>
        <article class="stat-card stat-card--warn"><h3>Open breaks</h3><p>15</p></article>
        <article class="stat-card"><h3>Notional</h3><p>$24.3M</p></article>
      </div>
    </section>

    <section aria-labelledby="feed">
      <h2 id="feed">Live trade feed</h2>
      <!--  aria-live so screen readers announce new trades -->
      <div id="trade-feed" role="status" aria-live="polite" aria-atomic="false"></div>
    </section>
  </main>

  <footer class="layout__footer" role="contentinfo">
    <small>ReconX TDI 2026 · static dashboard (Day 7)</small>
  </footer>

  <script src="js/theme.js"></script>
  <script src="js/sse.js"></script>
</body>
</html>
```

**Reference solution** (`static-dashboard/css/style.css`):

```css
/* ============================================================================
   TICKET-ADV099 / TICKET-ADV100 — CSS custom properties + dark theme via data-theme
   animations (fade-in, slide-in, pulse)
   responsive breakpoints (desktop -> tablet -> mobile)
   BEM naming: block__element--modifier
   ============================================================================ */
:root {
  --color-primary: #003366;
  --color-gold:    #C8A951;
  --color-slate:   #2E5FA3;
  --color-success: #28a745;
  --color-warning: #ffc107;
  --color-danger:  #dc3545;
  --color-bg:      #ffffff;
  --color-text:    #1a1a1a;
  --color-surface: #f5f5f5;
  --color-border:  #d4d4d4;
  --space-1: 4px; --space-2: 8px; --space-3: 12px; --space-4: 16px;
  --radius:  4px;
}

* { box-sizing: border-box; }
body {
  margin: 0;
  background: var(--color-bg);
  color: var(--color-text);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  /* page shell grid */
  display: grid;
  grid-template-columns: 240px 1fr;
  grid-template-rows: 64px 1fr 40px;
  grid-template-areas:
    "header  header"
    "sidebar main"
    "footer  footer";
  min-height: 100vh;
}

.layout__header  { grid-area: header;  display: flex; align-items: center; gap: var(--space-4); padding: 0 var(--space-4); background: var(--color-primary); color: #fff; }
.layout__title   { margin: 0; font-size: 18px; }
.layout__nav     { display: flex; gap: var(--space-3); margin-left: auto; }
.layout__nav a   { color: #fff; text-decoration: none; }
.layout__nav a[aria-current="page"] { border-bottom: 2px solid var(--color-gold); }
.layout__sidebar { grid-area: sidebar; padding: var(--space-3); border-right: 1px solid var(--color-border); background: var(--color-surface); }
.layout__main    { grid-area: main;    padding: var(--space-4); }
.layout__footer  { grid-area: footer;  display: flex; align-items: center; justify-content: center; border-top: 1px solid var(--color-border); }
```

</details>

**▶ Run the project — verify TICKET-ADV098 end-to-end**

Serve the static dashboard and check the app-shell grid + ARIA landmarks render correctly.

```bash
cd static-dashboard && python3 -m http.server 5500
# then open http://localhost:5500/dashboard.html
```

**Observe:**

- Header, sidebar, main, and footer are laid out as a grid — header spans the top, sidebar on the left, main beside it, footer pinned to the bottom.
- DevTools inspector shows `<header role="banner">`, `<main role="main">`, `<aside role="complementary">`, `<footer role="contentinfo">` — landmarks announce correctly to a screen reader.
- Run `npx @axe-core/cli http://localhost:5500/dashboard.html` — no violations.
- Failure signal: a flat single-column stack with no sidebar means the `grid-template-areas` rule isn't applying — check the `body { display: grid }` selector.

---

### TICKET-ADV099 — CSS custom properties (design tokens)

**Goal:** Replace every hardcoded colour, spacing value, radius and shadow in your stylesheet with CSS custom properties defined on `:root`, so the next two exercises (theming and animations) can reference them.

**What**
- A `:root { }` block at the top of `static-dashboard/css/style.css` defining brand (`--color-primary`, `--color-gold`), surface (`--color-bg`, `--color-surface`, `--color-border`, `--color-text`), 4px-step spacing (`--space-1`…`--space-4`), and `--radius` tokens, with every component rule rewritten to read via `var(...)`.

**Why**
- Tokens are the seam that makes ADV100's dark theme a one-line `[data-theme="dark"]` override instead of a stylesheet rewrite, and Day 8's React components will inherit the same vars without duplicating colour literals.

**Observe**
- DevTools → Elements → select `<html>` → Computed pane lists `--color-primary`, `--color-surface`, `--space-4`, `--radius` on `:root`; a grep for `#` in component rules returns nothing.

**Done when:**
- Brand colours, surfaces, spacing scale (4px base), radius and shadow tokens all live in a `:root { ... }` block at the top of `style.css`.
- No component rule contains a hex colour, an `rgba(...)`, or a raw pixel spacing value — everything goes through `var(--token-name)`.
- The token names follow a consistent convention (e.g. `--color-primary`, `--space-4`, `--radius`, `--shadow-sm`).

<details>
<summary>Hint 1 — gentle direction</summary>

CSS custom properties are scoped — define them where the broadest set of children can read them. Which selector matches every element in your document?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

The syntax is `--name: value;` for the declaration and `var(--name)` for the read. Define them on `:root` (not on `.card` or `body`). The Percipio lab from this morning covers the fallback form `var(--name, default)` — wire that in for any token that might be missing.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Group the tokens by purpose: brand colours (`--color-primary`, `--color-success`, `--color-danger`), surfaces (`--color-bg`, `--color-bg-alt`, `--color-border`, `--color-text`, `--color-text-muted`), a 4px-step spacing scale (`--space-1` through `--space-6`), `--radius` plus `--radius-lg`, and two shadow tokens. Type sizes (`--font-size-sm/md/lg`) are worth tokenising too.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Open `static-dashboard/css/style.css` and add a `:root { }` block at the very top of the file — before any component rules.
2. Declare brand colours: `--color-primary` (DB navy `#003366`), `--color-primary-2`, `--color-success`, `--color-warning`, `--color-danger`.
3. Declare surface tokens: `--color-bg`, `--color-bg-alt`, `--color-border`, `--color-text`, `--color-text-muted`.
4. Add the 4px-step spacing scale `--space-1: 4px;` through `--space-6: 32px;`.
5. Add `--radius`, `--radius-lg`, `--shadow-sm`, `--shadow-md`, and three type-size tokens `--font-size-sm/md/lg`.
6. Search your stylesheet for `#` and any raw `px` spacing — replace each with the matching `var(--token-name)`.
7. Reload the page and confirm nothing visually changed — token swap is purely a refactor.

**Reference solution** (`static-dashboard/css/style.css`):

```css
/* File: static-dashboard/css/style.css — top of file */
:root {
  --color-primary: #003366;
  --color-gold:    #C8A951;
  --color-slate:   #2E5FA3;
  --color-success: #28a745;
  --color-warning: #ffc107;
  --color-danger:  #dc3545;
  --color-bg:      #ffffff;
  --color-text:    #1a1a1a;
  --color-surface: #f5f5f5;
  --color-border:  #d4d4d4;
  --space-1: 4px; --space-2: 8px; --space-3: 12px; --space-4: 16px;
  --radius:  4px;
}
```

</details>

**▶ Run the project — verify TICKET-ADV099 end-to-end**

Serve the dashboard and inspect computed styles to confirm every value flows through `:root` tokens.

```bash
cd static-dashboard && python3 -m http.server 5500
# then open http://localhost:5500/dashboard.html
```

**Observe:**

- DevTools → Elements → select `<html>` → Computed pane shows `--color-gold`, `--color-slate`, `--color-surface`, `--space-4`, `--radius`, etc. all defined on `:root`.
- Inspect a `.trade-card` (or any styled element) — its computed `background-color` resolves from `var(--color-surface)`, not a hex literal.
- Failure signal: any component rule still showing a `#abc123` in the Styles pane means the refactor missed a hardcoded value.

---

### TICKET-ADV100 — Dark/light theme toggle

**Goal:** Add a toggle button that flips the whole dashboard between light and dark themes by mutating a single attribute on `<html>`, with the user's choice persisted across reloads and no flash of unstyled content.

**What**
- `static-dashboard/js/theme.js` carrying a head-loaded IIFE that reads `localStorage.getItem('reconx-theme')` and sets `data-theme` on `<html>` before paint, plus a `[data-theme="dark"]` block in `css/style.css` overriding the ADV099 surface tokens, wired to a `#theme-toggle` button click handler.

**Why**
- Persisted theme + zero-FOUC is the canonical demo of cascade-aware tokens — this exact attribute-flip pattern reappears on Day 8 where a React `useEffect` will drive the same `data-theme` mutation.

**Observe**
- DevTools → Application → Local Storage → key `reconx-theme` holds `dark` after toggling; a reload at "Slow 3G" shows the dark background paint without a white flash.

**Done when:**
- Clicking the toggle swaps `data-theme="light"` and `data-theme="dark"` on the `<html>` element.
- The preference survives a full page reload (test with DevTools → Application → Local Storage).
- On reload there is **no white flash** before the dark theme applies — the theme is set before the body paints.

<details>
<summary>Hint 1 — gentle direction</summary>

Custom properties live in the cascade. If you can re-declare them under a more specific selector, the cascade re-resolves at runtime. Which selector on `<html>` can you flip with one JS line?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Use a `[data-theme="dark"]` block in `style.css` that overrides the colour/surface/shadow tokens — components don't need any changes if they all read from `var(...)`. Persist the choice in `localStorage`, not `sessionStorage`. The FOUC fix lives in a tiny inline `<script>` placed in `<head>` **before** the stylesheet link.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

The head-of-document snippet is an IIFE that reads `localStorage.getItem('reconx-theme')`, defaults to `'light'`, and calls `documentElement.setAttribute('data-theme', saved)`. The toggle handler reads the current attribute, computes the next value, writes back to both the attribute and `localStorage`, and updates `aria-pressed` on the button. Two `setAttribute` calls and one `setItem`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. In `dashboard.html` `<head>`, add an inline `<script>` IIFE **before** the `<link rel="stylesheet">` that reads `localStorage.getItem('reconx-theme')` (default `'light'`) and writes it to `document.documentElement` via `setAttribute('data-theme', saved)`.
2. In `style.css`, add a `[data-theme="dark"]` block that overrides the surface tokens (`--color-bg`, `--color-bg-alt`, `--color-border`, `--color-text`, `--color-text-muted`) and shadow tokens.
3. Confirm components only ever read tokens (`background: var(--color-bg)`) — no hex literals.
4. Add `<button id="theme-toggle" aria-pressed="false">Theme</button>` somewhere in the header.
5. In `js/dashboard.js`, attach a click handler that reads the current `data-theme` from `<html>`, computes the opposite, calls `setAttribute('data-theme', next)`, persists with `localStorage.setItem('reconx-theme', next)`, and updates `aria-pressed`.
6. Reload with `data-theme="dark"` saved — verify zero white flash by throttling network to "Slow 3G" in DevTools.

**Reference solution** (`static-dashboard/css/style.css`):

```css
/* File: static-dashboard/css/style.css — theme overrides */
[data-theme="dark"] {
  --color-bg:      #1a1a1a;
  --color-text:    #f5f5f5;
  --color-surface: #2a2a2a;
  --color-border:  #3a3a3a;
}
```

**Reference solution** (`static-dashboard/js/theme.js`):

```js
// File: static-dashboard/js/theme.js
// theme toggle, persisted to localStorage; first paint reads
// the persisted value to avoid a FOUC flash of the wrong theme.
(function () {
  const stored = localStorage.getItem('reconx-theme') || 'light';
  document.documentElement.dataset.theme = stored;

  document.addEventListener('DOMContentLoaded', () => {
    const btn = document.getElementById('theme-toggle');
    btn && btn.addEventListener('click', () => {
      const next = document.documentElement.dataset.theme === 'light' ? 'dark' : 'light';
      document.documentElement.dataset.theme = next;
      localStorage.setItem('reconx-theme', next);
    });
  });
})();
```

</details>

**▶ Run the project — verify TICKET-ADV100 end-to-end**

Serve the dashboard, click the theme toggle, and confirm the attribute flip + persistence.

```bash
cd static-dashboard && python3 -m http.server 5500
# then open http://localhost:5500/dashboard.html
```

**Observe:**

- Click `#theme-toggle` — `<html data-theme="light">` flips to `data-theme="dark"`, background/text colours invert immediately.
- DevTools → Application → Local Storage → `http://localhost:5500` — key `reconx-theme` holds the current value (`dark` or `light`).
- Reload the page — the saved theme reapplies before paint, no white flash.
- Failure signal: a brief white flash on reload means the inline IIFE is loading after the stylesheet — move it above `<link rel="stylesheet">` in `<head>`.

---

### TICKET-ADV101 — Real-time trade feed area with slide-in animation

**Goal:** Carve out a dedicated feed column in the dashboard that will host live trade cards, and style each `.trade-card` with an entrance animation that fires when the card is inserted.

**What**
- `<section id="trade-feed">` inside `.app-main` plus a `.trade-card` rule in `css/style.css` (using ADV099 tokens for padding/border/radius, with `.trade-card--matched` / `.trade-card--break` left-border modifiers) carrying `animation: slide-in 0.3s ease-out` against a `@keyframes slide-in` block.

**Why**
- This is the visual landing pad for the ADV104 `EventSource` stream and ADV105 prepend logic — get the markup and entrance animation right now so the SSE wiring has nothing to fight against.

**Observe**
- Paste `<div class="trade-card trade-card--matched">test</div>` into `#trade-feed` via DevTools — the card slides in from the left and shows a green `var(--color-success)` left border.

**Done when:**
- A `<section id="trade-feed">` (or similar) exists in `dashboard.html` and is laid out as part of the main area without breaking the TICKET-ADV098 grid.
- A `.trade-card` class is styled with padding, a status-coloured left border, and a clean visual hierarchy using your TICKET-ADV099 tokens.
- Newly inserted cards play a slide-in entrance animation — visible when you manually add a `<div class="trade-card">` in DevTools.

<details>
<summary>Hint 1 — gentle direction</summary>

Animations that the GPU can composite (transform, opacity) are jank-free; layout-changing properties (top, left, width) are not. Which transform produces a horizontal slide?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Define `@keyframes slide-in` in `style.css`. The `from` state uses `translateX` of a small negative value plus `opacity: 0`; the `to` state is the resting position. Apply the animation to `.trade-card` itself so any newly-inserted card animates automatically without extra JS.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`animation: slide-in 0.3s ease-out` on `.trade-card`. The card itself uses `var(--color-bg)`, `var(--color-border)`, a 4px-wide status border on the left (via `border-left`), `var(--radius)`, and `var(--space-3)` padding. The feed column is a vertical stack — a plain `flex-direction: column` container with a `gap` from your spacing tokens.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. In `dashboard.html`, add `<section id="trade-feed" class="trade-feed"></section>` inside the main content area (it will be filled by SSE in TICKET-ADV104).
2. In `style.css`, add a `@keyframes slide-in` rule that animates `from { transform: translateX(-20px); opacity: 0 }` to `to { transform: translateX(0); opacity: 1 }`.
3. Style `.trade-card` with `background: var(--color-bg)`, a 1px `var(--color-border)`, a `border-left: 4px solid var(--color-text-muted)` for the status stripe, `var(--radius)`, and `var(--space-3)` padding.
4. Apply `animation: slide-in 0.3s ease-out` to `.trade-card` so any newly-inserted card animates on insert.
5. Style `.trade-feed` as `display: flex; flex-direction: column; gap: var(--space-2)`.
6. Manually paste `<div class="trade-card">test</div>` into `#trade-feed` via DevTools and confirm it slides in from the left.

**Reference solution** (`static-dashboard/css/style.css`):

```css
/* File: static-dashboard/css/style.css — TICKET-ADV101 / TICKET-ADV102 live feed slide-in */
#trade-feed { display: flex; flex-direction: column; gap: var(--space-2); margin-top: var(--space-3); max-height: 50vh; overflow-y: auto; }
.trade-card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: var(--space-2) var(--space-3); animation: slide-in 0.3s ease-out; }
.trade-card--matched { border-left: 4px solid var(--color-success); }
.trade-card--break   { border-left: 4px solid var(--color-danger); }
@keyframes slide-in  { from { transform: translateX(-10%); opacity: 0; } to { transform: translateX(0); opacity: 1; } }
```

</details>

**▶ Run the project — verify TICKET-ADV101 end-to-end**

Serve the dashboard, watch the demo trade events populate, and confirm status-coloured borders + entrance animation.

```bash
cd static-dashboard && python3 -m http.server 5500
# then open http://localhost:5500/dashboard.html
```

**Observe:**

- Three demo cards appear in `#trade-feed` on staggered 500ms ticks — each slides in from the left via the `translateX(-10%) → 0` keyframe.
- A `.trade-card--matched` card has a green (`--color-success`) left border; a `.trade-card--break` card has a red (`--color-danger`) left border.
- Manually paste `<div class="trade-card trade-card--matched">test</div>` into `#trade-feed` via DevTools — it slides in immediately.
- Failure signal: cards appear with no animation — confirm `animation: slide-in 0.3s ease-out` is on `.trade-card` (not on a parent) and the `@keyframes` block is defined.

---

### TICKET-ADV102 — CSS animations: fade-in, slide-in, pulse

**Goal:** Build out the animation library — three named keyframes (`slide-in`, `fade-in`, `pulse`) — and wire `pulse` to a danger-state alert. Honour `prefers-reduced-motion` so the animations disable for users who request it.

**What**
- Three `@keyframes` blocks (`slide-in`, `fade-in`, `pulse`) in `css/style.css`, a `.alert--danger` rule using `animation: pulse 2s ease-in-out infinite`, and a bottom-of-file `@media (prefers-reduced-motion: reduce)` block resetting `animation` / `transition` on `*, *::before, *::after`.

**Why**
- ADV105 reuses `slide-in` + `fade-in` via the `.trade-card--new` modifier on every SSE prepend, and the reduced-motion override is the accessibility hook the Day 10 axe-core run will check — wire it once here, never touch it again.

**Observe**
- DevTools → Rendering panel → "Emulate CSS prefers-reduced-motion: reduce" → reload — the `.alert--danger` pulse halts mid-animation and trade-card entrances skip.

**Done when:**
- Three `@keyframes` blocks exist: `slide-in`, `fade-in`, `pulse`.
- An `.alert--danger` (or equivalent) class uses `pulse` on a 2s infinite loop and is visible somewhere on the page.
- A `@media (prefers-reduced-motion: reduce)` block reduces all animation durations to a near-zero value — verify by toggling "Emulate CSS prefers-reduced-motion: reduce" in DevTools.

<details>
<summary>Hint 1 — gentle direction</summary>

Pulse means "grow slightly then shrink back". Which transform changes size without triggering layout? And how do you describe a back-and-forth in keyframe percentages?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

`pulse` is a three-stop keyframe (0%, 50%, 100%) using `transform: scale(...)` and an expanding `box-shadow` ring. The reduced-motion media query goes at the **bottom of the stylesheet** and uses the universal selector to override every animation. Test the toggle in DevTools → Rendering panel.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Pulse keyframes: 0% and 100% are identical (scale 1, shadow ring at full opacity zero-radius); 50% is scale ~1.02 with the shadow ring expanded to ~8px and fully transparent. The reduced-motion override sets `animation-duration: 0.01ms`, `animation-iteration-count: 1`, and `transition-duration: 0.01ms` with `!important` on `*, *::before, *::after`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. In `style.css`, declare `@keyframes fade-in` (`opacity: 0 → 1`).
2. Declare `@keyframes pulse` with three stops — 0% and 100% identical (`scale(1)`, `box-shadow` ring at full colour and zero spread); 50% is `scale(1.02)` and the shadow expanded to `0 0 0 8px rgba(220,53,69,0)`.
3. Add a `.trade-card--new` modifier that runs `slide-in 0.4s ease-out, fade-in 0.4s ease-out` (combined entrance — used by TICKET-ADV105).
4. Add `.alert--danger` styled with `animation: pulse 2s ease-in-out infinite`, and place a sample alert (e.g. an "Unmatched trades > 0" banner) in the dashboard.
5. At the **bottom** of `style.css`, add a `@media (prefers-reduced-motion: reduce)` block that targets `*, *::before, *::after` and sets `animation-duration: 0.01ms !important`, `animation-iteration-count: 1 !important`, `transition-duration: 0.01ms !important`.
6. In DevTools, open the Rendering panel, toggle "Emulate CSS prefers-reduced-motion: reduce", and confirm all animations stop.

**Reference solution** (`static-dashboard/css/style.css`):

```css
/* File: static-dashboard/css/style.css — animations + reduced motion */
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation: none !important; transition: none !important; }
}

.trade-card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: var(--space-2) var(--space-3); animation: slide-in 0.3s ease-out; }
.trade-card--matched { border-left: 4px solid var(--color-success); }
.trade-card--break   { border-left: 4px solid var(--color-danger); }
@keyframes slide-in  { from { transform: translateX(-10%); opacity: 0; } to { transform: translateX(0); opacity: 1; } }
```

> **Trainer-source note:** The trainer's `static-dashboard/css/style.css` only ships the `slide-in` keyframe and a simple `prefers-reduced-motion` reset. The `fade-in`, `pulse`, `.trade-card--new`, and `.alert--danger` rules described in the Steps are taught as live demos but are not present in the trainer source — implement them yourself following the Steps above.

</details>

**▶ Run the project — verify TICKET-ADV102 end-to-end**

Serve the dashboard and toggle the reduced-motion emulation in DevTools to confirm the override fires.

```bash
cd static-dashboard && python3 -m http.server 5500
# then open http://localhost:5500/dashboard.html
```

**Observe:**

- DevTools → ⋮ → More tools → Rendering → "Emulate CSS media feature prefers-reduced-motion" → `reduce`.
- Reload the page — trade cards still appear but with no slide animation; any `.alert--danger` pulse halts instantly.
- Switch emulation back to `no-preference` — animations run normally again.
- Failure signal: the `pulse` animation keeps running even with reduce enabled — confirm the `@media (prefers-reduced-motion: reduce)` block sits at the **bottom** of the stylesheet and uses `!important`.

---

### TICKET-ADV103 — Responsive breakpoints: desktop, tablet, mobile

**Goal:** Make the shell survive three target widths — desktop (3-column cards, side-by-side sidebar + main), tablet (2-column cards, sidebar collapses to a top strip), mobile (1-column everything, smaller header).

**What**
- Two media queries at the bottom of `css/style.css` — `@media (max-width: 1024px)` narrowing the sidebar to ~180px and re-flowing `.dashboard-grid` to 2-across, and `@media (max-width: 640px)` collapsing to a single column with `display: none` (or top-strip) on `.layout__sidebar`.

**Why**
- Mobile-survival is a Day 10 acceptance criterion (axe-core + visual QA on 375px iPhone preset) and Day 8 React components will reuse these exact breakpoints — the rule has to hold before SSE traffic starts pushing wide rows.

**Observe**
- DevTools device toolbar → 375px iPhone — no horizontal scrollbar, `.layout__sidebar` is hidden, every `.stat-card` is full-width, and the header height shrinks per the `(max-width: 640px)` rule.

**Done when:**
- At ≥1025px the layout matches TICKET-ADV098 (sidebar left, 3 cards across).
- At ≤1024px the sidebar drops above the main content as a horizontal strip and cards display 2-across.
- At ≤640px every card is full-width, the header height shrinks, and **no horizontal scrolling appears** at 375px width.

<details>
<summary>Hint 1 — gentle direction</summary>

Your TICKET-ADV098 layout is a row at desktop because `.app-body` has `flex-direction: row` by default. What's the single declaration that flips it to a vertical stack on tablet? And what controls how many cards fit per row?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Two media queries: `@media (max-width: 1024px)` for tablet, `@media (max-width: 640px)` for mobile. Inside each, override `.app-body`'s direction (for tablet) and adjust `.dashboard-grid > *` `flex-basis` to redo the column count. Don't forget to reset the sidebar's `flex` from `0 0 240px` to `0 0 auto` at tablet, or it will hog 240px of vertical space.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Tablet: `.app-body { flex-direction: column }`; `.app-sidebar { flex: 0 0 auto; border-right: none; border-bottom: 1px solid var(--color-border) }`; `.dashboard-grid > * { flex-basis: calc((100% - var(--space-4)) / 2) }`. Mobile: shrink `.app-header` flex-basis to ~56px, drop main padding to `--space-3`, set card `flex-basis: 100%`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. At the bottom of `style.css`, add `@media (max-width: 1024px) { ... }` for the tablet breakpoint.
2. Inside the tablet block, flip `.app-body { flex-direction: column }` so the sidebar drops above main.
3. Reset `.app-sidebar` to `flex: 0 0 auto; border-right: none; border-bottom: 1px solid var(--color-border)` — otherwise it hogs 240px vertically.
4. Re-flow cards 2-across by setting `.dashboard-grid > * { flex-basis: calc((100% - var(--space-4)) / 2) }`.
5. Add `@media (max-width: 640px) { ... }` for mobile: shrink `.app-header` to `flex-basis: 56px`, tighten padding, and force `.dashboard-grid > * { flex-basis: 100% }`.
6. In DevTools device toolbar, test at 1440 / 1024 / 375 widths and confirm zero horizontal scroll at 375px.

**Reference solution** (`static-dashboard/css/style.css`):

```css
/* File: static-dashboard/css/style.css — responsive layout */

/* tablet -> sidebar collapses */
@media (max-width: 1024px) {
  body { grid-template-columns: 180px 1fr; }
}
@media (max-width: 720px) {
  body { grid-template-columns: 1fr; grid-template-areas: "header" "main" "footer"; }
  .layout__sidebar { display: none; }
}
```

</details>

**▶ Run the project — verify TICKET-ADV103 end-to-end**

Serve the dashboard and resize the viewport across the three breakpoints to confirm the layout adapts.

```bash
cd static-dashboard && python3 -m http.server 5500
# then open http://localhost:5500/dashboard.html
```

**Observe:**

- At ≥1025px: sidebar fixed at 240px (or your chosen desktop width), main beside it, footer pinned bottom.
- Resize below 1024px (DevTools device toolbar → 1024px): sidebar narrows to 180px (per the trainer rule) — grid still side-by-side.
- Resize below 720px (or 375px iPhone preset): sidebar disappears, grid collapses to a single column, no horizontal scroll appears.
- Failure signal: a horizontal scrollbar at 375px means a fixed-width child (table, card grid) is overflowing — set `min-width: 0` on the affected flex/grid item.

---

### TICKET-ADV104 — Server-Sent Events subscription to `/api/v1/trades/stream`

**Goal:** Open a live subscription from the browser to the SSE endpoint scaffolded on Day 6 and update a visible "connection status" badge as the subscription opens, errors, and reconnects.

**What**
- `static-dashboard/js/sse.js` carrying an IIFE that constructs `new EventSource('/api/v1/trades/stream')` and wires `onopen` / `onmessage` / `onerror` handlers driving a `#sse-status` badge ("Live" / "Reconnecting…"), plus a `beforeunload` listener calling `sse?.close()`.

**Why**
- This consumes the Day 6 SSE endpoint and feeds the ADV105 prepend logic — letting `EventSource` handle reconnection (instead of looping inside `onerror`) is the textbook lesson that keeps the dev server from being DDoS'd by your own browser.

**Observe**
- DevTools → Network → filter "EventSource" — exactly one `/api/v1/trades/stream` request stays open (status: pending, type: eventsource); kill the backend and the badge flips to "Reconnecting…" without a flood of new requests.

**Done when:**
- `js/sse.js` opens a connection to `/api/v1/trades/stream` on page load.
- A `#sse-status` badge on the page reads "Live" once the connection is open and "Reconnecting…" if it drops.
- Killing the backend and restarting it shows the badge cycle through reconnecting → live again **without** you writing any reconnection logic yourself.

<details>
<summary>Hint 1 — gentle direction</summary>

The browser has a built-in API for one-way server-push streams that handles framing, parsing, and reconnection. Don't roll it yourself with `fetch` and a manual reader.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

`EventSource` is the API. Construct it with the stream URL, then wire three handlers: `onopen`, `onmessage`, `onerror`. **Critical pitfall:** do **not** call `new EventSource()` again inside `onerror` — the API auto-reconnects with backoff. Manual reconnects will DDoS your dev server. Just update the badge text and let the browser handle it.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Wrap the subscription in an IIFE that exits early if `#trade-feed` is absent. Keep `sse` and `connectionStatus` in module-scope variables. `onopen` updates the badge to "Live". `onmessage` does `JSON.parse(event.data)` inside a `try/catch` and hands the result to the TICKET-ADV105 prepend function. `onerror` updates badge to "Reconnecting…" and **nothing else**. Add a `beforeunload` listener that calls `sse?.close()`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `static-dashboard/js/sse.js` and wrap everything in an IIFE so module state doesn't leak to `window`.
2. Cache `FEED_EL = document.getElementById('trade-feed')` at the top; `return` early if it's null (the script may be loaded on pages without the feed).
3. Declare `STREAM_URL = '/api/v1/trades/stream'`, `let sse = null`, `let connectionStatus = 'connecting'`.
4. Write a `connect()` function that does `sse = new EventSource(STREAM_URL)` and wires three handlers: `onopen` updates badge to "Live"; `onmessage` runs `JSON.parse(event.data)` inside `try/catch` and calls `prependTradeRow(trade)` (defined in TICKET-ADV105); `onerror` updates badge to "Reconnecting…" and **does nothing else** — never call `connect()` from `onerror` or you DDoS the dev server.
5. Write a `updateConnectionBadge(text, variant)` helper that sets `#sse-status` textContent and className.
6. Register `window.addEventListener('beforeunload', () => sse?.close())` to clean up on navigation.
7. Call `connect()` once at the bottom of the IIFE.
8. Stop the backend, watch the badge cycle to "Reconnecting…", restart, watch it return to "Live" — all without your code reconnecting.

**Reference solution** (`static-dashboard/js/sse.js`):

```js
// File: static-dashboard/js/sse.js
// TICKET-ADV104 / TICKET-ADV105 — EventSource live feed with prepend + slide-in animation.
(function () {
  const feed = document.getElementById('trade-feed');
  if (!feed) return;

  // Hardcoded demo events for the static dashboard (no backend required).
  // Replace with: const sse = new EventSource('/api/v1/trades/stream');
  const demoEvents = [
    { tradeRef: 'EQU-20260603-0001', symbol: 'SAP.DE',  qty: 1000, price: 125.50, status: 'MATCHED' },
    { tradeRef: 'FX-20260603-0001',  symbol: 'EUR/USD', qty: 1_000_000, price: 1.0852, status: 'PENDING' },
    { tradeRef: 'EQU-20260603-0002', symbol: 'AAPL',    qty: 500,  price: 178.20, status: 'BREAK' },
  ];

  function prepend(trade) {
    const el = document.createElement('article');
    el.className = 'trade-card trade-card--' + trade.status.toLowerCase();
    el.innerHTML = `
      <strong>${trade.tradeRef}</strong>
      <span> ${trade.symbol} </span>
      <span> qty=${trade.qty} </span>
      <span> price=${trade.price} </span>
      <span> [${trade.status}]</span>`;
    feed.prepend(el);
  }

  demoEvents.forEach((e, i) => setTimeout(() => prepend(e), 500 * i));
})();
```

> **Trainer-source note:** The shipped trainer `sse.js` is a demo stub that fires three hardcoded events via `setTimeout` so the static page works without a running backend. The `EventSource('/api/v1/trades/stream')`, `onopen` / `onerror` badge wiring, and `beforeunload` cleanup described in the Steps are the production wiring you implement on top — the trainer source flags this with the inline `// Replace with: ...` comment.

</details>

**▶ Run the project — verify TICKET-ADV104 end-to-end**

For the demo stub, just the static server. For the real SSE wiring, also start the backend so `EventSource` can connect to `/api/v1/trades/stream`.

```bash
# Terminal 1 — backend (for real SSE wiring)
./mvnw spring-boot:run
# Terminal 2 — static dashboard
cd static-dashboard && python3 -m http.server 5500
# then open http://localhost:5500/dashboard.html
```

**Observe:**

- DevTools → Network tab → filter by "EventSource" (or "stream") — for the demo stub, three trade events arrive on the staggered 500ms `setTimeout` schedule.
- With the production wiring + backend running: a single `/api/v1/trades/stream` request stays open (status: pending, type: eventsource); `#sse-status` reads "Live".
- Stop the backend — badge flips to "Reconnecting…"; restart it — badge returns to "Live" without any code re-running `new EventSource()`.
- Failure signal: the Network tab shows repeated full requests to `/stream` — you're calling `connect()` from `onerror`; remove that and let the browser auto-reconnect.

---

### TICKET-ADV105 — SSE handler with prepend-and-animate

**Goal:** Render each incoming SSE trade as a `.trade-card` at the **top** of the feed, replaying the TICKET-ADV101 slide-in animation on insert, and cap the feed at 50 entries so the DOM stays bounded.

**What**
- A `prependTradeRow(trade)` function inside `js/sse.js`'s IIFE that builds an `<article class="trade-card trade-card--<status> trade-card--new">` via `document.createElement`, uses `escapeHtml` + `Intl.NumberFormat` for safe field interpolation, calls `FEED_EL.prepend(row)`, and runs `while (FEED_EL.children.length > 50) FEED_EL.lastElementChild.remove()`.

**Why**
- This is the visible payoff of ADV101's animation and ADV104's `EventSource` — the DOM cap is what keeps the page responsive after a multi-hour trading session, and `escapeHtml` is the XSS guard you'll need before Day 10 ships to a non-localhost origin.

**Observe**
- DevTools → Elements → `#trade-feed` first-child is always the most recent trade; flood the feed (Console: 60+ events) — `feed.children.length` plateaus at 50 and the oldest card at the bottom disappears.

**Done when:**
- Each new trade appears at the top of `#trade-feed` (newest first).
- The slide-in animation plays on every insert, and a temporary `.trade-card--new` modifier is removed after the entrance completes.
- After 50+ trades have arrived, the feed contains exactly 50 cards — the oldest is removed when a new one prepends.

<details>
<summary>Hint 1 — gentle direction</summary>

"Newest at the top" is one DOM API call away from "append at the bottom". And the way to retrigger a CSS animation on a freshly-inserted element is — surprise — to just insert it.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

`feedEl.prepend(row)` puts the new node first. Build the row with `document.createElement` and template-string `innerHTML`. **Always escape user-supplied strings** before interpolating (write a small `escapeHtml` helper using the standard `& < > " '` replacements) — trade refs and symbols arrive from a server you should not blindly trust. Use `setTimeout(..., 500)` to strip the `--new` modifier once the entrance is done.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Construct an `<article class="trade-card trade-card--<statusModifier> trade-card--new">`, fill `innerHTML` with a `<header>` (ref + status) and a `<div class="trade-card__body">` (symbol, formatted quantity, formatted price, currency). Use `Intl.NumberFormat` for quantity and price formatting. After prepending, run `while (feedEl.children.length > 50) feedEl.lastElementChild.remove()`. Status maps `MATCHED → trade-card--matched`, `UNMATCHED → trade-card--break`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. In `js/sse.js`, define `prependTradeRow(trade)` alongside the IIFE's `connect()` (it must close over `FEED_EL`).
2. Map `trade.status` to a CSS modifier: `MATCHED → trade-card--matched`, `UNMATCHED → trade-card--break`, else empty string.
3. Create `const row = document.createElement('article')` and assign `row.className = 'trade-card ' + statusModifier + ' trade-card--new'`.
4. Set `row.innerHTML` to a template string with `<header class="trade-card__header">` (escaped ref + escaped status) and `<div class="trade-card__body">` (symbol + formatted qty + formatted price + currency).
5. Add `escapeHtml(s)` that replaces `& < > " '` with their HTML entities — critical, never interpolate untrusted server strings raw.
6. Add `formatQty` (plain `Intl.NumberFormat('en-US')`) and `formatPrice` (with `minimumFractionDigits: 2`, `maximumFractionDigits: 4`).
7. `FEED_EL.prepend(row)`, then `setTimeout(() => row.classList.remove('trade-card--new'), 500)`.
8. Cap the DOM: `while (FEED_EL.children.length > 50) FEED_EL.lastElementChild.remove()`.

**Reference solution** (`static-dashboard/js/sse.js`):

```js
// File: static-dashboard/js/sse.js — TICKET-ADV105 (trainer source — inside the IIFE)
function prepend(trade) {
  const el = document.createElement('article');
  el.className = 'trade-card trade-card--' + trade.status.toLowerCase();
  el.innerHTML = `
    <strong>${trade.tradeRef}</strong>
    <span> ${trade.symbol} </span>
    <span> qty=${trade.qty} </span>
    <span> price=${trade.price} </span>
    <span> [${trade.status}]</span>`;
  feed.prepend(el);
}
```

> **Trainer-source note:** The shipped trainer `prepend()` is the minimum viable version — it lowercases `status` to derive the status-modifier class (`trade-card--matched` / `trade-card--break` / `trade-card--pending`) and interpolates fields directly. The `escapeHtml`, `Intl.NumberFormat` formatters, `trade-card--new` modifier, `setTimeout` cleanup, and 50-entry DOM cap described in the Steps are the production hardening on top — apply them yourself.

</details>

**▶ Run the project — verify TICKET-ADV105 end-to-end**

Serve the dashboard and watch the prepend order + DOM cap in DevTools.

```bash
cd static-dashboard && python3 -m http.server 5500
# then open http://localhost:5500/dashboard.html
```

**Observe:**

- Each new trade card lands at the **top** of `#trade-feed` (newest first); previous cards shift down — confirm in DevTools → Elements that the first child of `#trade-feed` is always the most recent event.
- Each insert plays the slide-in animation; the temporary `.trade-card--new` modifier disappears ~500ms after insert (inspect classList live).
- Inject 60+ events (or push fake objects from the Console) — `feed.children.length` caps at 50; the oldest card at the bottom is removed.
- Failure signal: new cards appear at the bottom — you used `appendChild` / `append` instead of `prepend`.

---

### TICKET-ADV106 — Advanced data table (sortable, resizable, frozen header)

**Goal:** Build a trades table on `trades.html` with three behaviours — click-to-sort columns (with ascending/descending toggle), drag-to-resize columns from a handle, and a header row that stays visible while the body scrolls.

**What**
- `static-dashboard/trades.html` carrying a `.table-scroll` wrapper around `<table class="data-table" id="trades-table">` with `data-col` / `data-dir` / `data-type` / `.resize-handle` on each `<th>`, plus `js/trades.js` driving click-to-sort (`Array.prototype.sort` against a canonical `rows` array), drag-to-resize (`mousemove` / `mouseup` listeners on `document`, not the handle), and `position: sticky` on `thead th` for the frozen header.

**Why**
- This is the table widget every later screen reuses — Day 8 React rebuilds it as a `<TradesTable />` component with the same `data-col` schema, and the "no ancestor `overflow: hidden`" rule is the trap most students hit when they later wrap the table in a Day 8 modal.

**Observe**
- Click the `Quantity` `<th>` — rows reorder numerically ascending, the `▲` indicator appears, and `aria-sort="ascending"` shows in DevTools; scroll the `.table-scroll` body and `<thead>` stays pinned thanks to `position: sticky; top: 0`.

**Done when:**
- Clicking a `<th>` sorts the rows by that column; clicking again reverses the sort. The active column shows an ▲ / ▼ indicator and `aria-sort` reflects the state.
- Dragging the small handle inside a `<th>` resizes that column smoothly — and the drag keeps working when the cursor leaves the handle.
- Scrolling the table body keeps the header row pinned at the top of the scroll container.

<details>
<summary>Hint 1 — gentle direction</summary>

Reach for a library and you've missed the point. Three behaviours, three small pieces of code: sort the data array (not the DOM) then re-render, listen for resize moves on the document not the handle, and use a CSS positioning value that means "scroll with me until you hit the container edge".

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Frozen header: `position: sticky; top: 0` on `thead th`, with the scroll container at `overflow: auto`. **Watch out:** sticky breaks silently if *any* ancestor has `overflow: hidden`. Sorting: store rows in a JS array, re-sort with `Array.prototype.sort` based on `data-col` and `data-type` attributes on the `<th>`, then re-render `<tbody>` in one `innerHTML` assignment. Resizing: on `mousedown` on `.resize-handle`, attach `mousemove` and `mouseup` listeners to **`document`**, not to the handle.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Header click handler: skip if `e.target` is the resize handle, read `data-col` and `data-type`, flip `aria-sort` between `ascending` and `descending`, clear the attribute on all other `<th>`s. Sort comparator branches on `data-type === 'number'` (subtract) vs string (`localeCompare`), multiplied by direction. Resize handler: capture `startX` and `startWidth = th.offsetWidth` on mousedown; on each document `mousemove` set `th.style.width = (startWidth + ev.clientX - startX) + 'px'`; on `mouseup` detach both listeners. Initial data comes from `fetch('/api/v1/trades?size=200')`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `trades.html` with a `<div class="table-scroll">` wrapper containing `<table class="data-table" id="trades-table">` — each `<th>` needs `data-col`, `data-dir="asc"`, optional `data-type="number"`, and an inner `<span class="resize-handle">`.
2. In `style.css`, give `.table-scroll` `overflow: auto; max-height: 60vh` — make sure no ancestor has `overflow: hidden` (sticky dies silently otherwise).
3. Pin the header: `.data-table thead th { position: sticky; top: 0; background: var(--color-bg-alt); z-index: 1 }` plus `cursor: pointer; user-select: none`.
4. Style the sort indicators via attribute selectors: `th[aria-sort="ascending"]::after { content: " ▲" }` and the descending equivalent.
5. Create `js/trades.js` and inside an IIFE keep `rows = []` as the canonical data array.
6. On each `<th>` click: skip if `e.target.classList.contains('resize-handle')`; read `data-col`/`data-type`; flip `aria-sort` ascending ↔ descending (clearing it on all other `<th>`s); sort `rows` with `Number` subtraction for `'number'` columns and `localeCompare` for strings, multiplied by direction; re-render via `tbody.innerHTML = rows.map(...).join('')`.
7. On each `.resize-handle` mousedown: capture `startX` and `startWidth = th.offsetWidth`; attach `mousemove` and `mouseup` listeners to **`document`** (not the handle), so the drag survives leaving the handle. `mousemove` sets `th.style.width = (startWidth + ev.clientX - startX) + 'px'`. `mouseup` detaches both listeners.
8. Initial data: `fetch('/api/v1/trades?size=200')` → assign `rows = data.content || data` → `renderRows()`.

**Reference solution** (`static-dashboard/trades.html` — table fragment):

```html
<!-- File: static-dashboard/trades.html — table fragment -->
<div class="table-scroll">
  <table class="data-table" id="trades-table">
    <thead>
      <tr>
        <th data-col="tradeRef"    data-dir="asc">Trade Ref <span class="resize-handle"></span></th>
        <th data-col="symbol"      data-dir="asc">Instrument <span class="resize-handle"></span></th>
        <th data-col="quantity"    data-dir="asc" data-type="number">Quantity <span class="resize-handle"></span></th>
        <th data-col="price"       data-dir="asc" data-type="number">Price <span class="resize-handle"></span></th>
        <th data-col="status"      data-dir="asc">Status <span class="resize-handle"></span></th>
      </tr>
    </thead>
    <tbody id="trades-tbody"><!-- rows injected by JS --></tbody>
  </table>
</div>
```

**Reference solution** (`static-dashboard/css/style.css`):

```css
/* File: static-dashboard/css/style.css — table */
.table-scroll {
  /* CRITICAL: no overflow:hidden ANYWHERE up the ancestor chain, or
     sticky header breaks. overflow:auto is fine. */
  overflow: auto;
  max-height: 60vh;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
}
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-sm);
}
.data-table th,
.data-table td {
  padding: var(--space-2) var(--space-3);
  text-align: left;
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
}
.data-table thead th {
  position: sticky;
  top: 0;
  background: var(--color-bg-alt);
  cursor: pointer;
  user-select: none;
  z-index: 1;
}
.data-table thead th[aria-sort="ascending"]::after  { content: " ▲"; }
.data-table thead th[aria-sort="descending"]::after { content: " ▼"; }

.resize-handle {
  display: inline-block;
  width: 4px;
  height: 16px;
  cursor: col-resize;
  background: var(--color-border);
  margin-left: var(--space-2);
  vertical-align: middle;
}
```

**Reference solution** (`static-dashboard/js/trades.js`):

```js
// File: static-dashboard/js/trades.js — TICKET-ADV106 sort + resize
(function () {
  const table = document.getElementById('trades-table');
  const tbody = document.getElementById('trades-tbody');
  let rows = []; // canonical data — sort operates on this

  // ---------- sortable columns ----------
  table.querySelectorAll('thead th').forEach(th => {
    th.addEventListener('click', (e) => {
      if (e.target.classList.contains('resize-handle')) return; // ignore resize clicks
      const col = th.dataset.col;
      const type = th.dataset.type || 'string';
      const dir = th.getAttribute('aria-sort') === 'ascending' ? 'descending' : 'ascending';

      // clear all, set this one
      table.querySelectorAll('thead th').forEach(o => o.removeAttribute('aria-sort'));
      th.setAttribute('aria-sort', dir);

      const mult = dir === 'ascending' ? 1 : -1;
      rows.sort((a, b) => {
        const av = a[col], bv = b[col];
        if (type === 'number') return (Number(av) - Number(bv)) * mult;
        return String(av).localeCompare(String(bv)) * mult;
      });
      renderRows();
    });
  });

  // ---------- resizable columns ----------
  table.querySelectorAll('.resize-handle').forEach(handle => {
    handle.addEventListener('mousedown', (e) => {
      e.preventDefault();
      const th = handle.closest('th');
      const startX = e.clientX;
      const startWidth = th.offsetWidth;

      // Listen on DOCUMENT so the drag survives leaving the handle.
      function onMove(ev) { th.style.width = (startWidth + ev.clientX - startX) + 'px'; }
      function onUp()     { document.removeEventListener('mousemove', onMove);
                            document.removeEventListener('mouseup', onUp); }
      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup', onUp);
    });
  });

  function renderRows() {
    tbody.innerHTML = rows.map(r => `
      <tr>
        <td>${r.tradeRef}</td><td>${r.symbol}</td>
        <td>${r.quantity}</td><td>${r.price}</td>
        <td>${r.status}</td>
      </tr>`).join('');
  }

  // initial load — hits the REST API from Day 5
  fetch('/api/v1/trades?size=200')
    .then(r => r.json())
    .then(data => { rows = data.content || data; renderRows(); });
})();
```

</details>

**▶ Run the project — verify TICKET-ADV106 end-to-end**

Serve `trades.html` (and run the backend if the table fetches from `/api/v1/trades`) and exercise sort + resize + frozen header.

```bash
# Terminal 1 — backend (so fetch('/api/v1/trades?size=200') returns data)
./mvnw spring-boot:run
# Terminal 2 — static dashboard
cd static-dashboard && python3 -m http.server 5500
# then open http://localhost:5500/trades.html
```

**Observe:**

- Table renders with seed rows; click the `Quantity` `<th>` — rows sort numerically ascending, the `▲` indicator appears, and `aria-sort="ascending"` shows in DevTools. Click again — descends with `▼`.
- Drag a `.resize-handle` to the right — column widens smoothly; the drag continues even when the cursor leaves the handle (mouse listener is on `document`).
- Scroll the `.table-scroll` body — the `<thead>` row stays pinned at the top thanks to `position: sticky`.
- Failure signal: the header scrolls away with the body — some ancestor has `overflow: hidden`; check the `.app-shell` / `.app-main` ancestor chain and remove it.

---

## End-of-day checklist

By 16:45 you should be able to demonstrate, with your laptop, every item below. If any of these fails, push it onto your Day 8 backlog explicitly — React will not magically fix layout or async-flow gaps for you tomorrow.

- [ ] `static-dashboard/dashboard.html` renders with a Flexbox shell — header, sidebar, main, footer — and no CSS Grid usage.
- [ ] The dark/light theme toggle works, persists across reloads, and produces **no white flash** when reloading a dark-themed page.
- [ ] CSS custom properties power every colour and spacing value in your stylesheet; a search for `#` in component rules returns nothing.
- [ ] `slide-in`, `fade-in`, and `pulse` keyframes are defined; the trade-feed cards animate in and `.alert--danger` pulses; `prefers-reduced-motion` disables motion when toggled in DevTools.
- [ ] The layout survives 1440px, 1024px, and 375px widths without horizontal scrolling.
- [ ] A live trade feed prepends new trades from `/api/v1/trades/stream`, animates each insert, and self-caps at 50 entries.
- [ ] The trades table on `trades.html` sorts on header click, resizes columns by drag, and keeps the header pinned while the body scrolls.
- [ ] At least one ES6+ `class` exists in your JS layer (for example a `TradeFeed` class wrapping the SSE state) — a tangible deliverable from the JS-Advanced theory block.
- [ ] A React project is scaffolded with Vite (or CRA) containing one JSX component with props and one hook from the `useMemo` / `useCallback` / `useReducer` trio — this is your launchpad into Day 8.
