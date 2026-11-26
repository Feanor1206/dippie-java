import React, { useEffect, useState } from 'react';
import {
  fetchRecapQuiz,
  submitRecapQuizAnswer,
  type RecapQuiz,
  type RecapQuizItem,
} from '@/lib/services/playFeaturesService';

export function RecapQuizOverlay({
  token,
  episodeId,
  nextEpisodeLabel,
  onUnlocked,
  onRewatchEpisode,
}: {
  token: string;
  episodeId: string;
  nextEpisodeLabel: string;
  onUnlocked: (targetEpisodeId: string | null) => void;
  onRewatchEpisode: () => void;
}) {
  const [quiz, setQuiz] = useState<RecapQuiz | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [orderedIds, setOrderedIds] = useState<string[]>([]);
  const [selectedChoiceId, setSelectedChoiceId] = useState('');
  const [draggingId, setDraggingId] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<{ kind: 'error' | 'success'; text: string } | null>(null);
  const [solvedTargetId, setSolvedTargetId] = useState<string | null | undefined>(undefined);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setQuiz(null);
    setOrderedIds([]);
    setSelectedChoiceId('');
    setFeedback(null);
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
          text: result.message || 'Chưa đúng rồi, bé thử lại nhé!',
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

  return (
    <div
      role="dialog"
      aria-modal="false"
      aria-labelledby="recap-quiz-title"
      data-testid="recap-quiz-overlay"
      className="absolute inset-0 z-30 flex items-center justify-center overflow-y-auto bg-ink/90 p-3 sm:p-5"
    >
      <div className="my-auto w-full max-w-lg rounded-2xl border-2 border-ink bg-paper-white p-4 text-left shadow-brand-lg sm:p-5">
        <div className="flex items-start justify-between gap-2">
          <div>
            <span className="inline-flex items-center rounded-full border border-ink bg-[#f2d18c] px-2.5 py-0.5 text-[11px] font-bold text-ink">
              Ôn tập qua tập mới
            </span>
            <h3 id="recap-quiz-title" className="mt-1.5 font-display text-base font-bold text-ink sm:text-lg">
              {quiz?.prompt || 'Cùng nhớ lại tập vừa xem nhé!'}
            </h3>
          </div>
          <button
            type="button"
            data-testid="recap-rewatch-button"
            onClick={onRewatchEpisode}
            className="btn-secondary min-h-[44px] shrink-0 px-3 py-1.5 text-xs"
          >
            Xem lại tập này
          </button>
        </div>

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
                className="rounded-xl border border-ink/40 bg-mint/70 px-4 py-3 text-xs font-bold text-ink sm:text-sm"
              >
                {feedback.text}
              </div>
            )}
            <button
              type="button"
              data-testid="recap-continue-next-button"
              onClick={() => onUnlocked(solvedTargetId)}
              className="btn-primary min-h-[44px] w-full justify-center text-xs font-bold sm:text-sm"
            >
              {nextEpisodeLabel ? `Mở ${nextEpisodeLabel}` : 'Xem tập tiếp theo'}
            </button>
          </div>
        )}

        {!loading && quiz && solvedTargetId === undefined && (
          <form onSubmit={handleSubmit} className="mt-3 space-y-3">
            {quiz.quizType === 'ORDER_SCENES' ? (
              <div className="space-y-3">
                <p className="text-[11px] text-muted">
                  Chạm từng thẻ theo thứ tự từ đầu đến cuối tập (hoặc kéo thả / bấm mũi tên để đổi chỗ).
                </p>

                {orderedIds.length > 0 && (
                  <div className="space-y-1.5" data-testid="recap-ordered-slots">
                    {orderedIds.map((id, index) => {
                      const item = itemById.get(id);
                      if (!item) return null;
                      return (
                        <div
                          key={id}
                          draggable
                          onDragStart={() => setDraggingId(id)}
                          onDragOver={(event) => event.preventDefault()}
                          onDrop={() => handleDropAt(index)}
                          data-testid={`recap-slot-${index + 1}`}
                          className="flex items-center gap-2 rounded-xl border-2 border-ink bg-mint/55 px-3 py-2 text-xs text-ink"
                        >
                          <span className="grid h-6 w-6 shrink-0 place-items-center rounded-full border border-ink bg-ink text-[11px] font-bold text-paper-white">
                            {index + 1}
                          </span>
                          <button
                            type="button"
                            onClick={() => toggleOrderItem(id)}
                            className="min-h-[36px] flex-1 text-left font-semibold text-ink cursor-pointer"
                          >
                            {item.text}
                          </button>
                          <div className="flex shrink-0 items-center gap-1">
                            <button
                              type="button"
                              aria-label={`Đưa cảnh ${index + 1} lên trên`}
                              disabled={index === 0}
                              onClick={() => moveOrderedItem(index, -1)}
                              className="inline-flex h-8 w-8 items-center justify-center rounded border border-ink bg-paper-white text-xs font-bold disabled:opacity-40 cursor-pointer"
                            >
                              ↑
                            </button>
                            <button
                              type="button"
                              aria-label={`Đưa cảnh ${index + 1} xuống dưới`}
                              disabled={index === orderedIds.length - 1}
                              onClick={() => moveOrderedItem(index, 1)}
                              className="inline-flex h-8 w-8 items-center justify-center rounded border border-ink bg-paper-white text-xs font-bold disabled:opacity-40 cursor-pointer"
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
                    {remainingItems.map((item) => (
                      <button
                        key={item.id}
                        type="button"
                        draggable
                        data-testid={`recap-item-${item.id}`}
                        onDragStart={() => setDraggingId(item.id)}
                        onClick={() => toggleOrderItem(item.id)}
                        className="flex min-h-[44px] w-full items-center gap-2 rounded-xl border border-surface-border bg-surface-muted px-3 py-2 text-left text-xs font-medium text-ink hover:border-ink hover:bg-paper-white cursor-pointer"
                      >
                        <span className="grid h-6 w-6 shrink-0 place-items-center rounded-full border border-ink/40 bg-paper-white text-[11px] font-bold text-muted">
                          +
                        </span>
                        <span>{item.text}</span>
                      </button>
                    ))}
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
                    className="text-xs font-semibold text-muted underline hover:text-ink cursor-pointer"
                  >
                    Xếp lại từ đầu
                  </button>
                )}
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-2" data-testid="recap-choices">
                {quiz.items.map((item) => {
                  const selected = selectedChoiceId === item.id;
                  return (
                    <button
                      key={item.id}
                      type="button"
                      data-testid={`recap-choice-${item.id}`}
                      aria-pressed={selected}
                      onClick={() => {
                        setSelectedChoiceId(item.id);
                        setFeedback(null);
                      }}
                      className={`min-h-[44px] w-full rounded-xl border-2 px-3.5 py-2.5 text-left text-xs font-semibold transition-colors cursor-pointer sm:text-sm ${
                        selected
                          ? 'border-ink bg-mint text-ink shadow-[2px_2px_0_#203328]'
                          : 'border-surface-border bg-surface-muted text-ink hover:border-ink/60'
                      }`}
                    >
                      {item.text}
                    </button>
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
              className="btn-primary min-h-[44px] w-full justify-center text-xs font-bold sm:text-sm disabled:opacity-50"
            >
              {submitting ? 'Đang kiểm tra…' : 'Trả lời để mở tập tiếp theo'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
