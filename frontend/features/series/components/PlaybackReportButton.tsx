import React, { useEffect, useId, useRef, useState } from 'react';
import { SiteDialog } from '@/components/ui/SiteDialog';
import { useAuth } from '@/lib/context/AuthContext';
import { PLAYBACK_REPORT_REASONS, submitPlaybackReport, type ReportReason } from '@/lib/services/playbackReportService';

export function PlaybackReportButton({
  episodeId,
  assetId,
  episodeTitle,
}: {
  episodeId: string;
  assetId: string | null;
  episodeTitle?: string;
}) {
  const { token } = useAuth();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState<ReportReason | ''>('');
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const request = useRef<AbortController | null>(null);
  const noteId = useId();
  const radioName = useId();

  useEffect(() => () => request.current?.abort(), []);

  const cleanEpisodeId = episodeId.trim();
  if (!cleanEpisodeId) return null;

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!reason || request.current) return;
    const controller = new AbortController();
    request.current = controller;
    setBusy(true);
    setError('');
    try {
      await submitPlaybackReport({ episodeId: cleanEpisodeId, assetId, reason, note }, token, controller.signal);
      setSent(true);
    } catch (fail) {
      if (!controller.signal.aborted) setError(fail instanceof Error ? fail.message : 'Chưa gửi được báo lỗi. Bạn thử lại nhé.');
    } finally {
      if (!controller.signal.aborted) setBusy(false);
      request.current = null;
    }
  };

  const close = () => {
    if (busy) return;
    setOpen(false);
  };

  return (
    <>
      <button
        type="button"
        data-testid="playback-report-trigger"
        className="btn-secondary min-h-[44px] px-3.5 py-2 text-xs"
        onClick={() => {
          setReason('');
          setNote('');
          setError('');
          setSent(false);
          setOpen(true);
        }}
      >
        <svg width={14} height={14} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden="true">
          <path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z" />
          <line x1="4" x2="4" y1="22" y2="15" />
        </svg>
        <span>Báo lỗi video</span>
      </button>
      {open && (
        <SiteDialog title="Báo lỗi video" onClose={close} busy={busy}>
          {sent ? (
            <div className="space-y-4">
              <div role="status" data-testid="playback-report-success" className="rounded-xl border border-ink/30 bg-mint/60 p-4 text-sm text-ink">
                <p className="font-bold">Đã nhận báo lỗi của bạn</p>
                <p className="mt-1 text-xs leading-relaxed text-ink/90">
                  Cảm ơn ba mẹ đã báo. Dippie sẽ kiểm tra lại nguồn phát của tập này.
                </p>
              </div>
              <div className="flex justify-end">
                <button type="button" autoFocus className="btn-primary text-xs" onClick={close}>Đóng</button>
              </div>
            </div>
          ) : (
            <form className="space-y-4" onSubmit={submit}>
              <p className="text-xs leading-relaxed text-muted">
                {episodeTitle
                  ? <>Cho Dippie biết lỗi ở <strong className="text-ink">{episodeTitle}</strong> để Dippie kiểm tra lại.</>
                  : 'Cho Dippie biết lỗi video bạn gặp để Dippie kiểm tra lại.'}
              </p>

              <fieldset className="space-y-2">
                <legend className="text-xs font-bold text-ink">Video bị lỗi gì?</legend>
                <div className="space-y-2 pt-1">
                  {Object.entries(PLAYBACK_REPORT_REASONS).map(([value, label], index) => {
                    const selected = reason === value;
                    return (
                      <label
                        key={value}
                        className={`flex min-h-[44px] cursor-pointer items-center gap-3 rounded-lg border px-3.5 py-2.5 text-sm transition-colors ${
                          selected
                            ? 'border-ink bg-mint/60 font-semibold text-ink shadow-[2px_2px_0_#203328]'
                            : 'border-surface-border/60 bg-surface-muted text-ink hover:border-ink hover:bg-surface-hover/60'
                        }`}
                      >
                        <input
                          type="radio"
                          name={radioName}
                          value={value}
                          autoFocus={index === 0}
                          required
                          disabled={busy}
                          checked={selected}
                          onChange={() => setReason(value as ReportReason)}
                          className="h-4 w-4 shrink-0 accent-[#203328]"
                        />
                        <span>{label}</span>
                      </label>
                    );
                  })}
                </div>
              </fieldset>

              <div className="space-y-1.5">
                <label htmlFor={noteId} className="block text-xs font-bold text-ink">
                  Ghi chú thêm (không bắt buộc)
                </label>
                <textarea
                  id={noteId}
                  value={note}
                  maxLength={500}
                  rows={3}
                  disabled={busy}
                  placeholder="Ví dụ: video đứng hình ở phút thứ 3, tiếng bị lệch…"
                  onChange={(event) => setNote(event.target.value)}
                  className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-base sm:text-sm text-ink placeholder:text-muted focus:border-ink focus:bg-paper-white focus:outline-none disabled:opacity-60"
                />
                <div className="flex flex-wrap items-center justify-between gap-2 text-[0.6875rem] text-muted">
                  <span>Đừng ghi thông tin cá nhân của gia đình hay của bé.</span>
                  <span className="tabular-nums font-medium">{note.length}/500</span>
                </div>
              </div>

              {error && (
                <div role="alert" className="rounded-lg border border-red-800/35 bg-red-50 px-3.5 py-2.5 text-xs leading-relaxed text-red-900">
                  {error}
                </div>
              )}

              <div className="flex flex-col-reverse gap-2 pt-1 sm:flex-row sm:justify-end">
                <button type="button" className="btn-secondary w-full sm:w-auto text-xs" disabled={busy} onClick={close}>Hủy</button>
                <button type="submit" className="btn-primary w-full sm:w-auto text-xs" disabled={busy || !reason}>
                  {busy ? 'Đang gửi…' : 'Gửi báo lỗi'}
                </button>
              </div>
            </form>
          )}
        </SiteDialog>
      )}
    </>
  );
}
