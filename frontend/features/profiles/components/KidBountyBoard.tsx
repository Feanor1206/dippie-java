import React, { useCallback, useEffect, useState } from 'react';
import { SiteDialog } from '@/components/ui/SiteDialog';
import { useOptionalScreenTime } from '@/lib/context/ScreenTimeContext';
import { validateParentPin } from '@/lib/services/profileService';
import { broadcastScreenTimeUpdated } from '@/lib/services/screenTimeService';
import {
  approveBountySubmission,
  fetchKidBountyBoard,
  getBountyIconMeta,
  rejectBountySubmission,
  submitKidBountyTask,
  type BountySubmission,
  type KidBountyTask,
} from '@/lib/services/bountyService';

const TASK_STATE_ORDER: Record<KidBountyTask['state'], number> = {
  AVAILABLE: 0,
  PENDING: 1,
  COMPLETED_TODAY: 2,
  COMPLETED: 3,
};

function sortKidTasks(tasks: KidBountyTask[]): KidBountyTask[] {
  return tasks
    .map((task, index) => ({ task, index }))
    .sort((a, b) => TASK_STATE_ORDER[a.task.state] - TASK_STATE_ORDER[b.task.state] || a.index - b.index)
    .map((entry) => entry.task);
}

function latestRejectionByTask(submissions: BountySubmission[] | undefined): Map<number, BountySubmission> {
  const seen = new Set<number>();
  const rejected = new Map<number, BountySubmission>();
  for (const submission of submissions ?? []) {
    if (seen.has(submission.taskId)) continue;
    seen.add(submission.taskId);
    if (submission.status === 'REJECTED') {
      rejected.set(submission.taskId, submission);
    }
  }
  return rejected;
}

type ParentReviewDialog = {
  submissionId: number;
  taskTitle: string;
  rewardMinutes: number;
  action: 'approve' | 'reject';
} | null;

export function KidBountyBoard({ token }: { token: string }) {
  const screenTime = typeof useOptionalScreenTime === 'function' ? useOptionalScreenTime() : null;
  const [tasks, setTasks] = useState<KidBountyTask[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [rejections, setRejections] = useState<Map<number, BountySubmission>>(() => new Map());
  const [submittingTaskId, setSubmittingTaskId] = useState<number | null>(null);
  const [reviewDialog, setReviewDialog] = useState<ParentReviewDialog>(null);
  const [pin, setPin] = useState('');
  const [reviewBusy, setReviewBusy] = useState(false);
  const [reviewError, setReviewError] = useState('');
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const loadBoard = useCallback(async () => {
    if (!token) return;
    try {
      const board = await fetchKidBountyBoard(token);
      setTasks(sortKidTasks(board.tasks ?? []));
      setRejections(latestRejectionByTask(board.submissions));
      setLoadError('');
    } catch (err: unknown) {
      setLoadError(err instanceof Error && err.message ? err.message : 'Chưa tải được bảng nhiệm vụ của bé.');
    } finally {
      setLoaded(true);
    }
  }, [token]);

  useEffect(() => {
    void loadBoard();
  }, [loadBoard]);

  const handleSubmitTask = async (task: KidBountyTask) => {
    if (submittingTaskId !== null) return;
    setSubmittingTaskId(task.id);
    setFeedback(null);
    try {
      await submitKidBountyTask(token, task.id);
      await loadBoard();
      setFeedback({
        type: 'success',
        text: `Bé đã nộp "${task.title}" rồi! Hãy gọi ba mẹ duyệt để nhận +${task.rewardMinutes} phút nhé.`,
      });
    } catch (err: unknown) {
      setFeedback({
        type: 'error',
        text: err instanceof Error ? err.message : 'Chưa nộp được nhiệm vụ này.',
      });
    } finally {
      setSubmittingTaskId(null);
    }
  };

  const resetReviewDialog = () => {
    setReviewDialog(null);
    setPin('');
    setReviewError('');
  };

  const handleReviewSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!reviewDialog || reviewBusy) return;
    const pinErr = validateParentPin(pin);
    if (pinErr) {
      setReviewError(pinErr);
      return;
    }
    setReviewBusy(true);
    setReviewError('');
    try {
      const result =
        reviewDialog.action === 'approve'
          ? await approveBountySubmission(token, reviewDialog.submissionId, pin)
          : await rejectBountySubmission(token, reviewDialog.submissionId, pin);
      if (reviewDialog.action === 'approve' && result.screenTime) {
        screenTime?.applyScreenTimeStatus(result.screenTime);
        broadcastScreenTimeUpdated(result.screenTime.profileId ?? undefined);
      }
      await loadBoard();
      const successText =
        reviewDialog.action === 'approve'
          ? `Tuyệt vời! Ba mẹ đã duyệt "${reviewDialog.taskTitle}" và cộng +${reviewDialog.rewardMinutes} phút xem phim.`
          : `Ba mẹ chưa duyệt "${reviewDialog.taskTitle}". Bé làm lại rồi nộp nhé!`;
      resetReviewDialog();
      setFeedback({ type: 'success', text: successText });
    } catch (err: unknown) {
      setPin('');
      setReviewError(err instanceof Error ? err.message : 'Mã PIN phụ huynh chưa đúng.');
    } finally {
      setReviewBusy(false);
    }
  };

  const header = (
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h2 className="font-display text-lg font-bold text-ink">Bảng nhiệm vụ đổi giờ xem</h2>
        <p className="text-xs text-muted">
          Làm xong việc ngoan rồi bấm &ldquo;Nộp nhiệm vụ&rdquo; để nhận thêm phút xem phim nhé!
        </p>
      </div>
    </div>
  );
  const boardClassName = 'rounded-2xl border-2 border-ink bg-paper-white p-4 shadow-[3px_3px_0_#203328] sm:p-5';

  if (!loaded) {
    return (
      <div data-testid="kid-bounty-board-loading" className={boardClassName}>
        {header}
        <p role="status" className="mt-3 text-xs text-muted">Đang tải nhiệm vụ…</p>
      </div>
    );
  }

  if (tasks.length === 0) {
    return (
      <div data-testid={loadError ? 'kid-bounty-board-error' : 'kid-bounty-board-empty'} className={boardClassName}>
        {header}
        {loadError ? (
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <p role="alert" className="text-xs font-semibold text-red-900">{loadError}</p>
            <button
              type="button"
              data-testid="kid-bounty-retry"
              onClick={() => void loadBoard()}
              className="btn-secondary min-h-[44px] px-3 text-xs font-bold"
            >
              Thử lại
            </button>
          </div>
        ) : (
          <p className="mt-3 rounded-xl border border-dashed border-ink/30 bg-surface px-3.5 py-3 text-sm text-ink">
            Bé chưa có nhiệm vụ nào. Nhờ ba mẹ giao nhiệm vụ trong phần Gia đình nhé!
          </p>
        )}
      </div>
    );
  }

  return (
    <div
      data-testid="kid-bounty-board"
      data-reveal-item=""
      className={boardClassName}
    >
      {header}

      {loadError && (
        <div role="alert" className="mt-3 flex flex-wrap items-center gap-3 rounded-xl border border-red-800/35 bg-red-50 px-3.5 py-2.5 text-xs text-red-900">
          <span>{loadError}</span>
          <button
            type="button"
            onClick={() => void loadBoard()}
            className="btn-secondary min-h-[44px] px-3 text-xs font-bold"
          >
            Thử lại
          </button>
        </div>
      )}

      {feedback && (
        <div
          role={feedback.type === 'error' ? 'alert' : 'status'}
          data-testid="kid-bounty-feedback"
          className={`motion-toast-enter mt-3 rounded-xl border px-3.5 py-2.5 text-xs font-semibold ${
            feedback.type === 'error'
              ? 'border-red-800/35 bg-red-50 text-red-900'
              : 'border-ink/40 bg-mint/70 text-ink'
          }`}
        >
          {feedback.text}
        </div>
      )}

      <ul className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {tasks.map((task) => {
          const iconMeta = getBountyIconMeta(task.icon);
          const rejection = task.state === 'AVAILABLE' ? rejections.get(task.id) : undefined;
          return (
            <li
              key={task.id}
              data-testid={`kid-bounty-card-${task.id}`}
              className="flex flex-col justify-between gap-3 rounded-xl border-2 border-ink/30 bg-surface p-3.5 transition-transform duration-smooth ease-out-smooth hover:-translate-y-0.5"
            >
              <div className="space-y-1.5">
                <div className="flex items-start justify-between gap-2">
                  <p className="text-sm font-bold text-ink">
                    <span aria-hidden="true">{iconMeta.emoji} </span>
                    {task.title}
                  </p>
                  <span className="shrink-0 rounded-full border border-ink bg-[#f2d18c] px-2 py-0.5 text-[0.6875rem] font-extrabold text-ink">
                    +{task.rewardMinutes} phút
                  </span>
                </div>
                <p className="text-[0.6875rem] text-muted">
                  {task.repeatableDaily ? 'Nhiệm vụ mỗi ngày' : 'Nhiệm vụ đặc biệt'}
                </p>
              </div>

              {rejection && (
                <p
                  data-testid={`kid-bounty-rejected-note-${task.id}`}
                  className="rounded-lg border border-red-800/30 bg-red-50 px-2.5 py-1.5 text-[0.6875rem] font-semibold text-red-900"
                >
                  Lần trước ba mẹ chưa duyệt{rejection.rejectNote ? `: ${rejection.rejectNote}` : ''}. Bé làm lại rồi nộp nhé!
                </p>
              )}

              {task.state === 'AVAILABLE' ? (
                <button
                  type="button"
                  data-testid={`kid-bounty-submit-${task.id}`}
                  disabled={submittingTaskId !== null}
                  onClick={() => void handleSubmitTask(task)}
                  className="btn-primary min-h-[44px] w-full justify-center text-xs font-bold disabled:opacity-50"
                >
                  {submittingTaskId === task.id ? 'Đang nộp…' : 'Nộp nhiệm vụ'}
                </button>
              ) : task.state === 'PENDING' ? (
                <div className="space-y-2">
                  <span
                    data-testid={`kid-bounty-pending-badge-${task.id}`}
                    className="inline-flex w-full justify-center rounded-lg border border-amber-700/40 bg-[#f2d18c]/65 px-2.5 py-1.5 text-xs font-bold text-ink"
                  >
                    Đang chờ ba mẹ duyệt
                  </span>
                  {task.pendingSubmissionId && (
                    <div className="flex gap-2">
                      <button
                        type="button"
                        data-testid={`kid-bounty-parent-approve-${task.pendingSubmissionId}`}
                        onClick={() => {
                          setPin('');
                          setReviewError('');
                          setReviewDialog({
                            submissionId: task.pendingSubmissionId!,
                            taskTitle: task.title,
                            rewardMinutes: task.rewardMinutes,
                            action: 'approve',
                          });
                        }}
                        className="btn-secondary min-h-[44px] flex-1 justify-center text-xs font-bold text-brand-dark"
                      >
                        Ba mẹ duyệt (PIN)
                      </button>
                      <button
                        type="button"
                        data-testid={`kid-bounty-parent-reject-${task.pendingSubmissionId}`}
                        onClick={() => {
                          setPin('');
                          setReviewError('');
                          setReviewDialog({
                            submissionId: task.pendingSubmissionId!,
                            taskTitle: task.title,
                            rewardMinutes: task.rewardMinutes,
                            action: 'reject',
                          });
                        }}
                        className="btn-secondary min-h-[44px] px-3 text-xs text-red-900"
                      >
                        Từ chối
                      </button>
                    </div>
                  )}
                </div>
              ) : (
                <span
                  data-testid={`kid-bounty-completed-badge-${task.id}`}
                  className="motion-correct-pop inline-flex min-h-[44px] w-full items-center justify-center rounded-lg border border-ink/40 bg-mint/70 px-3 text-xs font-bold text-ink"
                >
                  {task.state === 'COMPLETED_TODAY' ? 'Xong hôm nay ✓ Mai làm tiếp nhé!' : 'Đã hoàn thành ✓'}
                </span>
              )}
            </li>
          );
        })}
      </ul>

      {reviewDialog && (
        <SiteDialog
          title={
            reviewDialog.action === 'approve'
              ? 'Ba mẹ duyệt nhiệm vụ'
              : 'Ba mẹ từ chối lượt nộp'
          }
          tone={reviewDialog.action === 'reject' ? 'danger' : 'default'}
          onClose={() => {
            if (!reviewBusy) resetReviewDialog();
          }}
          busy={reviewBusy}
        >
          <form onSubmit={handleReviewSubmit} className="space-y-4">
            <p className="text-xs leading-relaxed text-muted">
              {reviewDialog.action === 'approve' ? (
                <>
                  Nhập mã PIN phụ huynh để duyệt <strong>{reviewDialog.taskTitle}</strong> và cộng{' '}
                  <strong className="text-ink">+{reviewDialog.rewardMinutes} phút</strong> cho bé ngay bây giờ.
                </>
              ) : (
                <>
                  Nhập mã PIN phụ huynh để từ chối lượt nộp <strong>{reviewDialog.taskTitle}</strong>.
                </>
              )}
            </p>
            <div>
              <label htmlFor="kid-bounty-review-pin" className="mb-1.5 block text-xs font-semibold text-ink">
                Mã PIN phụ huynh
              </label>
              <input
                id="kid-bounty-review-pin"
                data-testid="kid-bounty-review-pin-input"
                type="password"
                inputMode="numeric"
                pattern="[0-9]*"
                enterKeyHint="done"
                autoComplete="off"
                autoFocus
                maxLength={6}
                required
                disabled={reviewBusy}
                value={pin}
                onChange={(event) => setPin(event.target.value.replace(/[^0-9]/g, '').slice(0, 6))}
                placeholder="••••"
                className="min-h-[44px] w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-base sm:text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
              />
            </div>
            {reviewError && (
              <div role="alert" className="rounded-lg border border-red-800/35 bg-red-50 px-3.5 py-2.5 text-xs text-red-900">
                {reviewError}
              </div>
            )}
            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <button
                type="button"
                disabled={reviewBusy}
                onClick={resetReviewDialog}
                className="btn-secondary w-full sm:w-auto text-xs"
              >
                Hủy
              </button>
              <button
                type="submit"
                data-testid="kid-bounty-review-submit"
                disabled={reviewBusy}
                className="btn-primary w-full sm:w-auto text-xs"
              >
                {reviewBusy
                  ? 'Đang xử lý…'
                  : reviewDialog.action === 'approve'
                  ? `Duyệt (+${reviewDialog.rewardMinutes} phút)`
                  : 'Xác nhận từ chối'}
              </button>
            </div>
          </form>
        </SiteDialog>
      )}
    </div>
  );
}
