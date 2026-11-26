import React, { useState } from 'react';
import {
  fetchWatchPetMedicineChallenge,
  formatCountdownSeconds,
  submitWatchPetMedicineAnswer,
  type WatchPetMedicineChallenge,
  type WatchPetStage,
  type WatchPetStatus,
} from '@/lib/services/playFeaturesService';

const STAGE_LABEL: Record<WatchPetStage, string> = {
  HAPPY: 'Dieppie đang rất vui',
  SWEATY: 'Dieppie hơi mỏi mắt rồi nhé',
  DIZZY: 'Dieppie mệt quá rồi, nghỉ mắt chút nha',
  FAINTED: 'Dieppie buồn ngủ lắm rồi',
  RESTING: 'Dieppie đang ngủ nghỉ mắt',
};

const STAGE_ANIMATION: Record<WatchPetStage, string> = {
  HAPPY: 'watch-pet-bob',
  SWEATY: 'watch-pet-wobble',
  DIZZY: 'watch-pet-sway',
  FAINTED: '',
  RESTING: 'watch-pet-bob',
};

export function WatchPetArt({ stage, size = 56 }: { stage: WatchPetStage; size?: number }) {
  const bodyFill =
    stage === 'RESTING'
      ? '#d9e6f7'
      : stage === 'FAINTED'
      ? '#fde68a'
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
      <g transform={stage === 'FAINTED' ? 'translate(40 44) rotate(14) translate(-40 -44)' : undefined}>
        <path
          d="M20 26 L26 11 L35 22 Z M60 26 L54 11 L45 22 Z"
          fill="#8dad86"
          stroke="#203328"
          strokeWidth="2.5"
          strokeLinejoin="round"
        />
        <ellipse cx="40" cy="45" rx="25" ry="23" fill={bodyFill} stroke="#203328" strokeWidth="2.5" />
        <ellipse cx="40" cy="52" rx="14" ry="11" fill="#fffdf6" />
        <circle cx="24" cy="48" r="4" fill="#fca5a5" opacity={stage === 'FAINTED' ? 0.35 : 0.75} />
        <circle cx="56" cy="48" r="4" fill="#fca5a5" opacity={stage === 'FAINTED' ? 0.35 : 0.75} />

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
            <circle cx="31" cy="40" r="3.4" fill="#203328" />
            <circle cx="49" cy="40" r="3.4" fill="#203328" />
            <path d="M27 33 L35 35 M53 33 L45 35" stroke="#203328" strokeWidth="2.2" strokeLinecap="round" />
            <path d="M35 50 Q40 47 45 50" fill="none" stroke="#203328" strokeWidth="2.4" strokeLinecap="round" />
            <path d="M62 24 C66 30 66 35 62 35 C58 35 58 30 62 24 Z" fill="#60a5fa" stroke="#203328" strokeWidth="1.8" />
          </>
        )}

        {stage === 'DIZZY' && (
          <>
            <circle cx="31" cy="40" r="4.5" fill="none" stroke="#203328" strokeWidth="2.2" />
            <circle cx="31" cy="40" r="1.5" fill="#203328" />
            <circle cx="49" cy="40" r="4.5" fill="none" stroke="#203328" strokeWidth="2.2" />
            <circle cx="49" cy="40" r="1.5" fill="#203328" />
            <path d="M34 50 Q37 47 40 50 Q43 53 46 50" fill="none" stroke="#203328" strokeWidth="2.3" strokeLinecap="round" />
            <polygon points="22,12 24,16 28,16 25,18 26,22 22,20 18,22 19,18 16,16 20,16" fill="#f59e0b" stroke="#203328" strokeWidth="1.2" />
            <polygon points="58,10 60,14 64,14 61,16 62,20 58,18 54,20 55,16 52,14 56,14" fill="#f59e0b" stroke="#203328" strokeWidth="1.2" />
          </>
        )}

        {stage === 'FAINTED' && (
          <>
            <path d="M26 39 Q31 44 36 39" fill="none" stroke="#203328" strokeWidth="2.5" strokeLinecap="round" />
            <path d="M44 39 Q49 44 54 39" fill="none" stroke="#203328" strokeWidth="2.5" strokeLinecap="round" />
            <path d="M36 51 Q40 48 44 51" fill="none" stroke="#203328" strokeWidth="2.4" strokeLinecap="round" />
            <text x="58" y="25" fontSize="11" fontWeight="800" fill="#203328" className="watch-pet-zzz">
              z
            </text>
          </>
        )}

        {stage === 'RESTING' && (
          <>
            <path d="M26 40 Q31 44 36 40" fill="none" stroke="#203328" strokeWidth="2.4" strokeLinecap="round" />
            <path d="M44 40 Q49 44 54 40" fill="none" stroke="#203328" strokeWidth="2.4" strokeLinecap="round" />
            <circle cx="40" cy="50" r="2.2" fill="#203328" />
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
  onStartRest,
  onWatchPetUpdated,
  hideCorner = false,
}: {
  watchPet: WatchPetStatus | null;
  token: string | null;
  restCountdown: number;
  onStartRest: () => Promise<WatchPetStatus | null>;
  onWatchPetUpdated: (next: WatchPetStatus) => void;
  hideCorner?: boolean;
}) {
  const [medicineChallenge, setMedicineChallenge] = useState<WatchPetMedicineChallenge | null>(null);
  const [answerInput, setAnswerInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ kind: 'error' | 'success'; text: string } | null>(null);

  if (!watchPet || !watchPet.enabled) {
    return null;
  }

  const watchedMinutes = Math.floor(watchPet.continuousWatchSeconds / 60);
  const showOverlay = watchPet.fainted || watchPet.resting;

  const handleRestClick = async () => {
    if (busy) return;
    setBusy(true);
    setFeedback(null);
    setMedicineChallenge(null);
    try {
      await onStartRest();
    } catch (err: unknown) {
      setFeedback({
        kind: 'error',
        text: err instanceof Error ? err.message : 'Chưa cho Dieppie nghỉ mắt được.',
      });
    } finally {
      setBusy(false);
    }
  };

  const handleLoadMedicine = async () => {
    if (!token || busy) return;
    setBusy(true);
    setFeedback(null);
    setAnswerInput('');
    try {
      const challenge = await fetchWatchPetMedicineChallenge(token);
      setMedicineChallenge(challenge);
    } catch (err: unknown) {
      setFeedback({
        kind: 'error',
        text: err instanceof Error ? err.message : 'Chưa lấy được bài toán mua thuốc hồi sức cho Dieppie.',
      });
    } finally {
      setBusy(false);
    }
  };

  const handleMedicineSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!token || !medicineChallenge || busy) return;
    const trimmed = answerInput.trim();
    if (!/^-?\d+$/.test(trimmed)) {
      setFeedback({ kind: 'error', text: 'Bé hãy nhập đáp án bằng số nhé.' });
      return;
    }
    setBusy(true);
    setFeedback(null);
    try {
      const result = await submitWatchPetMedicineAnswer(token, medicineChallenge.token, parseInt(trimmed, 10));
      onWatchPetUpdated(result.watchPet);
      if (result.correct) {
        setMedicineChallenge(null);
        setAnswerInput('');
        setFeedback(null);
      } else {
        setAnswerInput('');
        setFeedback({
          kind: 'error',
          text: result.message || 'Chưa đúng rồi, bé thử tính lại nhé.',
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

  return (
    <>
      {!hideCorner && (
        <div
          role="status"
          aria-live="polite"
          data-testid="watch-pet-corner"
          data-pet-stage={watchPet.stage}
          aria-label={`${STAGE_LABEL[watchPet.stage]}, đã xem liên tục ${watchedMinutes} phút`}
          className="pointer-events-none absolute top-3 right-3 z-20 flex items-center gap-1.5 rounded-full border-2 border-ink bg-paper-white/95 px-2.5 py-1 shadow-[2px_2px_0_#203328]"
        >
          <WatchPetArt stage={watchPet.stage} size={34} />
          <div className="pr-1 text-left leading-tight">
            <p className="text-[10px] font-bold text-ink">{STAGE_LABEL[watchPet.stage]}</p>
            <p className="font-mono text-[10px] text-muted">
              {watchPet.resting
                ? `Ngủ ${formatCountdownSeconds(restCountdown)}`
                : `${watchedMinutes}/${Math.round(watchPet.faintThresholdSeconds / 60)} phút`}
            </p>
          </div>
        </div>
      )}

      {showOverlay && (
        <div
          role="dialog"
          aria-modal="false"
          aria-labelledby="watch-pet-dialog-title"
          data-testid="watch-pet-overlay"
          className="absolute inset-0 z-30 flex items-center justify-center overflow-y-auto bg-ink/85 p-3 sm:p-6"
        >
          <div className="my-auto w-full max-w-md rounded-2xl border-2 border-ink bg-paper-white p-5 text-center shadow-brand-lg sm:p-6">
            <div className="mx-auto mb-2 flex justify-center">
              <WatchPetArt stage={watchPet.resting ? 'RESTING' : 'FAINTED'} size={76} />
            </div>

            {watchPet.resting ? (
              <div className="space-y-3">
                <h3 id="watch-pet-dialog-title" className="font-display text-lg font-bold text-ink sm:text-xl">
                  Dieppie đang ngủ nghỉ mắt rồi
                </h3>
                <p className="text-xs leading-relaxed text-muted sm:text-sm">
                  Bé nhìn ra xa, chớp mắt và uống một ngụm nước trong lúc chờ Dieppie tỉnh dậy nhé!
                </p>
                <div
                  data-testid="watch-pet-rest-countdown"
                  className="mx-auto inline-flex items-center justify-center rounded-xl border-2 border-ink bg-mint px-5 py-2.5 font-mono text-2xl font-bold text-ink shadow-[2px_2px_0_#203328]"
                >
                  {formatCountdownSeconds(restCountdown)}
                </div>
              </div>
            ) : medicineChallenge ? (
              <form onSubmit={handleMedicineSubmit} className="space-y-3 text-left">
                <h3 id="watch-pet-dialog-title" className="text-center font-display text-lg font-bold text-ink">
                  Giải toán mua thuốc hồi sức cho Dieppie
                </h3>
                <div className="rounded-xl border-2 border-ink bg-mint/45 px-4 py-3 text-center">
                  <p className="text-[11px] font-semibold text-muted">
                    Giải đúng để Dieppie khỏe lại ngay nhé
                  </p>
                  <p
                    data-testid="watch-pet-medicine-question"
                    className="mt-1 font-display text-2xl font-bold text-ink"
                  >
                    {medicineChallenge.question}
                  </p>
                </div>
                <div>
                  <label htmlFor="watch-pet-medicine-input" className="mb-1 block text-xs font-semibold text-ink">
                    Nhập đáp án của bé
                  </label>
                  <input
                    id="watch-pet-medicine-input"
                    data-testid="watch-pet-medicine-input"
                    type="text"
                    inputMode="numeric"
                    pattern="[0-9]*"
                    enterKeyHint="done"
                    autoComplete="off"
                    autoFocus
                    required
                    disabled={busy}
                    value={answerInput}
                    onChange={(event) => {
                      setAnswerInput(event.target.value.replace(/[^0-9]/g, '').slice(0, 5));
                      if (feedback?.kind === 'error') setFeedback(null);
                    }}
                    placeholder="Ví dụ: 8"
                    className="min-h-[48px] w-full rounded-lg border-2 border-ink/40 bg-surface-muted px-3 py-2.5 text-center text-xl font-bold text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                  />
                </div>
                {feedback && (
                  <div
                    role="alert"
                    data-testid="watch-pet-feedback"
                    className="rounded-lg border border-red-800/35 bg-red-50 px-3 py-2 text-xs text-red-900"
                  >
                    {feedback.text}
                  </div>
                )}
                <div className="flex flex-col gap-2 sm:flex-row">
                  <button
                    type="submit"
                    data-testid="watch-pet-medicine-submit"
                    disabled={busy || !answerInput.trim()}
                    className="btn-primary min-h-[44px] flex-1 justify-center text-xs font-bold disabled:opacity-50"
                  >
                    {busy ? 'Đang kiểm tra…' : 'Cho Dieppie uống thuốc hồi sức'}
                  </button>
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => {
                      setMedicineChallenge(null);
                      setFeedback(null);
                    }}
                    className="btn-secondary min-h-[44px] justify-center px-3 text-xs"
                  >
                    Quay lại
                  </button>
                </div>
              </form>
            ) : (
              <div className="space-y-3">
                <h3 id="watch-pet-dialog-title" className="font-display text-lg font-bold text-ink sm:text-xl">
                  Dieppie mỏi mắt lắm rồi!
                </h3>
                <p className="text-xs leading-relaxed text-muted sm:text-sm">
                  Bé xem lâu rồi đó. Chọn một cách bên dưới để Dieppie khỏe lại nhé!
                </p>
                {feedback && (
                  <div
                    role="alert"
                    data-testid="watch-pet-feedback"
                    className="rounded-lg border border-red-800/35 bg-red-50 px-3 py-2 text-xs text-red-900"
                  >
                    {feedback.text}
                  </div>
                )}
                <div className="flex flex-col gap-2.5 pt-1">
                  <button
                    type="button"
                    data-testid="watch-pet-rest-button"
                    disabled={busy}
                    onClick={() => void handleRestClick()}
                    className="btn-primary min-h-[44px] w-full justify-center text-xs font-bold sm:text-sm"
                  >
                    Cho Dieppie ngủ {watchPet.restMinutes} phút
                  </button>
                  <button
                    type="button"
                    data-testid="watch-pet-medicine-button"
                    disabled={busy}
                    onClick={() => void handleLoadMedicine()}
                    className="btn-secondary min-h-[44px] w-full justify-center text-xs font-bold sm:text-sm"
                  >
                    Mua thuốc hồi sức cho Dieppie (giải 1 câu toán)
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </>
  );
}
