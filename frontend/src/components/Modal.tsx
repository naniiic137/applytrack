import { useEffect, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';

interface Props {
  title: string;
  onClose: () => void;
  children: ReactNode;
  variant?: 'dialog' | 'drawer';
}

const FOCUSABLE = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

/**
 * Marks everything outside `element` inert (not focusable, not clickable, hidden from screen readers),
 * walking up to <body>. Returns a function that restores what it changed.
 */
function makeOutsideInert(element: HTMLElement): () => void {
  const changed: HTMLElement[] = [];
  for (let node: HTMLElement | null = element; node && node !== document.body; node = node.parentElement) {
    for (const sibling of Array.from(node.parentElement?.children ?? [])) {
      if (sibling !== node && sibling instanceof HTMLElement && !sibling.hasAttribute('inert')) {
        sibling.setAttribute('inert', '');
        changed.push(sibling);
      }
    }
  }
  return () => changed.forEach((el) => el.removeAttribute('inert'));
}

/** Keeps Tab / Shift+Tab cycling inside the panel. */
function trapTab(event: KeyboardEvent, panel: HTMLElement) {
  const focusable = Array.from(panel.querySelectorAll<HTMLElement>(FOCUSABLE));
  if (focusable.length === 0) {
    event.preventDefault();
    panel.focus();
    return;
  }
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  const active = document.activeElement;
  if (event.shiftKey && (active === first || active === panel)) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && active === last) {
    event.preventDefault();
    first.focus();
  }
}

/** Accessible overlay used for both the centred form dialog and the right-hand detail drawer. */
export function Modal({ title, onClose, children, variant = 'dialog' }: Props) {
  const overlayRef = useRef<HTMLDivElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  // Parents pass a new onClose on every render. Reading it through a ref lets the effect below run once
  // per opening; before, it re-ran on every render and yanked the focus back to the panel.
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    const overlay = overlayRef.current;
    const panel = panelRef.current;
    if (!overlay || !panel) return;

    const previous = document.activeElement as HTMLElement | null;
    // Keep the focus if a child already took it (autoFocus), otherwise focus the panel itself.
    if (!panel.contains(document.activeElement)) panel.focus();

    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCloseRef.current();
      else if (e.key === 'Tab') trapTab(e, panel);
    };
    document.addEventListener('keydown', onKey);
    const restoreInert = makeOutsideInert(overlay);
    const { overflow } = document.body.style;
    document.body.style.overflow = 'hidden';

    return () => {
      document.removeEventListener('keydown', onKey);
      restoreInert();
      document.body.style.overflow = overflow;
      previous?.focus?.();
    };
  }, []);

  return (
    <div
      ref={overlayRef}
      className={`overlay overlay-${variant}`}
      onMouseDown={(e) => e.target === e.currentTarget && onCloseRef.current()}
    >
      <div
        ref={panelRef}
        className={`panel panel-${variant}`}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
      >
        <button type="button" className="icon-btn panel-close" onClick={() => onCloseRef.current()} aria-label="Close">
          <X size={18} />
        </button>
        {children}
      </div>
    </div>
  );
}
