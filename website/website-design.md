# Balfouriana — Website Design Document

## Purpose

This document is the single source of truth for the Balfouriana marketing landing page.
It governs structure, content direction, visual identity, and technical stack.
Every design and copy decision traces back to a decision made here.

---

## Goal

A clean, high-converting marketing landing page that:
- Explains the problem and the outcome with surgical precision
- Builds trust with a sceptical COO who has never heard of Balfouriana
- Drives a single conversion action: **Book a Demo** (Cal.com)
- Operates as a manual-delivery front door at pre-revenue stage

---

## Primary Audience

**COO / Head of Operations at a small-to-mid UK AIFM (sub-£500m AUM)**

This is the person who feels the operational pain daily — chasing custodian logins,
managing outsourced providers, fielding LEI rejection emails at 4pm on T+1 deadlines,
and carrying legal liability under SYSC 8 even when the work is outsourced.

They have enough authority at this fund size to champion a switch and move it forward.

**Secondary reader:** CFO / Finance Director — needs a cost/ROI anchor lower on the page.
The page leads with operational pain (COO hook), then references cost later (CFO reassurance).

---

## Core Objection to Neutralise

> "How much can I trust this?"

The COO is evaluating a compliance-critical system from a founder they've never heard of.
Trust is not built through customer logos (we have none yet) — it's built through
**demonstrated regulatory depth**: correct terminology, specific regulation names and dates,
accurate descriptions of the outsourced process we're replacing.

Every section either builds trust or moves toward the CTA. Nothing else earns its place.

---

## Emotional Register

**Clean & confident SaaS.**

Reference points: Clerk, Resend, Linear — adapted to a **light fintech** canvas.
- Light background (`#FAFAF9`) with white elevated surfaces
- Precise typography with aggressive whitespace
- Subtle data/terminal motifs — not full Bloomberg, not generic startup
- Feels like it was built by engineers who care

This signals technical seriousness without needing credentials we don't yet have,
and positions Balfouriana as the modern alternative to legacy vendors (SS&C, Broadridge)
that the COO already knows ignore funds at this size.

---

## Brand Identity

### Name
**Balfouriana** — named after *Pinus balfouriana*, the Foxtail Pine.
A rare, high-altitude pine endemic to California, found near Sequoia and Kings Canyon
National Parks — in the shadow of the sequoias that inspired Sequoia Capital.
Ancient (fossil record: 46 million years), precise, resilient. Brick-red bark, vivid green foliage.

This origin story is told in two sentences maximum on the page. It humanises the brand
without distracting from the compliance message.

### Logo
Minimal geometric silhouette of the foxtail pine — single colour, used as a mark
alongside the wordmark. Precise and distinctive, not a generic pine tree shape.
To be designed as a separate deliverable.

### Colour Direction
- Background: warm off-white (`#FAFAF9`)
- Primary text: near-black (`#0C0C0D`)
- Brand: pine green (`#166534`) — logo, labels, pipeline accents
- Accent: warm amber (`#C2410C`) — foxtail bark, highlight bars
- CTA band: stone (`#F0EFEC`) — subtle contrast without going dark
- No gradients except where used very deliberately (hero orbs, terminal frame)

### Typography
- Sans-serif: **Geist Sans** — clean and precise
- Monospace: **Geist Mono** — regulatory references (regulation names, dates, field counts)

---

## Page Structure

Seven sections. Order is intentional and non-negotiable.

---

### 1. Nav
**Job:** Orient and always offer the CTA.

- Logo + wordmark — left
- Single CTA button — right: **"Book a Demo"**
- Sticky — remains visible on scroll
- No other navigation links (nothing to distract from the one conversion action)

---

### 2. Hero
**Job:** Name the pain and promise the outcome in under 5 seconds.

- One sharp headline — outcome-first, specific to the regulatory regimes
  Example direction: *"Regulator-accepted MiFID II, AIFMD II and EMIR filings.
  Without the outsourced middleman."*
- One subheading sentence — context for the COO who needs 5 more words to be sure
- Single CTA button: **"Book a Demo"**
- No hero image at this stage — pure typography and negative space
- This is where the Clerk/Linear aesthetic does its heaviest lifting

---

### 3. Problem
**Job:** Make the COO feel seen before anything is sold.

No product mention in this section. Only pain — specific, named, accurate.
Domain depth here is the trust signal.

Pain points to cover:
- Multiple custodian logins, manual exports, clunky scripts — daily friction
- LEI rejections, ISIN errors, missing CFI codes — data quality battles
- T+1 deadlines for MiFID II, near real-time EMIR submissions — time pressure
- AIFMD II Annex IV quarterly scrambles with new delegation/LMT/loan fields (live April 2026)
- EMIR Refit Phase II reconciliation complexity (203 fields, Phase II April/Sept 2026)
- Legal liability under SYSC 8 — even when outsourced, the fund is responsible
- "Your outsourced provider's junior analyst is doing this manually. You're paying for that."

Tone: precise and empathetic — not angry, not salesy. Just accurate.

---

### 4. How It Works + Demo Placeholder
**Job:** Show the five-step pipeline simply. Make the COO think:
*"That's exactly what my outsourced provider does — but automated."*

Five steps, one line each:
1. **Data in** — SFTP, REST `/ingest`, or drop zone. CSV, JSON, FIX.
2. **Validated & enriched** — GLEIF LEI lookup, FIRDS enrichment, FCA/ESMA rule packs
3. **Rules applied** — deterministic, versioned business rules per regime
4. **Filing generated** — regulator-accepted XML / ISO 20022 output
5. **Audit trail complete** — immutable, full traceability for every decision

**Demo placeholder:**
A card with blurred/redacted screenshot or a clean "See it in action" frame
with label: *"Live demo coming soon — book a walkthrough in the meantime."*
This section gets replaced with an embedded interactive demo once built.

---

### 5. Why Trust Balfouriana
**Job:** Directly neutralise the core objection. Replace social proof (which we don't
have yet) with demonstrated regulatory depth and founder credentials.

Content:
- CISI IOC (Level 3) — passed
- FTIP certification (CFI)
- Specific regulatory knowledge with correct terminology and verified dates:
  - AIFMD II live 16 April 2026
  - EMIR Refit Phase II: EU 27 April 2026, UK September 2026
  - DTCC GTR UK MiFID ARM production go-live 18 May 2026
  - AIFMD II Annex IV full XML harmonisation expected 2027
- Event-sourced, immutable audit trail — every validation, calculation,
  filing, submission, and correction logged permanently
- The Foxtail Pine origin story — two sentences maximum:
  *Balfouriana is named after Pinus balfouriana — the foxtail pine, a rare
  high-altitude tree that grows near the sequoias of California's Sierra Nevada.
  Ancient, precise, resilient. Built for altitude.*

**Note:** This section is a v1 decision. Once the first client is live,
it gets restructured around their outcome (case study / testimonial format).

---

### 6. CTA Section
**Job:** Convert. One action, nothing else.

- Full-width CTA band — slightly darker stone background (`#F0EFEC`) for contrast
- One headline — direct and confident:
  Example: *"Ready to stop outsourcing your liability?"*
- One subline — what happens next: *"Book a 30-minute demo.
  We'll walk through your exact reporting requirements."*
- Cal.com embed or prominent link button
- No other content on this section

---

### 7. Footer
**Job:** Close cleanly, provide minimal reference info.

- Logo + wordmark
- balfouriana.com
- LinkedIn link
- MIT licence note (signals open development ethos)
- One-line description: *"Regulatory reporting engine for UK funds."*
- No sitemap, no cookie banner complexity at this stage

---

## Technical Stack

```
/website                 ← separate folder in root repo
  Next.js 15             ← App Router, static marketing page
  React 19               ← section components + Framer Motion
  shadcn/ui              ← Button, Badge, Card, Separator
  Tailwind CSS           ← design tokens via CSS variables
  TypeScript             ← everywhere by default
  Deployed → Vercel      ← one-command deploy, preview URLs per push
```

**Why this stack:**
- Next.js + Vercel: SSR/SEO, preview deploys, custom domain in minutes
- Framer Motion: scroll reveals and pipeline animation without sacrificing polish
- shadcn/ui + Tailwind: consistent primitives, engineer-maintainable design system
- Content centralized in `src/content/site.ts` — copy reviewable against this doc
- Cal.com embed lazy-loaded via `next/dynamic` to keep initial bundle lean

---

## Copy Principles

1. **Name the regulation correctly and specifically** — generic "compliance automation"
   language destroys trust with a COO who knows what EMIR Refit Phase II actually is
2. **Outcomes, not features** — "regulator-accepted filings with a full audit trail"
   not "automated workflow engine"
3. **Never oversell the stage** — no fake social proof, no invented logos
4. **One CTA throughout** — Book a Demo. Every section points to it or enables it.
5. **Brevity** — a busy COO reads this between meetings. Every sentence earns its place.

---

## What This Page Is Not

- Not a product tour (that's the demo)
- Not a documentation site (that comes later)
- Not a pricing page (too early — demo call handles pricing)
- Not a blog or thought leadership hub (separate concern)

---

## Version

v1.0 — Pre-revenue, pre-customer. Designed for manual delivery via demo calls.
Revisit after first paying client: add case study, restructure trust section,
consider adding a pricing tier signal.
