# Balfouriana marketing website

Marketing landing page for [balfouriana.com](https://balfouriana.com). Separate from the engine demo console in [`../frontend/`](../frontend/).

**Source of truth:** [`website-design.md`](website-design.md)

## Stack

- Next.js 15 (App Router)
- React 19 + Framer Motion
- Tailwind CSS
- TypeScript (strict)
- Cal.com embed for booking
- Deploy target: Vercel

## Prerequisites

- Node.js 20+

## Setup

```bash
cd website
npm install
cp .env.example .env
```

## Environment

| Variable | Purpose | Required for |
|---|---|---|
| `NEXT_PUBLIC_BOOKING_URL` | Cal.com event URL or `handle/event` path | Inline embed on production (placeholder → fallback button) |
| `NEXT_PUBLIC_LINKEDIN_URL` | LinkedIn profile or company page | Footer link |

Set in [`website/.env`](.env) locally and in Vercel → Project → Settings → Environment Variables for **Production** and **Preview**.

If migrating from the Astro site, rename `PUBLIC_BOOKING_URL` → `NEXT_PUBLIC_BOOKING_URL` and `PUBLIC_LINKEDIN_URL` → `NEXT_PUBLIC_LINKEDIN_URL` in Vercel, then redeploy.

## Project structure

```
website/src/
  app/              layout, page, globals.css
  components/
    layout/         Nav, Footer, Logo, BookDemoLink
    sections/       Hero, Problem, HowItWorks, WhyTrust, CtaSection
    ui/             shadcn primitives, motion, PipelineVisual, RichText
  content/          site.ts (all copy)
  lib/              utils.ts
```

## Commands

```bash
npm run dev      # http://localhost:3000
npm run build    # production build
npm run start    # serve production build locally
```

## Vercel deployment

1. Import the GitHub repo in [Vercel](https://vercel.com).
2. Set **Root Directory** to `website` (not repo root).
3. Framework: **Next.js** (auto-detected). Build/install commands are also in [`vercel.json`](vercel.json).
4. Add environment variables (see table above). Redeploy after changing vars.
5. Add domains `balfouriana.com` and `www.balfouriana.com`; apply DNS records from Vercel dashboard.
6. Redirect `www` → apex (canonical URL in [`src/content/site.ts`](src/content/site.ts) is `https://balfouriana.com`).

Preview deployments run automatically on pull requests when the Vercel GitHub integration is enabled.
