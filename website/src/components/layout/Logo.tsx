import Link from 'next/link';
import { LogoMark } from '@/components/layout/LogoMark';
import { cn } from '@/lib/utils';

interface LogoProps {
  className?: string;
}

export function Logo({ className = '' }: LogoProps) {
  return (
    <Link
      href="/"
      className={cn('inline-flex items-center gap-2.5 text-foreground', className)}
      aria-label="Balfouriana home"
    >
      <LogoMark />
      <span className="text-base font-semibold tracking-tight">Balfouriana</span>
    </Link>
  );
}
