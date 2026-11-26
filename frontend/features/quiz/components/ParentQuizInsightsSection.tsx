import React, { useEffect, useState } from 'react';
import { ArrowDownRight, ArrowUpRight } from 'lucide-react';
import { SiteDialog } from '@/components/ui/SiteDialog';
import { ChildAvatarBadge } from '@/features/profiles/components/ChildAvatarBadge';
import { validateParentPin } from '@/lib/services/profileService';
import {
  confirmChildGradeChange,
  type ChildQuizConfig,
  type GradeSuggestion,
} from '@/features/quiz/quizService';

type ConfirmGradeDialog = {
  child: ChildQuizConfig;
  suggestion: GradeSuggestion;
} | null;

export function ParentQuizInsightsSection({
  token,
  configs,
  loading,
  loadError,
  activeProfileId = null,
  hideChildTabs = false,
  onSelectProfile,
  sessionPin = '',
  onPinVerified,
  onPinRejected,
  onGradeChanged,
  onRetry,
}: {
  token: string;
  configs: ChildQuizConfig[];
  loading: boolean;
  loadError: string;
  activeProfileId?: number | null;
  hideChildTabs?: boolean;
  onSelectProfile?: (profileId: number) => void;
  sessionPin?: string;
  onPinVerified?: (pin: string) => void;
  onPinRejected?: () => void;
  onGradeChanged: (updated: ChildQuizConfig) => void;
  onRetry: () => Promise<void>;
}) {
  const [selectedProfileId, setSelectedProfileId] = useState<number | null>(activeProfileId);
  const [dialog, setDialog] = useState<ConfirmGradeDialog>(null);
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState('');
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  useEffect(() => {
    if (activeProfileId) {
      setSelectedProfileId(activeProfileId);
    }
  }, [activeProfileId]);

  const activeChild = configs.find((item) => item.profileId === selectedProfileId) ?? configs[0] ?? null;

  const openConfirmDialog = (child: ChildQuizConfig, suggestion: GradeSuggestion) => {
    setFeedback(null);
    setDialogError('');
    setPin(sessionPin);
    setDialog({ child, suggestion });
  };

  const closeDialog = () => {
    if (busy) return;
    setDialog(null);
    setPin('');
    setDialogError('');
  };

  const handleConfirmGrade = async (event: React.FormEvent<HTMLFormElement>) => {
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
      const updated = await confirmChildGradeChange(
        token,
        dialog.child.profileId,
        pin,
        dialog.suggestion.suggestedGrade
      );
      onPinVerified?.(pin);
      onGradeChanged(updated);
      const nextLabel = updated.gradeLabel;
      const childName = dialog.child.name;
      closeDialog();
      setFeedback({
        type: 'success',
        text: `Đã đổi khối lớp cho ${childName} sang ${nextLabel}.`,
      });
    } catch (err: unknown) {
      onPinRejected?.();
      setPin('');
      setDialogError(err instanceof Error ? err.message : 'Chưa đổi được khối lớp cho bé.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <section
      id="parent-quiz-insights-section"
      aria-labelledby="parent-quiz-insights-heading"
      data-testid="parent-quiz-insights-section"
      className="mt-6 scroll-mt-20 rounded-xl border border-surface-border bg-paper-white p-5 sm:p-6"
    >
      <div className="border-b border-surface-border pb-4">
        <h3 id="parent-quiz-insights-heading" className="text-base font-bold text-ink">
          Báo cáo trả lời 7 ngày qua
        </h3>
        <p className="mt-1 text-base leading-relaxed text-muted">
          Theo dõi số câu bé đã làm, tỷ lệ đúng theo từng bộ câu hỏi và những câu bé hay nhầm để ba mẹ cùng ôn. Nếu bé làm rất tốt hoặc hơi khó, hệ thống sẽ gợi ý đổi lớp và luôn đợi ba mẹ xác nhận bằng mã PIN.
        </p>
      </div>

      {feedback && (
        <div
          role={feedback.type === 'error' ? 'alert' : 'status'}
          data-testid="parent-quiz-insights-feedback"
          className={`mt-4 rounded-xl border p-3 text-xs ${
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
          className="mt-4 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-red-800/35 bg-red-50 px-3.5 py-3 text-xs text-red-900"
        >
          <span>{loadError}</span>
          <button type="button" onClick={() => void onRetry()} className="btn-secondary min-h-[44px] px-3 text-xs font-bold">
            Thử lại
          </button>
        </div>
      ) : loading && configs.length === 0 ? (
        <p role="status" className="mt-4 rounded-xl border border-surface-border bg-surface-muted p-4 text-center text-xs text-muted">
          Đang tải báo cáo trả lời tuần này…
        </p>
      ) : activeChild && (
        <div className="mt-4 space-y-4">
          {!hideChildTabs && (
            <div className="flex flex-wrap gap-2" role="tablist" aria-label="Chọn bé để xem báo cáo tuần">
              {configs.map((item) => {
                const selected = item.profileId === activeChild.profileId;
                return (
                  <button
                    key={item.profileId}
                    type="button"
                    role="tab"
                    aria-selected={selected}
                    data-testid={`insights-child-tab-${item.profileId}`}
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
                      {item.gradeLabel}
                    </span>
                  </button>
                );
              })}
            </div>
          )}

          {activeChild.weeklySummary.suggestion && (
            <div
              data-testid={`grade-suggestion-banner-${activeChild.profileId}`}
              className="flex flex-col gap-3 rounded-xl border-2 border-ink bg-[#fef3c7] p-4 sm:flex-row sm:items-center sm:justify-between"
            >
              <div className="flex items-start gap-2.5">
                <div className="grid h-9 w-9 shrink-0 place-items-center rounded-lg border-2 border-ink bg-paper-white text-ink">
                  {activeChild.weeklySummary.suggestion.direction === 'UP' ? (
                    <ArrowUpRight size={18} aria-hidden="true" />
                  ) : (
                    <ArrowDownRight size={18} aria-hidden="true" />
                  )}
                </div>
                <div className="text-xs leading-relaxed text-ink">
                  <p className="font-bold">Gợi ý điều chỉnh lớp học</p>
                  <p className="mt-0.5">{activeChild.weeklySummary.suggestion.message}</p>
                </div>
              </div>
              <button
                type="button"
                data-testid={`confirm-grade-suggestion-button-${activeChild.profileId}`}
                onClick={() => openConfirmDialog(activeChild, activeChild.weeklySummary.suggestion!)}
                className="btn-primary min-h-[44px] shrink-0 text-xs font-bold"
              >
                {activeChild.weeklySummary.suggestion.direction === 'UP'
                  ? `Nâng lên ${activeChild.weeklySummary.suggestion.suggestedGradeLabel} (PIN)`
                  : `Hạ xuống ${activeChild.weeklySummary.suggestion.suggestedGradeLabel} (PIN)`}
              </button>
            </div>
          )}

          <div className="grid grid-cols-3 gap-2.5 text-center">
            <div className="rounded-xl border border-surface-border bg-surface p-3">
              <p className="text-[0.6875rem] text-muted">Câu đã trả lời (7 ngày)</p>
              <p data-testid="insights-total-answered" className="mt-1 text-lg font-bold text-ink">
                {activeChild.weeklySummary.totalAnswered}
              </p>
            </div>
            <div className="rounded-xl border border-surface-border bg-surface p-3">
              <p className="text-[0.6875rem] text-muted">Tỷ lệ đúng</p>
              <p data-testid="insights-accuracy-percent" className="mt-1 text-lg font-bold text-brand-dark">
                {activeChild.weeklySummary.totalAnswered > 0 ? `${activeChild.weeklySummary.accuracyPercent}%` : '—'}
              </p>
            </div>
            <div className="rounded-xl border border-surface-border bg-surface p-3">
              <p className="text-[0.6875rem] text-muted">Trung bình mỗi câu</p>
              <p className="mt-1 text-lg font-bold text-ink">
                {activeChild.weeklySummary.totalAnswered > 0
                  ? `${Math.max(1, Math.round(activeChild.weeklySummary.averageResponseMs / 1000))} giây`
                  : '—'}
              </p>
            </div>
          </div>

          {activeChild.weeklySummary.totalAnswered === 0 ? (
            <p className="rounded-xl border border-dashed border-surface-border bg-surface-muted p-4 text-center text-xs text-muted">
              Tuần này {activeChild.name} chưa trả lời câu hỏi nào. Khi bé trả lời câu hỏi thưởng phút, thuốc Dieppie, rương báu hoặc phim cần trả lời câu hỏi, kết quả sẽ tự tổng hợp tại đây.
            </p>
          ) : (
            <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
              <div className="rounded-xl border border-surface-border bg-surface p-4 space-y-2.5">
                <h4 className="text-xs font-bold text-ink">Theo từng bộ câu hỏi</h4>
                <ul className="space-y-2">
                  {activeChild.weeklySummary.bySet.map((stat, idx) => (
                    <li key={`${stat.setId ?? stat.setName}-${idx}`} className="space-y-1 text-xs">
                      <div className="flex items-center justify-between gap-2">
                        <span className="truncate font-semibold text-ink">{stat.setName}</span>
                        <span className="shrink-0 text-[0.6875rem] font-bold text-muted">
                          {stat.correct}/{stat.answered} đúng ({stat.accuracyPercent}%)
                        </span>
                      </div>
                      <div className="h-2 w-full overflow-hidden rounded-full border border-ink/25 bg-paper-white">
                        <div className="motion-progress-fill h-full bg-brand" style={{ width: `${stat.accuracyPercent}%` }} />
                      </div>
                    </li>
                  ))}
                </ul>
              </div>

              <div className="rounded-xl border border-surface-border bg-surface p-4 space-y-2.5">
                <h4 className="text-xs font-bold text-ink">Các câu bé hay nhầm nhất</h4>
                {activeChild.weeklySummary.hardestQuestions.length === 0 ? (
                  <p className="text-xs text-muted">Tuyệt quá! Tuần này bé chưa làm sai câu nào.</p>
                ) : (
                  <ul className="space-y-2 text-xs">
                    {activeChild.weeklySummary.hardestQuestions.map((hard, idx) => (
                      <li key={idx} className="rounded-lg border border-surface-border bg-paper-white p-2.5">
                        <p className="font-semibold text-ink">{hard.questionText}</p>
                        <p className="mt-1 text-[0.6875rem] text-muted">
                          {hard.setName} · {hard.subjectLabel} · Sai {hard.wrongCount}/{hard.attempts} lần ({hard.accuracyPercent}% đúng)
                        </p>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {dialog && (
        <SiteDialog
          title={`Xác nhận đổi lớp · ${dialog.child.name}`}
          onClose={closeDialog}
          busy={busy}
        >
          <form onSubmit={handleConfirmGrade} data-testid="confirm-grade-change-form" className="space-y-4">
            <p className="text-xs leading-relaxed text-muted">
              Chuyển khối lớp câu hỏi của <strong>{dialog.child.name}</strong> từ{' '}
              <strong>{dialog.suggestion.currentGradeLabel}</strong> sang{' '}
              <strong className="text-ink">{dialog.suggestion.suggestedGradeLabel}</strong>?
            </p>
            <div>
              <label htmlFor="grade-change-pin" className="mb-1.5 block text-xs font-semibold text-ink">
                Mã PIN phụ huynh
              </label>
              <input
                id="grade-change-pin"
                data-testid="grade-change-pin-input"
                type="password"
                inputMode="numeric"
                pattern="[0-9]*"
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
              <button
                type="submit"
                data-testid="grade-change-pin-submit"
                disabled={busy}
                className="btn-primary w-full sm:w-auto text-xs font-bold"
              >
                {busy ? 'Đang lưu…' : `Chuyển sang ${dialog.suggestion.suggestedGradeLabel}`}
              </button>
            </div>
          </form>
        </SiteDialog>
      )}
    </section>
  );
}
