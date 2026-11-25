import React from 'react';
import { SiteDialog } from '@/components/ui/SiteDialog';
import type { GuestSyncSummary } from '@/lib/services/libraryService';

export type GuestSyncDialogProps = {
  summary: GuestSyncSummary;
  busy: boolean;
  onConfirm: () => void;
  onDecline: () => void;
  onDismiss: () => void;
};

function buildSummaryParts(summary: GuestSyncSummary): string[] {
  const parts: string[] = [];
  if (summary.watchlistCount > 0) parts.push(`${summary.watchlistCount} phim Xem sau`);
  if (summary.favoritesCount > 0) parts.push(`${summary.favoritesCount} phim Yêu thích`);
  if (summary.ratingsCount > 0) parts.push(`${summary.ratingsCount} lượt chấm điểm`);
  if (summary.progressCount > 0) parts.push(`${summary.progressCount} tập đang xem`);
  return parts;
}

export function GuestSyncDialog({
  summary,
  busy,
  onConfirm,
  onDecline,
  onDismiss,
}: GuestSyncDialogProps) {
  const parts = buildSummaryParts(summary);

  return (
    <SiteDialog
      title="Chuyển phim đã lưu trên máy vào tài khoản?"
      onClose={onDismiss}
      busy={busy}
      tone="warning"
    >
      <div className="space-y-3 text-sm leading-relaxed text-ink">
        {parts.length > 0 && (
          <div className="rounded-lg border border-ink/25 bg-surface-muted px-3.5 py-2.5 text-xs font-medium text-ink">
            Trên máy này đang có: <strong className="font-bold">{parts.join(', ')}</strong>.
          </div>
        )}
        <p className="text-muted">
          Nếu đây là thư viện của bạn, bấm <strong>Chuyển</strong> để gộp vào tài khoản mà không làm mất các mục đã có.
        </p>
        <p className="text-xs text-muted">
          Nếu nhiều người dùng chung máy này, bạn có thể xóa dữ liệu tạm để không gộp nhầm phim của người khác.
        </p>
      </div>
      <div className="mt-5 flex flex-col-reverse gap-2.5 sm:flex-row sm:justify-end">
        <button
          type="button"
          data-testid="guest-sync-decline"
          disabled={busy}
          onClick={onDecline}
          className="btn-secondary w-full sm:w-auto text-xs"
        >
          Không, xóa dữ liệu tạm
        </button>
        <button
          type="button"
          autoFocus
          data-testid="guest-sync-confirm"
          disabled={busy}
          onClick={onConfirm}
          className="btn-primary w-full sm:w-auto text-xs"
        >
          {busy ? 'Đang chuyển…' : 'Chuyển'}
        </button>
      </div>
    </SiteDialog>
  );
}
