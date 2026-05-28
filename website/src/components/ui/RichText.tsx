import { cn } from '@/lib/utils';

interface RegRefProps {
  children: React.ReactNode;
  className?: string;
}

export function RegRef({ children, className }: RegRefProps) {
  return (
    <span
      className={cn(
        'rounded px-1.5 py-0.5 font-mono text-xs text-muted-foreground bg-muted',
        className,
      )}
    >
      {children}
    </span>
  );
}

const REG_PATTERN = /(\{[^}]+\})/g;

export function RichText({ text }: { text: string }) {
  const parts = text.split(REG_PATTERN);

  return (
    <>
      {parts.map((part, i) => {
        if (part.startsWith('{') && part.endsWith('}')) {
          return <RegRef key={i}>{part.slice(1, -1)}</RegRef>;
        }
        return part;
      })}
    </>
  );
}
