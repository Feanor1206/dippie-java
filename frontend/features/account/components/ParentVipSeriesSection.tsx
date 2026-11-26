import React, { useCallback, useEffect, useState } from 'react';
import { SiteDialog } from '@/components/ui/SiteDialog';
import { ChildAvatarBadge } from '@/features/profiles/components/ChildAvatarBadge';
import { validateParentPin, type ChildProfile } from '@/lib/services/profileService';
import { listSeriesCards, type SeriesCard } from '@/lib/services/seriesCardService';
import {
  fetchParentVipSeriesOverview,
  updateChildVipSeries,
  type ChildProfileVipSeries,
} from '@/lib/services/vipService';

export function ParentVipSeriesSection({
  token,
  childProfiles,
  activeProfileId = null,
  hideChildTabs = false,
  onSelectProfile,
  sessionPin = '',
  onPinVerified,
  onPinRejected,
}: {
  token: string;
  childProfiles: ChildProfile[];
  activeProfileId?: number | null;
  hideChildTabs?: boolean;
  onSelectProfile?: (profileId: number) => void;
  sessionPin?: string;
  onPinVerified?: (pin: string) => void;
  onPinRejected?: () => void;
}) {
  const [overview, setOverview] = useState<ChildProfileVipSeries[]>([]);
  const [seriesList, setSeriesList] = useState<SeriesCard[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [selectedProfileId, setSelectedProfileId] = useState<number | null>(activeProfileId);
  const [draftByProfileId, setDraftByProfileId] = useState<Record<number, string[]>>({});
  const [pinDialogOpen, setPinDialogOpen] = useState(false);
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState('');
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  useEffect(() => {
    if (activeProfileId) {
      setSelectedProfileId(activeProfileId);
    }
  }, [activeProfileId]);

  const loadData = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setLoadError('');
    try {
      const [vipItems, cards] = await Promise.all([
        fetchParentVipSeriesOverview(token),
        listSeriesCards().catch(() => [] as SeriesCard[]),
      ]);
      setOverview(vipItems);
      setSeriesList(cards);
      setDraftByProfileId((prev) => {
        const next: Record<number, string[]> = { ...prev };
        for (const item of vipItems) {
          if (next[item.profileId] === undefined) {
            next[item.profileId] = [...item.seriesIds];
          }
        }
        return next;
      });
      setSelectedProfileId((prev) => {
        if (activeProfileId && vipItems.some((item: ChildProfileVipSeries) => item.profileId === activeProfileId)) {
          return activeProfileId;
        }
        if (prev && vipItems.some((item: ChildProfileVipSeries) => item.profileId === prev)) return prev;
        return vipItems[0]?.profileId ?? null;
      });
    } catch (err: unknown) {
      setLoadError(err instanceof Error ? err.message : 'Chưa tải được danh sách phim cần trả lời câu hỏi.');
    } finally {
      setLoading(false);
    }
  }, [token, activeProfileId]);

  const profileGradesKey = childProfiles.map((item) => `${item.id}:${item.quizGrade || ''}`).join(',');

  useEffect(() => {
    void loadData();
  }, [loadData, profileGradesKey]);

  const activeItem = overview.find((item) => item.profileId === selectedProfileId) ?? overview[0] ?? null;
  const draftSeriesIds = activeItem ? draftByProfileId[activeItem.profileId] ?? activeItem.seriesIds : [];

  const toggleSeries = (seriesId: string) => {
    if (!activeItem) return;
    const targetId = activeItem.profileId;
    setDraftByProfileId((prev) => {
      const current = prev[targetId] ?? activeItem.seriesIds;
      const nextIds = current.includes(seriesId)
        ? current.filter((id) => id !== seriesId)
        : [...current, seriesId];
      return { ...prev, [targetId]: nextIds };
    });
  };

  const resetDialog = () => {
    setPinDialogOpen(false);
    setPin('');
    setDialogError('');
  };

  const closeDialog = () => {
    if (busy) return;
    resetDialog();
  };

  const handleSaveSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!activeItem || busy) return;
    const pinErr = validateParentPin(pin);
    if (pinErr) {
      setDialogError(pinErr);
      return;
    }
    setBusy(true);
    setDialogError('');
    try {
      const updated = await updateChildVipSeries(token, activeItem.profileId, pin, draftSeriesIds);
      onPinVerified?.(pin);
      setOverview((prev) => prev.map((item) => (item.profileId === updated.profileId ? updated : item)));
      setDraftByProfileId((prev) => ({ ...prev, [updated.profileId]: [...updated.seriesIds] }));
      const childName = updated.name;
      resetDialog();
      setFeedback({ type: 'success', text: `Đã lưu danh sách phim cần trả lời câu hỏi cho ${childName}.` });
    } catch (err: unknown) {
      onPinRejected?.();
      setPin('');
      setDialogError(err instanceof Error ? err.message : 'Chưa lưu được danh sách phim cần trả lời câu hỏi.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <section
      id="parent-vip-series-section"
      aria-labelledby="parent-vip-heading"
      data-testid="parent-vip-series-section"
      className="mt-6 scroll-mt-20 rounded-xl border border-surface-border bg-paper-white p-5 sm:p-6"
    >
      <div className="border-b border-surface-border pb-4">
        <h3 id="parent-vip-heading" className="text-base font-bold text-ink">
          Phim cần trả lời câu hỏi
        </h3>
        <p className="mt-1 text-base leading-relaxed text-muted">
          Chọn các bộ phim cần trả lời câu hỏi cho từng bé. Để mở khóa mỗi tập trong ngày, bé cần trả lời đúng 1 câu hỏi khó hơn 1 lớp so với lớp hiện tại (hoặc lớp cao nhất hiện có).
        </p>
      </div>

      {feedback && (
        <div
          role={feedback.type === 'error' ? 'alert' : 'status'}
          data-testid="parent-vip-feedback"
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
          data-testid="parent-vip-load-error"
          className="mt-4 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-red-800/35 bg-red-50 px-3.5 py-3 text-xs text-red-900"
        >
          <span>{loadError}</span>
          <button
            type="button"
            onClick={() => void loadData()}
            className="btn-secondary min-h-[44px] px-3 text-xs font-bold"
          >
            Thử lại
          </button>
        </div>
      ) : loading && overview.length === 0 ? (
        <p role="status" className="mt-4 rounded-xl border border-surface-border bg-surface-muted p-4 text-center text-xs text-muted">
          Đang tải danh sách phim cần trả lời câu hỏi…
        </p>
      ) : overview.length > 0 && activeItem && (
        <div className="mt-4 space-y-4">
          {!hideChildTabs && (
            <div className="flex flex-wrap gap-2" role="tablist" aria-label="Chọn hồ sơ bé">
              {overview.map((item) => {
                const selected = item.profileId === activeItem.profileId;
                const itemDraftIds = draftByProfileId[item.profileId] ?? item.seriesIds;
                return (
                  <button
                    key={item.profileId}
                    type="button"
                    role="tab"
                    aria-selected={selected}
                    data-testid={`vip-child-tab-${item.profileId}`}
                    onClick={() => {
                      setSelectedProfileId(item.profileId);
                      onSelectProfile?.(item.profileId);
                      setFeedback(null);
                    }}
                    className={`inline-flex min-h-[44px] items-center gap-2 rounded-xl border-2 px-3 py-1.5 text-xs font-bold cursor-pointer ${
                      selected
                        ? 'border-ink bg-mint/70 text-ink shadow-[2px_2px_0_#203328]'
                        : 'border-surface-border bg-surface text-muted hover:border-ink/50'
                    }`}
                  >
                    <ChildAvatarBadge avatarKey={item.avatarKey} size="sm" />
                    <span>{item.name}</span>
                    <span className="rounded bg-paper-white px-1.5 py-0.5 text-[0.625rem] text-ink">
                      {itemDraftIds.length} phim
                    </span>
                  </button>
                );
              })}
            </div>
          )}

          <div className="rounded-lg border border-surface-border bg-surface-muted px-3.5 py-2.5 text-sm text-ink">
            Khối lớp hiện tại của <strong>{activeItem.name}</strong>: <strong>{activeItem.quizGradeLabel}</strong> · Câu hỏi mở khóa phim: <strong className="text-brand-dark">{activeItem.vipQuizGradeLabel}</strong>
          </div>

          {seriesList.length === 0 ? (
            <p className="rounded-xl border border-dashed border-surface-border bg-surface-muted p-4 text-center text-sm text-muted">
              Chưa có phim bộ nào trong kho để chọn làm phim cần trả lời câu hỏi.
            </p>
          ) : (
            <div className="grid grid-cols-1 gap-2 lg:grid-cols-2">
              {seriesList.map((series) => {
                const checked = draftSeriesIds.includes(series.id);
                return (
                  <label
                    key={series.id}
                    className={`flex min-h-[44px] items-center justify-between gap-2 rounded-lg border px-3 py-2 text-xs font-semibold cursor-pointer ${
                      checked
                        ? 'border-ink bg-[#f2d18c]/45 text-ink'
                        : 'border-surface-border bg-surface text-muted hover:text-ink'
                    }`}
                  >
                    <span className="flex items-center gap-2 min-w-0">
                      <input
                        type="checkbox"
                        data-testid={`vip-series-checkbox-${series.id}`}
                        checked={checked}
                        onChange={() => toggleSeries(series.id)}
                        className="h-4 w-4 shrink-0 accent-brand"
                      />
                      <span className="truncate">{series.title}</span>
                    </span>
                    {checked && (
                      <span className="motion-chip-pop shrink-0 rounded-full border border-ink bg-[#f2d18c] px-2 py-0.5 text-[0.625rem] font-extrabold text-ink">
                        Phim cần trả lời câu hỏi
                      </span>
                    )}
                  </label>
                );
              })}
            </div>
          )}

          <div className="flex justify-end">
            <button
              type="button"
              data-testid="save-vip-series-button"
              onClick={() => {
                setFeedback(null);
                setDialogError('');
                setPin(sessionPin);
                setPinDialogOpen(true);
              }}
              className="btn-primary min-h-[44px] text-xs font-bold"
            >
              Lưu phim cần trả lời câu hỏi cho {activeItem.name}
            </button>
          </div>
        </div>
      )}

      {pinDialogOpen && activeItem && (
        <SiteDialog
          title={`Lưu phim cần trả lời câu hỏi · ${activeItem.name}`}
          onClose={closeDialog}
          busy={busy}
        >
          <form onSubmit={handleSaveSubmit} className="space-y-4">
            <p className="text-xs leading-relaxed text-muted">
              Xác nhận lưu <strong>{draftSeriesIds.length}</strong> phim cần trả lời câu hỏi cho <strong>{activeItem.name}</strong>?
            </p>
            <div>
              <label htmlFor="vip-dialog-pin" className="mb-1.5 block text-xs font-semibold text-ink">
                Mã PIN phụ huynh
              </label>
              <input
                id="vip-dialog-pin"
                data-testid="vip-pin-input"
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
                {busy ? 'Đang lưu…' : 'Lưu danh sách phim'}
              </button>
            </div>
          </form>
        </SiteDialog>
      )}
    </section>
  );
}
