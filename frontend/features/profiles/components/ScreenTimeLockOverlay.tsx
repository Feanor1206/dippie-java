import React, { useState } from 'react';
import { Calculator, CheckCircle2, Clock, RefreshCw } from 'lucide-react';
import {
  fetchMathChallenge,
  formatRemainingTime,
  submitMathChallengeAnswer,
  type MathChallenge,
  type ScreenTimeStatus,
} from '@/lib/services/screenTimeService';

type OverlayStep = 'locked' | 'quiz' | 'solved';

export function ScreenTimeLockOverlay({
  status,
  token,
  onStatusUpdated,
  onContinueWatching,
}: {
  status: ScreenTimeStatus;
  token: string;
  onStatusUpdated: (next: ScreenTimeStatus) => void;
  onContinueWatching: () => void;
}) {
  const [step, setStep] = useState<OverlayStep>('locked');
  const [challenge, setChallenge] = useState<MathChallenge | null>(null);
  const [answerInput, setAnswerInput] = useState('');
  const [loadingChallenge, setLoadingChallenge] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [feedback, setFeedback] = useState<{ kind: 'error' | 'success'; text: string } | null>(null);
  const [latestStatus, setLatestStatus] = useState<ScreenTimeStatus>(status);

  const effectiveStatus = latestStatus.remainingSeconds !== status.remainingSeconds ? latestStatus : status;
  const capReached = effectiveStatus.bonusCapReached || effectiveStatus.maxBonusMinutes <= 0;

  const loadChallenge = async () => {
    if (!token || loadingChallenge) return;
    setLoadingChallenge(true);
    setFeedback(null);
    setAnswerInput('');
    try {
      const next = await fetchMathChallenge(token);
      setChallenge(next);
      setStep('quiz');
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Chưa lấy được câu hỏi toán. Bé thử lại nhé.';
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

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!token || !challenge || submitting) return;
    const trimmed = answerInput.trim();
    if (!/^-?\d+$/.test(trimmed)) {
      setFeedback({ kind: 'error', text: 'Bé hãy nhập một số nhé.' });
      return;
    }
    const parsed = parseInt(trimmed, 10);
    setSubmitting(true);
    setFeedback(null);
    try {
      const result = await submitMathChallengeAnswer(token, challenge.token, parsed);
      setLatestStatus(result.status);
      onStatusUpdated(result.status);
      if (result.correct) {
        setFeedback({
          kind: 'success',
          text: result.message || `Đúng rồi! Bé được cộng thêm ${result.earnedMinutes} phút xem phim.`,
        });
        setStep('solved');
      } else {
        setAnswerInput('');
        setFeedback({
          kind: 'error',
          text: result.message || 'Chưa đúng rồi, bé thử tính lại nhé.',
        });
      }
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Chưa kiểm tra được đáp án. Bé thử lại nhé.';
      setFeedback({ kind: 'error', text: message });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="false"
      aria-labelledby="screen-time-lock-title"
      data-testid="screen-time-lock-overlay"
      className="absolute inset-0 z-30 flex items-center justify-center bg-ink/90 p-4 sm:p-6"
    >
      <div className="w-full max-w-md rounded-2xl border-2 border-ink bg-paper-white p-5 text-center shadow-brand-lg sm:p-6">
        <div className="mx-auto mb-3 grid h-12 w-12 place-items-center rounded-full border-2 border-ink bg-[#f2d18c] text-ink">
          {step === 'solved' ? (
            <CheckCircle2 size={24} aria-hidden="true" />
          ) : step === 'quiz' ? (
            <Calculator size={22} aria-hidden="true" />
          ) : (
            <Clock size={22} aria-hidden="true" />
          )}
        </div>

        <h3 id="screen-time-lock-title" className="font-display text-lg font-bold text-ink sm:text-xl">
          {step === 'solved' ? 'Giỏi lắm! Đã có thêm giờ xem' : 'Hết giờ xem hôm nay rồi'}
        </h3>

        {step === 'locked' && (
          <div className="mt-3 space-y-4">
            {capReached ? (
              <p
                data-testid="bonus-cap-reached-note"
                className="text-xs leading-relaxed text-muted sm:text-sm"
              >
                Bé đã nhận đủ số phút thưởng từ giải toán hôm nay rồi. Hãy nhờ ba mẹ cho thêm thời gian nếu muốn xem tiếp nhé.
              </p>
            ) : (
              <>
                <p className="text-xs leading-relaxed text-muted sm:text-sm">
                  Bé đã dùng hết thời gian xem hôm nay. Giải đúng một phép tính để nhận thêm{' '}
                  <strong className="text-ink">{effectiveStatus.rewardMinutes} phút</strong> nhé!
                </p>
                <button
                  type="button"
                  data-testid="open-math-challenge-button"
                  disabled={loadingChallenge}
                  onClick={() => void loadChallenge()}
                  className="btn-primary min-h-[44px] w-full justify-center text-xs font-bold sm:text-sm disabled:opacity-50"
                >
                  {loadingChallenge ? 'Đang lấy câu hỏi…' : 'Giải toán để thêm phút'}
                </button>
              </>
            )}

            {feedback && (
              <div
                role="alert"
                data-testid="math-feedback"
                className="rounded-lg border border-red-800/35 bg-red-50 px-3 py-2 text-xs text-red-900"
              >
                {feedback.text}
              </div>
            )}
          </div>
        )}

        {step === 'quiz' && challenge && (
          <form onSubmit={handleSubmit} className="mt-3 space-y-3 text-left">
            <div className="rounded-xl border-2 border-ink bg-mint/45 px-4 py-3 text-center">
              <p className="text-[11px] font-semibold text-muted">
                Trả lời đúng để nhận thêm {challenge.rewardMinutes} phút
              </p>
              <p
                data-testid="math-challenge-question"
                className="mt-1 font-display text-2xl font-bold tracking-wide text-ink"
              >
                {challenge.question}
              </p>
            </div>

            <div>
              <label htmlFor="math-challenge-answer" className="mb-1 block text-xs font-semibold text-ink">
                Nhập đáp án của bé
              </label>
              <input
                id="math-challenge-answer"
                data-testid="math-answer-input"
                type="text"
                inputMode="numeric"
                autoComplete="off"
                required
                disabled={submitting}
                value={answerInput}
                onChange={(event) => {
                  setAnswerInput(event.target.value.replace(/[^0-9]/g, '').slice(0, 5));
                  if (feedback?.kind === 'error') setFeedback(null);
                }}
                placeholder="Ví dụ: 12"
                className="w-full rounded-lg border-2 border-ink/40 bg-surface-muted px-3 py-2.5 text-center text-lg font-bold text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
              />
            </div>

            {feedback && (
              <div
                role={feedback.kind === 'error' ? 'alert' : 'status'}
                data-testid="math-feedback"
                className={`rounded-lg border px-3 py-2 text-xs font-medium ${
                  feedback.kind === 'error'
                    ? 'border-red-800/35 bg-red-50 text-red-900'
                    : 'border-ink/40 bg-mint/60 text-ink'
                }`}
              >
                {feedback.text}
              </div>
            )}

            <div className="flex flex-col gap-2 sm:flex-row">
              <button
                type="submit"
                data-testid="math-answer-submit"
                disabled={submitting || !answerInput.trim()}
                className="btn-primary min-h-[44px] flex-1 justify-center text-xs font-bold disabled:opacity-50"
              >
                {submitting ? 'Đang kiểm tra…' : 'Kiểm tra đáp án'}
              </button>
              <button
                type="button"
                data-testid="math-challenge-refresh"
                disabled={submitting || loadingChallenge}
                onClick={() => void loadChallenge()}
                className="btn-secondary min-h-[44px] justify-center gap-1 px-3 text-xs"
              >
                <RefreshCw size={13} aria-hidden="true" />
                Đổi câu
              </button>
            </div>
          </form>
        )}

        {step === 'solved' && (
          <div className="mt-3 space-y-4">
            {feedback && (
              <div
                role="status"
                data-testid="math-feedback"
                className="rounded-xl border border-ink/40 bg-mint/60 px-3.5 py-2.5 text-xs font-semibold text-ink sm:text-sm"
              >
                {feedback.text}
              </div>
            )}

            <p data-testid="math-new-remaining" className="text-sm font-bold text-brand-dark">
              Thời gian còn lại: {formatRemainingTime(effectiveStatus.remainingSeconds, effectiveStatus.locked)}
            </p>

            {!effectiveStatus.locked && (
              <button
                type="button"
                data-testid="math-continue-watching"
                onClick={onContinueWatching}
                className="btn-primary min-h-[44px] w-full justify-center text-xs font-bold sm:text-sm"
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
