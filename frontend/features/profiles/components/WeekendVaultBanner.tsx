import React, { useState } from 'react';
import { Lock, Ticket, Unlock } from 'lucide-react';
import { SiteDialog } from '@/components/ui/SiteDialog';
import { validateParentPin } from '@/lib/services/profileService';
import {
  getWeekdayLabel,
  unlockWeekendVaultManually,
  type WeekendVaultStatus,
} from '@/lib/services/weekendVaultService';

export function WeekendVaultBanner({
  status,
  token,
  onStatusUpdated,
}: {
  status: WeekendVaultStatus;
  token: string;
  onStatusUpdated?: (next: WeekendVaultStatus) => void;
}) {
  const [pinDialogOpen, setPinDialogOpen] = useState(false);
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  if (!status.enabled) {
    return null;
  }

  const handleUnlock = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!status.profileId || busy) return;
    const pinErr = validateParentPin(pin);
    if (pinErr) {
      setError(pinErr);
      return;
    }
    setBusy(true);
    setError('');
    try {
      const updated = await unlockWeekendVaultManually(token, status.profileId, pin);
      setPinDialogOpen(false);
      setPin('');
      onStatusUpdated?.(updated.status);
    } catch (err: unknown) {
      setPin('');
      setError(err instanceof Error ? err.message : 'Mã PIN phụ huynh chưa đúng.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      data-testid="weekend-vault-banner"
      className="rounded-2xl border-2 border-ink bg-paper-white p-4 shadow-[3px_3px_0_#203328] sm:p-5"
    >
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-start gap-3">
          <div
            className={`grid h-11 w-11 shrink-0 place-items-center rounded-xl border-2 border-ink ${
              status.locked ? 'bg-[#f2d18c] text-ink' : 'bg-mint text-ink'
            }`}
          >
            {status.locked ? <Lock size={20} aria-hidden="true" /> : <Unlock size={20} aria-hidden="true" />}
          </div>
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="font-display text-base font-bold text-ink sm:text-lg">
                Két sắt phim cuối tuần · Vé xem phim
              </h2>
              <span
                data-testid="weekend-vault-lock-badge"
                className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-[11px] font-bold ${
                  status.locked
                    ? 'border-amber-700/40 bg-[#f2d18c]/70 text-ink'
                    : 'border-ink bg-mint text-ink'
                }`}
              >
                <Ticket size={12} aria-hidden="true" />
                {status.manualUnlocked
                  ? 'Ba mẹ đã mở tuần này'
                  : status.locked
                  ? 'Phim lẻ đang khóa'
                  : 'Phim lẻ đã mở!'}
              </span>
            </div>
            <p className="mt-1 text-xs text-muted">
              {status.locked
                ? status.weekend
                  ? 'Bé cần tích đủ 100% Vé xem phim từ thứ Hai đến thứ Sáu (hoặc nhờ ba mẹ mở khóa) để xem phim lẻ cuối tuần nhé!'
                  : 'Dừng xem trước khi hết giờ (+20%/ngày) và giải đủ 3 câu toán (+20%/ngày) từ thứ Hai đến thứ Sáu để mở phim lẻ vào thứ Bảy & Chủ Nhật!'
                : 'Chúc mừng bé! Két sắt phim lẻ đã mở, bé có thể chọn phim lẻ yêu thích để xem rồi.'}
            </p>
          </div>
        </div>

        {status.locked && status.profileId && (
          <button
            type="button"
            data-testid="kid-vault-unlock-button"
            onClick={() => {
              setPin('');
              setError('');
              setPinDialogOpen(true);
            }}
            className="btn-secondary min-h-[44px] shrink-0 px-3 text-xs font-bold"
          >
            Ba mẹ mở khóa (PIN)
          </button>
        )}
      </div>

      <div className="mt-4">
        <div className="flex items-center justify-between text-xs font-bold text-ink">
          <span>Vé xem phim</span>
          <span data-testid="weekend-vault-percent">{status.progressPercent}%</span>
        </div>
        <div
          role="progressbar"
          aria-label="Tiến độ Vé xem phim tuần này"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={status.progressPercent}
          className="mt-1.5 h-3.5 w-full overflow-hidden rounded-full border-2 border-ink bg-surface-muted"
        >
          <div
            className="h-full bg-brand transition-all duration-300"
            style={{ width: `${status.progressPercent}%` }}
          />
        </div>

        {status.days.length > 0 && (
          <div className="mt-3 grid grid-cols-5 gap-2 text-center">
            {status.days.map((day) => (
              <div
                key={day.day || day.dayOfWeek}
                className={`rounded-lg border px-2 py-1.5 text-[11px] font-bold ${
                  day.earnedPercent >= 40
                    ? 'border-ink bg-mint text-ink'
                    : day.earnedPercent >= 20
                    ? 'border-ink/50 bg-[#f2d18c]/55 text-ink'
                    : 'border-surface-border bg-surface text-muted'
                }`}
              >
                <div>{getWeekdayLabel(day.dayOfWeek)}</div>
                <div className="text-[10px]">+{day.earnedPercent}%</div>
              </div>
            ))}
          </div>
        )}
      </div>

      {pinDialogOpen && (
        <SiteDialog
          title="Ba mẹ mở khóa phim lẻ"
          onClose={() => {
            if (!busy) setPinDialogOpen(false);
          }}
          busy={busy}
        >
          <form onSubmit={handleUnlock} className="space-y-4">
            <p className="text-xs leading-relaxed text-muted">
              Nhập mã PIN phụ huynh để mở khóa phim lẻ trong két sắt cuối tuần cho bé ngay bây giờ.
            </p>
            <div>
              <label htmlFor="kid-vault-pin" className="mb-1.5 block text-xs font-semibold text-ink">
                Mã PIN phụ huynh
              </label>
              <input
                id="kid-vault-pin"
                data-testid="kid-vault-pin-input"
                type="password"
                inputMode="numeric"
                autoComplete="off"
                maxLength={6}
                required
                disabled={busy}
                value={pin}
                onChange={(event) => setPin(event.target.value.replace(/[^0-9]/g, '').slice(0, 6))}
                placeholder="••••"
                className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
              />
            </div>
            {error && (
              <div role="alert" className="rounded-lg border border-red-800/35 bg-red-50 px-3.5 py-2.5 text-xs text-red-900">
                {error}
              </div>
            )}
            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <button
                type="button"
                disabled={busy}
                onClick={() => setPinDialogOpen(false)}
                className="btn-secondary w-full sm:w-auto text-xs"
              >
                Hủy
              </button>
              <button
                type="submit"
                data-testid="kid-vault-pin-submit"
                disabled={busy}
                className="btn-primary w-full sm:w-auto text-xs"
              >
                {busy ? 'Đang mở khóa…' : 'Mở khóa phim lẻ'}
              </button>
            </div>
          </form>
        </SiteDialog>
      )}
    </div>
  );
}
