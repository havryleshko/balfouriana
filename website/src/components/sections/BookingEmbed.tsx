'use client';

import Cal from '@calcom/embed-react';

interface BookingEmbedProps {
  calLink: string;
}

export function BookingEmbed({ calLink }: BookingEmbedProps) {
  return (
    <div className="min-h-[630px] w-full overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
      <Cal
        calLink={calLink}
        config={{ layout: 'month_view' }}
        style={{ width: '100%', height: '630px', overflow: 'scroll' }}
      />
    </div>
  );
}
