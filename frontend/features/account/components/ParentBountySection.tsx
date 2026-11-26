import React, { useCallback, useEffect, useState } from 'react';
import { SiteDialog } from '@/components/ui/SiteDialog';
import { ChildAvatarBadge } from '@/features/profiles/components/ChildAvatarBadge';
import { validateParentPin, type ChildProfile } from '@/lib/services/profileService';
import { broadcastScreenTimeUpdated } from '@/lib/services/screenTimeService';
import {
  BOUNTY_ICON_OPTIONS,
  approveBountySubmission,
  createBountyTask,
  deleteBountyTask,
  fetchParentBountyBoard,
  getBountyIconMeta,
  rejectBountySubmission,
  updateBountyTask,
  validateBountyTaskInput,
  type BountySubmission,
  type BountyTask,
} from '@/lib/services/bountyService';

const REWARD_PRESETS = [10, 15, 30, 45] as const;

type BountyDialog =
  | { kind: 'create' }
  | { kind: 'edit'; task: BountyTask }
  | { kind: 'delete'; task: BountyTask }
  | { kind: 'approve'; submission: BountySubmission }
  | { kind: 'reject'; submission: BountySubmission }
  | null;

export function ParentBountySection({
  token,
  childProfiles,
  onScreenTimeUpdated,
}: {
  token: string;
  childProfiles: ChildProfile[];
  onScreenTimeUpdated?: () => void;
}) {
  const [tasks, setTasks] = useState<BountyTask[]>([]);
  const [submissions, setSubmissions] = useState<BountySubmission[]>([]);
  const [dialog, setDialog] = useState<BountyDialog>(null);
  const [title, setTitle] = useState('');
  const [rewardMinutes, setRewardMinutes] = useState('15');
  const [icon, setIcon] = useState('toys');
  const [active, setActive] = useState(true);
  const [repeatableDaily, setRepeatableDaily] = useState(true);
  const [selectedProfileIds, setSelectedProfileIds] = useState<number[]>([]);
  const [pin, setPin] = useState('');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState('');
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const loadBoard = useCallback(async () => {
    if (!token) return;
    try {
      const board = await fetchParentBountyBoard(token);
      setTasks(board.tasks);
      setSubmissions(board.submissions);
    } catch {}
  }, [token]);

  useEffect(() => {
    void loadBoard();
  }, [loadBoard, childProfiles.length]);

  const resetDialog = () => {
    setDialog(null);
    setPin('');
    setNote('');
    setDialogError('');
  };

  const closeDialog = () => {
    if (busy) return;
    resetDialog();
  };

  const openDialog = (next: BountyDialog) => {
    setFeedback(null);
    setDialogError('');
    setPin('');
    setNote('');
    if (next?.kind === 'create') {
      setTitle('');
      setRewardMinutes('15');
      setIcon('toys');
      setActive(true);
      setRepeatableDaily(true);
      setSelectedProfileIds(childProfiles.map((profile) => profile.id));
    } else if (next?.kind === 'edit') {
      setTitle(next.task.title);
      setRewardMinutes(String(next.task.rewardMinutes));
      setIcon(next.task.icon || 'star');
      setActive(next.task.active);
      setRepeatableDaily(next.task.repeatableDaily);
      setSelectedProfileIds(next.task.profileIds);
    }
    setDialog(next);
  };

  const toggleProfile = (profileId: number) => {
    setSelectedProfileIds((prev) =>
      prev.includes(profileId) ? prev.filter((id) => id !== profileId) : [...prev, profileId]
    );
  };

  const sanitizePin = (value: string) => value.replace(/[^0-9]/g, '').slice(0, 6);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!dialog || busy) return;
    setDialogError('');

    const pinErr = validateParentPin(pin);
    if (pinErr) {
      setDialogError(pinErr);
      return;
    }

    if (dialog.kind === 'create' || dialog.kind === 'edit') {
      const parsedReward = rewardMinutes.trim() === '' ? NaN : Number(rewardMinutes);
      const taskErr = validateBountyTaskInput({
        title,
        rewardMinutes: parsedReward,
        profileIds: selectedProfileIds,
      });
      if (taskErr) {
        setDialogError(taskErr);
        return;
      }
      setBusy(true);
      try {
        const payload = {
          pin,
          title: title.trim(),
          rewardMinutes: parsedReward,
          icon,
          active,
          repeatableDaily,
          profileIds: selectedProfileIds,
        };
        if (dialog.kind === 'create') {
          await createBountyTask(token, payload);
        } else {
          await updateBountyTask(token, dialog.task.id, payload);
        }
        await loadBoard();
        const msg = dialog.kind === 'create' ? 'Đã tạo nhiệm vụ mới cho bé.' : 'Đã cập nhật nhiệm vụ.';
        resetDialog();
        setFeedback({ type: 'success', text: msg });
      } catch (err: unknown) {
        setPin('');
        setDialogError(err instanceof Error ? err.message : 'Chưa lưu được nhiệm vụ.');
      } finally {
        setBusy(false);
      }
      return;
    }

    if (dialog.kind === 'delete') {
      setBusy(true);
      try {
        await deleteBountyTask(token, dialog.task.id, pin);
        await loadBoard();
        const removedTitle = dialog.task.title;
        resetDialog();
        setFeedback({ type: 'success', text: `Đã xóa nhiệm vụ "${removedTitle}".` });
      } catch (err: unknown) {
        setPin('');
        setDialogError(err instanceof Error ? err.message : 'Chưa xóa được nhiệm vụ.');
      } finally {
        setBusy(false);
      }
      return;
    }

    if (dialog.kind === 'approve' || dialog.kind === 'reject') {
      setBusy(true);
      try {
        const result =
          dialog.kind === 'approve'
            ? await approveBountySubmission(token, dialog.submission.id, pin, note.trim() || undefined)
            : await rejectBountySubmission(token, dialog.submission.id, pin, note.trim() || undefined);
        await loadBoard();
        if (dialog.kind === 'approve') {
          broadcastScreenTimeUpdated(dialog.submission.profileId);
          onScreenTimeUpdated?.();
        }
        resetDialog();
        setFeedback({
          type: 'success',
          text:
            result.message ||
            (dialog.kind === 'approve'
              ? `Đã duyệt nhiệm vụ và cộng +${dialog.submission.rewardMinutes} phút cho ${dialog.submission.profileName}.`
              : `Đã từ chối lượt nộp của ${dialog.submission.profileName}.`),
        });
      } catch (err: unknown) {
        setPin('');
        setDialogError(err instanceof Error ? err.message : 'Chưa xử lý được lượt nộp nhiệm vụ.');
      } finally {
        setBusy(false);
      }
    }
  };

  const pendingSubmissions = submissions.filter((item) => item.status === 'PENDING');
  const profileNameById = new Map(childProfiles.map((profile) => [profile.id, profile.name]));

  return (
    <section
      aria-labelledby="parent-bounty-heading"
      data-testid="parent-bounty-section"
      className="mt-6 rounded-xl border border-surface-border bg-paper-white p-5 sm:p-6"
    >
      <div className="flex flex-col gap-3 border-b border-surface-border pb-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h3 id="parent-bounty-heading" className="text-base font-bold text-ink">
              Bảng nhiệm vụ đời thực
            </h3>
            {pendingSubmissions.length > 0 && (
              <span
                data-testid="parent-bounty-pending-count"
                className="rounded-full border border-ink bg-[#f2d18c] px-2.5 py-0.5 text-[11px] font-bold text-ink"
              >
                {pendingSubmissions.length} lượt chờ duyệt
              </span>
            )}
          </div>
          <p className="mt-1 text-xs text-muted">
            Giao việc nhà hoặc thói quen tốt để bé hoàn thành, nộp nhiệm vụ và nhận thêm phút xem phim khi ba mẹ duyệt bằng mã PIN.
          </p>
        </div>
        <button
          type="button"
          data-testid="add-bounty-task-button"
          onClick={() => openDialog({ kind: 'create' })}
          className="btn-primary min-h-[44px] shrink-0 text-xs font-bold"
        >
          Thêm nhiệm vụ
        </button>
      </div>

      {feedback && (
        <div
          role={feedback.type === 'error' ? 'alert' : 'status'}
          data-testid="parent-bounty-feedback"
          className={`mt-4 rounded-xl border p-3 text-xs ${
            feedback.type === 'error'
              ? 'border-red-500/40 bg-red-500/10 text-red-900'
              : 'border-brand/50 bg-brand/15 text-ink'
          }`}
        >
          {feedback.text}
        </div>
      )}

      {pendingSubmissions.length > 0 && (
        <div className="mt-4 space-y-2.5" data-testid="parent-bounty-pending-list">
          <h4 className="text-xs font-bold text-ink">Đang chờ ba mẹ duyệt</h4>
          <ul className="space-y-2">
            {pendingSubmissions.map((submission) => {
              const iconMeta = getBountyIconMeta(submission.taskIcon);
              return (
                <li
                  key={submission.id}
                  data-testid={`parent-bounty-submission-${submission.id}`}
                  className="flex flex-col gap-3 rounded-xl border-2 border-ink/40 bg-[#f2d18c]/25 p-3.5 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <ChildAvatarBadge avatarKey={submission.profileAvatarKey} size="sm" />
                    <div className="min-w-0">
                      <p className="text-xs font-bold text-ink">
                        <span aria-hidden="true">{iconMeta.emoji} </span>
                        {submission.taskTitle}
                      </p>
                      <p className="text-[11px] text-muted">
                        {submission.profileName} vừa nộp · Thưởng <strong className="text-ink">+{submission.rewardMinutes} phút</strong>
                      </p>
                    </div>
                  </div>
                  <div className="flex flex-wrap items-center gap-2 shrink-0">
                    <button
                      type="button"
                      data-testid={`approve-bounty-submission-${submission.id}`}
                      onClick={() => openDialog({ kind: 'approve', submission })}
                      className="btn-primary min-h-[44px] px-3 text-xs font-bold"
                    >
                      Duyệt (+{submission.rewardMinutes} phút)
                    </button>
                    <button
                      type="button"
                      data-testid={`reject-bounty-submission-${submission.id}`}
                      onClick={() => openDialog({ kind: 'reject', submission })}
                      className="btn-secondary min-h-[44px] px-3 text-xs text-red-900"
                    >
                      Từ chối
                    </button>
                  </div>
                </li>
              );
            })}
          </ul>
        </div>
      )}

      <div className="mt-4">
        {tasks.length === 0 ? (
          <p className="rounded-xl border border-dashed border-surface-border bg-surface-muted p-4 text-center text-xs text-muted">
            Chưa có nhiệm vụ nào. Nhấn &ldquo;Thêm nhiệm vụ&rdquo; để tạo bảng nhiệm vụ cho bé nhé.
          </p>
        ) : (
          <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {tasks.map((task) => {
              const iconMeta = getBountyIconMeta(task.icon);
              const assignedNames = task.profileIds
                .map((id) => profileNameById.get(id))
                .filter((name): name is string => Boolean(name));
              return (
                <li
                  key={task.id}
                  data-testid={`parent-bounty-task-${task.id}`}
                  className="flex flex-col justify-between gap-3 rounded-xl border border-surface-border bg-surface p-3.5"
                >
                  <div className="space-y-1.5">
                    <div className="flex items-start justify-between gap-2">
                      <p className="text-sm font-bold text-ink">
                        <span aria-hidden="true">{iconMeta.emoji} </span>
                        {task.title}
                      </p>
                      <span className="shrink-0 rounded-full border border-ink/30 bg-mint px-2 py-0.5 text-[11px] font-bold text-ink">
                        +{task.rewardMinutes} phút
                      </span>
                    </div>
                    <div className="flex flex-wrap gap-1.5 text-[11px]">
                      <span className="rounded bg-surface-elevated px-2 py-0.5 font-medium text-ink">
                        {task.repeatableDaily ? 'Lặp mỗi ngày' : 'Làm một lần'}
                      </span>
                      <span
                        className={`rounded px-2 py-0.5 font-medium ${
                          task.active ? 'bg-mint/60 text-ink' : 'bg-surface-elevated text-muted'
                        }`}
                      >
                        {task.active ? 'Đang mở' : 'Tạm ẩn'}
                      </span>
                    </div>
                    {assignedNames.length > 0 && (
                      <p className="text-[11px] text-muted">Áp dụng cho: {assignedNames.join(', ')}</p>
                    )}
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <button
                      type="button"
                      data-testid={`edit-bounty-task-${task.id}`}
                      onClick={() => openDialog({ kind: 'edit', task })}
                      className="btn-secondary min-h-[44px] flex-1 justify-center text-xs"
                    >
                      Sửa
                    </button>
                    <button
                      type="button"
                      data-testid={`delete-bounty-task-${task.id}`}
                      onClick={() => openDialog({ kind: 'delete', task })}
                      className="btn-secondary min-h-[44px] px-3 text-xs text-red-900"
                    >
                      Xóa
                    </button>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      {dialog && (
        <SiteDialog
          title={
            dialog.kind === 'create'
              ? 'Thêm nhiệm vụ đời thực'
              : dialog.kind === 'edit'
              ? 'Sửa nhiệm vụ đời thực'
              : dialog.kind === 'delete'
              ? 'Xóa nhiệm vụ'
              : dialog.kind === 'approve'
              ? 'Duyệt nhiệm vụ cho bé'
              : 'Từ chối lượt nộp'
          }
          tone={dialog.kind === 'delete' || dialog.kind === 'reject' ? 'danger' : 'default'}
          onClose={closeDialog}
          busy={busy}
        >
          <form onSubmit={handleSubmit} className="space-y-4">
            {(dialog.kind === 'create' || dialog.kind === 'edit') && (
              <>
                <div>
                  <label htmlFor="bounty-task-title" className="mb-1.5 block text-xs font-semibold text-ink">
                    Tên nhiệm vụ
                  </label>
                  <input
                    id="bounty-task-title"
                    data-testid="bounty-title-input"
                    type="text"
                    maxLength={100}
                    required
                    disabled={busy}
                    value={title}
                    onChange={(event) => setTitle(event.target.value)}
                    placeholder="Ví dụ: Dọn đồ chơi gọn gàng"
                    className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                  />
                </div>

                <div>
                  <label htmlFor="bounty-task-reward" className="mb-1.5 block text-xs font-semibold text-ink">
                    Phút thưởng (1–120 phút)
                  </label>
                  <div className="mb-2 flex flex-wrap gap-1.5">
                    {REWARD_PRESETS.map((preset) => (
                      <button
                        key={preset}
                        type="button"
                        disabled={busy}
                        onClick={() => setRewardMinutes(String(preset))}
                        className={`min-h-[38px] rounded-lg border px-2.5 text-xs font-bold cursor-pointer ${
                          Number(rewardMinutes) === preset
                            ? 'border-ink bg-mint text-ink'
                            : 'border-surface-border bg-paper-white text-muted'
                        }`}
                      >
                        +{preset} phút
                      </button>
                    ))}
                  </div>
                  <input
                    id="bounty-task-reward"
                    data-testid="bounty-reward-input"
                    type="number"
                    min={1}
                    max={120}
                    required
                    disabled={busy}
                    value={rewardMinutes}
                    onChange={(event) => setRewardMinutes(event.target.value)}
                    className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                  />
                </div>

                <fieldset>
                  <legend className="mb-2 text-xs font-semibold text-ink">Biểu tượng</legend>
                  <div className="grid grid-cols-4 gap-2">
                    {BOUNTY_ICON_OPTIONS.map((option) => (
                      <button
                        key={option.key}
                        type="button"
                        disabled={busy}
                        aria-pressed={icon === option.key}
                        onClick={() => setIcon(option.key)}
                        className={`flex min-h-[54px] flex-col items-center justify-center rounded-lg border-2 p-1.5 text-[11px] font-semibold cursor-pointer ${
                          icon === option.key
                            ? 'border-ink bg-mint/60 text-ink'
                            : 'border-surface-border bg-paper-white text-muted'
                        }`}
                      >
                        <span className="text-base" aria-hidden="true">{option.emoji}</span>
                        <span className="truncate max-w-full">{option.label}</span>
                      </button>
                    ))}
                  </div>
                </fieldset>

                <div className="flex flex-col gap-2">
                  <label className="inline-flex min-h-[36px] items-center gap-2 text-xs font-semibold text-ink cursor-pointer">
                    <input
                      type="checkbox"
                      data-testid="bounty-repeatable-checkbox"
                      disabled={busy}
                      checked={repeatableDaily}
                      onChange={(event) => setRepeatableDaily(event.target.checked)}
                      className="h-4 w-4 accent-brand"
                    />
                    Lặp lại mỗi ngày (bỏ chọn nếu chỉ làm một lần)
                  </label>
                  <label className="inline-flex min-h-[36px] items-center gap-2 text-xs font-semibold text-ink cursor-pointer">
                    <input
                      type="checkbox"
                      data-testid="bounty-active-checkbox"
                      disabled={busy}
                      checked={active}
                      onChange={(event) => setActive(event.target.checked)}
                      className="h-4 w-4 accent-brand"
                    />
                    Hiển thị nhiệm vụ cho bé ngay
                  </label>
                </div>

                <fieldset>
                  <legend className="mb-2 text-xs font-semibold text-ink">Bé nào nhìn thấy nhiệm vụ này?</legend>
                  <div className="flex flex-wrap gap-2">
                    {childProfiles.map((profile) => {
                      const checked = selectedProfileIds.includes(profile.id);
                      return (
                        <label
                          key={profile.id}
                          className={`inline-flex min-h-[44px] items-center gap-2 rounded-lg border px-3 py-1.5 text-xs font-semibold cursor-pointer ${
                            checked ? 'border-ink bg-mint/60 text-ink' : 'border-surface-border bg-paper-white text-muted'
                          }`}
                        >
                          <input
                            type="checkbox"
                            data-testid={`bounty-profile-checkbox-${profile.id}`}
                            disabled={busy}
                            checked={checked}
                            onChange={() => toggleProfile(profile.id)}
                            className="h-4 w-4 accent-brand"
                          />
                          {profile.name}
                        </label>
                      );
                    })}
                  </div>
                </fieldset>
              </>
            )}

            {dialog.kind === 'delete' && (
              <p className="text-xs leading-relaxed text-muted">
                Xóa nhiệm vụ <strong>{dialog.task.title}</strong>? Các lượt chờ duyệt của nhiệm vụ này cũng sẽ bị xóa.
              </p>
            )}

            {(dialog.kind === 'approve' || dialog.kind === 'reject') && (
              <>
                <p className="text-xs leading-relaxed text-muted">
                  {dialog.kind === 'approve' ? (
                    <>
                      Duyệt nhiệm vụ <strong>{dialog.submission.taskTitle}</strong> của{' '}
                      <strong>{dialog.submission.profileName}</strong> và cộng thêm{' '}
                      <strong className="text-ink">+{dialog.submission.rewardMinutes} phút</strong> hôm nay?
                    </>
                  ) : (
                    <>
                      Từ chối lượt nộp <strong>{dialog.submission.taskTitle}</strong> của{' '}
                      <strong>{dialog.submission.profileName}</strong>? Bé vẫn có thể nộp lại sau khi làm xong.
                    </>
                  )}
                </p>
                {dialog.kind === 'reject' && (
                  <div>
                    <label htmlFor="bounty-reject-note" className="mb-1.5 block text-xs font-semibold text-ink">
                      Lời nhắn cho bé (không bắt buộc)
                    </label>
                    <input
                      id="bounty-reject-note"
                      data-testid="bounty-reject-note-input"
                      type="text"
                      maxLength={200}
                      disabled={busy}
                      value={note}
                      onChange={(event) => setNote(event.target.value)}
                      placeholder="Ví dụ: Cất thêm hộp bút màu nữa nhé"
                      className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                    />
                  </div>
                )}
              </>
            )}

            <div>
              <label htmlFor="bounty-dialog-pin" className="mb-1.5 block text-xs font-semibold text-ink">
                Mã PIN phụ huynh
              </label>
              <input
                id="bounty-dialog-pin"
                data-testid="bounty-pin-input"
                type="password"
                inputMode="numeric"
                autoComplete="off"
                maxLength={6}
                required
                disabled={busy}
                value={pin}
                onChange={(event) => setPin(sanitizePin(event.target.value))}
                placeholder="••••"
                className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
              />
            </div>

            {dialogError && (
              <div role="alert" className="rounded-lg border border-red-800/35 bg-red-50 px-3.5 py-2.5 text-xs text-red-900">
                {dialogError}
              </div>
            )}

            <div className="flex flex-col-reverse gap-2 pt-1 sm:flex-row sm:justify-end">
              <button type="button" disabled={busy} onClick={closeDialog} className="btn-secondary w-full sm:w-auto text-xs">
                Hủy
              </button>
              <button type="submit" disabled={busy} className="btn-primary w-full sm:w-auto text-xs">
                {busy
                  ? 'Đang xử lý…'
                  : dialog.kind === 'create'
                  ? 'Tạo nhiệm vụ'
                  : dialog.kind === 'edit'
                  ? 'Lưu nhiệm vụ'
                  : dialog.kind === 'delete'
                  ? 'Xóa nhiệm vụ'
                  : dialog.kind === 'approve'
                  ? 'Xác nhận duyệt'
                  : 'Xác nhận từ chối'}
              </button>
            </div>
          </form>
        </SiteDialog>
      )}
    </section>
  );
}
