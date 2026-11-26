import React, { useCallback, useEffect, useState } from 'react';
import { SiteDialog } from '@/components/ui/SiteDialog';
import { ChildAvatarBadge } from '@/features/profiles/components/ChildAvatarBadge';
import { validateParentPin, type ChildProfile } from '@/lib/services/profileService';
import {
  fetchParentWeekendVaultOverview,
  getWeekdayLabel,
  restoreWeekendVaultLock,
  unlockWeekendVaultManually,
  type ChildProfileWeekendVault,
} from '@/lib/services/weekendVaultService';

type VaultDialog =
  | { kind: 'unlock'; item: ChildProfileWeekendVault }
  | { kind: 'lock'; item: ChildProfileWeekendVault }
  | null;

export function ParentWeekendVaultSection({
  token,
  childProfiles,
  activeProfileId = null,
  sessionPin = '',
  onPinVerified,
  onPinRejected,
}: {
  token: string;
  childProfiles: ChildProfile[];
  activeProfileId?: number | null;
  sessionPin?: string;
  onPinVerified?: (pin: string) => void;
  onPinRejected?: () => void;
}) {
  const [items, setItems] = useState<ChildProfileWeekendVault[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [dialog, setDialog] = useState<VaultDialog>(null);
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState('');
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const loadVaults = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setLoadError('');
    try {
      const data = await fetchParentWeekendVaultOverview(token);
      setItems(data);
    } catch (err: unknown) {
      setLoadError(err instanceof Error ? err.message : 'Chưa tải được trạng thái phim lẻ cuối tuần.');
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    void loadVaults();
  }, [loadVaults, childProfiles.length]);

  const resetDialog = () => {
    setDialog(null);
    setPin('');
    setDialogError('');
  };

  const closeDialog = () => {
    if (busy) return;
    resetDialog();
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!dialog || busy) return;
    const pinErr = validateParentPin(pin);
    if (pinErr) {
      setDialogError(pinErr);
      return;
    }
    setBusy(true);
    setDialogError('');
    try {
      const updated =
        dialog.kind === 'unlock'
          ? await unlockWeekendVaultManually(token, dialog.item.profileId, pin)
          : await restoreWeekendVaultLock(token, dialog.item.profileId, pin);
      onPinVerified?.(pin);
      setItems((prev) => prev.map((entry) => (entry.profileId === updated.profileId ? updated : entry)));
      const childName = dialog.item.name;
      const actionKind = dialog.kind;
      resetDialog();
      setFeedback({
        type: 'success',
        text:
          actionKind === 'unlock'
            ? `Đã mở khóa phim lẻ cuối tuần cho ${childName}.`
            : `Đã khóa lại phim lẻ cuối tuần cho ${childName}.`,
      });
    } catch (err: unknown) {
      onPinRejected?.();
      setPin('');
      setDialogError(err instanceof Error ? err.message : 'Chưa cập nhật được trạng thái phim lẻ cuối tuần.');
    } finally {
      setBusy(false);
    }
  };

  const filteredItems = activeProfileId
    ? items.filter((entry) => entry.profileId === activeProfileId)
    : items;
  const visibleItems = filteredItems.length > 0 ? filteredItems : items;

  return (
    <section
      id="parent-weekend-vault-section"
      aria-labelledby="parent-vault-heading"
      data-testid="parent-weekend-vault-section"
      className="mt-4 scroll-mt-20 rounded-xl border border-surface-border bg-paper-white p-5 sm:p-6"
    >
      <div className="border-b border-surface-border pb-4">
        <h3 id="parent-vault-heading" className="text-base font-bold text-ink">
          Cuối tuần này bé xem được phim lẻ chưa?
        </h3>
        <p className="mt-1 text-base leading-relaxed text-muted">
          Phim lẻ tự khóa từ thứ Hai đến thứ Sáu cho các bé. Thanh Vé xem phim tăng +20% mỗi ngày bé dừng xem trước khi hết giờ và +20% mỗi ngày bé trả lời đúng đủ 3 câu hỏi thưởng giờ; đạt 100% sẽ tự mở vào thứ Bảy &amp; Chủ Nhật.
        </p>
      </div>

      {feedback && (
        <div
          role={feedback.type === 'error' ? 'alert' : 'status'}
          data-testid="parent-vault-feedback"
          className={`motion-toast-enter mt-4 rounded-xl border p-3 text-xs ${
            feedback.type === 'error'
              ? 'border-red-500/40 bg-red-500/10 text-red-900'
              : 'border-brand/50 bg-brand/15 text-ink'
          }`}
        >
          {feedback.text}
        </div>
      )}

      {loadError ? (
        <div
          role="alert"
          data-testid="parent-vault-load-error"
          className="mt-4 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-red-800/35 bg-red-50 px-3.5 py-3 text-xs text-red-900"
        >
          <span>{loadError}</span>
          <button
            type="button"
            onClick={() => void loadVaults()}
            className="btn-secondary min-h-[44px] px-3 text-xs font-bold"
          >
            Thử lại
          </button>
        </div>
      ) : loading && items.length === 0 ? (
        <p role="status" className="mt-4 rounded-xl border border-surface-border bg-surface-muted p-4 text-center text-xs text-muted">
          Đang tải tiến độ Vé xem phim…
        </p>
      ) : visibleItems.length > 0 && (
        <ul className={`mt-4 grid grid-cols-1 gap-3 ${visibleItems.length > 1 ? 'lg:grid-cols-2' : ''}`}>
          {visibleItems.map((item) => {
            const { status } = item;
            const remainingPercent = Math.max(0, 100 - status.progressPercent);
            const answerLine = status.manualUnlocked
              ? 'Xem được ngay — ba mẹ đã mở khóa phim lẻ cho tuần này.'
              : !status.locked
              ? 'Xem được ngay — bé đã mở được phim lẻ cuối tuần này.'
              : status.progressPercent >= 100
              ? 'Đã đủ 100% Vé xem phim — phim lẻ sẽ tự mở vào thứ Bảy & Chủ Nhật.'
              : `Chưa mở — cần thêm ${remainingPercent}% Vé xem phim nữa để tự mở vào cuối tuần.`;
            return (
              <li
                key={item.profileId}
                data-testid={`parent-vault-card-${item.profileId}`}
                className="flex flex-col justify-between gap-3 rounded-xl border border-surface-border bg-surface p-4"
              >
                <div className="space-y-3">
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2.5 min-w-0">
                      <ChildAvatarBadge avatarKey={item.avatarKey} size="sm" />
                      <p className="truncate text-sm font-bold text-ink">{item.name}</p>
                    </div>
                    <span
                      className={`shrink-0 rounded-full border px-2 py-0.5 text-[0.6875rem] font-bold ${
                        status.locked
                          ? 'border-amber-700/40 bg-[#f2d18c]/60 text-ink'
                          : 'border-ink/40 bg-mint text-ink'
                      }`}
                    >
                      {status.manualUnlocked
                        ? 'Ba mẹ đã mở'
                        : status.locked
                        ? 'Đang khóa'
                        : 'Đã mở cuối tuần'}
                    </span>
                  </div>

                  <p className="text-xs font-medium leading-relaxed text-ink/90">{answerLine}</p>

                  <div>
                    <div className="flex items-center justify-between text-xs font-semibold text-ink">
                      <span>Vé xem phim tuần này</span>
                      <span data-testid={`parent-vault-percent-${item.profileId}`}>{status.progressPercent}%</span>
                    </div>
                    <div
                      role="progressbar"
                      aria-valuemin={0}
                      aria-valuemax={100}
                      aria-valuenow={status.progressPercent}
                      className="mt-1.5 h-3 w-full overflow-hidden rounded-full border border-ink/40 bg-paper-white"
                    >
                      <div
                        className="motion-progress-fill h-full bg-brand"
                        style={{ width: `${status.progressPercent}%` }}
                      />
                    </div>
                  </div>

                  {status.days.length > 0 && (
                    <div className="grid grid-cols-5 gap-1.5 text-center">
                      {status.days.map((day) => (
                        <div
                          key={day.day || day.dayOfWeek}
                          className={`rounded-lg border px-1 py-1.5 text-[0.625rem] font-bold ${
                            day.earnedPercent >= 40
                              ? 'border-ink bg-mint text-ink'
                              : day.earnedPercent >= 20
                              ? 'border-ink/40 bg-[#f2d18c]/55 text-ink'
                              : 'border-surface-border bg-paper-white text-muted'
                          }`}
                        >
                          <div>{getWeekdayLabel(day.dayOfWeek)}</div>
                          <div className="mt-0.5">+{day.earnedPercent}%</div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                <div className="flex flex-wrap gap-2">
                  {status.manualUnlocked ? (
                    <button
                      type="button"
                      data-testid={`lock-vault-button-${item.profileId}`}
                      onClick={() => {
                        setFeedback(null);
                        setDialogError('');
                        setPin(sessionPin);
                        setDialog({ kind: 'lock', item });
                      }}
                      className="btn-secondary min-h-[44px] w-full justify-center text-xs"
                    >
                      Khóa lại theo quy tắc tuần
                    </button>
                  ) : (
                    <button
                      type="button"
                      data-testid={`unlock-vault-button-${item.profileId}`}
                      onClick={() => {
                        setFeedback(null);
                        setDialogError('');
                        setPin(sessionPin);
                        setDialog({ kind: 'unlock', item });
                      }}
                      className="btn-primary min-h-[44px] w-full justify-center text-xs font-bold"
                    >
                      Mở khóa phim lẻ tuần này (PIN)
                    </button>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      )}

      {dialog && (
        <SiteDialog
          title={
            dialog.kind === 'unlock'
              ? `Mở khóa phim lẻ · ${dialog.item.name}`
              : `Khóa lại phim lẻ · ${dialog.item.name}`
          }
          onClose={closeDialog}
          busy={busy}
        >
          <form onSubmit={handleSubmit} className="space-y-4">
            <p className="text-xs leading-relaxed text-muted">
              {dialog.kind === 'unlock'
                ? `Cho phép ${dialog.item.name} xem phim lẻ trong tuần này ngay cả khi chưa đến cuối tuần hoặc chưa đủ 100% Vé xem phim.`
                : `Áp dụng lại quy tắc khóa phim lẻ từ thứ Hai đến thứ Sáu cho ${dialog.item.name}.`}
            </p>
            <div>
              <label htmlFor="vault-dialog-pin" className="mb-1.5 block text-xs font-semibold text-ink">
                Mã PIN phụ huynh
              </label>
              <input
                id="vault-dialog-pin"
                data-testid="vault-pin-input"
                type="password"
                inputMode="numeric"
                pattern="[0-9]*"
                enterKeyHint="done"
                autoComplete="off"
                autoFocus
                maxLength={6}
                required
                disabled={busy}
                value={pin}
                onChange={(event) => setPin(event.target.value.replace(/[^0-9]/g, '').slice(0, 6))}
                placeholder="••••"
                className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
              />
              {sessionPin && pin === sessionPin && (
                <div className="mt-1.5 flex items-center justify-between gap-2 text-[0.6875rem] text-brand-dark">
                  <span>Đã tự điền mã PIN vừa xác nhận trong phiên này.</span>
                  <button
                    type="button"
                    onClick={() => {
                      onPinRejected?.();
                      setPin('');
                    }}
                    className="font-bold underline cursor-pointer"
                  >
                    Nhập lại
                  </button>
                </div>
              )}
            </div>
            {dialogError && (
              <div role="alert" className="rounded-lg border border-red-800/35 bg-red-50 px-3.5 py-2.5 text-xs text-red-900">
                {dialogError}
              </div>
            )}
            <div className="flex flex-col-reverse gap-2 pt-1 sm:flex-row sm:justify-end">
              <button type="button" disabled={busy} onClick={closeDialog} className="btn-secondary w-full sm:w-auto text-xs">
                Hủy
              </button>
              <button type="submit" disabled={busy} className="btn-primary w-full sm:w-auto text-xs">
                {busy ? 'Đang xử lý…' : dialog.kind === 'unlock' ? 'Mở khóa tuần này' : 'Khóa lại'}
              </button>
            </div>
          </form>
        </SiteDialog>
      )}
    </section>
  );
}
