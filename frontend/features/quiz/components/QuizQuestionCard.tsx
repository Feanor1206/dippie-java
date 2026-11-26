import React, { useState } from 'react';
import { Check, RefreshCw, Volume2, X } from 'lucide-react';
import {
  formatQuestionForDisplay,
  speakQuizOption,
  speakQuizQuestionAndOptions,
  type QuizOption,
  type QuizOptionKey,
} from '@/features/quiz/quizService';

const OPTION_STYLES: Record<
  QuizOptionKey,
  { button: string; selected: string; badge: string; shape: 'triangle' | 'diamond' | 'circle' | 'square' }
> = {
  A: {
    button: 'border-ink bg-[#dbeafe] text-ink hover:bg-[#bfdbfe]',
    selected: 'border-ink bg-[#bfdbfe] text-ink ring-2 ring-ink shadow-[3px_3px_0_#203328]',
    badge: 'bg-[#1d4ed8] text-white',
    shape: 'triangle',
  },
  B: {
    button: 'border-ink bg-[#fef3c7] text-ink hover:bg-[#fde68a]',
    selected: 'border-ink bg-[#fde68a] text-ink ring-2 ring-ink shadow-[3px_3px_0_#203328]',
    badge: 'bg-[#b45309] text-white',
    shape: 'diamond',
  },
  C: {
    button: 'border-ink bg-[#ede9fe] text-ink hover:bg-[#ddd6fe]',
    selected: 'border-ink bg-[#ddd6fe] text-ink ring-2 ring-ink shadow-[3px_3px_0_#203328]',
    badge: 'bg-[#6d28d9] text-white',
    shape: 'circle',
  },
  D: {
    button: 'border-ink bg-[#ffe4e6] text-ink hover:bg-[#fecdd3]',
    selected: 'border-ink bg-[#fecdd3] text-ink ring-2 ring-ink shadow-[3px_3px_0_#203328]',
    badge: 'bg-[#be123c] text-white',
    shape: 'square',
  },
};

const SPEECH_FALLBACK_NOTICE =
  'Thiết bị này chưa có giọng đọc tự động phù hợp, ba mẹ đọc giúp bé nhé.';

const DRAFT_STORE = new Map<string, QuizOptionKey | null>();
const SPEECH_NOTICE_STORE = new Map<string, string | null>();

function OptionShapeIcon({ shape }: { shape: 'triangle' | 'diamond' | 'circle' | 'square' }) {
  return (
    <svg width={12} height={12} viewBox="0 0 16 16" aria-hidden="true" className="shrink-0 fill-current">
      {shape === 'triangle' && <polygon points="8,2 14.5,13.5 1.5,13.5" />}
      {shape === 'diamond' && <polygon points="8,1.2 14.8,8 8,14.8 1.2,8" />}
      {shape === 'circle' && <circle cx="8" cy="8" r="5.8" />}
      {shape === 'square' && <rect x="2.4" y="2.4" width="11.2" height="11.2" rx="1.6" />}
    </svg>
  );
}

export type QuizWrongReveal = {
  chosenOption: QuizOptionKey;
  correctOption: QuizOptionKey | null;
  correctText: string | null;
  explanation: string | null;
  message: string;
};

export function QuizQuestionCard({
  question,
  options,
  subject,
  subjectLabel,
  gradeLabel,
  setName,
  subtitle,
  submitting,
  wrongReveal,
  errorMessage,
  compact = false,
  selectedOption,
  onDraftSelectOption,
  onReadAloud,
  questionTestId = 'quiz-question-text',
  feedbackTestId = 'quiz-feedback',
  onSelectOption,
  onNextQuestion,
  nextQuestionLabel = 'Thử câu khác',
}: {
  question: string;
  options: QuizOption[];
  subject?: string | null;
  subjectLabel?: string | null;
  gradeLabel?: string | null;
  setName?: string | null;
  subtitle?: string | null;
  submitting: boolean;
  wrongReveal?: QuizWrongReveal | null;
  errorMessage?: string | null;
  compact?: boolean;
  selectedOption?: QuizOptionKey | null;
  onDraftSelectOption?: (option: QuizOptionKey) => void;
  onReadAloud?: () => void;
  questionTestId?: string;
  feedbackTestId?: string;
  onSelectOption: (option: QuizOptionKey) => void;
  onNextQuestion?: () => void;
  nextQuestionLabel?: string;
}) {
  const lockedByReveal = Boolean(wrongReveal);
  const questionKey = `${question}::${options.map((o) => `${o.key}:${o.text}`).join('|')}`;
  const hasRealReactRuntime = typeof React.version === 'string' && typeof useState === 'function';
  const renderTickState = hasRealReactRuntime ? useState(0) : null;
  const bumpRender = renderTickState ? renderTickState[1] : null;

  const isControlled = selectedOption !== undefined || onDraftSelectOption !== undefined;
  const effectiveDraft = isControlled
    ? selectedOption ?? null
    : DRAFT_STORE.get(questionKey) ?? null;
  const speechNotice = SPEECH_NOTICE_STORE.get(questionKey) ?? null;
  const chosenOptionObj = effectiveDraft ? options.find((opt) => opt.key === effectiveDraft) ?? null : null;
  const displayQuestion =
    typeof formatQuestionForDisplay === 'function'
      ? formatQuestionForDisplay(question, subject, gradeLabel)
      : question;

  const handleReadAloud = () => {
    onReadAloud?.();
    const ok = speakQuizQuestionAndOptions(displayQuestion, options, subject);
    if (SPEECH_NOTICE_STORE.size > 64) SPEECH_NOTICE_STORE.clear();
    SPEECH_NOTICE_STORE.set(questionKey, ok ? null : SPEECH_FALLBACK_NOTICE);
    bumpRender?.((n) => n + 1);
  };

  const handleSpeakSingleOption = (opt: QuizOption) => {
    onReadAloud?.();
    const ok = speakQuizOption(opt.key, opt.text, subject);
    if (SPEECH_NOTICE_STORE.size > 64) SPEECH_NOTICE_STORE.clear();
    SPEECH_NOTICE_STORE.set(questionKey, ok ? null : SPEECH_FALLBACK_NOTICE);
    bumpRender?.((n) => n + 1);
  };

  const handleChooseDraft = (key: QuizOptionKey) => {
    if (submitting || lockedByReveal) return;
    if (!isControlled) {
      if (DRAFT_STORE.size > 64) DRAFT_STORE.clear();
      DRAFT_STORE.set(questionKey, key);
      bumpRender?.((n) => n + 1);
    }
    onDraftSelectOption?.(key);
  };

  const handleConfirmAnswer = () => {
    if (!effectiveDraft || submitting || lockedByReveal) return;
    if (!isControlled) {
      DRAFT_STORE.delete(questionKey);
      bumpRender?.((n) => n + 1);
    }
    onSelectOption(effectiveDraft);
  };

  return (
    <div data-testid="quiz-question-card" className={compact ? 'space-y-2.5 text-left' : 'space-y-3 text-left'}>
      <div className="rounded-xl border-2 border-ink bg-mint/40 px-3.5 py-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex flex-wrap items-center gap-1.5">
            {subjectLabel && (
              <span className="motion-chip-pop rounded-full border border-ink bg-paper-white px-2 py-0.5 text-[0.6875rem] font-bold text-ink">
                {subjectLabel}
              </span>
            )}
            {gradeLabel && (
              <span className="motion-chip-pop rounded-full border border-ink/40 bg-[#f2d18c]/70 px-2 py-0.5 text-[0.6875rem] font-bold text-ink">
                {gradeLabel}
              </span>
            )}
            {!compact && setName && (
              <span className="text-[0.6875rem] font-semibold text-muted">{setName}</span>
            )}
          </div>
          <button
            type="button"
            data-testid="quiz-read-aloud-button"
            onClick={handleReadAloud}
            aria-label={
              subject === 'TIENG_ANH'
                ? 'Nghe đọc câu hỏi và 4 đáp án tiếng Anh'
                : 'Nghe đọc câu hỏi và cả 4 đáp án'
            }
            className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center gap-1.5 rounded-lg border-2 border-ink bg-paper-white px-3 py-1.5 text-xs font-bold text-ink shadow-[2px_2px_0_#203328] hover:bg-[#fef3c7] cursor-pointer"
          >
            <Volume2 size={16} aria-hidden="true" />
            <span>{compact ? 'Nghe đọc hết' : 'Nghe đọc câu hỏi & 4 đáp án'}</span>
          </button>
        </div>

        {subtitle && (
          <p className="mt-1.5 text-[0.6875rem] font-semibold text-muted">{subtitle}</p>
        )}

        <p
          data-testid={questionTestId}
          className={
            compact
              ? 'mt-2 text-center font-display text-base font-bold leading-snug text-ink sm:text-lg'
              : 'mt-2 text-center font-display text-lg font-bold leading-snug text-ink sm:text-xl'
          }
        >
          {displayQuestion}
        </p>

        {speechNotice && (
          <p role="status" data-testid="quiz-speech-notice" className="mt-2 text-center text-xs font-medium text-muted">
            {speechNotice}
          </p>
        )}
      </div>

      <div
        data-testid="quiz-options-grid"
        className="grid grid-cols-1 gap-2 sm:grid-cols-2"
      >
        {options.map((opt) => {
          const baseStyle = OPTION_STYLES[opt.key];
          const isCorrectHighlight = lockedByReveal && wrongReveal?.correctOption === opt.key;
          const isChosenWrong =
            lockedByReveal && wrongReveal?.chosenOption === opt.key && wrongReveal?.correctOption !== opt.key;
          const isDraftSelected = !lockedByReveal && effectiveDraft === opt.key;

          const stateClass = isCorrectHighlight
            ? 'motion-correct-pop border-2 border-[#047857] bg-[#a7f3d0] text-ink ring-2 ring-[#047857]'
            : isChosenWrong
            ? 'border-2 border-red-800 bg-red-100 text-red-950 opacity-90'
            : isDraftSelected
            ? `${baseStyle.selected} border-2 -translate-y-0.5`
            : `${baseStyle.button} border-2 hover:-translate-y-0.5`;

          return (
            <div key={opt.key} className="flex items-stretch gap-1.5">
              <button
                type="button"
                data-testid={`quiz-option-${opt.key}`}
                data-option-shape={baseStyle.shape}
                aria-pressed={isDraftSelected}
                disabled={submitting || lockedByReveal}
                onClick={() => handleChooseDraft(opt.key)}
                className={`flex min-h-[56px] min-w-0 flex-1 items-center gap-2.5 rounded-xl px-3 py-2.5 text-left text-sm font-bold shadow-[2px_2px_0_#203328] transition-transform active:translate-y-0.5 cursor-pointer disabled:cursor-not-allowed ${
                  submitting && !lockedByReveal ? 'opacity-60' : ''
                } ${stateClass}`}
              >
                <span
                  className={`inline-flex h-9 min-w-[2.5rem] shrink-0 items-center justify-center gap-1 rounded-lg border border-ink px-1.5 text-xs font-extrabold ${
                    isCorrectHighlight
                      ? 'bg-[#047857] text-white'
                      : isChosenWrong
                      ? 'bg-red-800 text-white'
                      : baseStyle.badge
                  }`}
                >
                  {isCorrectHighlight ? (
                    <Check size={14} aria-hidden="true" data-testid={`quiz-option-correct-icon-${opt.key}`} />
                  ) : isChosenWrong ? (
                    <X size={14} aria-hidden="true" data-testid={`quiz-option-wrong-icon-${opt.key}`} />
                  ) : (
                    <OptionShapeIcon shape={baseStyle.shape} />
                  )}
                  <span>{opt.key}</span>
                </span>
                <span className="min-w-0 flex-1 break-words leading-snug">{opt.text}</span>
                {isDraftSelected && (
                  <span
                    data-testid="quiz-selected-pill"
                    className="shrink-0 rounded-full border border-ink bg-paper-white px-2 py-0.5 text-[0.6875rem] font-extrabold text-ink"
                  >
                    Đã chọn
                  </span>
                )}
              </button>

              <button
                type="button"
                data-testid={`quiz-speak-option-${opt.key}`}
                onClick={() => handleSpeakSingleOption(opt)}
                aria-label={`Nghe đáp án ${opt.key}: ${opt.text}`}
                className="inline-flex min-h-[56px] min-w-[44px] shrink-0 items-center justify-center rounded-xl border-2 border-ink bg-paper-white px-2.5 text-ink shadow-[2px_2px_0_#203328] hover:bg-[#fef3c7] cursor-pointer"
              >
                <Volume2 size={18} aria-hidden="true" />
              </button>
            </div>
          );
        })}
      </div>

      {!lockedByReveal && (
        <button
          type="button"
          data-testid="quiz-confirm-answer-button"
          disabled={!effectiveDraft || submitting}
          onClick={handleConfirmAnswer}
          className="btn-primary motion-sheen min-h-[56px] w-full justify-center px-4 text-sm font-bold disabled:opacity-50"
        >
          {submitting
            ? 'Đang kiểm tra đáp án…'
            : chosenOptionObj
            ? `Trả lời đáp án ${chosenOptionObj.key}: ${chosenOptionObj.text}`
            : 'Chạm chọn 1 đáp án rồi bấm trả lời nhé'}
        </button>
      )}

      {wrongReveal && (
        <div
          role="status"
          aria-live="polite"
          data-testid={feedbackTestId}
          className="motion-toast-enter rounded-xl border-2 border-ink bg-[#fef3c7] px-3.5 py-2.5 text-xs text-ink"
        >
          <p className="font-bold text-ink">{wrongReveal.message}</p>
          {wrongReveal.explanation && (
            <p data-testid="quiz-explanation" className="mt-1 text-xs leading-relaxed text-muted">
              {wrongReveal.explanation}
            </p>
          )}
        </div>
      )}

      {!wrongReveal && errorMessage && (
        <div
          role="alert"
          data-testid={feedbackTestId}
          className="rounded-lg border border-red-800/35 bg-red-50 px-3 py-2 text-xs font-medium text-red-900"
        >
          {errorMessage}
        </div>
      )}

      {onNextQuestion && (
        <div className="flex justify-end">
          <button
            type="button"
            data-testid="quiz-next-question-button"
            disabled={submitting}
            onClick={onNextQuestion}
            className={`${wrongReveal ? 'btn-primary w-full justify-center' : 'btn-secondary'} min-h-[44px] gap-1.5 px-3 text-xs font-bold`}
          >
            <RefreshCw size={14} aria-hidden="true" />
            <span>{wrongReveal ? nextQuestionLabel : 'Đổi câu khác'}</span>
          </button>
        </div>
      )}
    </div>
  );
}
