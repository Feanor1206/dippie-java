import React, { useEffect, useRef, useState } from 'react';
import { CheckCircle2, Clock, HelpCircle, Home, KeyRound } from 'lucide-react';
import { QuizQuestionCard, type QuizWrongReveal } from '@/features/quiz/components/QuizQuestionCard';
import type { QuizOptionKey } from '@/features/quiz/quizService';
import { useBlockingOverlayFocus } from '@/features/profiles/hooks/useBlockingOverlayFocus';
import {
  fetchScreenTimeChallenge,
  formatRemainingTime,
  grantExtraMinutesInKidMode,
  submitScreenTimeChallengeAnswer,
  type ScreenTimeChallenge,
  type ScreenTimeStatus,
} from '@/lib/services/screenTimeService';

type OverlayStep = 'locked' | 'quiz' | 'solved';
const PARENT_GRANT_PRESETS = [15, 30, 45] as const;

export function ScreenTimeLockOverlay({
  status,
  token,
  savedProgressLabel = null,
  onStatusUpdated,
  onContinueWatching,
  onFinishSession,
}: {
  status: ScreenTimeStatus;
  token: string;
  savedProgressLabel?: string | null;
  onStatusUpdated: (next: ScreenTimeStatus) => void;
  onContinueWatching: () => void;
  onFinishSession?: () => void;
}) {
  const overlayRef = useRef<HTMLDivElement | null>(null);
  useBlockingOverlayFocus(overlayRef, true);

  const [step, setStep] = useState<OverlayStep>('locked');
  const [challenge, setChallenge] = useState<ScreenTimeChallenge | null>(null);
  const [selectedOption, setSelectedOption] = useState<QuizOptionKey | null>(null);
  const [loadingChallenge, setLoadingChallenge] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [wrongReveal, setWrongReveal] = useState<QuizWrongReveal | null>(null);
  const [feedback, setFeedback] = useState<{ kind: 'error' | 'success'; text: string } | null>(null);
  const [latestStatus, setLatestStatus] = useState<ScreenTimeStatus>(status);
  const [showParentGrant, setShowParentGrant] = useState(false);
  const [grantMinutes, setGrantMinutes] = useState<number>(15);
  const [parentPin, setParentPin] = useState('');
  const [grantingByPin, setGrantingByPin] = useState(false);

  useEffect(() => {
    setLatestStatus(status);
  }, [status]);

  const effectiveStatus = latestStatus;
  const capReached = effectiveStatus.bonusCapReached || effectiveStatus.maxBonusMinutes <= 0;

  const loadChallenge = async () => {
    if (!token || loadingChallenge) return;
    setLoadingChallenge(true);
    setFeedback(null);
    setWrongReveal(null);
    setSelectedOption(null);
    try {
      const next = await fetchScreenTimeChallenge(token);
      setChallenge(next);
      setStep('quiz');
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Chưa lấy được câu hỏi. Bé thử lại nhé.';
      setFeedback({ kind: 'error', text: message });
      if ((err as { status?: number })?.status === 409) {
        const capped: ScreenTimeStatus = {
          ...effectiveStatus,
          bonusRemainingSeconds: 0,
          bonusCapReached: true,
        };
        setLatestStatus(capped);
        onStatusUpdated(capped);
        setStep('locked');
      }
    } finally {
      setLoadingChallenge(false);
    }
  };

  const handleSelectOption = async (option: QuizOptionKey) => {
    if (!token || !challenge || submitting || wrongReveal) return;
    setSubmitting(true);
    setFeedback(null);
    try {
      const result = await submitScreenTimeChallengeAnswer(token, challenge.token, option);
      setLatestStatus(result.status);
      onStatusUpdated(result.status);
      if (result.correct) {
        setWrongReveal(null);
        setFeedback({
          kind: 'success',
          text: result.message || `Đúng rồi! Bé được cộng thêm ${result.earnedMinutes} phút xem phim.`,
        });
        setStep('solved');
      } else {
        setWrongReveal({
          chosenOption: option,
          correctOption: result.correctOption,
          correctText: result.correctText,
          explanation: result.explanation,
          message:
            result.message ||
            (result.correctOption && result.correctText
              ? `Gần đúng rồi! Đáp án là ${result.correctOption}: ${result.correctText} nè`
              : 'Gần đúng rồi! Bé thử câu tiếp theo nhé.'),
        });
      }
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Chưa kiểm tra được đáp án. Bé thử lại nhé.';
      setFeedback({ kind: 'error', text: message });
    } finally {
      setSubmitting(false);
    }
  };

  const handleParentGrantSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const cleanPin = parentPin.trim();
    if (!/^\d{4}$/.test(cleanPin) || grantingByPin) {
      setFeedback({ kind: 'error', text: 'Ba mẹ nhập đúng mã PIN 4 chữ số nhé.' });
      return;
    }
    setGrantingByPin(true);
    setFeedback(null);
    try {
      const result = await grantExtraMinutesInKidMode(token, cleanPin, grantMinutes);
      setLatestStatus(result.status);
      onStatusUpdated(result.status);
      setParentPin('');
      setShowParentGrant(false);
      setFeedback({ kind: 'success', text: result.message });
      setStep('solved');
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Mã PIN chưa đúng hoặc chưa cộng thêm giờ được.';
      setFeedback({ kind: 'error', text: message });
    } finally {
      setGrantingByPin(false);
    }
  };

  return (
    <div
      ref={overlayRef}
      tabIndex={-1}
      role="dialog"
      aria-modal="true"
      aria-labelledby="screen-time-lock-title"
      data-testid="screen-time-lock-overlay"
      className="absolute inset-0 z-30 flex items-start justify-center overflow-y-auto overscroll-contain bg-ink/90 p-3 sm:items-center sm:p-6 animate-fade-in"
    >
      <div className="motion-overlay-overshoot my-auto w-full max-w-lg rounded-2xl border-2 border-ink bg-paper-white p-4 text-center shadow-brand-lg sm:p-6">
        <div
          className={`motion-icon-drop mx-auto mb-2.5 grid h-12 w-12 place-items-center rounded-full border-2 border-ink bg-[#f2d18c] text-ink ${
            step === 'solved' ? 'motion-burst-badge' : ''
          }`}
        >
          {step === 'solved' ? (
            <CheckCircle2 size={24} aria-hidden="true" />
          ) : step === 'quiz' ? (
            <HelpCircle size={22} aria-hidden="true" />
          ) : (
            <Clock size={22} aria-hidden="true" />
          )}
        </div>

        <h3 id="screen-time-lock-title" className="font-display text-lg font-bold text-ink sm:text-xl">
          {step === 'solved' ? 'Giỏi lắm! Đã có thêm giờ xem' : 'Hết giờ xem hôm nay rồi'}
        </h3>

        {step === 'locked' && (
          <div className="mt-3 space-y-3.5 text-left">
            <div
              data-testid="time-up-reassurance"
              className="rounded-xl border border-ink/35 bg-mint/45 px-3.5 py-2.5 text-xs leading-relaxed text-ink sm:text-sm"
            >
              <p data-testid="time-up-saved-progress" className="font-bold text-ink">
                {savedProgressLabel
                  ? `Dippie đã lưu chỗ bé đang xem dở (${savedProgressLabel}) rồi.`
                  : 'Dippie đã lưu lại tập phim bé đang xem dở rồi.'}
              </p>
              <p data-testid="time-up-next-available" className="mt-0.5 text-muted">
                Ngày mai giờ xem mới sẽ mở lại để bé xem tiếp nhé!
              </p>
            </div>

            <a
              href="/"
              data-autofocus=""
              data-testid="time-up-finish-today-button"
              onClick={(event) => {
                if (onFinishSession) {
                  event.preventDefault();
                  onFinishSession();
                }
              }}
              className="btn-primary motion-sheen min-h-[50px] w-full justify-center gap-2 text-sm font-bold"
            >
              <Home size={17} aria-hidden="true" />
              <span>Xong hôm nay · Về trang chủ</span>
            </a>

            <div className="space-y-2 border-t border-surface-border pt-3">
              {capReached ? (
                <p
                  data-testid="bonus-cap-reached-note"
                  className="text-center text-xs leading-relaxed text-muted sm:text-sm"
                >
                  Bé đã nhận đủ số phút thưởng từ câu hỏi hôm nay rồi. Bé hãy nhờ ba mẹ nhập mã PIN bên dưới nếu muốn xem thêm nhé.
                </p>
              ) : (
                <button
                  type="button"
                  data-testid="open-quiz-challenge-button"
                  disabled={loadingChallenge}
                  onClick={() => void loadChallenge()}
                  className="btn-secondary min-h-[44px] w-full justify-center text-xs font-semibold sm:text-sm disabled:opacity-50"
                >
                  {loadingChallenge
                    ? 'Đang lấy câu hỏi…'
                    : `Trả lời câu hỏi để thêm phút (+${effectiveStatus.rewardMinutes} phút)`}
                </button>
              )}

              <button
                type="button"
                data-testid="time-up-parent-grant-button"
                aria-expanded={showParentGrant}
                onClick={() => {
                  setShowParentGrant((prev) => !prev);
                  setFeedback(null);
                }}
                className="inline-flex min-h-[44px] w-full items-center justify-center gap-1.5 rounded-xl border border-ink/30 bg-surface-muted px-3 py-2 text-xs font-semibold text-ink hover:border-ink hover:bg-paper-white cursor-pointer"
              >
                <KeyRound size={14} aria-hidden="true" />
                <span>Ba mẹ cho thêm giờ (nhập mã PIN)</span>
              </button>

              {showParentGrant && (
                <form
                  onSubmit={handleParentGrantSubmit}
                  data-testid="time-up-parent-grant-form"
                  className="motion-accordion-open space-y-2.5 rounded-xl border-2 border-ink bg-[#fef3c7]/60 p-3"
                >
                  <p className="text-xs font-bold text-ink">Dành cho ba mẹ: chọn số phút cộng thêm hôm nay</p>
                  <div className="flex flex-wrap gap-2">
                    {PARENT_GRANT_PRESETS.map((mins) => (
                      <button
                        key={mins}
                        type="button"
                        data-testid={`time-up-grant-preset-${mins}`}
                        aria-pressed={grantMinutes === mins}
                        onClick={() => setGrantMinutes(mins)}
                        className={`min-h-[44px] flex-1 rounded-lg border px-2.5 py-1.5 text-xs font-bold cursor-pointer ${
                          grantMinutes === mins
                            ? 'border-ink bg-ink text-paper-white'
                            : 'border-ink/40 bg-paper-white text-ink'
                        }`}
                      >
                        +{mins} phút
                      </button>
                    ))}
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <label htmlFor="time-up-parent-pin" className="sr-only">
                      Mã PIN phụ huynh 4 chữ số
                    </label>
                    <input
                      id="time-up-parent-pin"
                      data-testid="time-up-parent-pin-input"
                      type="password"
                      inputMode="numeric"
                      pattern="[0-9]*"
                      maxLength={4}
                      autoComplete="off"
                      placeholder="Mã PIN 4 số"
                      value={parentPin}
                      onChange={(event) => setParentPin(event.target.value.replace(/\D/g, '').slice(0, 4))}
                      className="min-h-[44px] min-w-0 flex-1 rounded-lg border-2 border-ink bg-paper-white px-3 py-2 text-center font-mono text-sm font-bold tracking-widest text-ink"
                    />
                    <button
                      type="submit"
                      data-testid="time-up-parent-grant-submit"
                      disabled={grantingByPin}
                      className="btn-primary min-h-[44px] shrink-0 px-4 py-2 text-xs font-bold"
                    >
                      {grantingByPin ? 'Đang cộng…' : `Cho +${grantMinutes} phút`}
                    </button>
                  </div>
                </form>
              )}
            </div>

            {feedback && (
              <div
                role="alert"
                data-testid="quiz-feedback"
                className="rounded-lg border border-red-800/35 bg-red-50 px-3 py-2 text-xs text-red-900"
              >
                {feedback.text}
              </div>
            )}
          </div>
        )}

        {step === 'quiz' && challenge && (
          <div className="mt-3 space-y-3">
            <QuizQuestionCard
              question={challenge.question}
              options={challenge.options}
              subject={challenge.subject}
              subjectLabel={challenge.subjectLabel}
              gradeLabel={challenge.gradeLabel}
              setName={challenge.setName}
              subtitle={`Trả lời đúng để nhận thêm ${challenge.rewardMinutes} phút`}
              submitting={submitting || loadingChallenge}
              wrongReveal={wrongReveal}
              selectedOption={selectedOption}
              onDraftSelectOption={setSelectedOption}
              errorMessage={feedback?.kind === 'error' ? feedback.text : null}
              questionTestId="quiz-challenge-question"
              feedbackTestId="quiz-feedback"
              onSelectOption={(option) => void handleSelectOption(option)}
              onNextQuestion={() => void loadChallenge()}
              nextQuestionLabel="Thử câu khác"
            />
            <div className="flex justify-start">
              <button
                type="button"
                data-testid="time-up-back-button"
                disabled={submitting}
                onClick={() => {
                  setStep('locked');
                  setWrongReveal(null);
                  setFeedback(null);
                }}
                className="btn-secondary min-h-[44px] px-3 py-1.5 text-xs font-semibold"
              >
                Quay lại
              </button>
            </div>
          </div>
        )}

        {step === 'solved' && (
          <div className="mt-3 space-y-4">
            {feedback && (
              <div
                role="status"
                data-testid="quiz-feedback"
                className="motion-correct-pop rounded-xl border border-ink/40 bg-mint/60 px-3.5 py-2.5 text-xs font-semibold text-ink sm:text-sm"
              >
                {feedback.text}
              </div>
            )}

            <p data-testid="quiz-new-remaining" className="text-sm font-bold text-brand-dark">
              Thời gian còn lại: {formatRemainingTime(effectiveStatus.remainingSeconds, effectiveStatus.locked)}
            </p>

            {!effectiveStatus.locked && (
              <button
                type="button"
                data-testid="quiz-continue-watching"
                onClick={onContinueWatching}
                className="btn-primary motion-sheen min-h-[48px] w-full justify-center text-xs font-bold sm:text-sm"
              >
                Tiếp tục xem
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
