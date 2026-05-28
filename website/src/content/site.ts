export const site = {
  title: 'Balfouriana — Regulatory reporting for UK funds',
  description:
    'Regulator-accepted MiFID II, AIFMD II and EMIR filings for UK AIFMs. Book a demo to walk through your reporting requirements.',
  url: 'https://balfouriana.com',
  tagline: 'Regulatory reporting engine for UK funds.',
  licenseUrl: 'https://github.com/havryleshko/balfouriana/blob/main/LICENSE',
  linkedInUrl: 'https://linkedin.com/in/havryleshko',
  ctaLabel: 'Book a Demo',
  ctaHref: '#book-demo',
} as const;

export const hero = {
  badge: 'Regulatory reporting for UK AIFMs',
  headlineLine1: 'Regulator-accepted MiFID II, AIFMD II and EMIR filings.',
  headlineLine2: 'Without the outsourced middleman.',
  subheading:
    'For UK AIFMs carrying operational and {SYSC 8} liability — reporting deadlines do not wait for your custodian export or your provider\'s queue.',
  regimes: ['MiFID II', 'AIFMD II', 'EMIR'] as const,
} as const;

export const problem = {
  label: 'The problem',
  title: 'You already know this workflow.',
  cards: [
    {
      title: 'Daily friction',
      body: 'Multiple custodian logins, manual exports, and clunky scripts — the same operational drag every reporting cycle.',
    },
    {
      title: 'Data quality & deadlines',
      bullets: [
        'LEI rejections, ISIN errors, missing CFI codes — data quality battles before you can file.',
        'T+1 {MiFID II} and near real-time {EMIR} submissions — time pressure that does not slip.',
        '{AIFMD II Annex IV} quarterly scrambles with new delegation, LMT and loan fields — live {16 April 2026}.',
        '{EMIR Refit Phase II} reconciliation complexity — {203 fields}, EU {27 April 2026}, UK {September 2026}.',
      ],
    },
    {
      title: 'Liability & cost',
      body: 'Legal liability under {SYSC 8} — even when outsourced, the fund is responsible. Your outsourced provider\'s junior analyst is doing this manually. You\'re paying for that.',
    },
  ],
} as const;

export const howItWorks = {
  label: 'How it works',
  title: 'Five steps. One pipeline.',
  subtitle:
    'Exactly what your outsourced provider does — automated, versioned, and auditable.',
  steps: [
    {
      num: '01',
      title: 'Data in',
      body: 'SFTP, REST {/ingest}, or drop zone. CSV, JSON, FIX.',
    },
    {
      num: '02',
      title: 'Validated & enriched',
      body: 'GLEIF LEI lookup, FIRDS enrichment, FCA/ESMA rule packs',
    },
    {
      num: '03',
      title: 'Rules applied',
      body: 'Deterministic, versioned business rules per regime',
    },
    {
      num: '04',
      title: 'Filing generated',
      body: 'Regulator-accepted XML / ISO 20022 output per regime',
    },
    {
      num: '05',
      title: 'Audit trail complete',
      body: 'Immutable, full traceability for every decision',
    },
  ],
  demoPlaceholder: {
    title: 'Live demo coming soon',
    body: '— book a walkthrough in the meantime.',
  },
} as const;

export const whyTrust = {
  label: 'Why trust us',
  title: 'Demonstrated regulatory depth — not borrowed logos.',
  auditTrail:
    'Event-sourced, immutable audit trail — every validation, calculation, filing, submission, and correction logged permanently.',
  originStory:
    'Balfouriana is named after Pinus balfouriana — the foxtail pine, a rare high-altitude tree that grows near the sequoias of California\'s Sierra Nevada. Ancient, precise, resilient. Built for altitude.',
  credentials: ['CISI IOC (Level 3) — passed', 'FTIP certification (CFI)'] as const,
  regulatoryDates: [
    { label: 'AIFMD II', date: '16 April 2026' },
    { label: 'EMIR Refit Phase II (EU)', date: '27 April 2026' },
    { label: 'EMIR Refit Phase II (UK)', date: 'September 2026' },
    { label: 'DTCC GTR UK MiFID ARM', date: '18 May 2026' },
    { label: 'AIFMD II Annex IV harmonisation', date: '2027' },
  ] as const,
} as const;

export const cta = {
  label: 'Get started',
  title: 'Ready to stop outsourcing your liability?',
  subtitle: 'Book a 30-minute demo. We\'ll walk through your exact reporting requirements.',
} as const;

export function parseCalLink(url: string): string | null {
  const trimmed = url.trim();
  if (!trimmed || trimmed.includes('your-handle')) return null;

  if (trimmed.startsWith('https://cal.com/')) {
    const path = trimmed.replace('https://cal.com/', '').replace(/\/$/, '');
    return path || null;
  }

  if (!trimmed.includes('://')) {
    return trimmed.replace(/^\//, '') || null;
  }

  return null;
}

export function isBookingUrlConfigured(url: string | undefined): boolean {
  if (!url) return false;
  return parseCalLink(url) !== null;
}
