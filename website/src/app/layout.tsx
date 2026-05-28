import type { Metadata } from 'next';
import { GeistSans } from 'geist/font/sans';
import { GeistMono } from 'geist/font/mono';
import { site } from '@/content/site';
import { cn } from '@/lib/utils';
import './globals.css';

export const metadata: Metadata = {
  title: site.title,
  description: site.description,
  metadataBase: new URL(site.url),
  alternates: { canonical: site.url },
  openGraph: {
    title: site.title,
    description: site.description,
    type: 'website',
    url: site.url,
  },
  robots: { index: true, follow: true },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={cn(GeistSans.variable, GeistMono.variable, 'font-sans')}>
      <head>
        <link rel="icon" href="/favicon.svg" type="image/svg+xml" />
        <link rel="preconnect" href="https://cal.com" />
        <link rel="preconnect" href="https://app.cal.com" />
      </head>
      <body className="min-h-screen antialiased">{children}</body>
    </html>
  );
}
