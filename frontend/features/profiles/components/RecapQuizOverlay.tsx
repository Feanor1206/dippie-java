import React, { useEffect, useRef, useState } from 'react';
import { Check, Film, Home, Sparkles, Volume2 } from 'lucide-react';
import { ArtworkImage } from '@/features/series/components/ArtworkImage';
import { useBlockingOverlayFocus } from '@/features/profiles/hooks/useBlockingOverlayFocus';
import { speakVietnamesePrompt } from '@/features/quiz/quizService';
import {
  fetchRecapQuiz,
  submitRecapQuizAnswer,
  type RecapQuiz,
  type RecapQuizItem,
} from '@/lib/services/playFeaturesService';

const CHOICE_BADGES = ['▲', '◆', '●', '■'] as const;

const RECAP_CHARACTER_VI_LABELS: Record<string, { label: string; dotColor: string }> = {
  yellow: { label: 'Ấu trùng Vàng (Yellow)', dotColor: '#facc15' },
  red: { label: 'Ấu trùng Đỏ (Red)', dotColor: '#ef4444' },
  violet: { label: 'Ấu trùng Tím (Violet)', dotColor: '#8b5cf6' },
  brown: { label: 'Bọ Hung Nâu (Brown)', dotColor: '#92400e' },
  pink: { label: 'Ấu trùng Hồng (Pink)', dotColor: '#f472b6' },
  black: { label: 'Bọ Cánh Cứng Đen (Black)', dotColor: '#1f2937' },
  rainbow: { label: 'Ốc Sên Cầu Vồng (Rainbow)', dotColor: '#10b981' },
};

export function localizeRecapItemText(rawText: string): { text: string; dotColor: string | null } {
  const trimmed = (rawText || '').trim();
  if (!trimmed) return { text: '', dotColor: null };
  const exact = RECAP_CHARACTER_VI_LABELS[trimmed.toLowerCase()];
  if (exact) {
    return { text: exact.label, dotColor: exact.dotColor };
  }
  let localized = trimmed;
  let matchedColor: string | null = null;
  for (const [engKey, meta] of Object.entries(RECAP_CHARACTER_VI_LABELS)) {
    const pattern = new RegExp(`\\b${engKey}\\b`, 'gi');
    if (pattern.test(localized)) {
      localized = localized.replace(pattern, meta.label);
      if (!matchedColor) matchedColor = meta.dotColor;
    }
  }
  return { text: localized, dotColor: matchedColor };
}

export function RecapQuizOverlay({
  token,
  episodeId,
  nextEpisodeLabel,
  seriesTitle = '',
  artworkUrl = null,
  onUnlocked,
  onRewatchEpisode,
  onFinishSession,
}: {
  token: string;
  episodeId: string;
  nextEpisodeLabel: string;
  seriesTitle?: string;
  artworkUrl?: string | null;
  onUnlocked: (targetEpisodeId: string | null) => void;
  onRewatchEpisode: () => void;
  onFinishSession?: () => void;
}) {
  const overlayRef = useRef<HTMLDivElement | null>(null);
  useBlockingOverlayFocus(overlayRef, true);

  const [quiz, setQuiz] = useState<RecapQuiz | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [orderedIds, setOrderedIds] = useState<string[]>([]);
  const [selectedChoiceId, setSelectedChoiceId] = useState('');
  const [draggingId, setDraggingId] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<{ kind: 'error' | 'success'; text: string } | null>(null);
  const [speechNotice, setSpeechNotice] = useState<string | null>(null);
  const [solvedTargetId, setSolvedTargetId] = useState<string | null | undefined>(undefined);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setQuiz(null);
    setOrderedIds([]);
    setSelectedChoiceId('');
    setFeedback(null);
    setSpeechNotice(null);
    setSolvedTargetId(undefined);
    void fetchRecapQuiz(token, episodeId)
      .then((loaded) => {
        if (!active) return;
        setQuiz(loaded);
      })
      .catch((err: unknown) => {
        if (!active) return;
        setFeedback({
          kind: 'error',
          text: err instanceof Error ? err.message : 'Chưa tải được câu hỏi ôn tập.',
        });
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [token, episodeId]);

  const itemById = new Map<string, RecapQuizItem>((quiz?.items ?? []).map((item) => [item.id, item]));
  const remainingItems = (quiz?.items ?? []).filter((item) => !orderedIds.includes(item.id));

  const speakAndReport = (text: string) => {
    const ok = speakVietnamesePrompt(text);
    setSpeechNotice(
      ok ? null : 'Thiết bị này chưa có giọng đọc tiếng Việt tự động, ba mẹ đọc giúp bé nhé.'
    );
  };

  const handleReadFullRecap = () => {
    if (!quiz) {
      speakAndReport('Cùng nhớ lại tập vừa xem nhé!');
      return;
    }
    const intro =
      quiz.quizType === 'ORDER_SCENES'
        ? `${quiz.prompt}. Chạm chọn cảnh xảy ra trước rồi tới cảnh tiếp theo nhé.`
        : quiz.prompt;
    const itemLines = quiz.items.map(
      (item, idx) => `Lựa chọn ${idx + 1}: ${localizeRecapItemText(item.text).text}`
    );
    speakAndReport([intro, ...itemLines].join(' '));
  };

  const toggleOrderItem = (id: string) => {
    setFeedback(null);
    setOrderedIds((prev) => (prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]));
  };

  const moveOrderedItem = (index: number, delta: -1 | 1) => {
    const nextIndex = index + delta;
    setOrderedIds((prev) => {
      if (nextIndex < 0 || nextIndex >= prev.length) return prev;
      const copy = prev.slice();
      const [moved] = copy.splice(index, 1);
      copy.splice(nextIndex, 0, moved);
      return copy;
    });
  };

  const handleDropAt = (targetIndex: number) => {
    if (!draggingId) return;
    setOrderedIds((prev) => {
      const without = prev.filter((id) => id !== draggingId);
      const safeIndex = Math.max(0, Math.min(targetIndex, without.length));
      without.splice(safeIndex, 0, draggingId);
      return without;
    });
    setDraggingId(null);
  };

  const canSubmit =
    quiz !== null &&
    !submitting &&
    (quiz.quizType === 'ORDER_SCENES'
      ? orderedIds.length === quiz.items.length
      : Boolean(selectedChoiceId));

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!quiz || !canSubmit) return;
    setSubmitting(true);
    setFeedback(null);
    try {
      const payload =
        quiz.quizType === 'ORDER_SCENES'
          ? { token: quiz.token, orderedIds }
          : { token: quiz.token, choiceId: selectedChoiceId };
      const result = await submitRecapQuizAnswer(token, payload);
      if (result.correct) {
        setFeedback({
          kind: 'success',
          text: result.message || 'Giỏi quá! Bé đã mở khóa tập tiếp theo rồi!',
        });
        setSolvedTargetId(result.targetEpisodeId);
      } else {
        setFeedback({
          kind: 'error',
          text: result.message || 'Chưa đúng thứ tự rồi, bé nghe lại hoặc xếp lại nhé!',
        });
      }
    } catch (err: unknown) {
      setFeedback({
        kind: 'error',
        text: err instanceof Error ? err.message : 'Chưa kiểm tra được câu trả lời.',
      });
    } finally {
      setSubmitting(false);
    }
  };

  const orderStepGuide =
    quiz && quiz.quizType === 'ORDER_SCENES'
      ? orderedIds.length === 0
        ? 'Bước 1: Chạm vào cảnh xảy ra đầu tiên nhé!'
        : orderedIds.length < quiz.items.length
        ? `Bước ${orderedIds.length + 1}: Chạm vào cảnh xảy ra tiếp theo nhé!`
        : `Đã xếp đủ ${quiz.items.length} cảnh rồi! Bấm nút bên dưới để mở tập mới nhé.`
      : '';

  return (
    <div
      ref={overlayRef}
      tabIndex={-1}
      role="dialog"
      aria-modal="true"
      aria-labelledby="recap-quiz-title"
      data-testid="recap-quiz-overlay"
      className="relative z-30 flex min-h-[30rem] sm:min-h-[32rem] w-full flex-1 items-center justify-center bg-ink/90 p-3 sm:p-5 animate-fade-in"
    >
      <div className="motion-overlay-overshoot my-auto w-full max-w-lg rounded-2xl border-2 border-ink bg-paper-white p-4 text-left shadow-brand-lg sm:p-5">
        <div className="flex items-start gap-3">
          <ArtworkImage
            src={artworkUrl}
            alt={quiz?.episodeTitle || seriesTitle || 'Tập vừa xem'}
            title={seriesTitle || quiz?.episodeTitle || 'Dippie'}
            seed={episodeId}
            aspectClassName="h-16 w-20"
            className="h-16 w-20 shrink-0 rounded-xl border-2 border-ink"
          />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="motion-chip-pop inline-flex items-center gap-1 rounded-full border border-ink bg-[#f2d18c] px-2.5 py-0.5 text-[0.6875rem] font-bold text-ink">
                <Sparkles size={12} aria-hidden="true" />
                <span>Nhớ lại tập vừa xem</span>
              </span>
              <button
                type="button"
                data-autofocus=""
                data-testid="recap-read-aloud-button"
                onClick={handleReadFullRecap}
                aria-label="Nghe đọc câu hỏi ôn tập và các lựa chọn"
                className="inline-flex min-h-[44px] min-w-[44px] shrink-0 items-center justify-center gap-1.5 rounded-lg border-2 border-ink bg-[#fef3c7] px-3 py-1.5 text-xs font-bold text-ink shadow-[2px_2px_0_#203328] hover:bg-[#fde68a] cursor-pointer"
              >
                <Volume2 size={16} aria-hidden="true" />
                <span>Nghe đọc</span>
              </button>
            </div>
            <h3 id="recap-quiz-title" className="mt-1.5 font-display text-base font-bold text-ink sm:text-lg">
              {quiz?.prompt || 'Cùng nhớ lại tập vừa xem nhé!'}
            </h3>
          </div>
        </div>

        {speechNotice && (
          <p role="status" data-testid="recap-speech-notice" className="mt-2 rounded-lg border border-ink/20 bg-[#fef3c7]/65 px-3 py-1.5 text-xs font-medium text-ink">
            {speechNotice}
          </p>
        )}

        {loading && (
          <p role="status" className="py-6 text-center text-xs font-semibold text-muted">
            Đang chuẩn bị câu hỏi ôn tập cho bé…
          </p>
        )}

        {!loading && solvedTargetId !== undefined && (
          <div className="mt-4 space-y-3 text-center">
            {feedback && (
              <div
                role="status"
                data-testid="recap-feedback"
                className="motion-correct-pop rounded-xl border border-ink/40 bg-mint/70 px-4 py-3 text-xs font-bold text-ink sm:text-sm"
              >
                {feedback.text}
              </div>
            )}
            <button
              type="button"
              data-testid="recap-continue-next-button"
              onClick={() => onUnlocked(solvedTargetId)}
              className="btn-primary motion-sheen min-h-[48px] w-full justify-center text-xs font-bold sm:text-sm"
            >
              {nextEpisodeLabel ? `Mở ${nextEpisodeLabel}` : 'Xem tập tiếp theo'}
            </button>
          </div>
        )}

        {!loading && quiz && solvedTargetId === undefined && (
          <form onSubmit={handleSubmit} className="mt-3 space-y-3">
            {quiz.quizType === 'ORDER_SCENES' ? (
              <div className="space-y-3">
                <div
                  data-testid="recap-step-guide"
                  className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-ink/30 bg-mint/35 px-3 py-2 text-xs font-bold text-ink"
                >
                  <span>{orderStepGuide}</span>
                  <div className="flex items-center gap-1" aria-hidden="true">
                    {quiz.items.map((item, idx) => (
                      <span
                        key={item.id}
                        className={`grid h-6 w-6 place-items-center rounded-full border border-ink text-[0.6875rem] font-extrabold ${
                          idx < orderedIds.length ? 'bg-ink text-paper-white' : 'bg-paper-white text-muted'
                        }`}
                      >
                        {idx + 1}
                      </span>
                    ))}
                  </div>
                </div>

                {orderedIds.length > 0 && (
                  <div className="space-y-1.5" data-testid="recap-ordered-slots">
                    {orderedIds.map((id, index) => {
                      const item = itemById.get(id);
                      if (!item) return null;
                      const localized = localizeRecapItemText(item.text);
                      return (
                        <div
                          key={id}
                          draggable
                          onDragStart={() => setDraggingId(id)}
                          onDragOver={(event) => event.preventDefault()}
                          onDrop={() => handleDropAt(index)}
                          data-testid={`recap-slot-${index + 1}`}
                          className="flex items-center gap-2 rounded-xl border-2 border-ink bg-mint/55 px-2.5 py-2 text-xs text-ink"
                        >
                          <span className="grid h-7 w-7 shrink-0 place-items-center rounded-full border border-ink bg-ink text-xs font-extrabold text-paper-white">
                            {index + 1}
                          </span>
                          <button
                            type="button"
                            onClick={() => toggleOrderItem(id)}
                            className="min-h-[44px] min-w-0 flex-1 text-left font-semibold text-ink cursor-pointer"
                          >
                            {localized.text}
                          </button>
                          <div className="flex shrink-0 items-center gap-1">
                            <button
                              type="button"
                              data-testid={`recap-speak-slot-${index + 1}`}
                              aria-label={`Nghe đọc cảnh ${index + 1}: ${localized.text}`}
                              onClick={() => speakAndReport(`Cảnh số ${index + 1}: ${localized.text}`)}
                              className="inline-flex h-11 w-11 min-h-[44px] min-w-[44px] items-center justify-center rounded-lg border border-ink bg-paper-white text-ink hover:bg-[#fef3c7] cursor-pointer"
                            >
                              <Volume2 size={15} aria-hidden="true" />
                            </button>
                            <button
                              type="button"
                              aria-label={`Đưa cảnh ${index + 1} lên trên`}
                              disabled={index === 0}
                              onClick={() => moveOrderedItem(index, -1)}
                              className="inline-flex h-11 w-11 min-h-[44px] min-w-[44px] items-center justify-center rounded-lg border border-ink bg-paper-white text-sm font-bold disabled:opacity-40 cursor-pointer"
                            >
                              ↑
                            </button>
                            <button
                              type="button"
                              aria-label={`Đưa cảnh ${index + 1} xuống dưới`}
                              disabled={index === orderedIds.length - 1}
                              onClick={() => moveOrderedItem(index, 1)}
                              className="inline-flex h-11 w-11 min-h-[44px] min-w-[44px] items-center justify-center rounded-lg border border-ink bg-paper-white text-sm font-bold disabled:opacity-40 cursor-pointer"
                            >
                              ↓
                            </button>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}

                {remainingItems.length > 0 && (
                  <div className="space-y-1.5" data-testid="recap-pool-items">
                    {remainingItems.map((item) => {
                      const localized = localizeRecapItemText(item.text);
                      return (
                        <div key={item.id} className="flex items-stretch gap-1.5">
                          <button
                            type="button"
                            draggable
                            data-testid={`recap-item-${item.id}`}
                            onDragStart={() => setDraggingId(item.id)}
                            onClick={() => toggleOrderItem(item.id)}
                            className="flex min-h-[56px] min-w-0 flex-1 items-center gap-2.5 rounded-xl border-2 border-ink/40 bg-surface-muted px-3 py-2 text-left text-xs font-bold text-ink hover:border-ink hover:bg-paper-white cursor-pointer sm:text-sm"
                          >
                            <span className="grid h-7 w-7 shrink-0 place-items-center rounded-lg border border-ink bg-[#f2d18c] text-xs font-extrabold text-ink">
                              <Film size={15} aria-hidden="true" />
                            </span>
                            <span className="min-w-0 flex-1 break-words leading-snug">{localized.text}</span>
                          </button>
                          <button
                            type="button"
                            data-testid={`recap-speak-item-${item.id}`}
                            aria-label={`Nghe đọc cảnh: ${localized.text}`}
                            onClick={() => speakAndReport(localized.text)}
                            className="inline-flex min-h-[56px] min-w-[44px] shrink-0 items-center justify-center rounded-xl border-2 border-ink bg-paper-white px-2.5 text-ink hover:bg-[#fef3c7] cursor-pointer"
                          >
                            <Volume2 size={17} aria-hidden="true" />
                          </button>
                        </div>
                      );
                    })}
                  </div>
                )}

                {orderedIds.length > 0 && (
                  <button
                    type="button"
                    data-testid="recap-reset-order"
                    onClick={() => {
                      setOrderedIds([]);
                      setFeedback(null);
                    }}
                    className="inline-flex min-h-[44px] items-center px-2 text-xs font-semibold text-muted underline hover:text-ink cursor-pointer"
                  >
                    Xếp lại từ đầu
                  </button>
                )}
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-2" data-testid="recap-choices">
                {quiz.items.map((item, idx) => {
                  const selected = selectedChoiceId === item.id;
                  const badgeSymbol = CHOICE_BADGES[idx % CHOICE_BADGES.length];
                  const localized = localizeRecapItemText(item.text);
                  return (
                    <div key={item.id} className="flex items-stretch gap-1.5">
                      <button
                        type="button"
                        data-testid={`recap-choice-${item.id}`}
                        aria-pressed={selected}
                        onClick={() => {
                          setSelectedChoiceId(item.id);
                          setFeedback(null);
                        }}
                        className={`flex min-h-[56px] min-w-0 flex-1 items-center gap-2.5 rounded-xl border-2 px-3 py-2.5 text-left text-xs font-bold transition-colors cursor-pointer sm:text-sm ${
                          selected
                            ? 'border-ink bg-mint text-ink shadow-[3px_3px_0_#203328]'
                            : 'border-surface-border bg-surface-muted text-ink hover:border-ink/60'
                        }`}
                      >
                        <span
                          style={
                            !selected && localized.dotColor
                              ? ({ backgroundColor: localized.dotColor, color: '#fffdf6' } as React.CSSProperties)
                              : undefined
                          }
                          className="grid h-7 w-7 shrink-0 place-items-center rounded-lg border border-ink bg-paper-white text-xs font-extrabold text-ink"
                        >
                          {selected ? <Check size={15} aria-hidden="true" /> : badgeSymbol}
                        </span>
                        <span className="min-w-0 flex-1 break-words leading-snug">{localized.text}</span>
                        {selected && (
                          <span className="shrink-0 rounded-full border border-ink bg-paper-white px-2 py-0.5 text-[0.6875rem] font-extrabold text-ink">
                            Đã chọn
                          </span>
                        )}
                      </button>
                      <button
                        type="button"
                        data-testid={`recap-speak-item-${item.id}`}
                        aria-label={`Nghe đọc lựa chọn: ${localized.text}`}
                        onClick={() => speakAndReport(localized.text)}
                        className="inline-flex min-h-[56px] min-w-[44px] shrink-0 items-center justify-center rounded-xl border-2 border-ink bg-paper-white px-2.5 text-ink hover:bg-[#fef3c7] cursor-pointer"
                      >
                        <Volume2 size={17} aria-hidden="true" />
                      </button>
                    </div>
                  );
                })}
              </div>
            )}

            {feedback && (
              <div
                role="alert"
                data-testid="recap-feedback"
                className="rounded-lg border border-red-800/35 bg-red-50 px-3 py-2 text-xs text-red-900"
              >
                {feedback.text}
              </div>
            )}

            <button
              type="submit"
              data-testid="recap-submit-button"
              disabled={!canSubmit}
              className="btn-primary min-h-[52px] w-full justify-center text-xs font-bold sm:text-sm disabled:opacity-50"
            >
              {submitting ? 'Đang kiểm tra…' : 'Trả lời để mở tập tiếp theo'}
            </button>
          </form>
        )}

        <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-surface-border pt-3">
          <button
            type="button"
            data-testid="recap-rewatch-button"
            onClick={onRewatchEpisode}
            className="btn-secondary min-h-[44px] px-3 py-1.5 text-xs font-semibold"
          >
            Xem lại tập này
          </button>

          <a
            href="/"
            data-testid="recap-finish-today-button"
            onClick={(event) => {
              if (onFinishSession) {
                event.preventDefault();
                onFinishSession();
              }
            }}
            className="btn-secondary inline-flex min-h-[44px] items-center gap-1.5 px-3 py-1.5 text-xs font-bold"
          >
            <Home size={14} aria-hidden="true" />
            <span>Xong hôm nay · Về trang chủ</span>
          </a>
        </div>
      </div>
    </div>
  );
}
