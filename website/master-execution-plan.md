# Balfouriana — Website Master Execution Plan

## Overview

Marketing landing page at **https://balfouriana.com** — single CTA (Book a Demo via Cal.com), separate from the engine demo console in [`../frontend/`](../frontend/).

**Source of truth:** [`website-design.md`](website-design.md)

---

## Current position

| Phase | Status | Deliverable |
|---|---|---|
| W0 | Superseded | Astro scaffold (migrated to Next.js) |
| W1 | Complete | Design tokens, Nav, Footer, layout shell |
| W2 | Complete | Hero → CTA sections (content) |
| W3 | Complete | Cal.com embed, SEO/OG, a11y |
| W4 | Code complete — **manual launch pending** | Vercel + DNS + prod env vars |
| W5 | Complete | Next.js + shadcn + Framer Motion refactor (light theme) |
| W6 | Optional | Logo, interactive demo, case study |

---

## W4 manual launch (user)

1. [ ] Vercel project, root directory `website`
2. [ ] `NEXT_PUBLIC_BOOKING_URL` + `NEXT_PUBLIC_LINKEDIN_URL` in Production (migrate from `PUBLIC_*` if set)
3. [ ] Domain `balfouriana.com` DNS + SSL
4. [ ] Verify Cal.com embed on live site
5. [ ] Re-run Lighthouse on production URL with embed visible

See [`phase-w4-plan.md`](phase-w4-plan.md).

---

## Phase plans

- [`phase-w0-plan.md`](phase-w0-plan.md)
- [`phase-w1-plan.md`](phase-w1-plan.md)
- [`phase-w2-plan.md`](phase-w2-plan.md)
- [`phase-w3-plan.md`](phase-w3-plan.md)
- [`phase-w4-plan.md`](phase-w4-plan.md)

---

## Execution rule

Finish each phase validation checklist before starting the next. If a task conflicts with `website-design.md`, the design doc wins.
