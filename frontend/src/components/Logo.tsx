export function Logo() {
  return (
    <div className="logo">
      <svg viewBox="0 0 32 32" width="28" height="28" aria-hidden="true">
        <rect width="32" height="32" rx="8" fill="var(--surface-3)" />
        <path
          d="M9 21.5 14 11l4 8 2.5-4.5L23 21.5"
          fill="none"
          stroke="var(--accent)"
          strokeWidth="2.6"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      <span>ApplyTrack</span>
    </div>
  );
}
