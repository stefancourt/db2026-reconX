# Day 7 — Solved Files & How To Run

Day 7 is your frontend day. Zero backend Java changes — everything
happens in the `static-dashboard/` folder at the project root (HTML +
CSS + vanilla JS talking to the backend over `fetch` and SSE).

**How this folder works**

The real `static-dashboard/` tree at the project root ships the same
files as reference implementations. This folder contains the
**complete solved versions** — you can copy them straight over if
yours drifted, or diff against yours to compare approaches.

**What this folder ships:**

- `static-dashboard/dashboard.html`
- `static-dashboard/css/style.css`
- `static-dashboard/js/sse.js` — SSE handler (ADV104/ADV105)
- `static-dashboard/js/theme.js` — dark/light toggle (ADV100)

The backend surface these files call is already in place from Days 1–6
(`GET /v1/trades`, `/v1/trades/stream`, JWT auth). This file is a
friendly map of the day and how to run it.

## Quick start

```bash
# From the project root — one-shot overlay:
cp -R day7-solved-files/static-dashboard/ static-dashboard/
```

**In this file:**

1. What Day 7 covers.
2. How to run the static dashboard against the backend.
3. What success looks like.
4. Troubleshooting.

---

## What Day 7 covers

Nine tickets (ADV098–106), all in `static-dashboard/`:

| Ticket | Topic |
|---|---|
| ADV098 | Flexbox layout — sidebar, header, three-column main, footer |
| ADV099 | CSS custom properties as design tokens |
| ADV100 | Dark / light theme toggle |
| ADV101 | Real-time trade-feed area with a slide-in animation |
| ADV102 | Named CSS animations: fade-in, slide-in, pulse |
| ADV103 | Responsive breakpoints — desktop, tablet, mobile |
| ADV104 | Server-Sent Events subscription to `/api/v1/trades/stream` |
| ADV105 | SSE handler with prepend-and-animate |
| ADV106 | Advanced data table — sortable, resizable, frozen header |

No file overlay to do — you edit `static-dashboard/dashboard.html`,
`static-dashboard/css/*`, and `static-dashboard/js/*` directly.

---

## Run the project

You need two terminals: one for the backend (so `/api/v1/trades` and
`/api/v1/trades/stream` are live), one for a static file server (so
`dashboard.html` can be loaded over HTTP, not `file://` — otherwise the
`fetch` calls hit CORS trouble).

### Before you start

1. **Java 21** on the terminal that runs the backend: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
2. **Days 1–6 are applied** — the SSE endpoint and the trades REST are on the backend from earlier days:
   ```bash
   for d in day1 day2 day3 day4 day5 day6; do cp -R ${d}-solved-files/backend/ backend/; done
   ```
3. **Python 3** or Node available for the static server (`python3 -m http.server` is fine).

### Terminal 1 — backend

```bash
cd backend
./mvnw spring-boot:run
# waits on port 8081, context path /api
```

### Terminal 2 — static dashboard

```bash
cd static-dashboard
python3 -m http.server 5500
# then open http://localhost:5500/dashboard.html
```

If you're on the `/v1/trades` page, open
<http://localhost:5500/trades.html> instead.

Hit `Ctrl+C` in each terminal when you're done.

---

## What success looks like

- The dashboard loads at <http://localhost:5500/dashboard.html> with your Day-7 layout (sidebar + header + main + footer).
- Toggling the theme swaps every colour via the CSS custom properties — no per-element style edits needed.
- A new trade posted to `POST /api/v1/trades` from Swagger UI appears in the SSE feed with your slide-in animation.
- Resizing the browser to tablet / phone widths cleanly reflows the layout — no horizontal scroll, no overlapping panels.
- The data table on `trades.html` sorts on column click, columns are resizable, and the header stays frozen while the body scrolls.

---

## Troubleshooting

- **SSE feed doesn't update** — check the browser DevTools Network tab for a `text/event-stream` connection to `/api/v1/trades/stream` staying open. If it's returning 401, you're not sending the JWT in your `EventSource` init — pass it as a query param or a custom header depending on how the backend accepts it.
- **CORS error on `fetch`** — you loaded the HTML via `file://` instead of the static server. Use `python3 -m http.server 5500`.
- **Theme toggle only changes some elements** — some elements are hard-coding colours instead of using the design tokens. Grep your CSS for hex codes; each should be a `var(--…)` reference.
- **Table columns won't resize** — make sure you attached the resize handler on the `<th>` right edge, not the whole cell.
- **Port 5500 in use** — pick another (`python3 -m http.server 5600`).

Frontend day, so keep your DevTools panel open the whole time.
