import { site } from '@/content/site';
import { Logo } from '@/components/layout/Logo';
import { Separator } from '@/components/ui/separator';

const linkedInUrl = process.env.NEXT_PUBLIC_LINKEDIN_URL ?? site.linkedInUrl;

export function Footer() {
  return (
    <footer className="border-t border-border bg-card">
      <div className="mx-auto max-w-content px-6 py-12">
        <div className="flex flex-col gap-8 md:flex-row md:items-start md:justify-between">
          <div className="flex flex-col gap-3">
            <Logo />
            <p className="max-w-sm text-sm text-muted-foreground">{site.tagline}</p>
          </div>
          <div className="flex flex-col gap-2 text-sm text-muted-foreground">
            <a href="/" className="transition-colors hover:text-foreground">
              balfouriana.com
            </a>
            <a
              href={linkedInUrl}
              className="transition-colors hover:text-foreground"
              target="_blank"
              rel="noopener noreferrer"
            >
              LinkedIn
            </a>
            <a
              href={site.licenseUrl}
              className="transition-colors hover:text-foreground"
              target="_blank"
              rel="noopener noreferrer"
            >
              MIT License
            </a>
          </div>
        </div>
        <Separator className="my-8" />
        <p className="text-xs text-muted-foreground">© {new Date().getFullYear()} Balfouriana</p>
      </div>
    </footer>
  );
}
