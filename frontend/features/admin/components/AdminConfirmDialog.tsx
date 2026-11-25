import React, { useEffect, useRef } from 'react';
import { AlertTriangle, Loader2, XCircle } from 'lucide-react';

interface AdminConfirmDialogProps {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  cancelLabel?: string;
  tone?: 'primary' | 'danger';
  isSubmitting?: boolean;
  error?: string | null;
  onConfirm: () => void;
  onCancel: () => void;
}

export function AdminConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  cancelLabel = 'Hủy',
  tone = 'primary',
  isSubmitting = false,
  error = null,
  onConfirm,
  onCancel,
}: AdminConfirmDialogProps) {
  const modalRef = useRef<HTMLDivElement>(null);
  const openerRef = useRef<HTMLElement | null>(null);
  const dialogState = useRef({ isSubmitting, onCancel });
  useEffect(() => { dialogState.current = { isSubmitting, onCancel }; }, [isSubmitting, onCancel]);
  const cancelButtonRef = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    if (!open) return;

    if (document.activeElement && (!modalRef.current || !modalRef.current.contains(document.activeElement))) {
      openerRef.current = document.activeElement as HTMLElement | null;
    }

    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    const timer = setTimeout(() => {
      cancelButtonRef.current?.focus();
    }, 30);

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !dialogState.current.isSubmitting) {
        e.preventDefault();
        dialogState.current.onCancel();
        return;
      }

      if (e.key === 'Tab' && modalRef.current) {
        const focusableElements = modalRef.current.querySelectorAll<HTMLElement>(
          'button:not([disabled]), [href], input:not([disabled]), [tabindex]:not([tabindex="-1"])'
        );
        if (focusableElements.length === 0) {
          e.preventDefault();
          modalRef.current.focus();
          return;
        }

        const firstElement = focusableElements[0];
        const lastElement = focusableElements[focusableElements.length - 1];

        if (!modalRef.current.contains(document.activeElement)) {
          e.preventDefault();
          if (e.shiftKey) lastElement.focus();
          else firstElement.focus();
          return;
        }

        if (e.shiftKey) {
          if (document.activeElement === firstElement) {
            e.preventDefault();
            lastElement.focus();
          }
        } else {
          if (document.activeElement === lastElement) {
            e.preventDefault();
            firstElement.focus();
          }
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);

    return () => {
      clearTimeout(timer);
      document.body.style.overflow = originalOverflow;
      window.removeEventListener('keydown', handleKeyDown);
      openerRef.current?.focus();
    };
  }, [open]);

  useEffect(() => {
    if (isSubmitting && modalRef.current && !modalRef.current.contains(document.activeElement)) {
      modalRef.current.focus();
    }
  }, [isSubmitting]);

  if (!open) return null;

  return (
    <div
      ref={modalRef}
      role="dialog"
      tabIndex={-1}
      aria-modal="true"
      aria-labelledby="admin-confirm-dialog-title"
      aria-describedby="admin-confirm-dialog-desc"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm animate-fade-in"
    >
      <div className="relative w-full max-w-md rounded-xl border border-surface-border bg-surface p-6 shadow-2xl">
        <button
          type="button"
          onClick={onCancel}
          disabled={isSubmitting}
          aria-label="Đóng hộp thoại xác nhận"
          className="absolute right-3 top-3 flex min-h-[44px] min-w-[44px] items-center justify-center rounded-lg px-2 text-xs font-semibold text-muted hover:bg-surface-elevated hover:text-ink disabled:opacity-50"
        >
          ✕
        </button>

        <div className="flex items-start gap-3 pr-10">
          <div
            className={`grid h-10 w-10 shrink-0 place-items-center rounded-lg border ${
              tone === 'danger'
                ? 'border-red-500/30 bg-red-500/10 text-red-900'
                : 'border-brand/30 bg-brand/10 text-brand-dark'
            }`}
          >
            <AlertTriangle size={18} aria-hidden="true" />
          </div>
          <div className="min-w-0">
            <h2 id="admin-confirm-dialog-title" className="text-lg font-bold text-ink">
              {title}
            </h2>
            <p id="admin-confirm-dialog-desc" className="mt-1.5 text-xs leading-relaxed text-muted">
              {description}
            </p>
          </div>
        </div>

        {error && (
          <div
            role="alert"
            data-testid="admin-confirm-error"
            className="mt-4 flex items-start gap-2 rounded-lg border border-red-500/30 bg-red-500/10 p-3 text-xs text-red-900"
          >
            <XCircle size={15} className="mt-0.5 shrink-0" aria-hidden="true" />
            <span>{error}</span>
          </div>
        )}

        <div className="mt-6 flex flex-wrap items-center justify-end gap-3">
          <button
            ref={cancelButtonRef}
            type="button"
            onClick={onCancel}
            disabled={isSubmitting}
            className="btn-secondary min-h-[44px] text-xs font-semibold disabled:opacity-50"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            data-testid="admin-confirm-submit"
            onClick={onConfirm}
            disabled={isSubmitting}
            className={`inline-flex min-h-[44px] items-center justify-center gap-2 border-2 border-ink px-5 py-2.5 text-xs font-bold transition-transform duration-150 hover:-translate-y-0.5 active:translate-x-0.5 active:translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-50 ${
              tone === 'danger'
                ? 'bg-red-500/20 text-red-900 shadow-brand-sm'
                : 'btn-primary'
            }`}
          >
            {isSubmitting ? (
              <>
                <Loader2 size={14} className="animate-spin" aria-hidden="true" />
                <span>Đang lưu…</span>
              </>
            ) : (
              <span>{confirmLabel}</span>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
