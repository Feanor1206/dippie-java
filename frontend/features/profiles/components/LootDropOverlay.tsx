import React, { useState } from 'react';
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
  feedback,
  opening,
  submitting,
  onOpenChest,
  onSubmitAnswer,
}: {
  activeDrop: LootDropSpawn | null;
  dropSecondsLeft: number;
  activeQuiz: LootDropQuiz | null;
  quizSecondsLeft: number;
  feedback: LootFeedbackBanner | null;
  opening: boolean;
  submitting: boolean;
  onOpenChest: () => Promise<void>;
  onSubmitAnswer: (answer: number) => Promise<LootDropAnswerResult | null>;
}) {
  const [answerInput, setAnswerInput] = useState('');
  const [inputError, setInputError] = useState('');

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmed = answerInput.trim();
    if (!/^-?\d+$/.test(trimmed)) {
      setInputError('Nhập số nhé bé!');
      return;
    }
    setInputError('');
    setAnswerInput('');
    await onSubmitAnswer(parseInt(trimmed, 10));
  };

  return (
    <>
      {activeDrop && !activeQuiz && (
        <button
          type="button"
          data-testid="loot-chest-button"
          disabled={opening}
          onClick={() => void onOpenChest()}
          aria-label={`Rương báu bay ngang, bấm để giải toán nhanh nhận thêm phút xem, còn ${dropSecondsLeft} giây`}
          className="loot-float absolute bottom-14 left-3 z-20 flex min-h-[56px] items-center gap-2 rounded-2xl border-2 border-ink bg-[#fef3c7] px-3 py-1.5 text-left shadow-[3px_3px_0_#203328] hover:scale-105 cursor-pointer transition-transform"
        >
          <TreasureChestSvg />
          <div className="leading-tight">
            <span className="block text-xs font-extrabold text-ink">Rương báu!</span>
            <span className="block text-[10px] font-bold text-amber-900">
              Chạm mở · {dropSecondsLeft}s
            </span>
          </div>
        </button>
      )}

      {activeQuiz && (
        <div
          role="dialog"
          aria-modal="false"
          aria-labelledby="loot-quiz-title"
          data-testid="loot-quiz-card"
          className="absolute bottom-14 left-3 z-30 w-[min(92%,310px)] rounded-2xl border-2 border-ink bg-paper-white p-4 shadow-brand-lg"
        >
          <div className="flex items-center justify-between gap-2">
            <h4 id="loot-quiz-title" className="font-display text-sm font-bold text-ink">
              Mở rương +{activeQuiz.baseRewardMinutes + activeQuiz.potentialComboBonusMinutes} phút
            </h4>
            <span
              data-testid="loot-quiz-timer"
              className="rounded-full border border-ink bg-[#f2d18c] px-2 py-0.5 font-mono text-xs font-bold text-ink"
            >
              {quizSecondsLeft}s
            </span>
          </div>

          {activeQuiz.combo > 0 && (
            <p className="mt-1 text-[11px] font-bold text-brand-dark">
              Combo hiện tại: {activeQuiz.combo}
              {activeQuiz.potentialComboBonusMinutes > 0 ? ' · Đúng câu này thưởng thêm +1 phút!' : ''}
            </p>
          )}

          <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-surface-elevated">
            <div
              className="h-full bg-amber-500 transition-all duration-300"
              style={{ width: `${Math.max(0, Math.min(100, (quizSecondsLeft / activeQuiz.timerSeconds) * 100))}%` }}
            />
          </div>

          <p data-testid="loot-quiz-question" className="mt-2.5 text-center font-display text-xl font-bold text-ink">
            {activeQuiz.question}
          </p>

          <form onSubmit={handleSubmit} className="mt-2.5 flex items-center gap-2">
            <label htmlFor="loot-quiz-input" className="sr-only">
              Đáp án rương báu
            </label>
            <input
              id="loot-quiz-input"
              data-testid="loot-quiz-input"
              type="text"
              inputMode="numeric"
              autoComplete="off"
              autoFocus
              disabled={submitting}
              value={answerInput}
              onChange={(event) => {
                setAnswerInput(event.target.value.replace(/[^0-9]/g, '').slice(0, 5));
                if (inputError) setInputError('');
              }}
              placeholder="Đáp án"
              className="min-h-[44px] w-full rounded-lg border-2 border-ink/40 bg-surface-muted px-3 text-center text-base font-bold text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
            />
            <button
              type="submit"
              data-testid="loot-quiz-submit"
              disabled={submitting || !answerInput.trim()}
              className="btn-primary min-h-[44px] shrink-0 px-3 text-xs font-bold disabled:opacity-50"
            >
              Mở
            </button>
          </form>

          {inputError && (
            <p role="alert" className="mt-1.5 text-[11px] font-semibold text-red-900">
              {inputError}
            </p>
          )}
        </div>
      )}

      {feedback && !activeQuiz && (
        <div
          role="status"
          aria-live="polite"
          data-testid="loot-result-banner"
          className={`absolute bottom-14 left-3 z-20 max-w-[300px] rounded-xl border-2 border-ink px-3.5 py-2 text-xs font-bold shadow-[2px_2px_0_#203328] ${
            feedback.kind === 'success' ? 'bg-mint text-ink' : 'bg-paper-white text-ink'
          }`}
        >
          {feedback.text}
        </div>
      )}
    </>
  );
}
