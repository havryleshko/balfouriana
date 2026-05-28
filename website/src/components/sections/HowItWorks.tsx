'use client';

import { howItWorks } from '@/content/site';
import { FadeIn } from '@/components/ui/motion';
import { DemoTerminal, PipelineVisual } from '@/components/ui/PipelineVisual';

export function HowItWorks() {
  return (
    <section className="border-t border-border bg-background py-20 md:py-28">
      <div className="mx-auto max-w-content px-6">
        <FadeIn className="mb-14 max-w-2xl">
          <p className="mb-4 text-xs font-medium uppercase tracking-widest text-primary">
            {howItWorks.label}
          </p>
          <h2 className="text-3xl font-semibold tracking-tight text-foreground md:text-4xl">
            {howItWorks.title}
          </h2>
          <p className="mt-4 text-muted-foreground">{howItWorks.subtitle}</p>
        </FadeIn>

        <PipelineVisual />

        <FadeIn className="mt-12" delay={0.2}>
          <DemoTerminal />
        </FadeIn>
      </div>
    </section>
  );
}
