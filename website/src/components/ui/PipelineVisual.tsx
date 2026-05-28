'use client';

import { motion, useInView } from 'framer-motion';
import { useRef } from 'react';
import { howItWorks } from '@/content/site';
import { EASE } from '@/components/ui/motion';
import { RichText } from '@/components/ui/RichText';

export function PipelineVisual() {
  const ref = useRef<HTMLDivElement>(null);
  const isInView = useInView(ref, { once: true, margin: '-80px' });

  return (
    <div ref={ref} className="relative">
      <div className="hidden lg:block">
        <div className="relative flex items-start justify-between gap-4">
          <div className="absolute left-[10%] right-[10%] top-5 h-px bg-border" />
          <motion.div
            className="absolute left-[10%] top-5 h-px bg-primary"
            initial={{ width: 0 }}
            animate={isInView ? { width: '80%' } : { width: 0 }}
            transition={{ duration: 1.2, ease: EASE }}
          />
          {howItWorks.steps.map((step, i) => (
            <motion.div
              key={step.num}
              initial={{ opacity: 0, y: 16 }}
              animate={isInView ? { opacity: 1, y: 0 } : { opacity: 0, y: 16 }}
              transition={{ duration: 0.5, delay: 0.1 + i * 0.1, ease: EASE }}
              className="relative flex w-[18%] flex-col items-center text-center"
            >
              <div className="relative z-10 flex size-10 items-center justify-center rounded-full border-2 border-background bg-primary/10 shadow-sm">
                <span className="font-mono text-xs font-semibold text-primary">{step.num}</span>
              </div>
              <h3 className="mt-4 text-sm font-semibold text-foreground">{step.title}</h3>
              <p className="mt-2 text-xs leading-relaxed text-muted-foreground">
                <RichText text={step.body} />
              </p>
            </motion.div>
          ))}
        </div>
      </div>

      <ol className="flex flex-col gap-4 lg:hidden">
        {howItWorks.steps.map((step, i) => (
          <motion.li
            key={step.num}
            initial={{ opacity: 0, x: -12 }}
            animate={isInView ? { opacity: 1, x: 0 } : { opacity: 0, x: -12 }}
            transition={{ duration: 0.5, delay: 0.1 + i * 0.1, ease: EASE }}
            className="flex gap-4 rounded-2xl border border-border bg-card p-5 shadow-sm"
          >
            <div className="flex size-10 shrink-0 items-center justify-center rounded-full bg-primary/10">
              <span className="font-mono text-xs font-semibold text-primary">{step.num}</span>
            </div>
            <div>
              <h3 className="text-sm font-semibold text-foreground">{step.title}</h3>
              <p className="mt-1 text-sm leading-relaxed text-muted-foreground">
                <RichText text={step.body} />
              </p>
            </div>
          </motion.li>
        ))}
      </ol>
    </div>
  );
}

export function DemoTerminal() {
  return (
    <div
      className="relative overflow-hidden rounded-2xl border border-border bg-card shadow-sm"
      role="region"
      aria-labelledby="demo-placeholder-label"
    >
      <div className="flex items-center gap-2 border-b border-border bg-muted/50 px-4 py-3">
        <span className="size-2.5 rounded-full bg-red-400/80" />
        <span className="size-2.5 rounded-full bg-amber-400/80" />
        <span className="size-2.5 rounded-full bg-green-400/80" />
        <span className="ml-2 font-mono text-xs text-muted-foreground">balfouriana — pipeline</span>
      </div>
      <div className="relative bg-grid-light bg-[size:24px_24px] p-10 md:p-14">
        <div className="pointer-events-none absolute inset-0 bg-gradient-to-b from-transparent via-transparent to-background/40" />
        <div className="relative flex flex-col items-center gap-4 text-center">
          <div className="font-mono text-xs text-muted-foreground">
            <span className="text-primary">$</span> balfouriana run --demo
          </div>
          <p id="demo-placeholder-label" className="max-w-md text-sm text-muted-foreground">
            <span className="animate-shimmer font-medium text-foreground">
              {howItWorks.demoPlaceholder.title}
            </span>{' '}
            {howItWorks.demoPlaceholder.body}
          </p>
        </div>
      </div>
    </div>
  );
}
