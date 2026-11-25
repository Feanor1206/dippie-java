import React, { useLayoutEffect, useId, useRef } from 'react';

export function SiteDialog({
  title,
  children,
  onClose,
  busy = false,
  tone = 'default',
}: {
  title: string;
  children: React.ReactNode;
  onClose: () => void;
  busy?: boolean;
  tone?: 'default' | 'warning' | 'danger';
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useLayoutEffect(() => {
    const dialog = dialogRef.current;
    const opener = typeof document !== 'undefined' ? (document.activeElement as HTMLElement | null) : null;
    if (dialog && typeof dialog.showModal === 'function' && !dialog.open) {
      dialog.showModal();
    }
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

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      onClick={(event) => {
        if (!busy && event.target === dialogRef.current) onClose();
      }}
      onKeyDown={(event) => event.stopPropagation()}
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-md overflow-hidden rounded-xl border-2 border-ink bg-paper-white p-0 text-ink shadow-brand-lg backdrop:bg-ink/60"
    >
      <div className="flex max-h-[calc(100dvh-2rem)] flex-col">
        <div className={`flex items-start justify-between gap-3 border-b px-5 py-4 sm:px-6 ${headerToneClass}`}>
          <h2 id={titleId} className="font-display text-lg font-bold leading-snug text-ink pt-1">
            {title}
          </h2>
          <button
            type="button"
            disabled={busy}
            onClick={onClose}
            aria-label="Đóng hộp thoại"
            className="inline-flex h-11 w-11 min-h-[44px] min-w-[44px] shrink-0 items-center justify-center rounded-lg text-sm font-semibold text-muted transition-colors hover:bg-surface-elevated hover:text-ink cursor-pointer disabled:cursor-not-allowed disabled:opacity-40"
          >
            ✕
          </button>
        </div>
        <div className="overflow-y-auto p-5 sm:p-6">
          {children}
        </div>
      </div>
    </dialog>
  );
}
