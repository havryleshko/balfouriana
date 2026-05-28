'use client';

import dynamic from 'next/dynamic';
import { isBookingUrlConfigured, parseCalLink } from '@/content/site';
import { BookDemoLink } from '@/components/layout/BookDemoLink';
import { CtaContent } from '@/components/sections/CtaContent';

const BookingEmbed = dynamic(
  () => import('@/components/sections/BookingEmbed').then((m) => m.BookingEmbed),
  {
    ssr: false,
    loading: () => (
      <div className="flex min-h-[630px] items-center justify-center rounded-2xl border border-border bg-card">
        <p className="text-sm text-muted-foreground">Loading booking calendar…</p>
      </div>
    ),
  },
);

export function CtaSection() {
  const bookingUrl = process.env.NEXT_PUBLIC_BOOKING_URL;
  const calLink = bookingUrl ? parseCalLink(bookingUrl) : null;
  const embedActive = isBookingUrlConfigured(bookingUrl);

  return (
    <section id="book-demo" className="scroll-mt-20 border-t border-border bg-[#F0EFEC]">
      <div className="mx-auto max-w-content px-6 py-20 md:py-28">
        <CtaContent />
        {embedActive && calLink ? (
          <div className="mt-10">
            <BookingEmbed calLink={calLink} />
          </div>
        ) : (
          <div className="mt-8">
            <BookDemoLink />
          </div>
        )}
      </div>
    </section>
  );
}
