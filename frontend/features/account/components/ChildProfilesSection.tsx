import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { SiteDialog } from '@/components/ui/SiteDialog';
import { useAuth } from '@/lib/context/AuthContext';
import { ChildAvatarBadge } from '@/features/profiles/components/ChildAvatarBadge';
import {
  CHILD_AVATARS,
  MAX_CHILD_PROFILES,
  changeParentPin,
  createChildProfile,
  deleteChildProfile,
  disableParentMode,
  enableParentMode,
  updateChildProfile,
  validateChildProfileName,
  validateParentPin,
  type ChildProfile,
} from '@/lib/services/profileService';
import {
  MATH_LEVEL_OPTIONS,
  fetchParentScreenTimeOverview,
  formatRemainingTime,
  formatUsageDuration,
  grantChildExtraMinutes,
  updateChildScreenTimeSettings,
  validateGrantMinutes,
  validateScreenTimeSettings,
  type ChildProfileScreenTime,
} from '@/lib/services/screenTimeService';

type DialogMode =
  | { kind: 'enable' }
  | { kind: 'change-pin' }
  | { kind: 'disable' }
  | { kind: 'create' }
  | { kind: 'edit'; profile: ChildProfile }
  | { kind: 'delete'; profile: ChildProfile }
  | { kind: 'screen-time'; profile: ChildProfile }
  | { kind: 'grant'; profile: ChildProfile }
  | null;

const GRANT_PRESETS = [10, 15, 30] as const;

export function ChildProfilesSection() {
  const {
    token,
    parentModeEnabled,
    childProfiles,
    refreshProfiles,
    selectChildProfile,
  } = useAuth();
  const navigate = useNavigate();

  const [dialog, setDialog] = useState<DialogMode>(null);
  const [pin, setPin] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [currentPin, setCurrentPin] = useState('');
  const [childName, setChildName] = useState('');
  const [avatarKey, setAvatarKey] = useState('bear');
  const [dailyMinutes, setDailyMinutes] = useState('60');
  const [mathLevel, setMathLevel] = useState(1);
  const [rewardMinutes, setRewardMinutes] = useState('5');
  const [maxBonusMinutes, setMaxBonusMinutes] = useState('30');
  const [grantMinutes, setGrantMinutes] = useState('15');
  const [screenTimeByProfile, setScreenTimeByProfile] = useState<Record<number, ChildProfileScreenTime>>({});
  const [busy, setBusy] = useState(false);
  const [switchingId, setSwitchingId] = useState<number | null>(null);
  const [dialogError, setDialogError] = useState('');
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const loadScreenTimeOverview = useCallback(async () => {
    if (!token || !parentModeEnabled) {
      setScreenTimeByProfile({});
      return;
    }
    try {
      const items = await fetchParentScreenTimeOverview(token);
      const next: Record<number, ChildProfileScreenTime> = {};
      for (const item of items) {
        next[item.profileId] = item;
      }
      setScreenTimeByProfile(next);
    } catch {}
  }, [token, parentModeEnabled]);

  useEffect(() => {
    if (token) {
      void refreshProfiles();
    }
  }, [token, refreshProfiles]);

  useEffect(() => {
    void loadScreenTimeOverview();
  }, [loadScreenTimeOverview, childProfiles.length]);

  const closeDialog = () => {
    if (busy) return;
    setDialog(null);
    setPin('');
    setConfirmPin('');
    setCurrentPin('');
    setChildName('');
    setAvatarKey('bear');
    setDialogError('');
  };

  const openDialog = (next: DialogMode) => {
    setFeedback(null);
    setDialogError('');
    setPin('');
    setConfirmPin('');
    setCurrentPin('');
    if (next?.kind === 'edit') {
      setChildName(next.profile.name);
      setAvatarKey(next.profile.avatarKey);
    } else if (next?.kind === 'screen-time') {
      const summary = screenTimeByProfile[next.profile.id]?.status;
      setDailyMinutes(String(summary?.dailyMinutes ?? next.profile.dailyMinutes ?? 60));
      setMathLevel(summary?.mathLevel ?? next.profile.mathLevel ?? 1);
      setRewardMinutes(String(summary?.rewardMinutes ?? next.profile.rewardMinutes ?? 5));
      setMaxBonusMinutes(String(summary?.maxBonusMinutes ?? next.profile.maxBonusMinutes ?? 30));
    } else if (next?.kind === 'grant') {
      setGrantMinutes('15');
    } else {
      setChildName('');
      setAvatarKey('bear');
    }
    setDialog(next);
  };

  const sanitizePin = (value: string) => value.replace(/[^0-9]/g, '').slice(0, 6);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!token || !dialog || busy) return;
    setDialogError('');

    if (dialog.kind === 'enable') {
      const pinErr = validateParentPin(pin);
      if (pinErr) {
        setDialogError(pinErr);
        return;
      }
      if (pin !== confirmPin) {
        setDialogError('Mã PIN nhập lại chưa khớp.');
        return;
      }
      setBusy(true);
      try {
        await enableParentMode(token, pin);
        await refreshProfiles();
        closeDialog();
        setFeedback({ type: 'success', text: 'Đã bật chế độ hồ sơ trẻ em.' });
      } catch (err: unknown) {
        setDialogError(err instanceof Error ? err.message : 'Chưa bật được chế độ hồ sơ trẻ em.');
      } finally {
        setBusy(false);
      }
      return;
    }

    if (dialog.kind === 'change-pin') {
      const curErr = validateParentPin(currentPin);
      if (curErr) {
        setDialogError(curErr);
        return;
      }
      const nextErr = validateParentPin(pin);
      if (nextErr) {
        setDialogError(nextErr);
        return;
      }
      if (pin !== confirmPin) {
        setDialogError('Mã PIN mới nhập lại chưa khớp.');
        return;
      }
      if (pin === currentPin) {
        setDialogError('Mã PIN mới cần khác mã PIN hiện tại.');
        return;
      }
      setBusy(true);
      try {
        await changeParentPin(token, currentPin, pin);
        closeDialog();
        setFeedback({ type: 'success', text: 'Đã đổi mã PIN phụ huynh.' });
      } catch (err: unknown) {
        setCurrentPin('');
        setDialogError(err instanceof Error ? err.message : 'Chưa đổi được mã PIN phụ huynh.');
      } finally {
        setBusy(false);
      }
      return;
    }

    if (dialog.kind === 'disable') {
      const pinErr = validateParentPin(pin);
      if (pinErr) {
        setDialogError(pinErr);
        return;
      }
      setBusy(true);
      try {
        await disableParentMode(token, pin);
        await refreshProfiles();
        closeDialog();
        setFeedback({ type: 'success', text: 'Đã tắt chế độ hồ sơ trẻ em. Hồ sơ của các bé vẫn được giữ lại.' });
      } catch (err: unknown) {
        setPin('');
        setDialogError(err instanceof Error ? err.message : 'Chưa tắt được chế độ hồ sơ trẻ em.');
      } finally {
        setBusy(false);
      }
      return;
    }

    if (dialog.kind === 'create' || dialog.kind === 'edit') {
      const nameErr = validateChildProfileName(childName);
      if (nameErr) {
        setDialogError(nameErr);
        return;
      }
      const pinErr = validateParentPin(pin);
      if (pinErr) {
        setDialogError(pinErr);
        return;
      }
      setBusy(true);
      try {
        if (dialog.kind === 'create') {
          await createChildProfile(token, { pin, name: childName.trim(), avatarKey });
        } else {
          await updateChildProfile(token, dialog.profile.id, { pin, name: childName.trim(), avatarKey });
        }
        await refreshProfiles();
        await loadScreenTimeOverview();
        const message = dialog.kind === 'create' ? 'Đã tạo hồ sơ trẻ em.' : 'Đã cập nhật hồ sơ trẻ em.';
        closeDialog();
        setFeedback({ type: 'success', text: message });
      } catch (err: unknown) {
        setPin('');
        setDialogError(err instanceof Error ? err.message : 'Chưa lưu được hồ sơ trẻ em.');
      } finally {
        setBusy(false);
      }
      return;
    }

    if (dialog.kind === 'screen-time') {
      const parsedDaily = Number(dailyMinutes);
      const parsedReward = Number(rewardMinutes);
      const parsedMaxBonus = Number(maxBonusMinutes);
      const settingsErr = validateScreenTimeSettings({
        dailyMinutes: parsedDaily,
        mathLevel,
        rewardMinutes: parsedReward,
        maxBonusMinutes: parsedMaxBonus,
      });
      if (settingsErr) {
        setDialogError(settingsErr);
        return;
      }
      const pinErr = validateParentPin(pin);
      if (pinErr) {
        setDialogError(pinErr);
        return;
      }
      setBusy(true);
      try {
        const updated = await updateChildScreenTimeSettings(token, dialog.profile.id, {
          pin,
          dailyMinutes: parsedDaily,
          mathLevel,
          rewardMinutes: parsedReward,
          maxBonusMinutes: parsedMaxBonus,
        });
        setScreenTimeByProfile((prev) => ({ ...prev, [updated.profileId]: updated }));
        await refreshProfiles();
        const targetName = dialog.profile.name;
        closeDialog();
        setFeedback({ type: 'success', text: `Đã lưu cài đặt thời gian xem cho ${targetName}.` });
      } catch (err: unknown) {
        setPin('');
        setDialogError(err instanceof Error ? err.message : 'Chưa lưu được cài đặt thời gian xem.');
      } finally {
        setBusy(false);
      }
      return;
    }

    if (dialog.kind === 'grant') {
      const parsedGrant = Number(grantMinutes);
      const grantErr = validateGrantMinutes(parsedGrant);
      if (grantErr) {
        setDialogError(grantErr);
        return;
      }
      const pinErr = validateParentPin(pin);
      if (pinErr) {
        setDialogError(pinErr);
        return;
      }
      setBusy(true);
      try {
        const updated = await grantChildExtraMinutes(token, dialog.profile.id, pin, parsedGrant);
        setScreenTimeByProfile((prev) => ({ ...prev, [updated.profileId]: updated }));
        const targetName = dialog.profile.name;
        closeDialog();
        setFeedback({ type: 'success', text: `Đã cộng thêm ${parsedGrant} phút xem phim cho ${targetName}.` });
      } catch (err: unknown) {
        setPin('');
        setDialogError(err instanceof Error ? err.message : 'Chưa cộng thêm phút cho bé được.');
      } finally {
        setBusy(false);
      }
      return;
    }

    if (dialog.kind === 'delete') {
      const pinErr = validateParentPin(pin);
      if (pinErr) {
        setDialogError(pinErr);
        return;
      }
      setBusy(true);
      try {
        await deleteChildProfile(token, dialog.profile.id, pin);
        await refreshProfiles();
        await loadScreenTimeOverview();
        const removedName = dialog.profile.name;
        closeDialog();
        setFeedback({ type: 'success', text: `Đã xóa hồ sơ ${removedName}.` });
      } catch (err: unknown) {
        setPin('');
        setDialogError(err instanceof Error ? err.message : 'Chưa xóa được hồ sơ trẻ em.');
      } finally {
        setBusy(false);
      }
    }
  };

  const handleSwitchChild = async (profileId: number) => {
    if (switchingId !== null) return;
    setSwitchingId(profileId);
    setFeedback(null);
    try {
      const result = await selectChildProfile(profileId);
      if (result.success) {
        navigate('/', { replace: true });
      } else {
        setFeedback({ type: 'error', text: result.message || 'Chưa chuyển được sang hồ sơ trẻ em.' });
      }
    } finally {
      setSwitchingId(null);
    }
  };

  const reachedLimit = childProfiles.length >= MAX_CHILD_PROFILES;

  return (
    <section
      aria-labelledby="child-profiles-heading"
      data-testid="child-profiles-section"
      className="rounded-xl border border-surface-border bg-surface p-6 sm:p-8"
    >
      <div className="flex flex-col gap-4 border-b border-surface-border pb-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h2 id="child-profiles-heading" className="text-lg font-bold text-ink">
              Hồ sơ trẻ em
            </h2>
            <span
              className={`rounded-full border px-2.5 py-0.5 text-[11px] font-bold ${
                parentModeEnabled
                  ? 'border-ink bg-mint text-ink'
                  : 'border-surface-border bg-surface-elevated text-muted'
              }`}
            >
              {parentModeEnabled ? `Đang bật (${childProfiles.length}/${MAX_CHILD_PROFILES})` : 'Chưa bật'}
            </span>
          </div>
          <p className="mt-1 text-xs leading-relaxed text-muted">
            Tạo không gian xem riêng cho từng bé với lịch sử xem, yêu thích tách biệt, ẩn liên kết YouTube ngoài và khóa cài đặt bằng mã PIN phụ huynh.
          </p>
        </div>

        {!parentModeEnabled ? (
          <button
            type="button"
            data-testid="enable-parent-mode-button"
            onClick={() => openDialog({ kind: 'enable' })}
            className="btn-primary min-h-[44px] shrink-0 text-xs font-bold"
          >
            Bật hồ sơ trẻ em
          </button>
        ) : (
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              data-testid="add-child-profile-button"
              disabled={reachedLimit}
              onClick={() => openDialog({ kind: 'create' })}
              className="btn-primary min-h-[44px] text-xs font-bold disabled:opacity-50"
            >
              Thêm hồ sơ bé
            </button>
            <button
              type="button"
              data-testid="change-parent-pin-button"
              onClick={() => openDialog({ kind: 'change-pin' })}
              className="btn-secondary min-h-[44px] text-xs"
            >
              Đổi mã PIN
            </button>
            <button
              type="button"
              data-testid="disable-parent-mode-button"
              onClick={() => openDialog({ kind: 'disable' })}
              className="btn-secondary min-h-[44px] text-xs text-red-900"
            >
              Tắt chế độ
            </button>
          </div>
        )}
      </div>

      {feedback && (
        <div
          role={feedback.type === 'error' ? 'alert' : 'status'}
          className={`mt-4 rounded-xl border p-3 text-xs leading-relaxed ${
            feedback.type === 'error'
              ? 'border-red-500/40 bg-red-500/10 text-red-900'
              : 'border-brand/50 bg-brand/15 text-ink'
          }`}
        >
          {feedback.text}
        </div>
      )}

      {parentModeEnabled && (
        <div className="mt-5">
          {childProfiles.length === 0 ? (
            <div className="rounded-xl border border-dashed border-surface-border bg-paper-white p-6 text-center">
              <p className="text-sm font-semibold text-ink">Chưa có hồ sơ trẻ em nào</p>
              <p className="mt-1 text-xs text-muted">
                Nhấn &ldquo;Thêm hồ sơ bé&rdquo; để tạo tối đa {MAX_CHILD_PROFILES} hồ sơ riêng cho các bé trong nhà.
              </p>
            </div>
          ) : (
            <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {childProfiles.map((profile) => {
                const screenTime = screenTimeByProfile[profile.id]?.status;
                const effectiveDaily = screenTime?.dailyMinutes ?? profile.dailyMinutes ?? 60;
                const effectiveMathLevel = screenTime?.mathLevel ?? profile.mathLevel ?? 1;
                const effectiveReward = screenTime?.rewardMinutes ?? profile.rewardMinutes ?? 5;
                const effectiveMaxBonus = screenTime?.maxBonusMinutes ?? profile.maxBonusMinutes ?? 30;
                return (
                  <li
                    key={profile.id}
                    data-testid={`child-profile-card-${profile.id}`}
                    className="flex flex-col justify-between gap-3 rounded-xl border border-surface-border bg-paper-white p-4"
                  >
                    <div className="space-y-3">
                      <div className="flex items-center gap-3">
                        <ChildAvatarBadge avatarKey={profile.avatarKey} size="md" />
                        <div className="min-w-0">
                          <p className="truncate text-sm font-bold text-ink">{profile.name}</p>
                          <p className="text-[11px] text-muted">
                            Giới hạn {effectiveDaily} phút/ngày · Toán mức {effectiveMathLevel} (+{effectiveReward} phút, tối đa +{effectiveMaxBonus} phút)
                          </p>
                        </div>
                      </div>

                      {screenTime && (
                        <div
                          data-testid={`child-screen-time-summary-${profile.id}`}
                          className="grid grid-cols-3 gap-2 rounded-lg border border-surface-border bg-surface-muted p-2.5 text-center"
                        >
                          <div>
                            <p className="text-[10px] font-medium text-muted">Đã xem hôm nay</p>
                            <p className="mt-0.5 text-xs font-bold text-ink">
                              {formatUsageDuration(screenTime.watchedSeconds)}
                            </p>
                          </div>
                          <div>
                            <p className="text-[10px] font-medium text-muted">Thưởng toán</p>
                            <p className="mt-0.5 text-xs font-bold text-brand-dark">
                              {formatUsageDuration(screenTime.rewardSeconds)}
                            </p>
                            {screenTime.grantSeconds > 0 && (
                              <p className="text-[10px] text-muted">
                                +{formatUsageDuration(screenTime.grantSeconds)} ba mẹ cho
                              </p>
                            )}
                          </div>
                          <div>
                            <p className="text-[10px] font-medium text-muted">Còn lại</p>
                            <p
                              className={`mt-0.5 text-xs font-bold ${
                                screenTime.locked ? 'text-red-800' : 'text-ink'
                              }`}
                            >
                              {formatRemainingTime(screenTime.remainingSeconds, screenTime.locked)}
                            </p>
                          </div>
                        </div>
                      )}
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                      <button
                        type="button"
                        disabled={switchingId !== null}
                        onClick={() => void handleSwitchChild(profile.id)}
                        className="btn-primary min-h-[44px] flex-1 justify-center text-xs"
                      >
                        {switchingId === profile.id ? 'Đang chuyển…' : 'Mở hồ sơ bé'}
                      </button>
                      <button
                        type="button"
                        data-testid={`grant-time-button-${profile.id}`}
                        onClick={() => openDialog({ kind: 'grant', profile })}
                        className="btn-secondary min-h-[44px] px-3 text-xs font-bold text-brand-dark"
                      >
                        Cho thêm 15 phút
                      </button>
                      <button
                        type="button"
                        data-testid={`screen-time-settings-button-${profile.id}`}
                        onClick={() => openDialog({ kind: 'screen-time', profile })}
                        className="btn-secondary min-h-[44px] px-3 text-xs"
                      >
                        Thời gian & Toán
                      </button>
                      <button
                        type="button"
                        onClick={() => openDialog({ kind: 'edit', profile })}
                        className="btn-secondary min-h-[44px] px-3 text-xs"
                      >
                        Sửa
                      </button>
                      <button
                        type="button"
                        onClick={() => openDialog({ kind: 'delete', profile })}
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
          {reachedLimit && (
            <p className="mt-3 text-xs text-muted">
              Tài khoản đã đạt tối đa {MAX_CHILD_PROFILES} hồ sơ trẻ em.
            </p>
          )}
        </div>
      )}

      {dialog && (
        <SiteDialog
          title={
            dialog.kind === 'enable'
              ? 'Bật hồ sơ trẻ em'
              : dialog.kind === 'change-pin'
              ? 'Đổi mã PIN phụ huynh'
              : dialog.kind === 'disable'
              ? 'Tắt chế độ hồ sơ trẻ em'
              : dialog.kind === 'create'
              ? 'Thêm hồ sơ trẻ em'
              : dialog.kind === 'edit'
              ? 'Sửa hồ sơ trẻ em'
              : dialog.kind === 'screen-time'
              ? `Thời gian xem & Toán · ${dialog.profile.name}`
              : dialog.kind === 'grant'
              ? `Cho thêm thời gian · ${dialog.profile.name}`
              : 'Xóa hồ sơ trẻ em'
          }
          tone={dialog.kind === 'delete' || dialog.kind === 'disable' ? 'danger' : 'default'}
          onClose={closeDialog}
          busy={busy}
        >
          <form onSubmit={handleSubmit} className="space-y-4">
            {dialog.kind === 'enable' && (
              <>
                <p className="text-xs leading-relaxed text-muted">
                  Đặt mã PIN phụ huynh gồm 4 đến 6 chữ số. Mã PIN dùng để thoát khỏi hồ sơ trẻ em và quản lý hồ sơ của các bé.
                </p>
                <div>
                  <label htmlFor="parent-pin-new" className="mb-1.5 block text-xs font-semibold text-ink">
                    Mã PIN phụ huynh (4 đến 6 chữ số)
                  </label>
                  <input
                    id="parent-pin-new"
                    type="password"
                    inputMode="numeric"
                    autoComplete="off"
                    maxLength={6}
                    required
                    disabled={busy}
                    value={pin}
                    onChange={(event) => setPin(sanitizePin(event.target.value))}
                    placeholder="Ví dụ: 1234"
                    className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                  />
                </div>
                <div>
                  <label htmlFor="parent-pin-confirm" className="mb-1.5 block text-xs font-semibold text-ink">
                    Nhập lại mã PIN
                  </label>
                  <input
                    id="parent-pin-confirm"
                    type="password"
                    inputMode="numeric"
                    autoComplete="off"
                    maxLength={6}
                    required
                    disabled={busy}
                    value={confirmPin}
                    onChange={(event) => setConfirmPin(sanitizePin(event.target.value))}
                    placeholder="Nhập lại mã PIN"
                    className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                  />
                </div>
              </>
            )}

            {dialog.kind === 'change-pin' && (
              <>
                <div>
                  <label htmlFor="parent-pin-current" className="mb-1.5 block text-xs font-semibold text-ink">
                    Mã PIN hiện tại
                  </label>
                  <input
                    id="parent-pin-current"
                    type="password"
                    inputMode="numeric"
                    autoComplete="off"
                    maxLength={6}
                    required
                    disabled={busy}
                    value={currentPin}
                    onChange={(event) => setCurrentPin(sanitizePin(event.target.value))}
                    placeholder="••••"
                    className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                  />
                </div>
                <div>
                  <label htmlFor="parent-pin-next" className="mb-1.5 block text-xs font-semibold text-ink">
                    Mã PIN mới (4 đến 6 chữ số)
                  </label>
                  <input
                    id="parent-pin-next"
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
                <div>
                  <label htmlFor="parent-pin-next-confirm" className="mb-1.5 block text-xs font-semibold text-ink">
                    Nhập lại mã PIN mới
                  </label>
                  <input
                    id="parent-pin-next-confirm"
                    type="password"
                    inputMode="numeric"
                    autoComplete="off"
                    maxLength={6}
                    required
                    disabled={busy}
                    value={confirmPin}
                    onChange={(event) => setConfirmPin(sanitizePin(event.target.value))}
                    placeholder="••••"
                    className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                  />
                </div>
              </>
            )}

            {dialog.kind === 'disable' && (
              <>
                <p className="text-xs leading-relaxed text-muted">
                  Tắt chế độ hồ sơ trẻ em? Hồ sơ và lịch sử xem của các bé vẫn được giữ lại nếu bạn bật lại sau. Các thiết bị đang mở hồ sơ bé sẽ cần đăng nhập lại.
                </p>
                <div>
                  <label htmlFor="parent-pin-disable" className="mb-1.5 block text-xs font-semibold text-ink">
                    Nhập mã PIN phụ huynh để xác nhận
                  </label>
                  <input
                    id="parent-pin-disable"
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
              </>
            )}

            {(dialog.kind === 'create' || dialog.kind === 'edit') && (
              <>
                <div>
                  <label htmlFor="child-profile-name" className="mb-1.5 block text-xs font-semibold text-ink">
                    Tên hiển thị của bé
                  </label>
                  <input
                    id="child-profile-name"
                    type="text"
                    maxLength={50}
                    required
                    disabled={busy}
                    value={childName}
                    onChange={(event) => setChildName(event.target.value)}
                    placeholder="Ví dụ: Bé Bông"
                    className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                  />
                </div>
                <fieldset>
                  <legend className="mb-2 text-xs font-semibold text-ink">Chọn hình đại diện</legend>
                  <div className="grid grid-cols-4 gap-2">
                    {CHILD_AVATARS.map((item) => {
                      const selected = avatarKey === item.key;
                      return (
                        <button
                          key={item.key}
                          type="button"
                          disabled={busy}
                          aria-pressed={selected}
                          onClick={() => setAvatarKey(item.key)}
                          className={`flex min-h-[68px] flex-col items-center justify-center gap-1 rounded-xl border-2 p-2 text-[11px] font-semibold cursor-pointer transition-colors ${
                            selected
                              ? 'border-ink bg-mint/60 text-ink shadow-[2px_2px_0_#203328]'
                              : 'border-surface-border bg-paper-white text-muted hover:border-ink/50'
                          }`}
                        >
                          <ChildAvatarBadge avatarKey={item.key} size="sm" />
                          <span className="truncate">{item.label}</span>
                        </button>
                      );
                    })}
                  </div>
                </fieldset>
                <div>
                  <label htmlFor="child-profile-pin" className="mb-1.5 block text-xs font-semibold text-ink">
                    Mã PIN phụ huynh
                  </label>
                  <input
                    id="child-profile-pin"
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
              </>
            )}

            {dialog.kind === 'screen-time' && (
              <>
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <div>
                    <label htmlFor="child-daily-minutes" className="mb-1.5 block text-xs font-semibold text-ink">
                      Giới hạn mỗi ngày (0–600 phút)
                    </label>
                    <input
                      id="child-daily-minutes"
                      data-testid="screen-time-daily-minutes-input"
                      type="number"
                      min={0}
                      max={600}
                      required
                      disabled={busy}
                      value={dailyMinutes}
                      onChange={(event) => setDailyMinutes(event.target.value)}
                      className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                    />
                  </div>
                  <div>
                    <label htmlFor="child-reward-minutes" className="mb-1.5 block text-xs font-semibold text-ink">
                      Thưởng mỗi câu đúng (1–30 phút)
                    </label>
                    <input
                      id="child-reward-minutes"
                      data-testid="screen-time-reward-minutes-input"
                      type="number"
                      min={1}
                      max={30}
                      required
                      disabled={busy}
                      value={rewardMinutes}
                      onChange={(event) => setRewardMinutes(event.target.value)}
                      className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                    />
                  </div>
                </div>
                <div>
                  <label htmlFor="child-max-bonus-minutes" className="mb-1.5 block text-xs font-semibold text-ink">
                    Phút thưởng tối đa mỗi ngày (0–120 phút)
                  </label>
                  <input
                    id="child-max-bonus-minutes"
                    data-testid="screen-time-max-bonus-input"
                    type="number"
                    min={0}
                    max={120}
                    required
                    disabled={busy}
                    value={maxBonusMinutes}
                    onChange={(event) => setMaxBonusMinutes(event.target.value)}
                    className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                  />
                </div>
                <div>
                  <label htmlFor="child-math-level" className="mb-1.5 block text-xs font-semibold text-ink">
                    Cấp độ bài toán
                  </label>
                  <select
                    id="child-math-level"
                    data-testid="screen-time-math-level-select"
                    disabled={busy}
                    value={mathLevel}
                    onChange={(event) => setMathLevel(Number(event.target.value))}
                    className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                  >
                    {MATH_LEVEL_OPTIONS.map((option) => (
                      <option key={option.level} value={option.level}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label htmlFor="screen-time-settings-pin" className="mb-1.5 block text-xs font-semibold text-ink">
                    Mã PIN phụ huynh
                  </label>
                  <input
                    id="screen-time-settings-pin"
                    data-testid="screen-time-settings-pin-input"
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
              </>
            )}

            {dialog.kind === 'grant' && (
              <>
                <p className="text-xs leading-relaxed text-muted">
                  Cộng thêm thời gian xem đột xuất trong hôm nay cho <strong>{dialog.profile.name}</strong> (tính riêng, không trừ vào trần thưởng giải toán).
                </p>
                <div className="flex flex-wrap gap-2">
                  {GRANT_PRESETS.map((preset) => (
                    <button
                      key={preset}
                      type="button"
                      disabled={busy}
                      onClick={() => setGrantMinutes(String(preset))}
                      className={`min-h-[44px] rounded-lg border-2 px-3 text-xs font-bold cursor-pointer ${
                        Number(grantMinutes) === preset
                          ? 'border-ink bg-mint text-ink'
                          : 'border-surface-border bg-paper-white text-muted hover:border-ink/50'
                      }`}
                    >
                      +{preset} phút
                    </button>
                  ))}
                </div>
                <div>
                  <label htmlFor="child-grant-minutes" className="mb-1.5 block text-xs font-semibold text-ink">
                    Số phút cho thêm (1–180 phút)
                  </label>
                  <input
                    id="child-grant-minutes"
                    data-testid="grant-minutes-input"
                    type="number"
                    min={1}
                    max={180}
                    required
                    disabled={busy}
                    value={grantMinutes}
                    onChange={(event) => setGrantMinutes(event.target.value)}
                    className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                  />
                </div>
                <div>
                  <label htmlFor="child-grant-pin" className="mb-1.5 block text-xs font-semibold text-ink">
                    Mã PIN phụ huynh
                  </label>
                  <input
                    id="child-grant-pin"
                    data-testid="grant-pin-input"
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
              </>
            )}

            {dialog.kind === 'delete' && (
              <>
                <p className="text-xs leading-relaxed text-muted">
                  Xóa hẳn hồ sơ <strong>{dialog.profile.name}</strong>? Toàn bộ lịch sử xem, Xem sau, Yêu thích và điểm chấm riêng của hồ sơ này sẽ bị xóa.
                </p>
                <div>
                  <label htmlFor="child-profile-delete-pin" className="mb-1.5 block text-xs font-semibold text-ink">
                    Nhập mã PIN phụ huynh để xác nhận
                  </label>
                  <input
                    id="child-profile-delete-pin"
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
              </>
            )}

            {dialogError && (
              <div role="alert" className="rounded-lg border border-red-800/35 bg-red-50 px-3.5 py-2.5 text-xs text-red-900">
                {dialogError}
              </div>
            )}

            <div className="flex flex-col-reverse gap-2 pt-1 sm:flex-row sm:justify-end">
              <button
                type="button"
                disabled={busy}
                onClick={closeDialog}
                className="btn-secondary w-full sm:w-auto text-xs"
              >
                Hủy
              </button>
              <button
                type="submit"
                disabled={busy}
                className={
                  dialog.kind === 'delete' || dialog.kind === 'disable'
                    ? 'inline-flex min-h-[44px] w-full sm:w-auto items-center justify-center border-2 border-ink bg-red-800 px-5 py-2.5 text-xs font-bold text-paper-white shadow-brand-sm hover:bg-red-900 disabled:opacity-50 cursor-pointer'
                    : 'btn-primary w-full sm:w-auto text-xs disabled:opacity-50'
                }
              >
                {busy
                  ? 'Đang xử lý…'
                  : dialog.kind === 'enable'
                  ? 'Bật hồ sơ trẻ em'
                  : dialog.kind === 'change-pin'
                  ? 'Lưu mã PIN mới'
                  : dialog.kind === 'disable'
                  ? 'Tắt chế độ'
                  : dialog.kind === 'create'
                  ? 'Tạo hồ sơ'
                  : dialog.kind === 'edit'
                  ? 'Lưu thay đổi'
                  : dialog.kind === 'screen-time'
                  ? 'Lưu cài đặt'
                  : dialog.kind === 'grant'
                  ? `Cho thêm ${grantMinutes || 15} phút`
                  : 'Xóa hồ sơ'}
              </button>
            </div>
          </form>
        </SiteDialog>
      )}
    </section>
  );
}
