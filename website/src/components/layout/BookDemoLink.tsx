import { site } from '@/content/site';
import { buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/utils';

interface BookDemoLinkProps {
  className?: string;
}

export function BookDemoLink({ className }: BookDemoLinkProps) {
  return (
    <a
      href={site.ctaHref}
      className={cn(
        buttonVariants({ variant: 'outline', size: 'lg' }),
        'h-11 rounded-full border-foreground bg-foreground px-6 text-background shadow-sm transition-all hover:bg-foreground/90 hover:text-background hover:shadow-md',
        className,
      )}
    >
      {site.ctaLabel}
    </a>
  );
}
