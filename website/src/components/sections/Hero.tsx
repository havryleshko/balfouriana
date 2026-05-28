'use client';

import { motion } from 'framer-motion';
import { hero } from '@/content/site';
import { BookDemoLink } from '@/components/layout/BookDemoLink';
import { Badge } from '@/components/ui/badge';
import { RichText } from '@/components/ui/RichText';
import { EASE } from '@/components/ui/motion';

export function Hero() {
  return (
    <section className="relative overflow-hidden bg-background">
      <div className="pointer-events-none absolute inset-0 bg-grid-light bg-[size:48px_48px]" />
      <div className="pointer-events-none absolute -left-32 top-20 size-96 animate-float rounded-full bg-primary/5 blur-3xl" />
      <div className="pointer-events-none absolute -right-24 top-40 size-80 animate-float rounded-full bg-accent/10 blur-3xl [animation-delay:2s]" />

      <div className="relative mx-auto max-w-content px-6 pb-20 pt-16 md:pb-28 md:pt-24">
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, ease: EASE }}
          className="mb-8 inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary/5 px-4 py-1.5"
        >
          <span className="size-1.5 animate-pulse-soft rounded-full bg-primary" />
          <span className="text-xs font-medium text-primary">{hero.badge}</span>
        </motion.div>

        <div className="flex max-w-4xl flex-col gap-8">
          <h1 className="text-4xl font-semibold leading-[1.1] tracking-tight text-foreground md:text-6xl md:leading-[1.08]">
            <motion.span
              className="block"
              initial={{ opacity: 0, y: 24 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.1, ease: EASE }}
            >
              {hero.headlineLine1}
            </motion.span>
            <motion.span
              className="mt-2 block text-muted-foreground"
              initial={{ opacity: 0, y: 24 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.2, ease: EASE }}
            >
              {hero.headlineLine2}
            </motion.span>
          </h1>

          <motion.p
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.35, ease: EASE }}
            className="max-w-2xl text-lg leading-relaxed text-muted-foreground md:text-xl"
          >
            <RichText text={hero.subheading} />
          </motion.p>

          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.45, ease: EASE }}
            className="flex flex-wrap items-center gap-4"
          >
            <BookDemoLink />
            <div className="flex flex-wrap gap-2">
              {hero.regimes.map((regime, i) => (
                <motion.div
                  key={regime}
                  initial={{ opacity: 0, scale: 0.9 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ delay: 0.55 + i * 0.08, ease: EASE }}
                >
                  <Badge variant="outline">{regime}</Badge>
                </motion.div>
              ))}
            </div>
          </motion.div>
        </div>
      </div>
    </section>
  );
}
