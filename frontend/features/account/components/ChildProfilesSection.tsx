import React, { useCallback, useEffect, useRef, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { useNavigate } from 'react-router';
import { SiteDialog } from '@/components/ui/SiteDialog';
import { PlayFeaturesSettings } from '@/features/account/components/PlayFeaturesSettings';
import { useAuth } from '@/lib/context/AuthContext';
import { ChildAvatarBadge } from '@/features/profiles/components/ChildAvatarBadge';
import { useDetectedStandaloneFilmCount } from '@/features/profiles/components/KidModeDashboard';
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
  DAILY_MINUTE_PRESETS,
  broadcastScreenTimeUpdated,
  fetchParentScreenTimeOverview,
  formatDailyLimitLabel,
  formatRemainingTime,
  formatUsageDuration,
  grantChildExtraMinutes,
  updateChildScreenTimeSettings,
  validateGrantMinutes,
  validateScreenTimeSettings,
  type ChildProfileScreenTime,
} from '@/lib/services/screenTimeService';
import {
  QUIZ_GRADES,
  fetchChildQuizConfigs,
  fetchQuizSampleQuestions,
  fetchQuizSetsOverview,
  formatChildQuizSummaryLine,
  getQuizGradeLabel,
  type ChildQuizConfig,
  type QuizGradeCode,
  type QuizSampleQuestion,
  type QuizSetsOverview,
} from '@/features/quiz/quizService';
import { ChildQuizSettingsDialog } from '@/features/quiz/components/ChildQuizSettingsDialog';
import { ParentQuizBankSection } from '@/features/quiz/components/ParentQuizBankSection';
import { ParentQuizInsightsSection } from '@/features/quiz/components/ParentQuizInsightsSection';
import { ParentBountySection } from '@/features/account/components/ParentBountySection';
import { ParentWeekendVaultSection } from '@/features/account/components/ParentWeekendVaultSection';
import { ParentVipSeriesSection } from '@/features/account/components/ParentVipSeriesSection';

type DialogMode =
  | { kind: 'enable' }
  | { kind: 'change-pin' }
  | { kind: 'disable' }
  | { kind: 'create' }
  | { kind: 'edit'; profile: ChildProfile }
  | { kind: 'delete'; profile: ChildProfile }
  | { kind: 'screen-time'; profile: ChildProfile; presetMinutes?: number; presetUnlimited?: boolean }
  | { kind: 'grant'; profile: ChildProfile }
  | null;

const GRANT_PRESETS = [10, 15, 30] as const;
const SESSION_PIN_TTL_MS = 5 * 60 * 1000;

export function ChildProfilesSection({
  initialSessionPin = '',
  onSessionPinChange,
  standaloneFilmCount,
}: {
  initialSessionPin?: string;
  onSessionPinChange?: (pin: string) => void;
  standaloneFilmCount?: number;
} = {}) {
  const {
    token,
    parentModeEnabled,
    childProfiles,
    refreshProfiles,
    selectChildProfile,
  } = useAuth();
  const navigate = useNavigate();

  const [dialog, setDialog] = useState<DialogMode>(null);
  const [playSettingsProfile, setPlaySettingsProfile] = useState<ChildProfile | null>(null);
  const [quizSettingsProfile, setQuizSettingsProfile] = useState<ChildProfile | null>(null);
  const [verifiedPin, setVerifiedPin] = useState<string>(() => initialSessionPin || '');
  const verifiedPinExpiryRef = useRef<number>(initialSessionPin ? Date.now() + SESSION_PIN_TTL_MS : 0);
  const detectedStandalone = useDetectedStandaloneFilmCount(
    Boolean(token && parentModeEnabled) && standaloneFilmCount === undefined
  );
  const zeroStandaloneFilms =
    (typeof standaloneFilmCount === 'number' ? standaloneFilmCount : detectedStandalone) === 0;
  const [pin, setPin] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [currentPin, setCurrentPin] = useState('');
  const [childName, setChildName] = useState('');
  const [avatarKey, setAvatarKey] = useState('bear');
  const [dailyMinutes, setDailyMinutes] = useState('60');
  const [isUnlimited, setIsUnlimited] = useState(false);
  const [profileQuizGrade, setProfileQuizGrade] = useState<QuizGradeCode>('LOP_1');
  const [profileQuizSetIds, setProfileQuizSetIds] = useState<number[]>([]);
  const [profileQuizSamples, setProfileQuizSamples] = useState<QuizSampleQuestion[]>([]);
  const [loadingProfileSamples, setLoadingProfileSamples] = useState(false);
  const [rewardMinutes, setRewardMinutes] = useState('5');
  const [maxBonusMinutes, setMaxBonusMinutes] = useState('30');
  const [grantMinutes, setGrantMinutes] = useState('15');
  const [screenTimeByProfile, setScreenTimeByProfile] = useState<Record<number, ChildProfileScreenTime>>({});
  const [screenTimeLoading, setScreenTimeLoading] = useState(false);
  const [screenTimeError, setScreenTimeError] = useState('');
  const [quizOverview, setQuizOverview] = useState<QuizSetsOverview | null>(null);
  const [quizConfigs, setQuizConfigs] = useState<ChildQuizConfig[]>([]);
  const [quizLoading, setQuizLoading] = useState(false);
  const [quizLoadError, setQuizLoadError] = useState('');
  const [busy, setBusy] = useState(false);
  const [switchingId, setSwitchingId] = useState<number | null>(null);
  const [selectedChildId, setSelectedChildId] = useState<number | null>(null);
  const [recentSavedChild, setRecentSavedChild] = useState<{ id: number | null; name: string } | null>(null);
  const [dialogError, setDialogError] = useState('');
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const quizConfigByProfile: Record<number, ChildQuizConfig> = {};
  for (const cfg of quizConfigs) {
    quizConfigByProfile[cfg.profileId] = cfg;
  }

  const getActiveSessionPin = useCallback(() => {
    if (!verifiedPin || Date.now() >= verifiedPinExpiryRef.current) {
      return '';
    }
    return verifiedPin;
  }, [verifiedPin]);

  const rememberSessionPin = useCallback(
    (nextPin: string) => {
      if (!nextPin) return;
      verifiedPinExpiryRef.current = Date.now() + SESSION_PIN_TTL_MS;
      setVerifiedPin(nextPin);
      onSessionPinChange?.(nextPin);
    },
    [onSessionPinChange]
  );

  const clearSessionPin = useCallback(() => {
    verifiedPinExpiryRef.current = 0;
    setVerifiedPin('');
    onSessionPinChange?.('');
  }, [onSessionPinChange]);

  useEffect(() => {
    if (initialSessionPin) {
      verifiedPinExpiryRef.current = Date.now() + SESSION_PIN_TTL_MS;
      setVerifiedPin(initialSessionPin);
    }
  }, [initialSessionPin]);

  useEffect(() => {
    return () => {
      verifiedPinExpiryRef.current = 0;
    };
  }, []);

  const loadScreenTimeOverview = useCallback(async () => {
    if (!token || !parentModeEnabled) {
      setScreenTimeByProfile({});
      setScreenTimeError('');
      setScreenTimeLoading(false);
      return;
    }
    setScreenTimeLoading(true);
    setScreenTimeError('');
    try {
      const items = await fetchParentScreenTimeOverview(token);
      const next: Record<number, ChildProfileScreenTime> = {};
      for (const item of items) {
        next[item.profileId] = item;
      }
      setScreenTimeByProfile(next);
    } catch (err: unknown) {
      setScreenTimeError(
        err instanceof Error ? err.message : 'Chưa tải được thống kê thời gian xem hôm nay.'
      );
    } finally {
      setScreenTimeLoading(false);
    }
  }, [token, parentModeEnabled]);

  const loadQuizData = useCallback(async () => {
    if (!token || !parentModeEnabled) {
      setQuizOverview(null);
      setQuizConfigs([]);
      setQuizLoadError('');
      setQuizLoading(false);
      return;
    }
    setQuizLoading(true);
    setQuizLoadError('');
    try {
      const [overviewData, configsData] = await Promise.all([
        fetchQuizSetsOverview(token),
        fetchChildQuizConfigs(token),
      ]);
      setQuizOverview(overviewData);
      setQuizConfigs(configsData);
    } catch (err: unknown) {
      setQuizLoadError(err instanceof Error ? err.message : 'Chưa tải được ngân hàng câu hỏi.');
    } finally {
      setQuizLoading(false);
    }
  }, [token, parentModeEnabled]);

  useEffect(() => {
    if (token) {
      void refreshProfiles();
    }
  }, [token, refreshProfiles]);

  useEffect(() => {
    void loadScreenTimeOverview();
    void loadQuizData();
  }, [loadScreenTimeOverview, loadQuizData, childProfiles.length]);

  useEffect(() => {
    if (childProfiles.length === 0) {
      setSelectedChildId(null);
      return;
    }
    setSelectedChildId((prev) =>
      prev && childProfiles.some((profile) => profile.id === prev) ? prev : childProfiles[0].id
    );
  }, [childProfiles]);

  const loadProfileDialogSamples = useCallback(
    async (grade: QuizGradeCode, setIds: number[]) => {
      if (!token || setIds.length === 0) {
        setProfileQuizSamples([]);
        return;
      }
      setLoadingProfileSamples(true);
      try {
        const list = await fetchQuizSampleQuestions(token, grade, setIds);
        setProfileQuizSamples(list);
      } catch {
        setProfileQuizSamples([]);
      } finally {
        setLoadingProfileSamples(false);
      }
    },
    [token]
  );

  useEffect(() => {
    if (dialog?.kind !== 'create' && dialog?.kind !== 'edit') return;
    if (profileQuizSetIds.length === 0) {
      setProfileQuizSamples([]);
      return;
    }
    void loadProfileDialogSamples(profileQuizGrade, profileQuizSetIds);
  }, [dialog?.kind, profileQuizGrade, profileQuizSetIds.join(','), loadProfileDialogSamples]);

  const resetDialog = () => {
    setDialog(null);
    setPin('');
    setConfirmPin('');
    setCurrentPin('');
    setChildName('');
    setAvatarKey('bear');
    setDailyMinutes('60');
    setIsUnlimited(false);
    setProfileQuizGrade('LOP_1');
    setProfileQuizSetIds([]);
    setProfileQuizSamples([]);
    setDialogError('');
  };

  const closeDialog = () => {
    if (busy) return;
    resetDialog();
  };

  const defaultBuiltInSetIds = (): number[] => {
    const sets = quizOverview?.sets ?? [];
    const builtIns = sets.filter((s) => s.active && s.builtIn).map((s) => s.id);
    if (builtIns.length > 0) return builtIns;
    return sets.filter((s) => s.active).map((s) => s.id);
  };

  const openDialog = (next: DialogMode) => {
    setFeedback(null);
    setRecentSavedChild(null);
    setDialogError('');
    const prefilledPin =
      next && (next.kind === 'create' || next.kind === 'edit' || next.kind === 'screen-time' || next.kind === 'grant')
        ? getActiveSessionPin()
        : '';
    setPin(prefilledPin);
    setConfirmPin('');
    setCurrentPin('');
    if (next?.kind === 'edit') {
      const summary = screenTimeByProfile[next.profile.id]?.status;
      const cfg = quizConfigByProfile[next.profile.id];
      const unlimited = Boolean(summary?.unlimited ?? next.profile.unlimited ?? next.profile.dailyMinutes === null);
      setChildName(next.profile.name);
      setAvatarKey(next.profile.avatarKey);
      setIsUnlimited(unlimited);
      setDailyMinutes(unlimited ? '60' : String(summary?.dailyMinutes ?? next.profile.dailyMinutes ?? 60));
      setProfileQuizGrade((cfg?.grade ?? next.profile.quizGrade ?? 'LOP_1') as QuizGradeCode);
      setProfileQuizSetIds(cfg?.activeSetIds?.length ? [...cfg.activeSetIds] : defaultBuiltInSetIds());
    } else if (next?.kind === 'create') {
      setChildName('');
      setAvatarKey('bear');
      setIsUnlimited(false);
      setDailyMinutes('60');
      setProfileQuizGrade('LOP_1');
      setProfileQuizSetIds(defaultBuiltInSetIds());
    } else if (next?.kind === 'screen-time') {
      const summary = screenTimeByProfile[next.profile.id]?.status;
      const baseUnlimited = Boolean(summary?.unlimited ?? next.profile.unlimited ?? next.profile.dailyMinutes === null);
      const nextUnlimited = next.presetUnlimited !== undefined ? next.presetUnlimited : baseUnlimited;
      const nextDaily =
        next.presetMinutes !== undefined
          ? next.presetMinutes
          : nextUnlimited
          ? 60
          : summary?.dailyMinutes ?? next.profile.dailyMinutes ?? 60;
      setIsUnlimited(nextUnlimited);
      setDailyMinutes(String(nextDaily));
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

  const toggleProfileQuizSet = (setId: number) => {
    setProfileQuizSetIds((prev) =>
      prev.includes(setId) ? prev.filter((id) => id !== setId) : [...prev, setId]
    );
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
        rememberSessionPin(pin);
        await refreshProfiles();
        await loadQuizData();
        resetDialog();
        setFeedback({ type: 'success', text: 'Đã bật chế độ hồ sơ trẻ em. Giờ bạn có thể thêm hồ sơ bé đầu tiên!' });
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
        clearSessionPin();
        resetDialog();
        setFeedback({ type: 'success', text: 'Đã đổi mã PIN phụ huynh.' });
      } catch (err: unknown) {
        clearSessionPin();
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
        clearSessionPin();
        await refreshProfiles();
        resetDialog();
        setFeedback({ type: 'success', text: 'Đã tắt chế độ hồ sơ trẻ em. Hồ sơ của các bé vẫn được giữ lại.' });
      } catch (err: unknown) {
        clearSessionPin();
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
      const parsedDaily = isUnlimited ? null : dailyMinutes.trim() === '' ? NaN : Number(dailyMinutes);
      const dailyErr = validateScreenTimeSettings({ dailyMinutes: parsedDaily, unlimited: isUnlimited });
      if (dailyErr) {
        setDialogError(dailyErr);
        return;
      }
      const activeSetsAvailable = (quizOverview?.sets ?? []).filter((s) => s.active);
      if (activeSetsAvailable.length > 0 && profileQuizSetIds.length === 0) {
        setDialogError('Hãy chọn ít nhất 1 bộ câu hỏi cho bé.');
        return;
      }
      const pinErr = validateParentPin(pin);
      if (pinErr) {
        setDialogError(pinErr);
        return;
      }
      setBusy(true);
      try {
        const profilePayload = {
          pin,
          name: childName.trim(),
          avatarKey,
          dailyMinutes: isUnlimited ? null : parsedDaily,
          unlimited: isUnlimited,
          quizGrade: profileQuizGrade,
          ...(profileQuizSetIds.length > 0 ? { quizSetIds: profileQuizSetIds } : {}),
        };
        const trimmedChildName = childName.trim();
        let savedChildId: number | null = dialog.kind === 'edit' ? dialog.profile.id : null;
        let savedChildName = trimmedChildName;
        if (dialog.kind === 'create') {
          const created = await createChildProfile(token, profilePayload);
          if (created && typeof created === 'object' && typeof created.id === 'number') {
            savedChildId = created.id;
            savedChildName = created.name || trimmedChildName;
          }
        } else {
          const updated = await updateChildProfile(token, dialog.profile.id, profilePayload);
          if (updated && typeof updated === 'object' && typeof updated.id === 'number') {
            savedChildId = updated.id;
            savedChildName = updated.name || trimmedChildName;
          }
        }
        if (savedChildId !== null) {
          setSelectedChildId(savedChildId);
        }
        rememberSessionPin(pin);
        await refreshProfiles();
        await Promise.all([loadScreenTimeOverview(), loadQuizData()]);
        broadcastScreenTimeUpdated(dialog.kind === 'edit' ? dialog.profile.id : undefined);
        const message = dialog.kind === 'create' ? 'Đã tạo hồ sơ trẻ em.' : 'Đã cập nhật hồ sơ trẻ em.';
        resetDialog();
        setRecentSavedChild({ id: savedChildId, name: savedChildName });
        setFeedback({ type: 'success', text: message });
      } catch (err: unknown) {
        clearSessionPin();
        setPin('');
        setDialogError(err instanceof Error ? err.message : 'Chưa lưu được hồ sơ trẻ em.');
      } finally {
        setBusy(false);
      }
      return;
    }

    if (dialog.kind === 'screen-time') {
      const parsedDaily = isUnlimited ? null : dailyMinutes.trim() === '' ? NaN : Number(dailyMinutes);
      const parsedReward = rewardMinutes.trim() === '' ? NaN : Number(rewardMinutes);
      const parsedMaxBonus = maxBonusMinutes.trim() === '' ? NaN : Number(maxBonusMinutes);
      const settingsErr = validateScreenTimeSettings({
        dailyMinutes: parsedDaily,
        unlimited: isUnlimited,
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
          dailyMinutes: isUnlimited ? null : parsedDaily,
          unlimited: isUnlimited,
          rewardMinutes: parsedReward,
          maxBonusMinutes: parsedMaxBonus,
        });
        rememberSessionPin(pin);
        setScreenTimeByProfile((prev) => ({ ...prev, [updated.profileId]: updated }));
        await refreshProfiles();
        broadcastScreenTimeUpdated(updated.profileId);
        const targetId = dialog.profile.id;
        const targetName = dialog.profile.name;
        resetDialog();
        setSelectedChildId(targetId);
        setRecentSavedChild({ id: targetId, name: targetName });
        setFeedback({ type: 'success', text: `Đã lưu cài đặt thời gian xem cho ${targetName}.` });
      } catch (err: unknown) {
        clearSessionPin();
        setPin('');
        setDialogError(err instanceof Error ? err.message : 'Chưa lưu được cài đặt thời gian xem.');
      } finally {
        setBusy(false);
      }
      return;
    }

    if (dialog.kind === 'grant') {
      const parsedGrant = grantMinutes.trim() === '' ? NaN : Number(grantMinutes);
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
        rememberSessionPin(pin);
        setScreenTimeByProfile((prev) => ({ ...prev, [updated.profileId]: updated }));
        broadcastScreenTimeUpdated(updated.profileId);
        const targetId = dialog.profile.id;
        const targetName = dialog.profile.name;
        resetDialog();
        setSelectedChildId(targetId);
        setRecentSavedChild({ id: targetId, name: targetName });
        setFeedback({ type: 'success', text: `Đã cộng thêm ${parsedGrant} phút xem phim cho ${targetName}.` });
      } catch (err: unknown) {
        clearSessionPin();
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
        await Promise.all([loadScreenTimeOverview(), loadQuizData()]);
        const removedName = dialog.profile.name;
        resetDialog();
        setFeedback({ type: 'success', text: `Đã xóa hồ sơ ${removedName}.` });
      } catch (err: unknown) {
        clearSessionPin();
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

  const handleQuizConfigUpdated = (updated: ChildQuizConfig) => {
    setQuizConfigs((prev) => {
      const exists = prev.some((item) => item.profileId === updated.profileId);
      if (!exists) return [...prev, updated];
      return prev.map((item) => (item.profileId === updated.profileId ? updated : item));
    });
    void refreshProfiles();
    void loadScreenTimeOverview();
    broadcastScreenTimeUpdated(updated.profileId);
  };

  const reachedLimit = childProfiles.length >= MAX_CHILD_PROFILES;
  const activeSessionPin = getActiveSessionPin();
  const activeAvailableSets = (quizOverview?.sets ?? []).filter((s) => s.active);
  const activeChildProfile =
    childProfiles.find((profile) => profile.id === selectedChildId) ?? childProfiles[0] ?? null;
  const recentSavedProfileId = recentSavedChild
    ? recentSavedChild.id ??
      childProfiles.find((profile) => profile.name === recentSavedChild.name)?.id ??
      null
    : null;

  return (
    <section
      data-reveal-section=""
      aria-labelledby="child-profiles-heading"
      data-testid="child-profiles-section"
      className="rounded-xl border border-surface-border bg-surface p-6 sm:p-8"
    >
      <div className="flex flex-col gap-4 border-b border-surface-border pb-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h2 id="child-profiles-heading" className="text-lg font-bold text-ink">
              Thiết lập cho bé
            </h2>
            <span
              className={`rounded-full border px-2.5 py-0.5 text-[0.6875rem] font-bold ${
                parentModeEnabled
                  ? 'border-ink bg-mint text-ink'
                  : 'border-surface-border bg-surface-elevated text-muted'
              }`}
            >
              {parentModeEnabled ? `Đang bật (${childProfiles.length}/${MAX_CHILD_PROFILES})` : 'Chưa bật'}
            </span>
          </div>
          <p className="mt-1 text-base leading-relaxed text-muted">
            {zeroStandaloneFilms
              ? 'Quản lý tập trung từng bé ở cùng một nơi: thời gian xem, bộ câu hỏi theo lớp, phim cần trả lời câu hỏi và nhiệm vụ đổi giờ.'
              : 'Quản lý tập trung từng bé ở cùng một nơi: thời gian xem, bộ câu hỏi theo lớp, phim lẻ cuối tuần, phim cần trả lời câu hỏi và nhiệm vụ đổi giờ.'}
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
          className={`motion-toast-enter mt-4 flex flex-wrap items-center justify-between gap-2 rounded-xl border p-3 text-xs leading-relaxed ${
            feedback.type === 'error'
              ? 'border-red-500/40 bg-red-500/10 text-red-900'
              : 'border-brand/50 bg-brand/15 text-ink'
          }`}
        >
          <span>{feedback.text}</span>
          {feedback.type === 'success' && recentSavedChild && recentSavedProfileId !== null && (
            <button
              type="button"
              data-testid="open-saved-child-profile-button"
              disabled={switchingId !== null}
              onClick={() => void handleSwitchChild(recentSavedProfileId)}
              className="btn-primary min-h-[40px] px-3 py-1.5 text-xs font-bold"
            >
              {switchingId === recentSavedProfileId ? 'Đang chuyển…' : `Mở hồ sơ của ${recentSavedChild.name}`}
            </button>
          )}
        </div>
      )}

      {parentModeEnabled && (
        <div className="mt-5">
          {childProfiles.length > 0 && (
            <nav
              aria-label="Đi nhanh tới các mục cài đặt cho bé"
              data-testid="parent-sections-nav"
              className="mb-4 flex flex-wrap items-center gap-2 rounded-xl border border-surface-border bg-surface-muted p-2.5"
            >
              <a
                href="#parent-profiles-list"
                className="inline-flex min-h-[44px] items-center rounded-lg border border-surface-border bg-paper-white px-3 text-xs font-bold text-ink hover:bg-mint/60"
              >
                Hồ sơ &amp; Thời gian
              </a>
              {!zeroStandaloneFilms && (
                <a
                  href="#parent-weekend-vault-section"
                  className="inline-flex min-h-[44px] items-center rounded-lg border border-surface-border bg-paper-white px-3 text-xs font-bold text-ink hover:bg-mint/60"
                >
                  Phim lẻ cuối tuần
                </a>
              )}
              <a
                href="#parent-vip-series-section"
                className="inline-flex min-h-[44px] items-center rounded-lg border border-surface-border bg-paper-white px-3 text-xs font-bold text-ink hover:bg-mint/60"
              >
                Phim cần trả lời câu hỏi
              </a>
              <a
                href="#parent-bounty-section"
                className="inline-flex min-h-[44px] items-center rounded-lg border border-surface-border bg-paper-white px-3 text-xs font-bold text-ink hover:bg-mint/60"
              >
                Nhiệm vụ đổi giờ
              </a>
              <a
                href="#parent-quiz-insights-section"
                className="inline-flex min-h-[44px] items-center rounded-lg border border-surface-border bg-paper-white px-3 text-xs font-bold text-ink hover:bg-mint/60"
              >
                Báo cáo học tập
              </a>
              <a
                href="#parent-quiz-bank-section"
                className="inline-flex min-h-[44px] items-center rounded-lg border border-surface-border bg-paper-white px-3 text-xs font-bold text-ink hover:bg-mint/60"
              >
                Ngân hàng câu hỏi
              </a>
              {activeChildProfile && (
                <button
                  type="button"
                  data-testid="jump-play-features-button"
                  onClick={() => {
                    setFeedback(null);
                    setRecentSavedChild(null);
                    setPlaySettingsProfile(activeChildProfile);
                  }}
                  className="inline-flex min-h-[44px] items-center rounded-lg border border-ink/40 bg-mint/55 px-3 text-xs font-bold text-ink hover:bg-mint cursor-pointer"
                >
                  Tính năng xem phim ({activeChildProfile.name})
                </button>
              )}
            </nav>
          )}

          {screenTimeError && childProfiles.length > 0 && (
            <div
              role="alert"
              data-testid="screen-time-overview-error"
              className="motion-toast-enter mb-4 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-red-800/35 bg-red-50 px-3.5 py-2.5 text-xs text-red-900"
            >
              <span>{screenTimeError}</span>
              <button
                type="button"
                data-testid="retry-screen-time-overview"
                onClick={() => void loadScreenTimeOverview()}
                className="btn-secondary min-h-[44px] px-3 text-xs font-bold"
              >
                Thử lại
              </button>
            </div>
          )}

          {screenTimeLoading && childProfiles.length > 0 && Object.keys(screenTimeByProfile).length === 0 && (
            <p role="status" className="mb-3 text-xs text-muted">
              Đang tải thời gian xem hôm nay của các bé…
            </p>
          )}

          {childProfiles.length === 0 ? (
            <div className="rounded-xl border border-dashed border-surface-border bg-paper-white p-6 text-center">
              <p className="text-base font-semibold text-ink">Chưa có hồ sơ trẻ em nào</p>
              <p className="mt-1 text-base text-muted">
                Tạo tối đa {MAX_CHILD_PROFILES} hồ sơ riêng để cài giờ xem, bộ câu hỏi theo lớp, Dieppie, nhiệm vụ và phim cần trả lời câu hỏi cho từng bé.
              </p>
              <button
                type="button"
                data-testid="add-first-child-profile-button"
                onClick={() => openDialog({ kind: 'create' })}
                className="btn-primary mt-4 min-h-[44px] text-xs font-bold"
              >
                Thêm hồ sơ bé đầu tiên
              </button>
            </div>
          ) : (
            <ul id="parent-profiles-list" className="scroll-mt-20 grid grid-cols-1 gap-4 lg:grid-cols-2">
              {childProfiles.map((profile) => {
                const screenTime = screenTimeByProfile[profile.id]?.status;
                const quizConfig = quizConfigByProfile[profile.id];
                const effectiveUnlimited = Boolean(
                  screenTime?.unlimited ?? profile.unlimited ?? profile.dailyMinutes === null
                );
                const effectiveDaily = effectiveUnlimited ? null : screenTime?.dailyMinutes ?? profile.dailyMinutes ?? 60;
                const effectiveReward = screenTime?.rewardMinutes ?? profile.rewardMinutes ?? 5;
                const effectiveMaxBonus = screenTime?.maxBonusMinutes ?? profile.maxBonusMinutes ?? 30;
                const effectiveGradeLabel =
                  quizConfig?.gradeLabel ??
                  screenTime?.quizGradeLabel ??
                  profile.quizGradeLabel ??
                  getQuizGradeLabel(profile.quizGrade);
                const activeSetNames = quizConfig?.activeSets?.map((item) => item.name) ?? [];
                const quizSummaryLine = formatChildQuizSummaryLine(effectiveGradeLabel, activeSetNames);
                const dailyLimitLabel = formatDailyLimitLabel(effectiveDaily, effectiveUnlimited);
                const isSelectedCard = childProfiles.length > 1 && activeChildProfile?.id === profile.id;
                const watchedLabel = screenTime ? formatUsageDuration(screenTime.watchedSeconds) : '0 phút';
                const remainingLabel = effectiveUnlimited
                  ? 'Không giới hạn'
                  : screenTime
                  ? formatRemainingTime(screenTime.remainingSeconds, screenTime.locked)
                  : `Còn ${effectiveDaily ?? 60} phút`;
                const extraParts: string[] = [];
                if (screenTime) {
                  extraParts.push(`${formatUsageDuration(screenTime.rewardSeconds)} câu hỏi`);
                  if (screenTime.grantSeconds > 0) {
                    extraParts.push(`+${formatUsageDuration(screenTime.grantSeconds)} ba mẹ cho`);
                  }
                  if (screenTime.bountySeconds > 0) {
                    extraParts.push(`+${formatUsageDuration(screenTime.bountySeconds)} nhiệm vụ`);
                  }
                } else {
                  extraParts.push(`+${effectiveReward} phút/câu đúng (tối đa +${effectiveMaxBonus} phút)`);
                }

                return (
                  <li
                    key={profile.id}
                    data-testid={`child-profile-card-${profile.id}`}
                    className={`flex flex-col justify-between gap-4 rounded-xl border bg-paper-white p-5 transition-colors ${
                      isSelectedCard ? 'border-2 border-ink shadow-brand-sm' : 'border-surface-border'
                    }`}
                  >
                    <div className="space-y-3">
                      <div className="flex items-start justify-between gap-2 min-w-0">
                        <div className="flex items-center gap-3 min-w-0">
                          <ChildAvatarBadge avatarKey={profile.avatarKey} size="md" />
                          <div className="min-w-0 flex-1">
                            <p className="truncate text-base font-bold text-ink">{profile.name}</p>
                            <p
                              data-testid={`child-quiz-summary-${profile.id}`}
                              className="mt-0.5 text-xs font-medium leading-snug text-muted"
                            >
                              {quizSummaryLine}
                            </p>
                          </div>
                        </div>
                        <div className="flex shrink-0 items-center gap-1">
                          <button
                            type="button"
                            onClick={() => openDialog({ kind: 'edit', profile })}
                            className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-lg border border-surface-border bg-surface-muted px-2.5 text-xs font-semibold text-ink hover:bg-surface-elevated cursor-pointer"
                          >
                            Sửa
                          </button>
                          <button
                            type="button"
                            onClick={() => openDialog({ kind: 'delete', profile })}
                            className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-lg border border-surface-border bg-surface-muted px-2.5 text-xs font-semibold text-red-900 hover:bg-red-50 cursor-pointer"
                          >
                            Xóa
                          </button>
                        </div>
                      </div>

                      <div
                        data-testid={`child-screen-time-summary-${profile.id}`}
                        className="rounded-xl border border-ink/25 bg-surface-muted p-3.5 space-y-2.5"
                      >
                        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-surface-border/70 pb-2">
                          <p
                            data-testid={`child-daily-limit-${profile.id}`}
                            className="text-sm font-bold text-ink"
                          >
                            Thời gian xem mỗi ngày: {dailyLimitLabel}
                          </p>
                          <span
                            className={`rounded-full border px-2.5 py-0.5 text-xs font-bold ${
                              screenTime?.locked
                                ? 'border-red-800/40 bg-red-50 text-red-900'
                                : 'border-ink/30 bg-mint/70 text-ink'
                            }`}
                          >
                            {remainingLabel}
                          </span>
                        </div>

                        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 text-xs">
                          <div className="rounded-lg bg-paper-white px-3 py-2 border border-surface-border/60">
                            <span className="text-muted">Đã xem hôm nay: </span>
                            <strong className="font-bold text-ink">{watchedLabel}</strong>
                          </div>
                          <div className="rounded-lg bg-paper-white px-3 py-2 border border-surface-border/60">
                            <span className="text-muted">Cộng thêm: </span>
                            <strong className="font-bold text-brand-dark">{extraParts.join(' · ')}</strong>
                          </div>
                        </div>
                      </div>
                    </div>

                    <div className="space-y-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <button
                          type="button"
                          disabled={switchingId !== null}
                          onClick={() => void handleSwitchChild(profile.id)}
                          className="btn-primary min-h-[44px] flex-1 justify-center text-xs font-bold"
                        >
                          {switchingId === profile.id ? 'Đang chuyển…' : `Mở hồ sơ của ${profile.name}`}
                        </button>
                        <button
                          type="button"
                          data-testid={`grant-time-button-${profile.id}`}
                          onClick={() => openDialog({ kind: 'grant', profile })}
                          className="btn-secondary min-h-[44px] px-3.5 text-xs font-bold text-brand-dark"
                        >
                          Cho thêm thời gian hôm nay
                        </button>
                      </div>

                      <div className="flex flex-wrap items-center gap-2">
                        <button
                          type="button"
                          data-testid={`screen-time-settings-button-${profile.id}`}
                          onClick={() => openDialog({ kind: 'screen-time', profile })}
                          className="btn-secondary min-h-[44px] px-3 text-xs"
                        >
                          Thời gian xem
                        </button>
                        <button
                          type="button"
                          data-testid={`edit-child-quiz-button-${profile.id}`}
                          onClick={() => {
                            setFeedback(null);
                            setQuizSettingsProfile(profile);
                          }}
                          className="btn-secondary min-h-[44px] px-3 text-xs"
                        >
                          Đổi bộ câu hỏi
                        </button>
                        <button
                          type="button"
                          data-testid={`play-features-settings-button-${profile.id}`}
                          onClick={() => {
                            setFeedback(null);
                            setRecentSavedChild(null);
                            setPlaySettingsProfile(profile);
                          }}
                          className="btn-secondary min-h-[44px] px-3 text-xs"
                        >
                          Tính năng xem phim
                        </button>
                      </div>
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

          {token && childProfiles.length > 0 && activeChildProfile && (
            <div
              data-testid="child-unified-settings-group"
              className="mt-6 rounded-xl border-2 border-ink/35 bg-surface-muted/70 p-4 sm:p-5"
            >
              <div className="flex flex-col gap-3 border-b border-surface-border pb-4 sm:flex-row sm:items-center sm:justify-between">
                <div className="flex items-center gap-3 min-w-0">
                  <ChildAvatarBadge avatarKey={activeChildProfile.avatarKey} size="sm" />
                  <div className="min-w-0">
                    <p className="text-base font-bold text-ink">
                      Mọi cài đặt của {activeChildProfile.name} ở cùng một nơi
                    </p>
                    <p className="text-sm text-muted">
                      {zeroStandaloneFilms
                        ? 'Phim cần trả lời câu hỏi, nhiệm vụ đổi giờ và báo cáo học tập của bé.'
                        : 'Phim lẻ cuối tuần, phim cần trả lời câu hỏi, nhiệm vụ đổi giờ và báo cáo học tập của bé.'}
                    </p>
                  </div>
                </div>
                {childProfiles.length > 1 && (
                  <div className="flex flex-wrap gap-1.5" role="tablist" aria-label="Chọn bé để xem toàn bộ cài đặt">
                    {childProfiles.map((profile) => {
                      const active = profile.id === activeChildProfile.id;
                      return (
                        <button
                          key={profile.id}
                          type="button"
                          role="tab"
                          aria-selected={active}
                          data-testid={`unified-child-tab-${profile.id}`}
                          onClick={() => setSelectedChildId(profile.id)}
                          className={`inline-flex min-h-[44px] items-center gap-1.5 rounded-lg border px-3 py-1 text-xs font-bold cursor-pointer ${
                            active
                              ? 'border-ink bg-mint text-ink shadow-[2px_2px_0_#203328]'
                              : 'border-surface-border bg-paper-white text-muted hover:text-ink'
                          }`}
                        >
                          <ChildAvatarBadge avatarKey={profile.avatarKey} size="sm" />
                          <span>{profile.name}</span>
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>

              {!zeroStandaloneFilms && (
                <ParentWeekendVaultSection
                  token={token}
                  childProfiles={childProfiles}
                  activeProfileId={activeChildProfile.id}
                  sessionPin={activeSessionPin}
                  onPinVerified={rememberSessionPin}
                  onPinRejected={clearSessionPin}
                />
              )}
              <ParentVipSeriesSection
                token={token}
                childProfiles={childProfiles}
                activeProfileId={activeChildProfile.id}
                hideChildTabs
                onSelectProfile={setSelectedChildId}
                sessionPin={activeSessionPin}
                onPinVerified={rememberSessionPin}
                onPinRejected={clearSessionPin}
              />
              <ParentBountySection
                token={token}
                childProfiles={childProfiles}
                activeProfileId={activeChildProfile.id}
                sessionPin={activeSessionPin}
                onPinVerified={rememberSessionPin}
                onPinRejected={clearSessionPin}
                onScreenTimeUpdated={() => void loadScreenTimeOverview()}
              />
              <ParentQuizInsightsSection
                token={token}
                configs={quizConfigs}
                loading={quizLoading}
                loadError={quizLoadError}
                activeProfileId={activeChildProfile.id}
                hideChildTabs
                onSelectProfile={setSelectedChildId}
                sessionPin={activeSessionPin}
                onPinVerified={rememberSessionPin}
                onPinRejected={clearSessionPin}
                onGradeChanged={handleQuizConfigUpdated}
                onRetry={loadQuizData}
              />
            </div>
          )}

          {token && (
            <ParentQuizBankSection
              token={token}
              overview={quizOverview}
              loading={quizLoading}
              loadError={quizLoadError}
              childProfiles={childProfiles}
              quizConfigs={quizConfigs}
              onChildQuizUpdated={handleQuizConfigUpdated}
              sessionPin={activeSessionPin}
              onPinVerified={rememberSessionPin}
              onPinRejected={clearSessionPin}
              onRefresh={loadQuizData}
            />
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
              ? `Thời gian xem · ${dialog.profile.name}`
              : dialog.kind === 'grant'
              ? `Cho thêm thời gian · ${dialog.profile.name}`
              : 'Xóa hồ sơ trẻ em'
          }
          tone={dialog.kind === 'delete' || dialog.kind === 'disable' ? 'danger' : 'default'}
          size={dialog.kind === 'create' || dialog.kind === 'edit' || dialog.kind === 'screen-time' ? 'lg' : 'md'}
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
                    pattern="[0-9]*"
                    autoComplete="off"
                    autoFocus
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
                    autoFocus
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
                          className={`flex min-h-[68px] flex-col items-center justify-center gap-1 rounded-xl border-2 p-2 text-[0.6875rem] font-semibold cursor-pointer transition-colors ${
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
                <fieldset>
                  <legend className="mb-2 text-xs font-semibold text-ink">Thời gian xem mỗi ngày</legend>
                  <div className="mb-2 flex flex-wrap gap-1.5">
                    {DAILY_MINUTE_PRESETS.map((preset) => (
                      <button
                        key={preset}
                        type="button"
                        disabled={busy}
                        data-testid={`create-daily-preset-${preset}`}
                        onClick={() => {
                          setIsUnlimited(false);
                          setDailyMinutes(String(preset));
                        }}
                        className={`min-h-[44px] rounded-lg border px-3 text-xs font-bold cursor-pointer ${
                          !isUnlimited && Number(dailyMinutes) === preset
                            ? 'border-ink bg-mint text-ink'
                            : 'border-surface-border bg-paper-white text-muted'
                        }`}
                      >
                        {preset} phút
                      </button>
                    ))}
                    <button
                      type="button"
                      disabled={busy}
                      data-testid="create-daily-unlimited-button"
                      onClick={() => setIsUnlimited(true)}
                      className={`min-h-[44px] rounded-lg border px-3 text-xs font-bold cursor-pointer ${
                        isUnlimited
                          ? 'border-ink bg-mint text-ink'
                          : 'border-surface-border bg-paper-white text-muted'
                      }`}
                    >
                      Không giới hạn
                    </button>
                  </div>
                  {!isUnlimited && (
                    <input
                      id="child-profile-daily-minutes"
                      data-testid="create-daily-minutes-input"
                      type="number"
                      min={0}
                      max={600}
                      required
                      disabled={busy}
                      value={dailyMinutes}
                      onChange={(event) => setDailyMinutes(event.target.value)}
                      className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                    />
                  )}
                </fieldset>
                <div>
                  <label htmlFor="child-profile-grade" className="mb-1.5 block text-xs font-semibold text-ink">
                    Khối lớp của bé
                  </label>
                  <select
                    id="child-profile-grade"
                    data-testid="child-profile-grade-select"
                    disabled={busy}
                    value={profileQuizGrade}
                    onChange={(event) => setProfileQuizGrade(event.target.value as QuizGradeCode)}
                    className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                  >
                    {QUIZ_GRADES.map((item) => (
                      <option key={item.code} value={item.code}>
                        {item.label} · {item.description}
                      </option>
                    ))}
                  </select>
                </div>
                {activeAvailableSets.length > 0 && (
                  <fieldset>
                    <legend className="mb-2 text-xs font-semibold text-ink">Bộ câu hỏi dùng cho bé</legend>
                    <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                      {activeAvailableSets.map((set) => {
                        const checked = profileQuizSetIds.includes(set.id);
                        return (
                          <label
                            key={set.id}
                            className={`flex min-h-[44px] items-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold cursor-pointer ${
                              checked
                                ? 'border-ink bg-mint/50 text-ink'
                                : 'border-surface-border bg-paper-white text-muted hover:text-ink'
                            }`}
                          >
                            <input
                              type="checkbox"
                              data-testid={`create-quiz-set-${set.id}`}
                              checked={checked}
                              disabled={busy}
                              onChange={() => toggleProfileQuizSet(set.id)}
                              className="h-4 w-4 shrink-0 accent-brand"
                            />
                            <span className="min-w-0 flex-1 truncate">{set.name}</span>
                            <span className="shrink-0 rounded bg-surface-muted px-1.5 py-0.5 text-[0.625rem] font-bold text-muted">
                              {set.builtIn ? 'Sẵn có' : `${set.questionCount} câu`}
                            </span>
                          </label>
                        );
                      })}
                    </div>
                  </fieldset>
                )}
                {activeAvailableSets.length > 0 && (
                  <div
                    data-testid="create-quiz-samples"
                    className="rounded-xl border border-surface-border bg-surface-muted p-3 space-y-2"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-xs font-bold text-ink">Xem thử vài câu hỏi của bé</span>
                      <button
                        type="button"
                        disabled={loadingProfileSamples || profileQuizSetIds.length === 0}
                        onClick={() => void loadProfileDialogSamples(profileQuizGrade, profileQuizSetIds)}
                        className="inline-flex min-h-[36px] items-center gap-1 rounded-md border border-surface-border bg-paper-white px-2.5 text-[0.6875rem] font-bold text-ink hover:bg-mint/40 cursor-pointer disabled:opacity-50"
                      >
                        <RefreshCw size={12} aria-hidden="true" />
                        Đổi câu mẫu
                      </button>
                    </div>
                    {loadingProfileSamples ? (
                      <p className="text-[0.6875rem] text-muted">Đang lấy câu hỏi mẫu…</p>
                    ) : profileQuizSamples.length === 0 ? (
                      <p className="text-[0.6875rem] text-muted">Chọn ít nhất 1 bộ câu hỏi để xem thử.</p>
                    ) : (
                      <ul className="space-y-2">
                        {profileQuizSamples.map((sample, index) => (
                          <li
                            key={`${sample.question}-${index}`}
                            className="rounded-lg border border-surface-border bg-paper-white p-2.5 text-xs"
                          >
                            <div className="flex flex-wrap items-center gap-1.5 text-[0.625rem] font-bold text-muted">
                              <span className="rounded bg-mint/60 px-1.5 py-0.5 text-ink">{sample.subjectLabel}</span>
                              <span>{sample.gradeLabel}</span>
                              {sample.setName && <span>· {sample.setName}</span>}
                            </div>
                            <p className="mt-1 font-bold text-ink">{sample.question}</p>
                            <p className="mt-1 text-[0.6875rem] text-muted">
                              {sample.options.map((opt) => `${opt.key}. ${opt.text}`).join('  |  ')}
                            </p>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                )}
                <div>
                  <label htmlFor="child-profile-pin" className="mb-1.5 block text-xs font-semibold text-ink">
                    Mã PIN phụ huynh
                  </label>
                  <input
                    id="child-profile-pin"
                    type="password"
                    inputMode="numeric"
                    pattern="[0-9]*"
                    enterKeyHint="done"
                    autoComplete="off"
                    maxLength={6}
                    required
                    disabled={busy}
                    value={pin}
                    onChange={(event) => setPin(sanitizePin(event.target.value))}
                    placeholder="••••"
                    className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                  />
                  {activeSessionPin && pin === activeSessionPin && (
                    <div
                      data-testid="session-pin-prefilled-note"
                      className="mt-1.5 flex items-center justify-between gap-2 text-[0.6875rem] text-brand-dark"
                    >
                      <span>Đã tự điền mã PIN vừa xác nhận trong phiên này.</span>
                      <button
                        type="button"
                        onClick={() => {
                          clearSessionPin();
                          setPin('');
                        }}
                        className="font-bold underline cursor-pointer"
                      >
                        Nhập lại
                      </button>
                    </div>
                  )}
                </div>
              </>
            )}

            {dialog.kind === 'screen-time' && (
              <>
                <fieldset>
                  <legend className="mb-2 text-xs font-semibold text-ink">Chọn nhanh thời gian xem mỗi ngày</legend>
                  <div className="flex flex-wrap gap-1.5">
                    {DAILY_MINUTE_PRESETS.map((preset) => (
                      <button
                        key={preset}
                        type="button"
                        disabled={busy}
                        data-testid={`screen-time-preset-${preset}`}
                        onClick={() => {
                          setIsUnlimited(false);
                          setDailyMinutes(String(preset));
                        }}
                        className={`min-h-[44px] rounded-lg border px-3 text-xs font-bold cursor-pointer ${
                          !isUnlimited && Number(dailyMinutes) === preset
                            ? 'border-ink bg-mint text-ink'
                            : 'border-surface-border bg-paper-white text-muted'
                        }`}
                      >
                        {preset} phút
                      </button>
                    ))}
                    <button
                      type="button"
                      disabled={busy}
                      data-testid="screen-time-unlimited-toggle"
                      aria-pressed={isUnlimited}
                      onClick={() => setIsUnlimited((prev) => !prev)}
                      className={`min-h-[44px] rounded-lg border px-3 text-xs font-bold cursor-pointer ${
                        isUnlimited
                          ? 'border-ink bg-mint text-ink'
                          : 'border-surface-border bg-paper-white text-muted'
                      }`}
                    >
                      Không giới hạn
                    </button>
                  </div>
                </fieldset>

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
                      required={!isUnlimited}
                      disabled={busy || isUnlimited}
                      value={isUnlimited ? '' : dailyMinutes}
                      placeholder={isUnlimited ? 'Không giới hạn' : '60'}
                      onChange={(event) => {
                        setIsUnlimited(false);
                        setDailyMinutes(event.target.value);
                      }}
                      className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none disabled:opacity-50"
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
                <div className="space-y-2">
                  <label className="flex min-h-[44px] items-center gap-2.5 rounded-lg border border-surface-border bg-surface-muted px-3 py-2 text-xs font-semibold text-ink cursor-pointer">
                    <input
                      type="checkbox"
                      data-testid="screen-time-disable-bonus-toggle"
                      checked={maxBonusMinutes.trim() !== '' && Number(maxBonusMinutes) === 0}
                      disabled={busy}
                      onChange={(event) => {
                        setMaxBonusMinutes(event.target.checked ? '0' : '30');
                      }}
                      className="h-4 w-4 shrink-0 accent-brand"
                    />
                    <span>Hết giờ là nghỉ (tắt cộng thêm phút từ câu hỏi trong ngày)</span>
                  </label>
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
                    pattern="[0-9]*"
                    enterKeyHint="done"
                    autoComplete="off"
                    maxLength={6}
                    required
                    disabled={busy}
                    value={pin}
                    onChange={(event) => setPin(sanitizePin(event.target.value))}
                    placeholder="••••"
                    className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                  />
                  {activeSessionPin && pin === activeSessionPin && (
                    <div
                      data-testid="session-pin-prefilled-note"
                      className="mt-1.5 flex items-center justify-between gap-2 text-[0.6875rem] text-brand-dark"
                    >
                      <span>Đã tự điền mã PIN vừa xác nhận trong phiên này.</span>
                      <button
                        type="button"
                        onClick={() => {
                          clearSessionPin();
                          setPin('');
                        }}
                        className="font-bold underline cursor-pointer"
                      >
                        Nhập lại
                      </button>
                    </div>
                  )}
                </div>
              </>
            )}

            {dialog.kind === 'grant' && (
              <>
                <p className="text-xs leading-relaxed text-muted">
                  Cộng thêm thời gian xem đột xuất trong hôm nay cho <strong>{dialog.profile.name}</strong> (tính riêng, không trừ vào trần thưởng câu hỏi).
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
                    pattern="[0-9]*"
                    enterKeyHint="done"
                    autoComplete="off"
                    maxLength={6}
                    required
                    disabled={busy}
                    value={pin}
                    onChange={(event) => setPin(sanitizePin(event.target.value))}
                    placeholder="••••"
                    className="w-full rounded-lg border border-surface-border bg-surface-muted px-3 py-2.5 text-sm text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
                  />
                  {activeSessionPin && pin === activeSessionPin && (
                    <div
                      data-testid="session-pin-prefilled-note"
                      className="mt-1.5 flex items-center justify-between gap-2 text-[0.6875rem] text-brand-dark"
                    >
                      <span>Đã tự điền mã PIN vừa xác nhận trong phiên này.</span>
                      <button
                        type="button"
                        onClick={() => {
                          clearSessionPin();
                          setPin('');
                        }}
                        className="font-bold underline cursor-pointer"
                      >
                        Nhập lại
                      </button>
                    </div>
                  )}
                </div>
              </>
            )}

            {dialog.kind === 'delete' && (
              <>
                <p className="text-xs leading-relaxed text-muted">
                  Xóa hẳn hồ sơ <strong>{dialog.profile.name}</strong>? Toàn bộ lịch sử xem, lịch sử trả lời câu hỏi, Xem sau, Yêu thích và điểm chấm riêng của hồ sơ này sẽ bị xóa.
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

            <div className="dialog-sticky-footer flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
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

      {quizSettingsProfile && token && (
        <ChildQuizSettingsDialog
          token={token}
          profile={quizSettingsProfile}
          currentConfig={quizConfigByProfile[quizSettingsProfile.id]}
          availableSets={quizOverview?.sets ?? []}
          sessionPin={activeSessionPin}
          onPinVerified={rememberSessionPin}
          onPinRejected={clearSessionPin}
          onSaved={(updated) => {
            handleQuizConfigUpdated(updated);
            const targetId = updated.profileId || quizSettingsProfile.id;
            const targetName = updated.name || quizSettingsProfile.name;
            setQuizSettingsProfile(null);
            setSelectedChildId(targetId);
            setRecentSavedChild({ id: targetId, name: targetName });
            setFeedback({
              type: 'success',
              text: `Đã lưu khối lớp và bộ câu hỏi cho ${targetName}.`,
            });
          }}
          onClose={() => setQuizSettingsProfile(null)}
        />
      )}

      {playSettingsProfile && token && (
        <PlayFeaturesSettings
          token={token}
          profile={playSettingsProfile}
          sessionPin={activeSessionPin}
          onPinVerified={rememberSessionPin}
          onPinRejected={clearSessionPin}
          onClose={() => setPlaySettingsProfile(null)}
          onSaved={(saved) => {
            const targetId = saved.profileId || playSettingsProfile.id;
            const targetName = saved.name || playSettingsProfile.name;
            setSelectedChildId(targetId);
            setRecentSavedChild({ id: targetId, name: targetName });
            setFeedback({
              type: 'success',
              text: `Đã lưu cài đặt tính năng xem phim cho ${targetName}.`,
            });
          }}
        />
      )}
    </section>
  );
}
