import { useEffect, useRef, useState } from 'react';
import {
  PLAY_FEATURES_LOCKED_EVENT,
  SCREEN_TIME_HEARTBEAT_EVENT,
  fetchPlayFeaturesStatus,
  missLootDrop,
  openLootDrop,
  spawnLootDrop,
  startWatchPetRest,
  submitLootDropAnswer,
  type LootDropAnswerResult,
  type LootDropQuiz,
  type LootDropSpawn,
  type PlayFeaturesStatus,
  type WatchPetStatus,
} from '@/lib/services/playFeaturesService';
import type { ScreenTimeStatus } from '@/lib/services/screenTimeService';
import type { QuizOptionKey } from '@/features/quiz/quizService';

export type LootFeedbackBanner = {
  kind: 'success' | 'miss';
  text: string;
  explanation?: string | null;
};

export function usePlayFeatures({
  enabled,
  token,
  seriesId,
  isPlaying,
  isScreenTimeLocked,
  isBlockedByHigherOverlay = false,
  onScreenTimeUpdated,
}: {
  enabled: boolean;
  token: string | null;
  seriesId?: string | null;
  isPlaying: boolean;
  isScreenTimeLocked: boolean;
  isBlockedByHigherOverlay?: boolean;
  onScreenTimeUpdated?: (next: ScreenTimeStatus) => void;
}) {
  const [status, setStatus] = useState<PlayFeaturesStatus | null>(null);
  const [restCountdown, setRestCountdown] = useState(0);
  const [activeDrop, setActiveDrop] = useState<LootDropSpawn | null>(null);
  const [dropSecondsLeft, setDropSecondsLeft] = useState(0);
  const [activeLootQuiz, setActiveLootQuiz] = useState<LootDropQuiz | null>(null);
  const [lootQuizSecondsLeft, setLootQuizSecondsLeft] = useState(0);
  const [lootSelectedOption, setLootSelectedOption] = useState<QuizOptionKey | null>(null);
  const [lootRetryError, setLootRetryError] = useState<string | null>(null);
  const [lootFeedback, setLootFeedback] = useState<LootFeedbackBanner | null>(null);
  const [lootOpening, setLootOpening] = useState(false);
  const [lootSubmitting, setLootSubmitting] = useState(false);

  const scopeKey = enabled && token ? `${token}:${seriesId || ''}` : '';
  const scopeKeyRef = useRef(scopeKey);
  scopeKeyRef.current = scopeKey;
  const tokenRef = useRef(token);
  tokenRef.current = token;
  const seriesIdRef = useRef(seriesId);
  seriesIdRef.current = seriesId;
  const spawningRef = useRef(false);
  const onScreenTimeUpdatedRef = useRef(onScreenTimeUpdated);
  onScreenTimeUpdatedRef.current = onScreenTimeUpdated;

  const refreshStatus = async (): Promise<PlayFeaturesStatus | null> => {
    const currentScope = scopeKeyRef.current;
    const currentToken = tokenRef.current;
    if (!currentScope || !currentToken) {
      setStatus(null);
      return null;
    }
    try {
      const next = await fetchPlayFeaturesStatus(currentToken, seriesIdRef.current);
      if (scopeKeyRef.current !== currentScope) return null;
      setStatus(next.active ? next : null);
      setRestCountdown(next.watchPet.resting ? next.watchPet.restRemainingSeconds : 0);
      return next;
    } catch {
      return null;
    }
  };
  const refreshStatusRef = useRef(refreshStatus);
  refreshStatusRef.current = refreshStatus;

  useEffect(() => {
    if (!scopeKey) {
      setStatus(null);
      setRestCountdown(0);
      setActiveDrop(null);
      setActiveLootQuiz(null);
      setLootSelectedOption(null);
      setLootRetryError(null);
      setLootFeedback(null);
      return;
    }
    void refreshStatusRef.current();
  }, [scopeKey]);

  useEffect(() => {
    if (!scopeKey || typeof window === 'undefined' || typeof window.addEventListener !== 'function') {
      return;
    }
    const handleRefresh = () => {
      void refreshStatusRef.current();
    };
    window.addEventListener(SCREEN_TIME_HEARTBEAT_EVENT, handleRefresh);
    window.addEventListener(PLAY_FEATURES_LOCKED_EVENT, handleRefresh);
    return () => {
      window.removeEventListener(SCREEN_TIME_HEARTBEAT_EVENT, handleRefresh);
      window.removeEventListener(PLAY_FEATURES_LOCKED_EVENT, handleRefresh);
    };
  }, [scopeKey]);

  const watchPet = status?.watchPet ?? null;
  const isPetLocked = Boolean(watchPet?.enabled && (watchPet.fainted || watchPet.resting));
  const isLootSuppressed = Boolean(isScreenTimeLocked || isPetLocked || isBlockedByHigherOverlay);
  const isLootSuppressedRef = useRef(isLootSuppressed);
  isLootSuppressedRef.current = isLootSuppressed;

  useEffect(() => {
    if (!isLootSuppressed) return;
    setActiveDrop(null);
    setDropSecondsLeft(0);
    setActiveLootQuiz(null);
    setLootQuizSecondsLeft(0);
    setLootSelectedOption(null);
    setLootRetryError(null);
    setLootFeedback(null);
  }, [isLootSuppressed]);

  useEffect(() => {
    if (!watchPet?.resting || restCountdown <= 0) return;
    const timer = setTimeout(() => {
      setRestCountdown((prev) => {
        if (prev <= 1) {
          void refreshStatusRef.current();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearTimeout(timer);
  }, [watchPet?.resting, restCountdown]);

  useEffect(() => {
    if (
      !scopeKey ||
      !token ||
      !isPlaying ||
      isLootSuppressed ||
      !status?.loot.enabled ||
      !status?.loot.ready ||
      activeDrop ||
      activeLootQuiz ||
      spawningRef.current
    ) {
      return;
    }
    spawningRef.current = true;
    void spawnLootDrop(token)
      .then((spawned) => {
        if (scopeKeyRef.current !== scopeKey || isLootSuppressedRef.current || !spawned.dropToken) return;
        setActiveDrop(spawned);
        setDropSecondsLeft(spawned.visibleSeconds);
        setStatus((prev) =>
          prev ? { ...prev, loot: { ...prev.loot, ready: false, combo: spawned.combo } } : prev
        );
      })
      .catch(() => {})
      .finally(() => {
        spawningRef.current = false;
      });
  }, [
    scopeKey,
    token,
    isPlaying,
    isLootSuppressed,
    status?.loot.enabled,
    status?.loot.ready,
    activeDrop,
    activeLootQuiz,
  ]);

  useEffect(() => {
    if (!activeDrop || activeLootQuiz || dropSecondsLeft <= 0) return;
    const timer = setTimeout(() => {
      if (dropSecondsLeft <= 1) {
        const missedToken = activeDrop.dropToken;
        setActiveDrop(null);
        setDropSecondsLeft(0);
        if (tokenRef.current) {
          void missLootDrop(tokenRef.current, missedToken)
            .then((loot) => {
              setStatus((prev) => (prev ? { ...prev, loot } : prev));
            })
            .catch(() => {});
        }
      } else {
        setDropSecondsLeft((prev) => prev - 1);
      }
    }, 1000);
    return () => clearTimeout(timer);
  }, [activeDrop, activeLootQuiz, dropSecondsLeft]);

  useEffect(() => {
    if (!activeLootQuiz || lootQuizSecondsLeft <= 0 || lootSubmitting || lootRetryError) return;
    const timer = setTimeout(() => {
      if (lootQuizSecondsLeft <= 1) {
        const expiredToken = activeLootQuiz.token;
        setActiveLootQuiz(null);
        setLootQuizSecondsLeft(0);
        setLootSelectedOption(null);
        setLootRetryError(null);
        setLootFeedback({ kind: 'miss', text: 'Hết giờ mở rương rồi! Hẹn bé ở rương báu sau nhé.' });
        if (tokenRef.current) {
          void missLootDrop(tokenRef.current, expiredToken)
            .then((loot) => {
              setStatus((prev) => (prev ? { ...prev, loot } : prev));
            })
            .catch(() => {});
        }
      } else {
        setLootQuizSecondsLeft((prev) => prev - 1);
      }
    }, 1000);
    return () => clearTimeout(timer);
  }, [activeLootQuiz, lootQuizSecondsLeft, lootSubmitting, lootRetryError]);

  useEffect(() => {
    if (!lootFeedback) return;
    const timer = setTimeout(() => setLootFeedback(null), 5000);
    return () => clearTimeout(timer);
  }, [lootFeedback]);

  const applyWatchPetStatus = (nextPet: WatchPetStatus) => {
    setStatus((prev) => (prev ? { ...prev, watchPet: nextPet } : prev));
    setRestCountdown(nextPet.resting ? nextPet.restRemainingSeconds : 0);
  };

  const handleStartRest = async () => {
    if (!tokenRef.current) return null;
    const nextPet = await startWatchPetRest(tokenRef.current);
    applyWatchPetStatus(nextPet);
    return nextPet;
  };

  const handleOpenLootChest = async () => {
    if (!tokenRef.current || !activeDrop || lootOpening) return;
    const dropToken = activeDrop.dropToken;
    setLootOpening(true);
    try {
      const quiz = await openLootDrop(tokenRef.current, dropToken);
      setActiveDrop(null);
      setDropSecondsLeft(0);
      setLootSelectedOption(null);
      setLootRetryError(null);
      setActiveLootQuiz(quiz);
      setLootQuizSecondsLeft(quiz.timerSeconds);
    } catch (err: unknown) {
      setActiveDrop(null);
      setDropSecondsLeft(0);
      setLootFeedback({
        kind: 'miss',
        text: err instanceof Error ? err.message : 'Rương báu đã bay mất rồi.',
      });
    } finally {
      setLootOpening(false);
    }
  };

  const handleSubmitLootAnswer = async (option: QuizOptionKey): Promise<LootDropAnswerResult | null> => {
    if (!tokenRef.current || !activeLootQuiz || lootSubmitting) return null;
    const quizToken = activeLootQuiz.token;
    setLootSelectedOption(option);
    setLootRetryError(null);
    setLootSubmitting(true);
    try {
      const result = await submitLootDropAnswer(tokenRef.current, quizToken, option);
      setActiveLootQuiz(null);
      setLootQuizSecondsLeft(0);
      setLootSelectedOption(null);
      setLootRetryError(null);
      setStatus((prev) =>
        prev ? { ...prev, loot: { ...prev.loot, ready: false, combo: result.combo } } : prev
      );
      onScreenTimeUpdatedRef.current?.(result.status);
      setLootFeedback({
        kind: result.correct ? 'success' : 'miss',
        text:
          result.message ||
          (result.correct
            ? `Đúng rồi! Bé được cộng thêm ${result.earnedMinutes} phút.`
            : result.correctOption && result.correctText
            ? `Gần đúng rồi! Đáp án là ${result.correctOption}: ${result.correctText} nè`
            : 'Gần đúng rồi! Hẹn bé ở rương sau nhé.'),
        explanation: !result.correct ? result.explanation : null,
      });
      return result;
    } catch (err: unknown) {
      const errStatus =
        typeof (err as { status?: unknown })?.status === 'number'
          ? (err as { status: number }).status
          : -1;
      if (errStatus === 0 || errStatus >= 500) {
        setLootRetryError(
          'Mạng hoặc hệ thống đang bận nên chưa gửi được câu trả lời. Đáp án của bé vẫn được giữ nguyên, bé thử gửi lại hoặc đóng rương xem tiếp nhé.'
        );
        return null;
      }
      setActiveLootQuiz(null);
      setLootQuizSecondsLeft(0);
      setLootSelectedOption(null);
      setLootRetryError(null);
      setLootFeedback({
        kind: 'miss',
        text: err instanceof Error ? err.message : 'Rương báu đã hết giờ rồi.',
      });
      return null;
    } finally {
      setLootSubmitting(false);
    }
  };

  const handleDismissLootQuiz = () => {
    setActiveLootQuiz(null);
    setLootQuizSecondsLeft(0);
    setLootSelectedOption(null);
    setLootRetryError(null);
  };

  const markRecapUnlocked = (targetEpisodeId: string | null) => {
    if (!targetEpisodeId) return;
    setStatus((prev) => {
      if (!prev) return prev;
      const pendingGates = prev.recap.pendingGates.filter((gate) => gate.targetEpisodeId !== targetEpisodeId);
      const unlockedEpisodeIds = prev.recap.unlockedEpisodeIds.includes(targetEpisodeId)
        ? prev.recap.unlockedEpisodeIds
        : [...prev.recap.unlockedEpisodeIds, targetEpisodeId];
      return {
        ...prev,
        recap: {
          ...prev.recap,
          pendingGates,
          unlockedEpisodeIds,
        },
      };
    });
  };

  const markRecapPendingLocally = (sourceEpisodeId: string, targetEpisodeId: string) => {
    setStatus((prev) => {
      if (!prev || !prev.recap.enabled) return prev;
      if (prev.recap.unlockedEpisodeIds.includes(targetEpisodeId)) return prev;
      if (prev.recap.pendingGates.some((gate) => gate.targetEpisodeId === targetEpisodeId)) return prev;
      return {
        ...prev,
        recap: {
          ...prev.recap,
          pendingGates: [...prev.recap.pendingGates, { sourceEpisodeId, targetEpisodeId }],
        },
      };
    });
  };

  return {
    status,
    watchPet,
    isPetLocked,
    restCountdown,
    activeDrop,
    dropSecondsLeft,
    activeLootQuiz,
    lootQuizSecondsLeft,
    lootSelectedOption,
    lootRetryError,
    lootFeedback,
    lootOpening,
    lootSubmitting,
    refreshStatus,
    applyWatchPetStatus,
    handleStartRest,
    handleOpenLootChest,
    handleSubmitLootAnswer,
    handleDismissLootQuiz,
    setLootSelectedOption,
    markRecapUnlocked,
    markRecapPendingLocally,
  };
}
