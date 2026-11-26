import React, { useEffect, useState } from 'react';
import { Sparkles } from 'lucide-react';
import { SiteDialog } from '@/components/ui/SiteDialog';
import { QuizQuestionCard, type QuizWrongReveal } from '@/features/quiz/components/QuizQuestionCard';
import type { QuizOptionKey } from '@/features/quiz/quizService';
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
  const [selectedOption, setSelectedOption] = useState<QuizOptionKey | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [wrongReveal, setWrongReveal] = useState<QuizWrongReveal | null>(null);
  const [feedback, setFeedback] = useState<{ kind: 'error' | 'success'; text: string } | null>(null);

  const loadChallenge = async () => {
    setLoading(true);
    setFeedback(null);
    setWrongReveal(null);
    setSelectedOption(null);
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
        text: err instanceof Error ? err.message : 'Chưa lấy được câu hỏi mở khóa tập phim.',
      });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadChallenge();
  }, [episodeId, token]);

  const handleSelectOption = async (option: QuizOptionKey) => {
    if (!challenge || submitting || wrongReveal) return;
    setSubmitting(true);
    setFeedback(null);
    try {
      const result = await submitVipEpisodeUnlock(token, episodeId, challenge.token, option);
      if (result.correct && result.unlocked) {
        onUnlocked(episodeId);
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
              : 'Gần đúng rồi! Bé thử câu tiếp theo nhé!'),
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
      title="Mở khóa phim cần trả lời câu hỏi"
      onClose={onClose}
      busy={submitting}
    >
      <div data-testid="vip-unlock-dialog" className="space-y-4">
        <div className="flex items-center gap-3">
          <div className="motion-icon-drop grid h-11 w-11 shrink-0 place-items-center rounded-xl border-2 border-ink bg-[#f2d18c] text-ink">
            <Sparkles size={20} aria-hidden="true" />
          </div>
          <div className="min-w-0">
            <p className="text-xs font-bold text-ink">
              {episodeTitle ? `Tập: ${episodeTitle}` : 'Phim cần trả lời câu hỏi'}
            </p>
            <p className="text-xs text-muted">
              Trả lời đúng 1 câu hỏi thử thách để mở khóa tập này trong hôm nay nhé!
            </p>
          </div>
        </div>

        {loading ? (
          <p role="status" className="py-4 text-center text-xs text-muted">
            Đang tạo câu hỏi mở khóa…
          </p>
        ) : challenge ? (
          <QuizQuestionCard
            question={challenge.question}
            options={challenge.options}
            subject={challenge.subject}
            subjectLabel={challenge.subjectLabel}
            gradeLabel={`Thử thách mở phim · ${challenge.gradeLabel}`}
            setName={challenge.setName}
            submitting={submitting || loading}
            wrongReveal={wrongReveal}
            selectedOption={selectedOption}
            onDraftSelectOption={setSelectedOption}
            errorMessage={feedback?.kind === 'error' ? feedback.text : null}
            questionTestId="vip-challenge-question"
            feedbackTestId="vip-feedback"
            onSelectOption={(option) => void handleSelectOption(option)}
            onNextQuestion={() => void loadChallenge()}
            nextQuestionLabel="Thử câu khác"
          />
        ) : (
          <div className="space-y-3 text-center">
            {feedback && (
              <div role="alert" data-testid="vip-feedback" className="rounded-lg border border-red-800/35 bg-red-50 px-3 py-2 text-xs text-red-900">
                {feedback.text}
              </div>
            )}
            <button type="button" onClick={() => void loadChallenge()} className="btn-secondary min-h-[44px] text-xs">
              Thử lấy câu khác
            </button>
          </div>
        )}
      </div>
    </SiteDialog>
  );
}
