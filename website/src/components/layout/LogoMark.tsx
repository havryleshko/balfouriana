interface LogoMarkProps {
  className?: string;
}

export function LogoMark({ className = 'size-7 shrink-0 rounded-md' }: LogoMarkProps) {
  return (
    <svg
      viewBox="0 0 100 100"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      aria-hidden="true"
    >
      <rect width="100" height="100" rx="12" fill="#0C1D38" />
      <path
        d="M51,8 Q56,11 60,16 Q75,22 62,29 Q91,36 64,46 Q99,52 65,60 Q86,65 60,72 Q59,74 57,76 L56,96 L44,96 L43,76 Q41,74 40,72 Q12,65 38,60 Q1,52 36,46 Q9,38 38,30 Q25,23 40,16 Q44,11 51,8 Z"
        fill="#EBF2F8"
      />
      <rect x="45" y="74" width="10" height="22" rx="2" fill="#C4915A" />
    </svg>
  );
}
