import React, { useRef, useState } from 'react';
import { QuizQuestionCard } from '@/features/quiz/components/QuizQuestionCard';
import type { QuizOptionKey } from '@/features/quiz/quizService';
import { useBlockingOverlayFocus } from '@/features/profiles/hooks/useBlockingOverlayFocus';
import type { LootFeedbackBanner } from '@/features/profiles/hooks/usePlayFeatures';
import type { LootDropAnswerResult, LootDropQuiz, LootDropSpawn } from '@/lib/services/playFeaturesService';

function TreasureChestSvg() {
  return (
    <svg width={44} height={44} viewBox="0 0 64 64" aria-hidden="true">
      <rect x="8" y="24" width="48" height="28" rx="5" fill="#d97706" stroke="#203328" strokeWidth="3" />
      <path d="M8 28 C8 16 18 12 32 12 C46 12 56 16 56 28 Z" fill="#f59e0b" stroke="#203328" strokeWidth="3" />
      <rect x="8" y="27" width="48" height="6" fill="#fde68a" stroke="#203328" strokeWidth="2" />
      <rect x="27" y="24" width="10" height="14" rx="2" fill="#fef3c7" stroke="#203328" strokeWidth="2.5" />
      <circle cx="32" cy="31" r="2" fill="#203328" />
      <polygon points="14,8 16,12 20,12 17,14 18,18 14,16 10,18 11,14 8,12 12,12" fill="#fef08a" stroke="#203328" strokeWidth="1.2" />
      <polygon points="52,6 53.5,9 57,9 54,11 55,14 52,12 49,14 50,11 47,9 50.5,9" fill="#fef08a" stroke="#203328" strokeWidth="1.2" />
    </svg>
  );
}

export function LootDropOverlay({
  activeDrop,
  dropSecondsLeft,
  activeQuiz,
  quizSecondsLeft,
  selectedOption = null,
  retryError = null,
  feedback,
  opening,
  submitting,
  onOpenChest,
  onSubmitAnswer,
  onDismissQuiz,
  onSelectDraftOption,
  onExtendTimer,
}: {
  activeDrop: LootDropSpawn | null;
  dropSecondsLeft: number;
  activeQuiz: LootDropQuiz | null;
  quizSecondsLeft: number;
  selectedOption?: QuizOptionKey | null;
  retryError?: string | null;
  feedback: LootFeedbackBanner | null;
  opening: boolean;
  submitting: boolean;
  onOpenChest: () => Promise<void>;
  onSubmitAnswer: (option: QuizOptionKey) => Promise<LootDropAnswerResult | null>;
  onDismissQuiz?: () => void;
  onSelectDraftOption?: (option: QuizOptionKey) => void;
  onExtendTimer?: () => void;
}) {
  const quizDialogRef = useRef<HTMLDivElement | null>(null);
  const [localDraft, setLocalDraft] = useState<QuizOptionKey | null>(null);
  useBlockingOverlayFocus(quizDialogRef, Boolean(activeQuiz));

  const effectiveSelected = selectedOption ?? localDraft;

  return (
    <>
      {activeDrop && !activeQuiz && (
        <button
          type="button"
          data-testid="loot-chest-button"
          disabled={opening}
          onClick={() => void onOpenChest()}
          aria-label={`Rương câu hỏi bất ngờ, bấm để trả lời câu hỏi nhận thêm phút xem, còn ${dropSecondsLeft} giây`}
          className="loot-float motion-sheen-loop absolute bottom-14 left-3 z-20 flex min-h-[56px] items-center gap-2 rounded-2xl border-2 border-ink bg-[#fef3c7] px-3 py-1.5 text-left shadow-[3px_3px_0_#203328] hover:scale-105 cursor-pointer transition-transform"
        >
          <TreasureChestSvg />
          <div className="leading-tight">
            <span className="block text-xs font-extrabold text-ink">Rương báu!</span>
            <span className="block text-[0.6875rem] font-bold text-amber-900">
              Chạm mở · {dropSecondsLeft}s
            </span>
          </div>
        </button>
      )}

      {activeQuiz && (
        <div
          ref={quizDialogRef}
          tabIndex={-1}
          role="dialog"
          aria-modal="true"
          aria-labelledby="loot-quiz-title"
          data-testid="loot-quiz-card"
          className="relative z-30 flex min-h-[30rem] sm:min-h-[32rem] w-full flex-1 items-center justify-center bg-ink/85 p-2.5 sm:p-4 animate-fade-in"
        >
          <div className="motion-overlay-overshoot my-auto w-full max-w-lg rounded-2xl border-2 border-ink bg-paper-white p-3.5 shadow-brand-lg sm:p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h4 id="loot-quiz-title" className="font-display text-sm font-bold text-ink sm:text-base">
                Mở rương +{activeQuiz.baseRewardMinutes + activeQuiz.potentialComboBonusMinutes} phút
              </h4>
              <div className="flex items-center gap-2">
                <span
                  data-testid="loot-quiz-timer"
                  className="rounded-full border border-ink bg-[#f2d18c] px-2.5 py-0.5 font-mono text-xs font-bold text-ink"
                >
                  {retryError ? 'Tạm dừng' : `${quizSecondsLeft}s`}
                </span>
                {onDismissQuiz && (
                  <button
                    type="button"
                    data-testid="loot-dismiss-button"
                    disabled={submitting}
                    onClick={onDismissQuiz}
                    className="inline-flex min-h-[44px] items-center justify-center rounded-lg border border-ink/40 bg-surface-muted px-2.5 py-1 text-xs font-semibold text-ink hover:bg-surface-elevated cursor-pointer"
                  >
                    Đóng rương
                  </button>
                )}
              </div>
            </div>

            {activeQuiz.combo > 0 && (
              <p className="mt-1 text-[0.6875rem] font-bold text-brand-dark">
                Chuỗi trả lời đúng: {activeQuiz.combo}
                {activeQuiz.potentialComboBonusMinutes > 0 ? ' · Đúng câu này thưởng thêm +1 phút!' : ''}
              </p>
            )}

            <div className="mt-2 mb-2.5 h-1.5 w-full overflow-hidden rounded-full bg-surface-elevated">
              <div
                className="h-full bg-amber-500 transition-all duration-300"
                style={{ width: `${Math.max(0, Math.min(100, (quizSecondsLeft / activeQuiz.timerSeconds) * 100))}%` }}
              />
            </div>

            {retryError && (
              <div
                role="alert"
                data-testid="loot-retry-banner"
                className="mb-3 space-y-2.5 rounded-xl border-2 border-amber-700/60 bg-[#fef3c7] p-3 text-left text-xs text-ink"
              >
                <p className="font-semibold leading-relaxed">{retryError}</p>
                <div className="flex flex-wrap items-center gap-2">
                  {effectiveSelected && (
                    <button
                      type="button"
                      data-testid="loot-retry-submit-button"
                      disabled={submitting}
                      onClick={() => void onSubmitAnswer(effectiveSelected)}
                      className="btn-primary min-h-[44px] px-3.5 py-1.5 text-xs font-bold"
                    >
                      {submitting ? 'Đang gửi lại…' : `Gửi lại đáp án ${effectiveSelected}`}
                    </button>
                  )}
                  {onDismissQuiz && (
                    <button
                      type="button"
                      data-testid="loot-retry-dismiss-button"
                      disabled={submitting}
                      onClick={onDismissQuiz}
                      className="btn-secondary min-h-[44px] px-3.5 py-1.5 text-xs font-semibold"
                    >
                      Bỏ qua rương này · Xem tiếp
                    </button>
                  )}
                </div>
              </div>
            )}

            <QuizQuestionCard
              question={activeQuiz.question}
              options={activeQuiz.options}
              subject={activeQuiz.subject}
              subjectLabel={activeQuiz.subjectLabel}
              gradeLabel={activeQuiz.gradeLabel}
              setName={activeQuiz.setName}
              submitting={submitting}
              compact
              selectedOption={effectiveSelected}
              onDraftSelectOption={(opt) => {
                setLocalDraft(opt);
                onSelectDraftOption?.(opt);
              }}
              questionTestId="loot-quiz-question"
              feedbackTestId="loot-quiz-feedback"
              onReadAloud={onExtendTimer}
              onSelectOption={(option) => void onSubmitAnswer(option)}
            />
          </div>
        </div>
      )}

      {feedback && !activeQuiz && (
        <div
          role="status"
          aria-live="polite"
          data-testid="loot-result-banner"
          className={`${
            feedback.kind === 'success' ? 'motion-correct-pop bg-mint text-ink' : 'motion-toast-enter bg-[#fef3c7] text-ink'
          } absolute bottom-3 left-3 right-3 z-20 rounded-xl border-2 border-ink px-3.5 py-2.5 text-xs shadow-[2px_2px_0_#203328] sm:bottom-14 sm:right-auto sm:max-w-[360px]`}
        >
          <p className="font-bold">{feedback.text}</p>
          {feedback.explanation && (
            <p data-testid="loot-result-explanation" className="mt-1 text-[0.6875rem] leading-snug text-muted">
              {feedback.explanation}
            </p>
          )}
        </div>
      )}
    </>
  );
}
