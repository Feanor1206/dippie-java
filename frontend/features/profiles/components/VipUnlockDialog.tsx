import React, { useEffect, useState } from 'react';
import { Calculator, RefreshCw } from 'lucide-react';
import { SiteDialog } from '@/components/ui/SiteDialog';
import {
  fetchVipEpisodeChallenge,
  submitVipEpisodeUnlock,
  type VipChallenge,
} from '@/lib/services/vipService';

export function VipUnlockDialog({
  episodeId,
  episodeTitle,
  token,
  onUnlocked,
  onClose,
}: {
  episodeId: string;
  episodeTitle?: string;
  token: string;
  onUnlocked: (episodeId: string) => void;
  onClose: () => void;
}) {
  const [challenge, setChallenge] = useState<VipChallenge | null>(null);
  const [loading, setLoading] = useState(true);
  const [answerInput, setAnswerInput] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [feedback, setFeedback] = useState<{ kind: 'error' | 'success'; text: string } | null>(null);

  const loadChallenge = async () => {
    setLoading(true);
    setFeedback(null);
    setAnswerInput('');
    try {
      const next = await fetchVipEpisodeChallenge(token, episodeId);
      setChallenge(next);
    } catch (err: unknown) {
      if ((err as { status?: number })?.status === 409) {
        onUnlocked(episodeId);
        return;
      }
      setFeedback({
        kind: 'error',
        text: err instanceof Error ? err.message : 'Chưa lấy được bài toán mở khóa tập VIP.',
      });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadChallenge();
  }, [episodeId, token]);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!challenge || submitting) return;
    const trimmed = answerInput.trim();
    if (!/^-?\d+$/.test(trimmed)) {
      setFeedback({ kind: 'error', text: 'Bé hãy nhập một số nhé.' });
      return;
    }
    setSubmitting(true);
    setFeedback(null);
    try {
      const result = await submitVipEpisodeUnlock(token, episodeId, challenge.token, parseInt(trimmed, 10));
      if (result.correct && result.unlocked) {
        onUnlocked(episodeId);
      } else {
        setAnswerInput('');
        setFeedback({
          kind: 'error',
          text: result.message || 'Chưa đúng rồi, bé thử tính lại nhé!',
        });
      }
    } catch (err: unknown) {
      setFeedback({
        kind: 'error',
        text: err instanceof Error ? err.message : 'Chưa kiểm tra được đáp án. Bé thử lại nhé.',
      });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <SiteDialog
      title="Mở khóa tập phim VIP"
      onClose={onClose}
      busy={submitting}
    >
      <div data-testid="vip-unlock-dialog" className="space-y-4">
        <div className="flex items-center gap-3">
          <div className="grid h-11 w-11 shrink-0 place-items-center rounded-xl border-2 border-ink bg-[#f2d18c] text-ink">
            <Calculator size={20} aria-hidden="true" />
          </div>
          <div className="min-w-0">
            <p className="text-xs font-bold text-ink">
              {episodeTitle ? `Tập: ${episodeTitle}` : 'Tập phim VIP'}
            </p>
            <p className="text-xs text-muted">
              Giải đúng 1 phép tính để mở khóa tập này trong hôm nay nhé!
            </p>
          </div>
        </div>

        {loading ? (
          <p role="status" className="py-4 text-center text-xs text-muted">
            Đang tạo câu hỏi toán VIP…
          </p>
        ) : challenge ? (
          <form onSubmit={handleSubmit} className="space-y-3">
            <div className="rounded-xl border-2 border-ink bg-mint/45 px-4 py-3 text-center">
              <p className="text-[11px] font-semibold text-muted">
                Thử thách VIP · Toán mức {challenge.mathLevel}
              </p>
              <p
                data-testid="vip-challenge-question"
                className="mt-1 font-display text-2xl font-bold tracking-wide text-ink"
              >
                {challenge.question}
              </p>
            </div>

            <div>
              <label htmlFor="vip-challenge-answer" className="mb-1 block text-xs font-semibold text-ink">
                Nhập đáp án của bé
              </label>
              <input
                id="vip-challenge-answer"
                data-testid="vip-answer-input"
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
                placeholder="Ví dụ: 18"
                className="w-full rounded-lg border-2 border-ink/40 bg-surface-muted px-3 py-2.5 text-center text-lg font-bold text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
              />
            </div>

            {feedback && (
              <div
                role="alert"
                data-testid="vip-feedback"
                className="rounded-lg border border-red-800/35 bg-red-50 px-3 py-2 text-xs font-medium text-red-900"
              >
                {feedback.text}
              </div>
            )}

            <div className="flex flex-col gap-2 sm:flex-row">
              <button
                type="submit"
                data-testid="vip-answer-submit"
                disabled={submitting || !answerInput.trim()}
                className="btn-primary min-h-[44px] flex-1 justify-center text-xs font-bold disabled:opacity-50"
              >
                {submitting ? 'Đang kiểm tra…' : 'Mở khóa tập này'}
              </button>
              <button
                type="button"
                data-testid="vip-challenge-refresh"
                disabled={submitting || loading}
                onClick={() => void loadChallenge()}
                className="btn-secondary min-h-[44px] justify-center gap-1 px-3 text-xs"
              >
                <RefreshCw size={13} aria-hidden="true" />
                Đổi câu
              </button>
            </div>
          </form>
        ) : (
          <div className="space-y-3 text-center">
            {feedback && (
              <div role="alert" className="rounded-lg border border-red-800/35 bg-red-50 px-3 py-2 text-xs text-red-900">
                {feedback.text}
              </div>
            )}
            <button type="button" onClick={() => void loadChallenge()} className="btn-secondary text-xs">
              Thử lấy câu khác
            </button>
          </div>
        )}
      </div>
    </SiteDialog>
  );
}
