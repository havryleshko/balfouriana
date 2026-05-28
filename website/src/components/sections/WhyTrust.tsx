'use client';

import { motion } from 'framer-motion';
import { whyTrust } from '@/content/site';
import { FadeIn, StaggerGroup, StaggerItem } from '@/components/ui/motion';
import { RegRef } from '@/components/ui/RichText';
import { Check } from 'lucide-react';

export function WhyTrust() {
  return (
    <section className="border-t border-border bg-card py-20 md:py-28">
      <div className="mx-auto max-w-content px-6">
        <div className="grid gap-16 lg:grid-cols-2 lg:gap-20">
          <FadeIn>
            <p className="mb-4 text-xs font-medium uppercase tracking-widest text-primary">
              {whyTrust.label}
            </p>
            <h2 className="text-3xl font-semibold tracking-tight text-foreground md:text-4xl">
              {whyTrust.title}
            </h2>
            <p className="mt-6 text-sm leading-relaxed text-muted-foreground">{whyTrust.auditTrail}</p>
            <blockquote className="mt-8 border-l-2 border-primary/30 pl-5 text-sm italic leading-relaxed text-muted-foreground">
              {whyTrust.originStory}
            </blockquote>
          </FadeIn>

          <div className="flex flex-col gap-10">
            <FadeIn delay={0.1}>
              <h3 className="mb-4 text-sm font-semibold uppercase tracking-wider text-muted-foreground">
                Credentials
              </h3>
              <StaggerGroup className="flex flex-col gap-3">
                {whyTrust.credentials.map((cred) => (
                  <StaggerItem key={cred}>
                    <motion.div
                      whileHover={{ x: 4 }}
                      className="flex items-center gap-3 rounded-xl border border-border bg-background px-5 py-4"
                    >
                      <span className="flex size-6 items-center justify-center rounded-full bg-primary/10">
                        <Check className="size-3.5 text-primary" />
                      </span>
                      <span className="text-sm font-medium text-foreground">{cred}</span>
                    </motion.div>
                  </StaggerItem>
                ))}
              </StaggerGroup>
            </FadeIn>

            <FadeIn delay={0.2}>
              <h3 className="mb-4 text-sm font-semibold uppercase tracking-wider text-muted-foreground">
                Regulatory calendar
              </h3>
              <StaggerGroup className="flex flex-col gap-2">
                {whyTrust.regulatoryDates.map((item) => (
                  <StaggerItem key={item.label}>
                    <motion.div
                      whileHover={{ scale: 1.01 }}
                      className="flex items-center justify-between gap-4 rounded-xl border border-border bg-muted/50 px-5 py-3.5 transition-colors hover:border-primary/30 hover:bg-primary/5"
                    >
                      <span className="text-sm text-foreground">{item.label}</span>
                      <RegRef>{item.date}</RegRef>
                    </motion.div>
                  </StaggerItem>
                ))}
              </StaggerGroup>
            </FadeIn>
          </div>
        </div>
      </div>
    </section>
  );
}
