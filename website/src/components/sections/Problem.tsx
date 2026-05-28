'use client';

import { motion } from 'framer-motion';
import { problem } from '@/content/site';
import { FadeIn, StaggerGroup, StaggerItem } from '@/components/ui/motion';
import { RichText } from '@/components/ui/RichText';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

export function Problem() {
  return (
    <section className="border-t border-border bg-card py-20 md:py-28">
      <div className="mx-auto max-w-content px-6">
        <FadeIn className="mb-14 max-w-2xl">
          <p className="mb-4 text-xs font-medium uppercase tracking-widest text-primary">
            {problem.label}
          </p>
          <h2 className="text-3xl font-semibold tracking-tight text-foreground md:text-4xl">
            {problem.title}
          </h2>
        </FadeIn>

        <StaggerGroup className="grid gap-6 md:grid-cols-3">
          {problem.cards.map((point) => (
            <StaggerItem key={point.title}>
              <motion.div
                whileHover={{ y: -4 }}
                transition={{ duration: 0.25 }}
                className="h-full"
              >
                <Card className="group h-full shadow-sm transition-shadow hover:shadow-md">
                  <CardHeader className="pb-2">
                    <div className="mb-2 h-1 w-8 rounded-full bg-brand-accent transition-all duration-300 group-hover:w-12" />
                    <CardTitle className="text-base">{point.title}</CardTitle>
                  </CardHeader>
                  <CardContent>
                    {'body' in point && point.body && (
                      <p className="text-sm leading-relaxed text-muted-foreground">
                        <RichText text={point.body} />
                      </p>
                    )}
                    {'bullets' in point && point.bullets && (
                      <ul className="flex flex-col gap-3 text-sm leading-relaxed text-muted-foreground">
                        {point.bullets.map((bullet, i) => (
                          <li key={i} className="flex gap-2">
                            <span className="mt-2 size-1 shrink-0 rounded-full bg-brand-accent" />
                            <span>
                              <RichText text={bullet} />
                            </span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </CardContent>
                </Card>
              </motion.div>
            </StaggerItem>
          ))}
        </StaggerGroup>
      </div>
    </section>
  );
}
