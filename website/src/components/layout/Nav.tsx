'use client';

import { useEffect, useState } from 'react';
import { Logo } from '@/components/layout/Logo';
import { BookDemoLink } from '@/components/layout/BookDemoLink';
import { cn } from '@/lib/utils';

export function Nav() {
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 20);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  return (
    <header
      className={cn(
        'sticky top-0 z-50 border-b border-transparent backdrop-blur-xl transition-all duration-300',
        scrolled && 'border-border bg-background/85 shadow-sm shadow-black/[0.03]',
      )}
    >
      <div className="mx-auto flex h-16 max-w-content items-center justify-between px-6">
        <Logo />
        <BookDemoLink />
      </div>
    </header>
  );
}
