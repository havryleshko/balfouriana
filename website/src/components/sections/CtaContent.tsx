'use client';

import { cta } from '@/content/site';
import { FadeIn } from '@/components/ui/motion';

export function CtaContent() {
  return (
    <FadeIn className="flex max-w-2xl flex-col gap-4">
      <p className="text-xs font-medium uppercase tracking-widest text-primary">{cta.label}</p>
      <h2 className="text-3xl font-semibold tracking-tight text-foreground md:text-4xl">
        {cta.title}
      </h2>
      <p className="text-lg text-muted-foreground">{cta.subtitle}</p>
    </FadeIn>
  );
}
