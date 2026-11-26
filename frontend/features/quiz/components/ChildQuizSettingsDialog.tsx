import React, { useCallback, useEffect, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { SiteDialog } from '@/components/ui/SiteDialog';
import { validateParentPin, type ChildProfile } from '@/lib/services/profileService';
import {
  QUIZ_GRADES,
  fetchQuizSampleQuestions,
  getQuizGradeLabel,
  updateChildQuizSettings,
  type ChildQuizConfig,
  type QuizGradeCode,
  type QuizSampleQuestion,
  type QuizSet,
} from '@/features/quiz/quizService';

export function ChildQuizSettingsDialog({
  token,
  profile,
  currentConfig,
  availableSets,
  sessionPin = '',
  onPinVerified,
  onPinRejected,
  onSaved,
  onClose,
}: {
  token: string;
  profile: ChildProfile;
  currentConfig?: ChildQuizConfig | null;
  availableSets: QuizSet[];
  sessionPin?: string;
  onPinVerified?: (pin: string) => void;
  onPinRejected?: () => void;
  onSaved: (updated: ChildQuizConfig) => void;
  onClose: () => void;
}) {
  const activeAvailableSets = availableSets.filter((set) => set.active);
  const initialGrade: QuizGradeCode =
    currentConfig?.grade ?? (profile.quizGrade as QuizGradeCode | undefined) ?? 'LOP_1';
  const initialSetIds: number[] =
    currentConfig && currentConfig.activeSetIds.length > 0
      ? currentConfig.activeSetIds
      : activeAvailableSets.filter((set) => set.builtIn).map((set) => set.id);

  const [grade, setGrade] = useState<QuizGradeCode>(initialGrade);
  const [selectedSetIds, setSelectedSetIds] = useState<number[]>(initialSetIds);
  const [samples, setSamples] = useState<QuizSampleQuestion[]>([]);
  const [loadingSamples, setLoadingSamples] = useState(false);
  const [pin, setPin] = useState(sessionPin);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const loadSamples = useCallback(
    async (targetGrade: QuizGradeCode, targetSetIds: number[]) => {
      if (!token || targetSetIds.length === 0) {
        setSamples([]);
        return;
      }
      setLoadingSamples(true);
      try {
        const list = await fetchQuizSampleQuestions(token, targetGrade, targetSetIds);
        setSamples(list);
      } catch {
        setSamples([]);
      } finally {
        setLoadingSamples(false);
      }
    },
    [token]
  );

  useEffect(() => {
    void loadSamples(grade, selectedSetIds);
  }, [grade, selectedSetIds.join(','), loadSamples]);

  const toggleSet = (setId: number) => {
    setSelectedSetIds((prev) =>
      prev.includes(setId) ? prev.filter((id) => id !== setId) : [...prev, setId]
    );
    if (error) setError('');
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (busy) return;
    if (selectedSetIds.length === 0) {
      setError('Hãy chọn ít nhất 1 bộ câu hỏi cho bé.');
      return;
    }
    const pinErr = validateParentPin(pin);
    if (pinErr) {
      setError(pinErr);
      return;
    }
    setBusy(true);
    setError('');
    try {
      const updated = await updateChildQuizSettings(token, profile.id, {
        pin,
        grade,
        activeSetIds: selectedSetIds,
      });
      onPinVerified?.(pin);
      onSaved(updated);
      onClose();
    } catch (err: unknown) {
      onPinRejected?.();
      setPin('');
      setError(err instanceof Error ? err.message : 'Chưa lưu được cài đặt câu hỏi cho bé.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <SiteDialog title={`Bộ câu hỏi · ${profile.name}`} size="lg" onClose={onClose} busy={busy}>
      <form onSubmit={handleSubmit} data-testid="child-quiz-settings-form" className="space-y-4">
        <p className="text-base leading-relaxed text-muted">
          Chọn khối lớp và bộ câu hỏi phù hợp để {profile.name} trả lời khi nhận thêm phút xem phim.
        </p>
        <div>
          <label htmlFor="child-quiz-grade-select" className="mb-1.5 block text-xs font-semibold text-ink">
            Khối lớp của bé
          </label>
          <select
            id="child-quiz-grade-select"
            data-testid="child-quiz-grade-select"
            disabled={busy}
            value={grade}
            onChange={(event) => setGrade(event.target.value as QuizGradeCode)}
            className="min-h-[44px] w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2 text-sm font-semibold text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
          >
            {QUIZ_GRADES.map((item) => (
              <option key={item.code} value={item.code}>
                {item.label} — {item.description}
              </option>
            ))}
          </select>
        </div>

        <fieldset>
          <legend className="mb-2 text-xs font-semibold text-ink">
            Các bộ câu hỏi đang bật cho {profile.name}
          </legend>
          <div className="space-y-2">
            {activeAvailableSets.map((set) => {
              const checked = selectedSetIds.includes(set.id);
              return (
                <label
                  key={set.id}
                  className={`flex min-h-[44px] items-start justify-between gap-2 rounded-lg border px-3 py-2 text-xs cursor-pointer ${
                    checked
                      ? 'border-ink bg-mint/55 text-ink'
                      : 'border-surface-border bg-surface text-muted hover:text-ink'
                  }`}
                >
                  <span className="flex items-start gap-2.5 min-w-0">
                    <input
                      type="checkbox"
                      data-testid={`child-quiz-set-checkbox-${set.id}`}
                      checked={checked}
                      disabled={busy}
                      onChange={() => toggleSet(set.id)}
                      className="mt-0.5 h-4 w-4 shrink-0 accent-brand"
                    />
                    <span className="min-w-0">
                      <span className="block font-bold text-ink">{set.name}</span>
                      <span className="block text-[0.6875rem] text-muted">
                        {set.dynamicGenerator
                          ? 'Tự động sinh câu theo lớp'
                          : `${set.questionCount} câu · ${set.builtIn ? 'Có sẵn' : 'Bộ của gia đình'}`}
                      </span>
                    </span>
                  </span>
                </label>
              );
            })}
          </div>
        </fieldset>

        {activeAvailableSets.some(
          (set) =>
            selectedSetIds.includes(set.id) &&
            !set.builtIn &&
            !set.dynamicGenerator &&
            Boolean(set.defaultGrade) &&
            set.defaultGrade !== grade
        ) && (
          <div
            role="status"
            data-testid="child-quiz-grade-mismatch-warning"
            className="rounded-xl border border-amber-700/45 bg-[#fef3c7]/75 px-3.5 py-2.5 text-xs leading-relaxed text-ink"
          >
            Lưu ý: Một số bộ câu hỏi riêng đang bật khác với khối lớp <strong>{getQuizGradeLabel(grade)}</strong> của {profile.name}. Dippie sẽ ưu tiên lấy các câu khớp {getQuizGradeLabel(grade)} trước, và tự bù bằng bộ câu hỏi có sẵn nếu bộ riêng chưa có câu cùng lớp.
          </div>
        )}

        <div
          data-testid="child-quiz-sample-preview"
          className="rounded-xl border border-ink/30 bg-surface-muted p-3 space-y-2"
        >
          <div className="flex items-center justify-between gap-2">
            <p className="text-xs font-bold text-ink">Xem thử câu hỏi bé sẽ gặp</p>
            <button
              type="button"
              data-testid="refresh-quiz-samples-button"
              disabled={loadingSamples || selectedSetIds.length === 0}
              onClick={() => void loadSamples(grade, selectedSetIds)}
              className="inline-flex min-h-[44px] items-center gap-1 rounded-md border border-ink/40 bg-paper-white px-3 text-xs font-bold text-ink hover:bg-mint/50 cursor-pointer disabled:opacity-50"
            >
              <RefreshCw size={12} aria-hidden="true" />
              Đổi câu mẫu
            </button>
          </div>
          {loadingSamples ? (
            <p className="text-[0.6875rem] text-muted">Đang lấy câu hỏi mẫu…</p>
          ) : samples.length === 0 ? (
            <p className="text-[0.6875rem] text-muted">Chọn ít nhất 1 bộ câu hỏi để xem thử câu mẫu.</p>
          ) : (
            <ul className="space-y-2">
              {samples.slice(0, 3).map((sample, index) => (
                <li
                  key={`${sample.question}-${index}`}
                  data-testid={`quiz-sample-item-${index}`}
                  className="rounded-lg border border-surface-border bg-paper-white p-2.5 text-xs"
                >
                  <div className="flex flex-wrap items-center gap-1.5 text-[0.625rem] font-bold text-muted">
                    <span className="rounded bg-mint/70 px-1.5 py-0.5 text-ink">{sample.subjectLabel}</span>
                    <span>{sample.gradeLabel}</span>
                    <span>· {sample.setName}</span>
                  </div>
                  <p className="mt-1 font-bold text-ink">{sample.question}</p>
                  <div className="mt-1 grid grid-cols-2 gap-1 text-[0.6875rem]">
                    {sample.options.map((opt) => (
                      <span
                        key={opt.key}
                        className={`rounded px-1.5 py-0.5 ${
                          opt.key === sample.correctOption
                            ? 'bg-mint/70 font-bold text-ink'
                            : 'text-muted'
                        }`}
                      >
                        {opt.key}. {opt.text}
                      </span>
                    ))}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div>
          <label htmlFor="child-quiz-settings-pin" className="mb-1.5 block text-xs font-semibold text-ink">
            Mã PIN phụ huynh
          </label>
          <input
            id="child-quiz-settings-pin"
            data-testid="child-quiz-settings-pin-input"
            type="password"
            inputMode="numeric"
            pattern="[0-9]*"
            enterKeyHint="done"
            autoComplete="off"
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

        {error && (
          <div role="alert" data-testid="child-quiz-settings-error" className="rounded-lg border border-red-800/35 bg-red-50 px-3.5 py-2.5 text-xs text-red-900">
            {error}
          </div>
        )}

        <div className="dialog-sticky-footer flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button type="button" disabled={busy} onClick={onClose} className="btn-secondary w-full sm:w-auto text-xs">
            Hủy
          </button>
          <button
            type="submit"
            data-testid="child-quiz-settings-submit"
            disabled={busy}
            className="btn-primary w-full sm:w-auto text-xs font-bold disabled:opacity-50"
          >
            {busy ? 'Đang lưu…' : 'Lưu bộ câu hỏi'}
          </button>
        </div>
      </form>
    </SiteDialog>
  );
}
