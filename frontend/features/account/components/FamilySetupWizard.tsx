import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { useAuth } from '@/lib/context/AuthContext';
import { ChildAvatarBadge } from '@/features/profiles/components/ChildAvatarBadge';
import {
  CHILD_AVATARS,
  MAX_CHILD_PROFILES,
  createChildProfile,
  enableParentMode,
  validateChildProfileName,
  validateParentPin,
  type ChildProfile,
} from '@/lib/services/profileService';
import {
  broadcastScreenTimeUpdated,
  validateScreenTimeSettings,
} from '@/lib/services/screenTimeService';
import {
  QUIZ_GRADES,
  fetchQuizSampleQuestions,
  fetchQuizSetsOverview,
  getQuizGradeLabel,
  type QuizGradeCode,
  type QuizSampleQuestion,
  type QuizSetsOverview,
} from '@/features/quiz/quizService';

const WIZARD_TIME_PRESETS = [20, 30, 45, 60] as const;

const GRADE_AGE_HINTS: Record<QuizGradeCode, string> = {
  MAU_GIAO: '4–5 tuổi',
  LOP_1: '6 tuổi',
  LOP_2: '7 tuổi',
  LOP_3: '8 tuổi',
  LOP_4: '9 tuổi',
  LOP_5: '10 tuổi',
};

type WizardStep = 'pin' | 'child' | 'time' | 'learning' | 'done';

export function FamilySetupWizard({
  onCancel,
  onCompleted,
  sessionPin = '',
  onPinVerified,
}: {
  onCancel?: () => void;
  onCompleted?: (created: ChildProfile, pin: string) => void;
  sessionPin?: string;
  onPinVerified?: (pin: string) => void;
}) {
  const {
    token,
    parentModeEnabled,
    childProfiles,
    refreshProfiles,
    selectChildProfile,
  } = useAuth();
  const navigate = useNavigate();

  const [step, setStep] = useState<WizardStep>(() => (parentModeEnabled ? 'child' : 'pin'));
  const [pin, setPin] = useState(sessionPin || '');
  const [confirmPin, setConfirmPin] = useState('');
  const [showPin, setShowPin] = useState(false);
  const [childName, setChildName] = useState('');
  const [avatarKey, setAvatarKey] = useState('bear');
  const [dailyMinutes, setDailyMinutes] = useState('30');
  const [isUnlimited, setIsUnlimited] = useState(false);
  const [quizGrade, setQuizGrade] = useState<QuizGradeCode>('LOP_1');
  const [quizSetIds, setQuizSetIds] = useState<number[]>([]);
  const [quizOverview, setQuizOverview] = useState<QuizSetsOverview | null>(null);
  const [samples, setSamples] = useState<QuizSampleQuestion[]>([]);
  const [loadingSamples, setLoadingSamples] = useState(false);
  const [busy, setBusy] = useState(false);
  const [switching, setSwitching] = useState(false);
  const [error, setError] = useState('');
  const [createdChild, setCreatedChild] = useState<ChildProfile | null>(null);

  useEffect(() => {
    if (sessionPin && !pin) {
      setPin(sessionPin);
    }
  }, [sessionPin, pin]);

  const loadQuizOverviewIfReady = useCallback(async () => {
    if (!token || !parentModeEnabled) return;
    try {
      const overview = await fetchQuizSetsOverview(token);
      setQuizOverview(overview);
      const builtInIds = overview.sets.filter((item) => item.active && item.builtIn).map((item) => item.id);
      const fallbackIds = overview.sets.filter((item) => item.active).map((item) => item.id);
      setQuizSetIds((prev) => (prev.length > 0 ? prev : builtInIds.length > 0 ? builtInIds : fallbackIds));
    } catch {}
  }, [token, parentModeEnabled]);

  useEffect(() => {
    void loadQuizOverviewIfReady();
  }, [loadQuizOverviewIfReady]);

  const loadSamples = useCallback(
    async (grade: QuizGradeCode, setIds: number[]) => {
      if (!token || !parentModeEnabled || setIds.length === 0) {
        setSamples([]);
        return;
      }
      setLoadingSamples(true);
      try {
        const list = await fetchQuizSampleQuestions(token, grade, setIds);
        setSamples(list.slice(0, 2));
      } catch {
        setSamples([]);
      } finally {
        setLoadingSamples(false);
      }
    },
    [token, parentModeEnabled]
  );

  useEffect(() => {
    if (step !== 'learning' || quizSetIds.length === 0) return;
    void loadSamples(quizGrade, quizSetIds);
  }, [step, quizGrade, quizSetIds.join(','), loadSamples]);

  const sanitizePin = (raw: string) => raw.replace(/[^0-9]/g, '').slice(0, 6);

  const visibleSteps: { key: Exclude<WizardStep, 'done'>; label: string }[] = parentModeEnabled
    ? [
        { key: 'child', label: 'Hồ sơ của bé' },
        { key: 'time', label: 'Giờ xem mỗi ngày' },
        { key: 'learning', label: 'Câu hỏi & Mặc định' },
      ]
    : [
        { key: 'pin', label: 'Mã PIN phụ huynh' },
        { key: 'child', label: 'Hồ sơ của bé' },
        { key: 'time', label: 'Giờ xem mỗi ngày' },
        { key: 'learning', label: 'Câu hỏi & Mặc định' },
      ];

  const currentStepIndex = visibleSteps.findIndex((item) => item.key === step);

  const handleEnablePinStep = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!token || busy) return;
    setError('');
    const pinErr = validateParentPin(pin);
    if (pinErr) {
      setError(pinErr);
      return;
    }
    if (pin !== confirmPin) {
      setError('Mã PIN nhập lại chưa khớp.');
      return;
    }
    setBusy(true);
    try {
      await enableParentMode(token, pin);
      onPinVerified?.(pin);
      await refreshProfiles();
      const overview = await fetchQuizSetsOverview(token).catch(() => null);
      if (overview) {
        setQuizOverview(overview);
        const builtInIds = overview.sets.filter((item) => item.active && item.builtIn).map((item) => item.id);
        const fallbackIds = overview.sets.filter((item) => item.active).map((item) => item.id);
        setQuizSetIds(builtInIds.length > 0 ? builtInIds : fallbackIds);
      }
      setStep('child');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Chưa bật được chế độ gia đình.');
    } finally {
      setBusy(false);
    }
  };

  const handleChildStep = (event: React.FormEvent) => {
    event.preventDefault();
    setError('');
    const nameErr = validateChildProfileName(childName);
    if (nameErr) {
      setError(nameErr);
      return;
    }
    setStep('time');
  };

  const handleTimeStep = (event: React.FormEvent) => {
    event.preventDefault();
    setError('');
    const parsedDaily = isUnlimited ? null : dailyMinutes.trim() === '' ? NaN : Number(dailyMinutes);
    const timeErr = validateScreenTimeSettings({ dailyMinutes: parsedDaily, unlimited: isUnlimited });
    if (timeErr) {
      setError(timeErr);
      return;
    }
    setStep('learning');
  };

  const toggleSet = (setId: number) => {
    setQuizSetIds((prev) => (prev.includes(setId) ? prev.filter((id) => id !== setId) : [...prev, setId]));
  };

  const handleSaveProfile = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!token || busy) return;
    setError('');
    const nameErr = validateChildProfileName(childName);
    if (nameErr) {
      setError(nameErr);
      setStep('child');
      return;
    }
    const parsedDaily = isUnlimited ? null : dailyMinutes.trim() === '' ? NaN : Number(dailyMinutes);
    const timeErr = validateScreenTimeSettings({ dailyMinutes: parsedDaily, unlimited: isUnlimited });
    if (timeErr) {
      setError(timeErr);
      setStep('time');
      return;
    }
    const activeSets = (quizOverview?.sets ?? []).filter((item) => item.active);
    if (activeSets.length > 0 && quizSetIds.length === 0) {
      setError('Ba mẹ chọn ít nhất 1 bộ câu hỏi cho bé nhé.');
      return;
    }
    const pinErr = validateParentPin(pin);
    if (pinErr) {
      setError(pinErr);
      return;
    }
    setBusy(true);
    try {
      const created = await createChildProfile(token, {
        pin,
        name: childName.trim(),
        avatarKey,
        dailyMinutes: isUnlimited ? null : parsedDaily,
        unlimited: isUnlimited,
        quizGrade,
        ...(quizSetIds.length > 0 ? { quizSetIds } : {}),
      });
      onPinVerified?.(pin);
      await refreshProfiles();
      broadcastScreenTimeUpdated(created.id);
      setCreatedChild(created);
      onCompleted?.(created, pin);
      setStep('done');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Chưa tạo được hồ sơ cho bé.');
    } finally {
      setBusy(false);
    }
  };

  const handleOpenCreatedChild = async () => {
    if (!createdChild || switching) return;
    setSwitching(true);
    setError('');
    try {
      const result = await selectChildProfile(createdChild.id);
      if (result.success) {
        navigate('/', { replace: true });
      } else {
        setError(result.message || 'Chưa mở được hồ sơ của bé.');
      }
    } finally {
      setSwitching(false);
    }
  };

  const handleAddAnotherChild = () => {
    setCreatedChild(null);
    setChildName('');
    const usedAvatars = new Set(childProfiles.map((item) => item.avatarKey));
    const nextAvatar = CHILD_AVATARS.find((item) => !usedAvatars.has(item.key))?.key ?? 'bunny';
    setAvatarKey(nextAvatar);
    setDailyMinutes('30');
    setIsUnlimited(false);
    setQuizGrade('LOP_1');
    setError('');
    setStep('child');
  };

  const activeAvailableSets = (quizOverview?.sets ?? []).filter((item) => item.active);
  const reachedMaxProfiles = childProfiles.length >= MAX_CHILD_PROFILES;

  return (
    <section
      data-testid="family-setup-wizard"
      aria-labelledby="family-wizard-heading"
      className="rounded-2xl border-2 border-ink bg-paper-white p-5 shadow-brand sm:p-7"
    >
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-surface-border pb-4">
        <div>
          <span className="inline-flex rounded-full border border-ink bg-mint px-2.5 py-0.5 text-xs font-bold text-ink">
            Thiết lập nhanh cho gia đình
          </span>
          <h2 id="family-wizard-heading" className="mt-2 font-display text-xl sm:text-2xl font-bold text-ink">
            {step === 'done' && createdChild
              ? `Hồ sơ của ${createdChild.name} đã sẵn sàng!`
              : 'Tạo không gian xem phim an toàn cho bé'}
          </h2>
        </div>
        {onCancel && step !== 'done' && (
          <button
            type="button"
            disabled={busy}
            onClick={onCancel}
            className="btn-secondary min-h-[44px] px-3.5 text-xs font-semibold"
          >
            Xem bảng cài đặt đầy đủ
          </button>
        )}
      </div>

      {step !== 'done' && (
        <ol
          aria-label="Các bước thiết lập"
          className="mt-4 grid grid-cols-2 gap-2 sm:flex sm:flex-wrap sm:items-center"
        >
          {visibleSteps.map((item, idx) => {
            const isCurrent = idx === currentStepIndex;
            const isDone = idx < currentStepIndex;
            return (
              <li
                key={item.key}
                aria-current={isCurrent ? 'step' : undefined}
                className={`flex min-h-[40px] items-center gap-2 rounded-lg border px-3 py-1.5 text-xs font-bold ${
                  isCurrent
                    ? 'border-2 border-ink bg-[#f2d18c]/65 text-ink'
                    : isDone
                    ? 'border-ink/40 bg-mint/60 text-ink'
                    : 'border-surface-border bg-surface-muted text-muted'
                }`}
              >
                <span className="grid h-5 w-5 shrink-0 place-items-center rounded-full border border-ink/40 bg-paper-white text-[0.6875rem]">
                  {isDone ? '✓' : idx + 1}
                </span>
                <span className="truncate">{item.label}</span>
              </li>
            );
          })}
        </ol>
      )}

      {error && (
        <div
          role="alert"
          data-testid="family-wizard-error"
          className="mt-4 rounded-xl border border-red-800/35 bg-red-50 px-4 py-3 text-sm text-red-900"
        >
          {error}
        </div>
      )}

      {step === 'pin' && (
        <form onSubmit={handleEnablePinStep} className="mt-5 space-y-4">
          <p className="text-base leading-relaxed text-muted">
            Bước 1: Đặt mã PIN từ 4 đến 6 chữ số để khóa cài đặt gia đình và xác nhận mỗi khi thoát khỏi hồ sơ trẻ em.
          </p>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label htmlFor="wizard-pin-input" className="mb-1.5 block text-sm font-semibold text-ink">
                Mã PIN phụ huynh (4–6 chữ số)
              </label>
              <input
                id="wizard-pin-input"
                data-testid="wizard-pin-input"
                type={showPin ? 'text' : 'password'}
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
                className="w-full rounded-lg border border-surface-border bg-surface-muted px-3.5 py-2.5 text-base text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
              />
            </div>
            <div>
              <label htmlFor="wizard-pin-confirm-input" className="mb-1.5 block text-sm font-semibold text-ink">
                Nhập lại mã PIN
              </label>
              <input
                id="wizard-pin-confirm-input"
                data-testid="wizard-pin-confirm-input"
                type={showPin ? 'text' : 'password'}
                inputMode="numeric"
                pattern="[0-9]*"
                autoComplete="off"
                maxLength={6}
                required
                disabled={busy}
                value={confirmPin}
                onChange={(event) => setConfirmPin(sanitizePin(event.target.value))}
                placeholder="Nhập lại mã PIN"
                className="w-full rounded-lg border border-surface-border bg-surface-muted px-3.5 py-2.5 text-base text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
              />
            </div>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
            <button
              type="button"
              onClick={() => setShowPin((prev) => !prev)}
              className="btn-secondary min-h-[44px] px-3.5 text-xs font-semibold"
            >
              {showPin ? 'Ẩn mã PIN' : 'Hiện mã PIN'}
            </button>
            <button
              type="submit"
              data-testid="wizard-pin-next"
              disabled={busy}
              className="btn-primary min-h-[44px] px-6 text-sm font-bold"
            >
              {busy ? 'Đang lưu mã PIN…' : 'Tiếp tục chọn hồ sơ bé'}
            </button>
          </div>
        </form>
      )}

      {step === 'child' && (
        <form onSubmit={handleChildStep} className="mt-5 space-y-5">
          <p className="text-base leading-relaxed text-muted">
            Đặt tên thân mật ở nhà và chọn hình đại diện để bé nhận ra góc xem phim riêng của bé ngay khi mở Dippie.
          </p>
          <div>
            <label htmlFor="wizard-child-name-input" className="mb-1.5 block text-sm font-semibold text-ink">
              Tên gọi của bé
            </label>
            <input
              id="wizard-child-name-input"
              data-testid="wizard-child-name-input"
              type="text"
              maxLength={50}
              autoFocus
              required
              value={childName}
              onChange={(event) => setChildName(event.target.value)}
              placeholder="Ví dụ: Bé Bông, Bé Khoai…"
              className="w-full rounded-lg border border-surface-border bg-surface-muted px-3.5 py-2.5 text-base text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
            />
          </div>

          <fieldset>
            <legend className="mb-2 text-sm font-semibold text-ink">Hình đại diện yêu thích</legend>
            <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
              {CHILD_AVATARS.map((item) => {
                const selected = avatarKey === item.key;
                return (
                  <button
                    key={item.key}
                    type="button"
                    data-testid={`wizard-avatar-${item.key}`}
                    aria-pressed={selected}
                    onClick={() => setAvatarKey(item.key)}
                    className={`flex min-h-[72px] items-center gap-2.5 rounded-xl border-2 p-2.5 text-left text-xs font-bold cursor-pointer transition-colors ${
                      selected
                        ? 'border-ink bg-mint/65 text-ink shadow-[2px_2px_0_#203328]'
                        : 'border-surface-border bg-paper-white text-muted hover:border-ink/50 hover:text-ink'
                    }`}
                  >
                    <ChildAvatarBadge avatarKey={item.key} size="sm" />
                    <span className="truncate">{item.label}</span>
                  </button>
                );
              })}
            </div>
          </fieldset>

          <div className="flex flex-wrap items-center justify-end gap-2 pt-1">
            <button
              type="submit"
              data-testid="wizard-child-next"
              className="btn-primary min-h-[44px] px-6 text-sm font-bold"
            >
              Tiếp tục chọn giờ xem
            </button>
          </div>
        </form>
      )}

      {step === 'time' && (
        <form onSubmit={handleTimeStep} className="mt-5 space-y-5">
          <p className="text-base leading-relaxed text-muted">
            Chọn thời lượng xem cơ bản mỗi ngày cho <strong>{childName.trim() || 'bé'}</strong>. Mức gợi ý cho trẻ nhỏ là 30 phút/ngày, ba mẹ có thể điều chỉnh bất cứ lúc nào.
          </p>

          <fieldset>
            <legend className="mb-2 text-sm font-semibold text-ink">Chọn nhanh thời lượng mỗi ngày</legend>
            <div className="flex flex-wrap gap-2">
              {WIZARD_TIME_PRESETS.map((preset) => {
                const active = !isUnlimited && Number(dailyMinutes) === preset;
                return (
                  <button
                    key={preset}
                    type="button"
                    data-testid={`wizard-daily-preset-${preset}`}
                    aria-pressed={active}
                    onClick={() => {
                      setIsUnlimited(false);
                      setDailyMinutes(String(preset));
                    }}
                    className={`min-h-[44px] rounded-lg border-2 px-4 text-sm font-bold cursor-pointer ${
                      active
                        ? 'border-ink bg-mint text-ink shadow-[2px_2px_0_#203328]'
                        : 'border-surface-border bg-paper-white text-muted hover:border-ink/50 hover:text-ink'
                    }`}
                  >
                    {preset} phút{preset === 30 ? ' (Gợi ý)' : ''}
                  </button>
                );
              })}
              <button
                type="button"
                data-testid="wizard-daily-unlimited"
                aria-pressed={isUnlimited}
                onClick={() => setIsUnlimited(true)}
                className={`min-h-[44px] rounded-lg border-2 px-4 text-sm font-bold cursor-pointer ${
                  isUnlimited
                    ? 'border-ink bg-mint text-ink shadow-[2px_2px_0_#203328]'
                    : 'border-surface-border bg-paper-white text-muted hover:border-ink/50 hover:text-ink'
                }`}
              >
                Không giới hạn
              </button>
            </div>
          </fieldset>

          {!isUnlimited && (
            <div className="max-w-xs">
              <label htmlFor="wizard-daily-minutes-input" className="mb-1.5 block text-sm font-semibold text-ink">
                Hoặc tự nhập số phút mỗi ngày (0–600 phút)
              </label>
              <input
                id="wizard-daily-minutes-input"
                data-testid="wizard-daily-minutes-input"
                type="number"
                min={0}
                max={600}
                required
                value={dailyMinutes}
                onChange={(event) => setDailyMinutes(event.target.value)}
                className="w-full rounded-lg border border-surface-border bg-surface-muted px-3.5 py-2.5 text-base text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
              />
            </div>
          )}

          <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
            <button
              type="button"
              onClick={() => setStep('child')}
              className="btn-secondary min-h-[44px] px-4 text-sm"
            >
              Quay lại
            </button>
            <button
              type="submit"
              data-testid="wizard-time-next"
              className="btn-primary min-h-[44px] px-6 text-sm font-bold"
            >
              Tiếp tục chọn câu hỏi học tập
            </button>
          </div>
        </form>
      )}

      {step === 'learning' && (
        <form onSubmit={handleSaveProfile} className="mt-5 space-y-5">
          <p className="text-base leading-relaxed text-muted">
            Chọn độ tuổi / khối lớp và các chủ đề câu hỏi để bé vừa xem hoạt hình vừa ôn bài nhẹ nhàng.
          </p>

          <fieldset>
            <legend className="mb-2 text-sm font-semibold text-ink">Độ tuổi &amp; khối lớp của bé</legend>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {QUIZ_GRADES.map((grade) => {
                const selected = quizGrade === grade.code;
                return (
                  <button
                    key={grade.code}
                    type="button"
                    data-testid={`wizard-grade-${grade.code}`}
                    aria-pressed={selected}
                    onClick={() => setQuizGrade(grade.code)}
                    className={`flex min-h-[56px] flex-col items-start justify-center rounded-xl border-2 px-3.5 py-2.5 text-left cursor-pointer ${
                      selected
                        ? 'border-ink bg-mint/65 text-ink shadow-[2px_2px_0_#203328]'
                        : 'border-surface-border bg-paper-white text-ink hover:border-ink/50'
                    }`}
                  >
                    <span className="text-sm font-bold">
                      {grade.label} · {GRADE_AGE_HINTS[grade.code]}
                    </span>
                    <span className="mt-0.5 text-xs text-muted">{grade.description}</span>
                  </button>
                );
              })}
            </div>
          </fieldset>

          {activeAvailableSets.length > 0 && (
            <fieldset>
              <legend className="mb-2 text-sm font-semibold text-ink">Bộ câu hỏi bật sẵn cho bé</legend>
              <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                {activeAvailableSets.map((set) => {
                  const checked = quizSetIds.includes(set.id);
                  return (
                    <label
                      key={set.id}
                      className={`flex min-h-[48px] items-center gap-2.5 rounded-lg border px-3.5 py-2 text-sm font-semibold cursor-pointer ${
                        checked
                          ? 'border-ink bg-mint/50 text-ink'
                          : 'border-surface-border bg-paper-white text-muted hover:text-ink'
                      }`}
                    >
                      <input
                        type="checkbox"
                        data-testid={`wizard-quiz-set-${set.id}`}
                        checked={checked}
                        disabled={busy}
                        onChange={() => toggleSet(set.id)}
                        className="h-4 w-4 shrink-0 accent-brand"
                      />
                      <span className="min-w-0 flex-1 truncate">{set.name}</span>
                      <span className="shrink-0 rounded bg-surface-muted px-2 py-0.5 text-xs font-bold text-muted">
                        {set.builtIn ? 'Có sẵn' : `${set.questionCount} câu`}
                      </span>
                    </label>
                  );
                })}
              </div>
            </fieldset>
          )}

          {samples.length > 0 && (
            <div
              data-testid="wizard-quiz-samples"
              className="rounded-xl border border-surface-border bg-surface-muted p-3.5 space-y-2"
            >
              <p className="text-xs font-bold text-ink">
                Ví dụ câu hỏi bé sẽ gặp ({getQuizGradeLabel(quizGrade)}):
              </p>
              {loadingSamples ? (
                <p className="text-xs text-muted">Đang tải câu hỏi mẫu…</p>
              ) : (
                <ul className="space-y-2">
                  {samples.map((sample, idx) => (
                    <li
                      key={`${sample.question}-${idx}`}
                      className="rounded-lg border border-surface-border bg-paper-white p-2.5 text-xs"
                    >
                      <p className="font-bold text-ink">{sample.question}</p>
                      <p className="mt-1 text-muted">
                        {sample.options.map((opt) => `${opt.key}. ${opt.text}`).join(' · ')}
                      </p>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}

          <div
            data-testid="family-setup-defaults-summary"
            className="rounded-xl border-2 border-ink bg-[#fef3c7]/55 p-4 space-y-2"
          >
            <h3 className="text-sm font-bold text-ink">
              Các thiết lập mặc định sẽ áp dụng cho {childName.trim() || 'bé'} trước khi lưu
            </h3>
            <ul className="space-y-1.5 text-sm leading-relaxed text-ink">
              <li>
                • <strong>Giờ xem cơ bản mỗi ngày:</strong>{' '}
                {isUnlimited ? 'Không giới hạn' : `${dailyMinutes} phút/ngày`}
              </li>
              <li>
                • <strong>Thưởng trả lời câu hỏi đúng:</strong> +5 phút mỗi câu đúng (tối đa +30 phút thưởng/ngày)
              </li>
              <li>
                • <strong>Nhắc nghỉ mắt cùng Dieppie:</strong> Đang bật · nhắc nghỉ mắt 5 phút sau mỗi 20 phút xem liên tục
              </li>
              <li>
                • <strong>Tương tác khi xem:</strong> Rương câu hỏi bất ngờ và câu hỏi nhớ nội dung tập phim đều bật sẵn
              </li>
              <li>
                • <strong>Phim lẻ cuối tuần:</strong> Mở vào thứ Bảy &amp; Chủ nhật khi bé giữ đúng giờ xem trong tuần (ba mẹ luôn mở sớm được bằng mã PIN)
              </li>
            </ul>
            <p className="text-xs text-muted">
              Mọi mục trên đều có thể chỉnh riêng từng bé ở trang Gia đình sau khi lưu.
            </p>
          </div>

          {!sessionPin && (
            <div className="max-w-xs">
              <label htmlFor="wizard-confirm-save-pin" className="mb-1.5 block text-sm font-semibold text-ink">
                Nhập mã PIN phụ huynh để lưu hồ sơ
              </label>
              <input
                id="wizard-confirm-save-pin"
                data-testid="wizard-save-pin-input"
                type={showPin ? 'text' : 'password'}
                inputMode="numeric"
                pattern="[0-9]*"
                autoComplete="off"
                maxLength={6}
                required
                disabled={busy}
                value={pin}
                onChange={(event) => setPin(sanitizePin(event.target.value))}
                placeholder="••••"
                className="w-full rounded-lg border border-surface-border bg-surface-muted px-3.5 py-2.5 text-base text-ink focus:border-ink focus:bg-paper-white focus:outline-none"
              />
            </div>
          )}

          <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
            <button
              type="button"
              disabled={busy}
              onClick={() => setStep('time')}
              className="btn-secondary min-h-[44px] px-4 text-sm"
            >
              Quay lại
            </button>
            <button
              type="submit"
              data-testid="wizard-save-button"
              disabled={busy}
              className="btn-primary min-h-[44px] px-6 text-sm font-bold"
            >
              {busy ? 'Đang tạo hồ sơ…' : 'Lưu hồ sơ của bé'}
            </button>
          </div>
        </form>
      )}

      {step === 'done' && createdChild && (
        <div data-testid="family-setup-complete" className="mt-5 space-y-5">
          <div className="flex items-center gap-4 rounded-xl border border-ink/30 bg-mint/40 p-4">
            <ChildAvatarBadge avatarKey={createdChild.avatarKey} size="lg" />
            <div>
              <p className="text-base font-bold text-ink">{createdChild.name}</p>
              <p className="mt-0.5 text-sm text-muted">
                {isUnlimited ? 'Không giới hạn giờ xem' : `${dailyMinutes} phút/ngày`} · {getQuizGradeLabel(quizGrade)} · Đã bật nhắc nghỉ mắt cùng Dieppie
              </p>
            </div>
          </div>

          <p className="text-base leading-relaxed text-muted">
            Ba mẹ có thể chuyển ngay sang góc xem phim của <strong>{createdChild.name}</strong> hoặc tiếp tục tạo thêm hồ sơ cho bé khác trong nhà.
          </p>

          <div className="flex flex-wrap items-center gap-3">
            <button
              type="button"
              data-testid="wizard-open-child-profile"
              disabled={switching}
              onClick={handleOpenCreatedChild}
              className="btn-primary min-h-[44px] px-6 text-sm font-bold"
            >
              {switching ? 'Đang mở hồ sơ…' : 'Mở hồ sơ của bé'}
            </button>
            <button
              type="button"
              data-testid="wizard-add-another-child"
              disabled={switching || reachedMaxProfiles}
              onClick={handleAddAnotherChild}
              className="btn-secondary min-h-[44px] px-5 text-sm font-semibold"
            >
              Thêm bé khác
            </button>
            {onCancel && (
              <button
                type="button"
                data-testid="wizard-view-family-settings"
                disabled={switching}
                onClick={onCancel}
                className="btn-ghost min-h-[44px] px-4 text-sm"
              >
                Xem cài đặt Gia đình
              </button>
            )}
          </div>
        </div>
      )}
    </section>
  );
}
