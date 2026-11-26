import React, { useEffect, useState } from 'react';
import { SiteDialog } from '@/components/ui/SiteDialog';
import { validateParentPin, type ChildProfile } from '@/lib/services/profileService';
import {
  fetchParentPlaySettings,
  updateParentPlaySettings,
  validatePlaySettings,
  type ChildProfilePlaySettings,
} from '@/lib/services/playFeaturesService';

export function PlayFeaturesSettings({
  token,
  profile,
  onClose,
  onSaved,
}: {
  token: string;
  profile: ChildProfile;
  onClose: () => void;
  onSaved?: (saved: ChildProfilePlaySettings) => void;
}) {
  const [watchPetEnabled, setWatchPetEnabled] = useState(true);
  const [watchPetFaintMinutes, setWatchPetFaintMinutes] = useState('20');
  const [watchPetRestMinutes, setWatchPetRestMinutes] = useState('5');
  const [lootDropEnabled, setLootDropEnabled] = useState(true);
  const [recapToNextEnabled, setRecapToNextEnabled] = useState(true);
  const [pin, setPin] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    void fetchParentPlaySettings(token)
      .then((list) => {
        if (!active) return;
        const found = list.find((item) => item.profileId === profile.id);
        if (found) {
          setWatchPetEnabled(found.watchPetEnabled);
          setWatchPetFaintMinutes(String(found.watchPetFaintMinutes));
          setWatchPetRestMinutes(String(found.watchPetRestMinutes));
          setLootDropEnabled(found.lootDropEnabled);
          setRecapToNextEnabled(found.recapToNextEnabled);
        }
      })
      .catch((err: unknown) => {
        if (!active) return;
        setError(err instanceof Error ? err.message : 'Chưa tải được cài đặt tính năng xem phim.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [token, profile.id]);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (busy || loading) return;
    setError('');

    const parsedFaint = Number(watchPetFaintMinutes);
    const parsedRest = Number(watchPetRestMinutes);
    const settingsErr = validatePlaySettings({
      watchPetEnabled,
      watchPetFaintMinutes: parsedFaint,
      watchPetRestMinutes: parsedRest,
      lootDropEnabled,
      recapToNextEnabled,
    });
    if (settingsErr) {
      setError(settingsErr);
      return;
    }
    const pinErr = validateParentPin(pin);
    if (pinErr) {
      setError(pinErr);
      return;
    }

    setBusy(true);
    try {
      const saved = await updateParentPlaySettings(token, profile.id, {
        pin,
        watchPetEnabled,
        watchPetFaintMinutes: parsedFaint,
        watchPetRestMinutes: parsedRest,
        lootDropEnabled,
        recapToNextEnabled,
      });
      onSaved?.(saved);
      onClose();
    } catch (err: unknown) {
      setPin('');
      setError(err instanceof Error ? err.message : 'Chưa lưu được cài đặt tính năng xem phim.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <SiteDialog
      title={`Tính năng xem phim · ${profile.name}`}
      onClose={onClose}
      busy={busy}
    >
      <form onSubmit={handleSubmit} data-testid="play-features-settings-form" className="space-y-4">
        {loading ? (
          <p role="status" className="py-4 text-center text-xs text-muted">
            Đang tải cài đặt của bé…
          </p>
        ) : (
          <>
            <div className="rounded-xl border border-surface-border bg-surface-muted p-3.5 space-y-3">
              <label className="flex min-h-[44px] items-start gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  data-testid="play-setting-watch-pet-toggle"
                  checked={watchPetEnabled}
                  disabled={busy}
                  onChange={(event) => setWatchPetEnabled(event.target.checked)}
                  className="mt-1 h-4 w-4 accent-brand"
                />
                <span>
                  <span className="block text-xs font-bold text-ink">Thú ảo canh giờ nghỉ mắt</span>
                  <span className="block text-[11px] text-muted">
                    Nhắc bé nghỉ mắt khi xem liên tục quá lâu, ngất ở ngưỡng chọn để bé cho thú ngủ hoặc giải toán hồi máu.
                  </span>
                </span>
              </label>

              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div>
                  <label htmlFor="play-pet-faint-minutes" className="mb-1 block text-xs font-semibold text-ink">
                    Ngưỡng thú mệt xỉu (10–60 phút)
                  </label>
                  <input
                    id="play-pet-faint-minutes"
                    data-testid="play-setting-faint-minutes-input"
                    type="number"
                    min={10}
                    max={60}
                    required
                    disabled={busy || !watchPetEnabled}
                    value={watchPetFaintMinutes}
                    onChange={(event) => setWatchPetFaintMinutes(event.target.value)}
                    className="w-full rounded-lg border border-surface-border bg-paper-white px-3 py-2 text-sm text-ink disabled:opacity-50"
                  />
                </div>
                <div>
                  <label htmlFor="play-pet-rest-minutes" className="mb-1 block text-xs font-semibold text-ink">
                    Thời gian cho thú ngủ (3–15 phút)
                  </label>
                  <input
                    id="play-pet-rest-minutes"
                    data-testid="play-setting-rest-minutes-input"
                    type="number"
                    min={3}
                    max={15}
                    required
                    disabled={busy || !watchPetEnabled}
                    value={watchPetRestMinutes}
                    onChange={(event) => setWatchPetRestMinutes(event.target.value)}
                    className="w-full rounded-lg border border-surface-border bg-paper-white px-3 py-2 text-sm text-ink disabled:opacity-50"
                  />
                </div>
              </div>
            </div>

            <label className="flex min-h-[44px] items-start gap-3 rounded-xl border border-surface-border bg-surface-muted p-3.5 cursor-pointer">
              <input
                type="checkbox"
                data-testid="play-setting-loot-toggle"
                checked={lootDropEnabled}
                disabled={busy}
                onChange={(event) => setLootDropEnabled(event.target.checked)}
                className="mt-1 h-4 w-4 accent-brand"
              />
              <span>
                <span className="block text-xs font-bold text-ink">Rương báu bay ngang</span>
                <span className="block text-[11px] text-muted">
                  Mỗi 5–7 phút xem có rương báu xuất hiện 15 giây, chạm để giải toán nhanh 10 giây nhận +3 phút (combo 3 tặng thêm +1 phút).
                </span>
              </span>
            </label>

            <label className="flex min-h-[44px] items-start gap-3 rounded-xl border border-surface-border bg-surface-muted p-3.5 cursor-pointer">
              <input
                type="checkbox"
                data-testid="play-setting-recap-toggle"
                checked={recapToNextEnabled}
                disabled={busy}
                onChange={(event) => setRecapToNextEnabled(event.target.checked)}
                className="mt-1 h-4 w-4 accent-brand"
              />
              <span>
                <span className="block text-xs font-bold text-ink">Xếp lại cốt truyện để qua tập</span>
                <span className="block text-[11px] text-muted">
                  Sau khi xem hết một tập, bé trả lời nhanh câu hỏi ôn tập để mở khóa tập tiếp theo.
                </span>
              </span>
            </label>

            <div>
              <label htmlFor="play-settings-pin" className="mb-1.5 block text-xs font-semibold text-ink">
                Mã PIN phụ huynh
              </label>
              <input
                id="play-settings-pin"
                data-testid="play-setting-pin-input"
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
              <div
                role="alert"
                data-testid="play-settings-error"
                className="rounded-lg border border-red-800/35 bg-red-50 px-3.5 py-2.5 text-xs text-red-900"
              >
                {error}
              </div>
            )}

            <div className="flex flex-col-reverse gap-2 pt-1 sm:flex-row sm:justify-end">
              <button
                type="button"
                disabled={busy}
                onClick={onClose}
                className="btn-secondary w-full sm:w-auto text-xs"
              >
                Hủy
              </button>
              <button
                type="submit"
                data-testid="play-setting-submit-button"
                disabled={busy}
                className="btn-primary w-full sm:w-auto text-xs disabled:opacity-50"
              >
                {busy ? 'Đang lưu…' : 'Lưu cài đặt'}
              </button>
            </div>
          </>
        )}
      </form>
    </SiteDialog>
  );
}
