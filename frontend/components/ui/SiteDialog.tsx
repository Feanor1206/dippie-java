import React, { useLayoutEffect, useId, useRef } from 'react';

export type SiteDialogSize = 'sm' | 'md' | 'lg' | 'xl';

const FOCUSABLE_SELECTOR =
  'button:not([disabled]), [href], input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

const PRIMARY_INPUT_SELECTOR =
  '[autofocus], [data-autofocus="true"], input:not([disabled]):not([type="hidden"]):not([type="checkbox"]):not([type="radio"]), select:not([disabled]), textarea:not([disabled])';

export function SiteDialog({
  title,
  children,
  onClose,
  busy = false,
  tone = 'default',
  size = 'md',
  footer,
}: {
  title: string;
  children: React.ReactNode;
  onClose: () => void;
  busy?: boolean;
  tone?: 'default' | 'warning' | 'danger';
  size?: SiteDialogSize;
  footer?: React.ReactNode;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const bodyRef = useRef<HTMLDivElement>(null);
  const footerRef = useRef<HTMLDivElement>(null);
  const closeBtnRef = useRef<HTMLButtonElement>(null);
  const titleId = useId();

  useLayoutEffect(() => {
    const dialog = dialogRef.current;
    const opener = typeof document !== 'undefined' ? (document.activeElement as HTMLElement | null) : null;
    if (dialog && typeof dialog.showModal === 'function' && !dialog.open) {
      dialog.showModal();
    }
    const bodyEl = bodyRef.current;
    const footerEl = footerRef.current;
    const preferredTarget =
      bodyEl?.querySelector<HTMLElement>(PRIMARY_INPUT_SELECTOR) ||
      bodyEl?.querySelector<HTMLElement>(FOCUSABLE_SELECTOR) ||
      footerEl?.querySelector<HTMLElement>(FOCUSABLE_SELECTOR) ||
      closeBtnRef.current;
    preferredTarget?.focus?.();

    return () => {
      if (dialog && typeof dialog.close === 'function' && dialog.open) {
        dialog.close();
      }
      if (opener?.isConnected) opener.focus();
    };
  }, []);

  const headerToneClass =
    tone === 'danger'
      ? 'bg-red-50/90 border-red-800/25'
      : tone === 'warning'
      ? 'bg-[#f2d18c]/35 border-ink/25'
      : 'bg-surface-muted border-ink/20';

  const sizeClass =
    size === 'sm'
      ? 'max-w-sm'
      : size === 'lg'
      ? 'max-w-xl'
      : size === 'xl'
      ? 'max-w-2xl'
      : 'max-w-md';

  const handleKeyDown = (event: React.KeyboardEvent<HTMLDialogElement>) => {
    event.stopPropagation();
    if (event.key === 'Tab' && dialogRef.current && typeof dialogRef.current.querySelectorAll === 'function') {
      const focusable = dialogRef.current.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR);
      if (!focusable.length) return;
      const firstElement = focusable[0];
      const lastElement = focusable[focusable.length - 1];
      if (typeof document !== 'undefined' && !dialogRef.current.contains(document.activeElement)) {
        event.preventDefault();
        firstElement.focus();
        return;
      }
      if (event.shiftKey) {
        if (typeof document !== 'undefined' && document.activeElement === firstElement) {
          event.preventDefault();
          lastElement.focus();
        }
      } else if (typeof document !== 'undefined' && document.activeElement === lastElement) {
        event.preventDefault();
        firstElement.focus();
      }
    }
  };

  return (
    <dialog
      ref={dialogRef}
      data-dialog-size={size}
      aria-labelledby={titleId}
      onClick={(event) => {
        if (!busy && event.target === dialogRef.current) onClose();
      }}
      onKeyDown={handleKeyDown}
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
      className={`motion-dialog-enter m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] ${sizeClass} overflow-hidden rounded-xl border-2 border-ink bg-paper-white p-0 text-ink shadow-brand-lg backdrop:bg-ink/60`}
    >
      <div className="flex max-h-[calc(100dvh-2rem)] flex-col">
        <div className={`flex shrink-0 items-start justify-between gap-3 border-b px-5 py-4 sm:px-6 ${headerToneClass}`}>
          <h2 id={titleId} className="font-display text-lg font-bold leading-snug text-ink pt-1">
            {title}
          </h2>
          <button
            ref={closeBtnRef}
            type="button"
            disabled={busy}
            onClick={onClose}
            aria-label="Đóng hộp thoại"
            className="inline-flex h-11 w-11 min-h-[44px] min-w-[44px] shrink-0 items-center justify-center rounded-lg text-sm font-semibold text-muted transition-colors hover:bg-surface-elevated hover:text-ink cursor-pointer disabled:cursor-not-allowed disabled:opacity-40"
          >
            ✕
          </button>
        </div>
        <div ref={bodyRef} className="min-h-0 flex-1 overflow-y-auto p-5 sm:p-6">
          {children}
        </div>
        {footer && (
          <div ref={footerRef} className="shrink-0 border-t border-surface-border bg-paper-white px-5 py-3.5 sm:px-6">
            {footer}
          </div>
        )}
      </div>
    </dialog>
  );
}
