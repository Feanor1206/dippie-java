import { useEffect, type RefObject } from 'react';

const FOCUSABLE_SELECTOR =
  '[data-autofocus], button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

export function useBlockingOverlayFocus(
  containerRef: RefObject<HTMLElement | null>,
  active: boolean,
  fallbackRestoreRef?: RefObject<HTMLElement | null>
): void {
  useEffect(() => {
    if (!active || typeof document === 'undefined') {
      return;
    }

    const activeBefore = document.activeElement as HTMLElement | null;
    const opener =
      activeBefore && !containerRef.current?.contains?.(activeBefore) ? activeBefore : null;

    const focusFirstInside = () => {
      const container = containerRef.current;
      if (!container) return;
      if (typeof document.querySelector === 'function' && document.querySelector('dialog[open]')) {
        return;
      }
      if (typeof container.contains === 'function' && container.contains(document.activeElement)) {
        return;
      }
      const preferred =
        typeof container.querySelector === 'function'
          ? container.querySelector<HTMLElement>('[data-autofocus]:not([disabled])')
          : null;
      if (preferred && typeof preferred.focus === 'function') {
        preferred.focus();
        return;
      }
      const focusable =
        typeof container.querySelectorAll === 'function'
          ? container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)
          : [];
      if (focusable.length > 0 && typeof focusable[0].focus === 'function') {
        focusable[0].focus();
        return;
      }
      if (typeof container.focus === 'function') {
        container.focus();
      }
    };

    focusFirstInside();
    const timer = setTimeout(focusFirstInside, 25);

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Tab') return;
      if (typeof document.querySelector === 'function' && document.querySelector('dialog[open]')) {
        return;
      }
      const container = containerRef.current;
      if (!container || typeof container.querySelectorAll !== 'function') return;
      const focusable = Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));
      if (focusable.length === 0) {
        event.preventDefault();
        container.focus?.();
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const current = document.activeElement as HTMLElement | null;
      const isInside = Boolean(current && typeof container.contains === 'function' && container.contains(current));

      if (!isInside) {
        event.preventDefault();
        (event.shiftKey ? last : first).focus?.();
        return;
      }
      if (event.shiftKey && current === first) {
        event.preventDefault();
        last.focus?.();
      } else if (!event.shiftKey && current === last) {
        event.preventDefault();
        first.focus?.();
      }
    };

    if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
      window.addEventListener('keydown', handleKeyDown);
    }

    return () => {
      clearTimeout(timer);
      if (typeof window !== 'undefined' && typeof window.removeEventListener === 'function') {
        window.removeEventListener('keydown', handleKeyDown);
      }
      const restoreTarget =
        opener &&
        typeof opener.focus === 'function' &&
        (typeof document.contains !== 'function' || document.contains(opener))
          ? opener
          : fallbackRestoreRef?.current ?? null;
      if (restoreTarget && typeof restoreTarget.focus === 'function') {
        try {
          restoreTarget.focus();
        } catch {}
      }
    };
  }, [active, containerRef, fallbackRestoreRef]);
}
