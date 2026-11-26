import React, { useRef, useState } from 'react';
import { Home, KeyRound } from 'lucide-react';
import { QuizQuestionCard, type QuizWrongReveal } from '@/features/quiz/components/QuizQuestionCard';
import type { QuizOptionKey } from '@/features/quiz/quizService';
import { useBlockingOverlayFocus } from '@/features/profiles/hooks/useBlockingOverlayFocus';
import {
  fetchWatchPetMedicineChallenge,
  formatCountdownSeconds,
  submitWatchPetMedicineAnswer,
  type WatchPetMedicineChallenge,
  type WatchPetStage,
  type WatchPetStatus,
} from '@/lib/services/playFeaturesService';
import {
  grantExtraMinutesInKidMode,
  type ScreenTimeStatus,
} from '@/lib/services/screenTimeService';

const STAGE_LABEL: Record<WatchPetStage, string> = {
  HAPPY: 'Mắt khỏe cùng Dieppie',
  SWEATY: 'Sắp tới giờ nghỉ mắt',
  DIZZY: 'Mắt cần nghỉ ngơi chút nha',
  FAINTED: 'Đến giờ nghỉ mắt',
  RESTING: 'Nghỉ cùng Dieppie',
};

const STAGE_SHORT_BADGE: Record<WatchPetStage, string> = {
  HAPPY: 'Mắt khỏe',
  SWEATY: 'Sắp nghỉ mắt',
  DIZZY: 'Chuẩn bị nghỉ mắt',
  FAINTED: 'Đến giờ nghỉ mắt',
  RESTING: 'Đang nghỉ mắt',
};

const STAGE_ANIMATION: Record<WatchPetStage, string> = {
  HAPPY: 'watch-pet-bob',
  SWEATY: 'watch-pet-wobble',
  DIZZY: 'watch-pet-sway',
  FAINTED: 'watch-pet-faint',
  RESTING: 'watch-pet-bob',
};

export function WatchPetArt({ stage, size = 56 }: { stage: WatchPetStage; size?: number }) {
  const bodyFill =
    stage === 'RESTING'
      ? '#d9e6f7'
      : stage === 'FAINTED'
      ? '#e6f0dc'
      : stage === 'DIZZY'
      ? '#fde68a'
      : stage === 'SWEATY'
      ? '#f2d18c'
      : '#c9ddbd';

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 80 80"
      role="img"
      aria-label={STAGE_LABEL[stage]}
      className={STAGE_ANIMATION[stage]}
    >
      <g>
        <path
          d="M20 26 L26 11 L35 22 Z M60 26 L54 11 L45 22 Z"
          fill="#8dad86"
          stroke="#203328"
          strokeWidth="2.5"
          strokeLinejoin="round"
        />
        <ellipse cx="40" cy="45" rx="25" ry="23" fill={bodyFill} stroke="#203328" strokeWidth="2.5" />
        <ellipse cx="40" cy="52" rx="14" ry="11" fill="#fffdf6" />
        <circle cx="24" cy="48" r="4" fill="#fca5a5" opacity={0.68} />
        <circle cx="56" cy="48" r="4" fill="#fca5a5" opacity={0.68} />

        {stage === 'HAPPY' && (
          <>
            <circle cx="31" cy="39" r="3.6" fill="#203328" />
            <circle cx="32.2" cy="37.8" r="1.2" fill="#fffdf6" />
            <circle cx="49" cy="39" r="3.6" fill="#203328" />
            <circle cx="50.2" cy="37.8" r="1.2" fill="#fffdf6" />
            <path d="M34 49 Q40 55 46 49" fill="none" stroke="#203328" strokeWidth="2.5" strokeLinecap="round" />
          </>
        )}

        {stage === 'SWEATY' && (
          <>
            <circle cx="31" cy="40" r="3.2" fill="#203328" />
            <circle cx="49" cy="40" r="3.2" fill="#203328" />
            <path d="M27 34 L35 35 M53 34 L45 35" stroke="#203328" strokeWidth="2" strokeLinecap="round" />
            <path d="M35 50 Q40 53 45 50" fill="none" stroke="#203328" strokeWidth="2.2" strokeLinecap="round" />
          </>
        )}

        {stage === 'DIZZY' && (
          <>
            <path d="M26 39 Q31 36 36 39" fill="none" stroke="#203328" strokeWidth="2.4" strokeLinecap="round" />
            <path d="M44 39 Q49 36 54 39" fill="none" stroke="#203328" strokeWidth="2.4" strokeLinecap="round" />
            <path d="M35 50 Q40 53 45 50" fill="none" stroke="#203328" strokeWidth="2.2" strokeLinecap="round" />
          </>
        )}

        {stage === 'FAINTED' && (
          <>
            <path d="M26 40 Q31 44 36 40" fill="none" stroke="#203328" strokeWidth="2.4" strokeLinecap="round" />
            <path d="M44 40 Q49 44 54 40" fill="none" stroke="#203328" strokeWidth="2.4" strokeLinecap="round" />
            <path d="M35 49 Q40 54 45 49" fill="none" stroke="#203328" strokeWidth="2.4" strokeLinecap="round" />
            <path
              d="M60 21 C64 16 69 18 67 24 C65 28 60 27 60 21 Z"
              fill="#8dad86"
              stroke="#203328"
              strokeWidth="1.8"
            />
          </>
        )}

        {stage === 'RESTING' && (
          <>
            <path d="M26 40 Q31 44 36 40" fill="none" stroke="#203328" strokeWidth="2.4" strokeLinecap="round" />
            <path d="M44 40 Q49 44 54 40" fill="none" stroke="#203328" strokeWidth="2.4" strokeLinecap="round" />
            <path d="M36 50 Q40 53 44 50" fill="none" stroke="#203328" strokeWidth="2.2" strokeLinecap="round" />
            <text x="58" y="24" fontSize="11" fontWeight="800" fill="#203328" className="watch-pet-zzz">
              Z
            </text>
            <text x="66" y="15" fontSize="9" fontWeight="800" fill="#36563c" className="watch-pet-zzz">
              z
            </text>
          </>
        )}
      </g>
    </svg>
  );
}

export function WatchPetWidget({
  watchPet,
  token,
  restCountdown,
  savedProgressLabel = null,
  onStartRest,
  onWatchPetUpdated,
  onScreenTimeUpdated,
  onFinishSession,
  hideCorner = false,
}: {
  watchPet: WatchPetStatus | null;
  token: string | null;
  restCountdown: number;
  savedProgressLabel?: string | null;
  onStartRest: () => Promise<WatchPetStatus | null>;
  onWatchPetUpdated: (next: WatchPetStatus) => void;
  onScreenTimeUpdated?: (next: ScreenTimeStatus) => void;
  onFinishSession?: () => void;
  hideCorner?: boolean;
}) {
  const overlayRef = useRef<HTMLDivElement | null>(null);
  const [medicineChallenge, setMedicineChallenge] = useState<WatchPetMedicineChallenge | null>(null);
  const [selectedOption, setSelectedOption] = useState<QuizOptionKey | null>(null);
  const [wrongReveal, setWrongReveal] = useState<QuizWrongReveal | null>(null);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ kind: 'error' | 'success'; text: string } | null>(null);
  const [showParentGrant, setShowParentGrant] = useState(false);
  const [parentPin, setParentPin] = useState('');
  const [grantMinutes, setGrantMinutes] = useState(15);

  const showOverlay = Boolean(watchPet?.enabled && (watchPet.fainted || watchPet.resting));
  useBlockingOverlayFocus(overlayRef, showOverlay);

  if (!watchPet || !watchPet.enabled) {
    return null;
  }

  const comfortDots =
    watchPet.stage === 'HAPPY'
      ? 3
      : watchPet.stage === 'SWEATY'
      ? 2
      : watchPet.stage === 'DIZZY'
      ? 1
      : 0;

  const handleRestClick = async () => {
    if (busy) return;
    setBusy(true);
    setFeedback(null);
    setWrongReveal(null);
    setMedicineChallenge(null);
    try {
      await onStartRest();
    } catch (err: unknown) {
      setFeedback({
        kind: 'error',
        text: err instanceof Error ? err.message : 'Chưa bắt đầu giờ nghỉ mắt cùng Dieppie được.',
      });
    } finally {
      setBusy(false);
    }
  };

  const handleLoadMedicine = async () => {
    if (!token || busy) return;
    setBusy(true);
    setFeedback(null);
    setWrongReveal(null);
    setSelectedOption(null);
    try {
      const challenge = await fetchWatchPetMedicineChallenge(token);
      setMedicineChallenge(challenge);
    } catch (err: unknown) {
      setFeedback({
        kind: 'error',
        text: err instanceof Error ? err.message : 'Chưa lấy được câu hỏi để xem tiếp.',
      });
    } finally {
      setBusy(false);
    }
  };

  const handleSelectOption = async (option: QuizOptionKey) => {
    if (!token || !medicineChallenge || busy || wrongReveal) return;
    setBusy(true);
    setFeedback(null);
    try {
      const result = await submitWatchPetMedicineAnswer(token, medicineChallenge.token, option);
      onWatchPetUpdated(result.watchPet);
      if (result.correct) {
        setMedicineChallenge(null);
        setWrongReveal(null);
        setFeedback(null);
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
      setFeedback({
        kind: 'error',
        text: err instanceof Error ? err.message : 'Chưa kiểm tra được đáp án.',
      });
    } finally {
      setBusy(false);
    }
  };

  const handleParentGrantSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!token || busy) return;
    const cleanPin = parentPin.trim();
    if (!/^\d{4}$/.test(cleanPin)) {
      setFeedback({ kind: 'error', text: 'Ba mẹ nhập đúng mã PIN 4 chữ số nhé.' });
      return;
    }
    setBusy(true);
    setFeedback(null);
    try {
      const res = await grantExtraMinutesInKidMode(token, cleanPin, grantMinutes);
      onScreenTimeUpdated?.(res.status);
      setParentPin('');
      setShowParentGrant(false);
      setFeedback({ kind: 'success', text: res.message });
    } catch (err: unknown) {
      setFeedback({
        kind: 'error',
        text: err instanceof Error ? err.message : 'Chưa cộng thêm giờ xem được.',
      });
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      {!hideCorner && (
        <div
          data-testid="watch-pet-corner"
          data-pet-stage={watchPet.stage}
          aria-label={STAGE_LABEL[watchPet.stage]}
          className="motion-chip-pop pointer-events-none absolute top-3 right-3 z-20 flex items-center gap-1.5 rounded-full border-2 border-ink bg-paper-white/95 px-2.5 py-1 shadow-[2px_2px_0_#203328]"
        >
          <WatchPetArt stage={watchPet.stage} size={32} />
          <div className="pr-1 text-left leading-tight">
            <p className="text-[0.6875rem] font-bold text-ink">{STAGE_SHORT_BADGE[watchPet.stage]}</p>
            <div className="mt-0.5 flex items-center gap-1" aria-hidden="true">
              {[0, 1, 2].map((idx) => (
                <span
                  key={idx}
                  className={`h-1.5 w-3 rounded-full border border-ink/50 ${
                    idx < comfortDots ? 'bg-sage' : 'bg-surface-elevated'
                  }`}
                />
              ))}
            </div>
          </div>
        </div>
      )}

      {showOverlay && (
        <div
          ref={overlayRef}
          tabIndex={-1}
          role="dialog"
          aria-modal="true"
          aria-labelledby="watch-pet-dialog-title"
          data-testid="watch-pet-overlay"
          className="absolute inset-0 z-30 flex items-start justify-center overflow-y-auto overscroll-contain bg-ink/85 p-3 sm:items-center sm:p-6 animate-fade-in"
        >
          <div className="motion-overlay-overshoot my-auto w-full max-w-lg rounded-2xl border-2 border-ink bg-paper-white p-4 text-center shadow-brand-lg sm:p-6">
            <div className="mx-auto mb-2 flex justify-center">
              <WatchPetArt stage={watchPet.resting ? 'RESTING' : 'FAINTED'} size={68} />
            </div>

            {watchPet.resting ? (
              <div className="space-y-3">
                <h3 id="watch-pet-dialog-title" className="font-display text-lg font-bold text-ink sm:text-xl">
                  Nghỉ cùng Dieppie nhé
                </h3>
                <p className="text-xs leading-relaxed text-muted sm:text-sm">
                  Bé nhìn ra xa, chớp mắt nhẹ nhàng và uống một ngụm nước. Hết đồng hồ bên dưới là bé xem tiếp được ngay!
                </p>
                <div
                  data-testid="watch-pet-rest-countdown"
                  className="mx-auto inline-flex items-center justify-center rounded-xl border-2 border-ink bg-mint px-5 py-2.5 font-mono text-2xl font-bold text-ink shadow-[2px_2px_0_#203328]"
                >
                  {formatCountdownSeconds(restCountdown)}
                </div>

                <div
                  data-testid="watch-pet-saved-progress"
                  className="rounded-xl border border-ink/30 bg-surface-muted px-3.5 py-2 text-xs text-ink"
                >
                  {savedProgressLabel
                    ? `Đã lưu chỗ đang xem (${savedProgressLabel}). Xem tiếp ngay sau khi nghỉ mắt xong nhé!`
                    : 'Dippie đã lưu chỗ bé đang xem dở rồi. Nghỉ mắt xong là xem tiếp ngay nhé!'}
                </div>

                <a
                  href="/"
                  data-autofocus=""
                  data-testid="watch-pet-finish-today-button"
                  onClick={(event) => {
                    if (onFinishSession) {
                      event.preventDefault();
                      onFinishSession();
                    }
                  }}
                  className="btn-secondary inline-flex min-h-[46px] w-full items-center justify-center gap-2 text-xs font-bold sm:text-sm"
                >
                  <Home size={16} aria-hidden="true" />
                  <span>Xong hôm nay · Về trang chủ</span>
                </a>
              </div>
            ) : medicineChallenge ? (
              <div className="space-y-3 text-left">
                <h3 id="watch-pet-dialog-title" className="text-center font-display text-lg font-bold text-ink">
                  Trả lời câu hỏi để xem tiếp cùng Dieppie
                </h3>
                <QuizQuestionCard
                  question={medicineChallenge.question}
                  options={medicineChallenge.options}
                  subject={medicineChallenge.subject}
                  subjectLabel={medicineChallenge.subjectLabel}
                  gradeLabel={medicineChallenge.gradeLabel}
                  setName={medicineChallenge.setName}
                  subtitle="Trả lời đúng 1 câu để mở lại video ngay nhé"
                  submitting={busy}
                  wrongReveal={wrongReveal}
                  selectedOption={selectedOption}
                  onDraftSelectOption={setSelectedOption}
                  errorMessage={feedback?.kind === 'error' ? feedback.text : null}
                  questionTestId="watch-pet-medicine-question"
                  feedbackTestId="watch-pet-feedback"
                  onSelectOption={(option) => void handleSelectOption(option)}
                  onNextQuestion={() => void handleLoadMedicine()}
                  nextQuestionLabel="Thử câu khác"
                />
                <div className="flex justify-start">
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => {
                      setMedicineChallenge(null);
                      setWrongReveal(null);
                      setFeedback(null);
                    }}
                    className="btn-secondary min-h-[44px] justify-center px-3 text-xs"
                  >
                    Quay lại
                  </button>
                </div>
              </div>
            ) : (
              <div className="space-y-3 text-left">
                <h3 id="watch-pet-dialog-title" className="text-center font-display text-lg font-bold text-ink sm:text-xl">
                  Đến giờ nghỉ mắt rồi!
                </h3>
                <p className="text-center text-xs leading-relaxed text-muted sm:text-sm">
                  Bé xem liên tục một lúc rồi đó. Cùng thư giãn mắt với Dieppie ít phút rồi xem tiếp nhé!
                </p>

                <div
                  data-testid="watch-pet-saved-progress"
                  className="rounded-xl border border-ink/35 bg-mint/45 px-3.5 py-2.5 text-xs leading-relaxed text-ink"
                >
                  <p className="font-bold">
                    {savedProgressLabel
                      ? `Dippie đã lưu chỗ đang xem (${savedProgressLabel}) rồi.`
                      : 'Dippie đã lưu lại tập phim bé đang xem dở rồi.'}
                  </p>
                  <p data-testid="watch-pet-next-available" className="mt-0.5 text-muted">
                    Bé có thể xem tiếp ngay sau {watchPet.restMinutes} phút nghỉ mắt, hoặc dừng xem hôm nay nhé.
                  </p>
                </div>

                {feedback && (
                  <div
                    role={feedback.kind === 'error' ? 'alert' : 'status'}
                    data-testid="watch-pet-feedback"
                    className={`rounded-lg border px-3 py-2 text-xs ${
                      feedback.kind === 'error'
                        ? 'border-red-800/35 bg-red-50 text-red-900'
                        : 'border-ink/40 bg-mint/60 font-semibold text-ink'
                    }`}
                  >
                    {feedback.text}
                  </div>
                )}

                <div className="flex flex-col gap-2 pt-1">
                  <button
                    type="button"
                    data-autofocus=""
                    data-testid="watch-pet-rest-button"
                    disabled={busy}
                    onClick={() => void handleRestClick()}
                    className="btn-primary motion-sheen min-h-[48px] w-full justify-center text-xs font-bold sm:text-sm"
                  >
                    Nghỉ cùng Dieppie {watchPet.restMinutes} phút
                  </button>

                  <a
                    href="/"
                    data-testid="watch-pet-finish-today-button"
                    onClick={(event) => {
                      if (onFinishSession) {
                        event.preventDefault();
                        onFinishSession();
                      }
                    }}
                    className="btn-secondary inline-flex min-h-[44px] w-full items-center justify-center gap-1.5 text-xs font-bold sm:text-sm"
                  >
                    <Home size={15} aria-hidden="true" />
                    <span>Xong hôm nay · Về trang chủ</span>
                  </a>

                  <button
                    type="button"
                    data-testid="watch-pet-medicine-button"
                    disabled={busy}
                    onClick={() => void handleLoadMedicine()}
                    className="inline-flex min-h-[44px] w-full items-center justify-center rounded-xl border border-ink/35 bg-surface-muted px-3 py-2 text-xs font-semibold text-ink hover:border-ink hover:bg-paper-white cursor-pointer"
                  >
                    Trả lời 1 câu hỏi để xem tiếp ngay
                  </button>

                  {token && (
                    <button
                      type="button"
                      data-testid="watch-pet-parent-grant-button"
                      aria-expanded={showParentGrant}
                      onClick={() => {
                        setShowParentGrant((prev) => !prev);
                        setFeedback(null);
                      }}
                      className="inline-flex min-h-[44px] w-full items-center justify-center gap-1.5 rounded-xl border border-ink/25 bg-paper-white px-3 py-2 text-xs font-semibold text-muted hover:text-ink cursor-pointer"
                    >
                      <KeyRound size={14} aria-hidden="true" />
                      <span>Ba mẹ cho thêm giờ xem hôm nay (PIN)</span>
                    </button>
                  )}

                  {showParentGrant && token && (
                    <form
                      onSubmit={handleParentGrantSubmit}
                      data-testid="watch-pet-parent-grant-form"
                      className="space-y-2 rounded-xl border-2 border-ink bg-[#fef3c7]/60 p-3"
                    >
                      <div className="flex gap-2">
                        {[15, 30].map((mins) => (
                          <button
                            key={mins}
                            type="button"
                            aria-pressed={grantMinutes === mins}
                            onClick={() => setGrantMinutes(mins)}
                            className={`min-h-[44px] flex-1 rounded-lg border px-2 py-1 text-xs font-bold cursor-pointer ${
                              grantMinutes === mins
                                ? 'border-ink bg-ink text-paper-white'
                                : 'border-ink/40 bg-paper-white text-ink'
                            }`}
                          >
                            +{mins} phút
                          </button>
                        ))}
                      </div>
                      <div className="flex items-center gap-2">
                        <label htmlFor="watch-pet-parent-pin" className="sr-only">
                          Mã PIN phụ huynh 4 chữ số
                        </label>
                        <input
                          id="watch-pet-parent-pin"
                          data-testid="watch-pet-parent-pin-input"
                          type="password"
                          inputMode="numeric"
                          pattern="[0-9]*"
                          maxLength={4}
                          autoComplete="off"
                          placeholder="PIN 4 số"
                          value={parentPin}
                          onChange={(event) => setParentPin(event.target.value.replace(/\D/g, '').slice(0, 4))}
                          className="min-h-[44px] min-w-0 flex-1 rounded-lg border-2 border-ink bg-paper-white px-3 py-1.5 text-center font-mono text-sm font-bold tracking-widest text-ink"
                        />
                        <button
                          type="submit"
                          data-testid="watch-pet-parent-grant-submit"
                          disabled={busy}
                          className="btn-primary min-h-[44px] shrink-0 px-3 py-1.5 text-xs font-bold"
                        >
                          Cộng giờ
                        </button>
                      </div>
                    </form>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </>
  );
}
