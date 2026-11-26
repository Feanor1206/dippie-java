import {
  NETWORK_ERROR_MESSAGE,
  apiFetch,
  formatRateLimitMessage,
  isLikelyNetworkError,
  withRequestTimeout,
} from '@/lib/services/apiRequest';
import type { ScreenTimeStatus } from '@/lib/services/screenTimeService';
import {
  normalizeQuizOptions,
  type QuizOption,
  type QuizOptionKey,
} from '@/features/quiz/quizService';

const API = '/api';
const DEFAULT_TIMEOUT_MS = 8000;

export const PLAY_FEATURES_LOCKED_EVENT = 'dippie:play-features-locked';
export const SCREEN_TIME_HEARTBEAT_EVENT = 'dippie:screen-time-heartbeat';
export const LOCK_REASON_HEADER = 'X-Dippie-Lock-Reason';

export type WatchPetStage = 'HAPPY' | 'SWEATY' | 'DIZZY' | 'FAINTED' | 'RESTING';
export type RecapQuizType = 'ORDER_SCENES' | 'CHARACTER_CHOICE' | 'TITLE_CHOICE';

export type PlaySettings = {
  watchPetEnabled: boolean;
  watchPetFaintMinutes: number;
  watchPetRestMinutes: number;
  lootDropEnabled: boolean;
  recapToNextEnabled: boolean;
};

export type ChildProfilePlaySettings = PlaySettings & {
  profileId: number;
  name: string;
  avatarKey: string;
};

export type UpdatePlaySettingsPayload = PlaySettings & {
  pin: string;
};

export type WatchPetStatus = {
  enabled: boolean;
  stage: WatchPetStage;
  continuousWatchSeconds: number;
  sweatyThresholdSeconds: number;
  dizzyThresholdSeconds: number;
  faintThresholdSeconds: number;
  restMinutes: number;
  fainted: boolean;
  resting: boolean;
  restLockUntil: string | null;
  restRemainingSeconds: number;
};

export type WatchPetMedicineChallenge = {
  token: string;
  question: string;
  options: QuizOption[];
  subject: string;
  subjectLabel: string;
  grade: string;
  gradeLabel: string;
  setName: string;
  expiresAt: string;
  expiresInSeconds: number;
};

export type WatchPetMedicineAnswerResult = {
  correct: boolean;
  correctOption: QuizOptionKey | null;
  correctText: string | null;
  explanation: string | null;
  message: string;
  watchPet: WatchPetStatus;
};

export type LootStatus = {
  enabled: boolean;
  ready: boolean;
  combo: number;
};

export type LootDropSpawn = {
  dropToken: string;
  visibleSeconds: number;
  expiresAt: string;
  combo: number;
};

export type LootDropQuiz = {
  token: string;
  question: string;
  options: QuizOption[];
  subject: string;
  subjectLabel: string;
  grade: string;
  gradeLabel: string;
  setName: string;
  timerSeconds: number;
  expiresAt: string;
  combo: number;
  baseRewardMinutes: number;
  potentialComboBonusMinutes: number;
};

export type LootDropAnswerResult = {
  correct: boolean;
  correctOption: QuizOptionKey | null;
  correctText: string | null;
  explanation: string | null;
  earnedSeconds: number;
  earnedMinutes: number;
  combo: number;
  comboBonusMinutes: number;
  bonusCapReached: boolean;
  message: string;
  status: ScreenTimeStatus;
};

export type RecapGateItem = {
  sourceEpisodeId: string;
  targetEpisodeId: string;
};

export type RecapStatus = {
  enabled: boolean;
  pendingGates: RecapGateItem[];
  unlockedEpisodeIds: string[];
};

export type PlayFeaturesStatus = {
  active: boolean;
  settings: PlaySettings;
  watchPet: WatchPetStatus;
  loot: LootStatus;
  recap: RecapStatus;
};

export type RecapQuizItem = {
  id: string;
  text: string;
};

export type RecapQuiz = {
  token: string;
  episodeId: string;
  episodeTitle: string;
  targetEpisodeId: string | null;
  quizType: RecapQuizType;
  prompt: string;
  items: RecapQuizItem[];
  expiresAt: string;
  expiresInSeconds: number;
};

export type RecapQuizAnswerResult = {
  correct: boolean;
  episodeId: string;
  targetEpisodeId: string | null;
  unlocked: boolean;
  message: string;
};

export type PlayFeaturesRequestOptions = {
  baseUrl?: string;
  signal?: AbortSignal;
  timeoutMs?: number;
};

export class PlayFeaturesApiError extends Error {
  status: number;
  retryAfterSeconds?: number;

  constructor(message: string, status: number, retryAfterSeconds?: number) {
    super(message);
    this.name = 'PlayFeaturesApiError';
    this.status = status;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

function normalizeOptionKey(raw: unknown): QuizOptionKey | null {
  if (typeof raw !== 'string') return null;
  const upper = raw.trim().toUpperCase();
  if (upper === 'A' || upper === 'B' || upper === 'C' || upper === 'D') return upper;
  return null;
}

export function notifyPlayFeaturesLocked(reason: string): void {
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function') {
    return;
  }
  if (typeof CustomEvent === 'function') {
    window.dispatchEvent(new CustomEvent(PLAY_FEATURES_LOCKED_EVENT, { detail: { reason } }));
  } else if (typeof Event === 'function') {
    window.dispatchEvent(new Event(PLAY_FEATURES_LOCKED_EVENT));
  }
}

export function notifyScreenTimeHeartbeat(): void {
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function' || typeof Event !== 'function') {
    return;
  }
  window.dispatchEvent(new Event(SCREEN_TIME_HEARTBEAT_EVENT));
}

export function formatCountdownSeconds(seconds: number): string {
  const safe = Math.max(0, Math.ceil(Number.isFinite(seconds) ? seconds : 0));
  const mins = Math.floor(safe / 60);
  const secs = safe % 60;
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
}

export function validatePlaySettings(input: PlaySettings): string | null {
  if (!Number.isInteger(input.watchPetFaintMinutes) || input.watchPetFaintMinutes < 10 || input.watchPetFaintMinutes > 60) {
    return 'Thời gian Dieppie nhắc nghỉ mắt cần từ 10 đến 60 phút.';
  }
  if (!Number.isInteger(input.watchPetRestMinutes) || input.watchPetRestMinutes < 3 || input.watchPetRestMinutes > 15) {
    return 'Thời gian cho Dieppie nghỉ mắt cần từ 3 đến 15 phút.';
  }
  return null;
}

function authHeaders(token: string, withBody = false): Record<string, string> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token.trim()}`,
  };
  if (withBody) {
    headers['Content-Type'] = 'application/json';
  }
  return headers;
}

function nonNeg(value: unknown, fallback = 0): number {
  const num = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(num) && num >= 0 ? num : fallback;
}

function normalizeStage(raw: unknown): WatchPetStage {
  if (raw === 'HAPPY' || raw === 'SWEATY' || raw === 'DIZZY' || raw === 'FAINTED' || raw === 'RESTING') {
    return raw;
  }
  return 'HAPPY';
}

function normalizeQuizType(raw: unknown): RecapQuizType {
  if (raw === 'ORDER_SCENES' || raw === 'CHARACTER_CHOICE' || raw === 'TITLE_CHOICE') {
    return raw;
  }
  return 'TITLE_CHOICE';
}

function normalizeWatchPet(raw: any): WatchPetStatus {
  const enabled = Boolean(raw?.enabled);
  return {
    enabled,
    stage: normalizeStage(raw?.stage),
    continuousWatchSeconds: nonNeg(raw?.continuousWatchSeconds, 0),
    sweatyThresholdSeconds: nonNeg(raw?.sweatyThresholdSeconds, 900),
    dizzyThresholdSeconds: nonNeg(raw?.dizzyThresholdSeconds, 1080),
    faintThresholdSeconds: nonNeg(raw?.faintThresholdSeconds, 1200),
    restMinutes: Math.max(3, Math.min(15, nonNeg(raw?.restMinutes, 5) || 5)),
    fainted: enabled && Boolean(raw?.fainted),
    resting: enabled && Boolean(raw?.resting),
    restLockUntil: typeof raw?.restLockUntil === 'string' ? raw.restLockUntil : null,
    restRemainingSeconds: nonNeg(raw?.restRemainingSeconds, 0),
  };
}

function normalizeSettings(raw: any): PlaySettings {
  return {
    watchPetEnabled: Boolean(raw?.watchPetEnabled),
    watchPetFaintMinutes: Math.max(10, Math.min(60, nonNeg(raw?.watchPetFaintMinutes, 20) || 20)),
    watchPetRestMinutes: Math.max(3, Math.min(15, nonNeg(raw?.watchPetRestMinutes, 5) || 5)),
    lootDropEnabled: Boolean(raw?.lootDropEnabled),
    recapToNextEnabled: Boolean(raw?.recapToNextEnabled),
  };
}

function normalizeChildSettings(raw: any): ChildProfilePlaySettings | null {
  if (!raw || typeof raw !== 'object') return null;
  const profileId = typeof raw.profileId === 'number' ? raw.profileId : Number(raw.profileId);
  if (!Number.isSafeInteger(profileId) || profileId <= 0) return null;
  return {
    profileId,
    name: typeof raw.name === 'string' ? raw.name.trim() : '',
    avatarKey: typeof raw.avatarKey === 'string' ? raw.avatarKey.trim() : 'bear',
    ...normalizeSettings(raw),
  };
}

function normalizeScreenTimeStatus(raw: any): ScreenTimeStatus {
  const unlimited = raw?.unlimited === true || (raw?.dailyMinutes === null && raw?.limited === false);
  const limited = !unlimited && Boolean(raw?.limited);
  const rawProfileId = raw?.profileId;
  const profileId =
    typeof rawProfileId === 'number' && Number.isSafeInteger(rawProfileId) && rawProfileId > 0 ? rawProfileId : null;
  const remainingSeconds =
    limited && raw?.remainingSeconds !== null && raw?.remainingSeconds !== undefined
      ? nonNeg(raw.remainingSeconds, 0)
      : null;
  const bonusRemainingSeconds = nonNeg(raw?.bonusRemainingSeconds, 0);
  const locked = limited && (Boolean(raw?.locked) || (remainingSeconds !== null && remainingSeconds <= 0));
  const dailyMinutes = unlimited || raw?.dailyMinutes === null ? null : nonNeg(raw?.dailyMinutes, 60);
  return {
    limited,
    unlimited,
    profileId,
    day: typeof raw?.day === 'string' ? raw.day : '',
    dailyMinutes,
    quizGrade: typeof raw?.quizGrade === 'string' && raw.quizGrade.trim() ? raw.quizGrade.trim() : null,
    quizGradeLabel:
      typeof raw?.quizGradeLabel === 'string' && raw.quizGradeLabel.trim() ? raw.quizGradeLabel.trim() : null,
    rewardMinutes: nonNeg(raw?.rewardMinutes, 5),
    maxBonusMinutes: nonNeg(raw?.maxBonusMinutes, 30),
    dailySeconds: nonNeg(raw?.dailySeconds, 0),
    watchedSeconds: nonNeg(raw?.watchedSeconds, 0),
    rewardSeconds: nonNeg(raw?.rewardSeconds, 0),
    grantSeconds: nonNeg(raw?.grantSeconds, 0),
    bountySeconds: nonNeg(raw?.bountySeconds, 0),
    remainingSeconds,
    locked,
    bonusCapSeconds: nonNeg(raw?.bonusCapSeconds, 0),
    bonusRemainingSeconds,
    bonusCapReached: limited && (Boolean(raw?.bonusCapReached) || bonusRemainingSeconds <= 0),
  };
}

async function runPlayFeaturesRequest<T>(
  action: (signal: AbortSignal) => Promise<T>,
  options: PlayFeaturesRequestOptions = {}
): Promise<T> {
  try {
    return await withRequestTimeout(action, {
      signal: options.signal,
      timeoutMs: options.timeoutMs ?? DEFAULT_TIMEOUT_MS,
    });
  } catch (error) {
    if (isLikelyNetworkError(error)) {
      throw new PlayFeaturesApiError(NETWORK_ERROR_MESSAGE, 0);
    }
    throw error;
  }
}

async function parseEnvelope(response: Response, fallbackMessage: string): Promise<{ data: any; message: string }> {
  const retryHeader = response.headers?.get?.('Retry-After');
  const retryAfter = retryHeader && /^\d+$/.test(retryHeader) ? parseInt(retryHeader, 10) : undefined;
  let json: any = null;
  try {
    json = await response.json();
  } catch {
    const message =
      response.status === 429
        ? formatRateLimitMessage('', fallbackMessage, retryAfter)
        : fallbackMessage;
    throw new PlayFeaturesApiError(message, response.status, retryAfter);
  }
  const serverMessage = typeof json?.message === 'string' && json.message.trim() ? json.message.trim() : '';
  if (!response.ok || json?.success !== true) {
    const message =
      response.status === 429
        ? formatRateLimitMessage(serverMessage, fallbackMessage, retryAfter)
        : serverMessage || fallbackMessage;
    throw new PlayFeaturesApiError(message, response.status, retryAfter);
  }
  return { data: json.data, message: serverMessage };
}

export async function fetchPlayFeaturesStatus(
  token: string,
  seriesId?: string | null,
  options: PlayFeaturesRequestOptions = {}
): Promise<PlayFeaturesStatus> {
  const query = seriesId ? `?seriesId=${encodeURIComponent(seriesId)}` : '';
  const url = `${options.baseUrl || API}/users/play-features/status${query}`;
  return runPlayFeaturesRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'GET',
        headers: authHeaders(token),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa tải được trạng thái tính năng trẻ em.');
      const pendingGates: RecapGateItem[] = Array.isArray(data?.recap?.pendingGates)
        ? data.recap.pendingGates
            .filter(
              (item: any) =>
                item &&
                typeof item.sourceEpisodeId === 'string' &&
                typeof item.targetEpisodeId === 'string'
            )
            .map((item: any) => ({
              sourceEpisodeId: item.sourceEpisodeId,
              targetEpisodeId: item.targetEpisodeId,
            }))
        : [];
      const unlockedEpisodeIds: string[] = Array.isArray(data?.recap?.unlockedEpisodeIds)
        ? data.recap.unlockedEpisodeIds.filter((id: unknown): id is string => typeof id === 'string')
        : [];
      return {
        active: Boolean(data?.active),
        settings: normalizeSettings(data?.settings),
        watchPet: normalizeWatchPet(data?.watchPet),
        loot: {
          enabled: Boolean(data?.loot?.enabled),
          ready: Boolean(data?.loot?.ready),
          combo: nonNeg(data?.loot?.combo, 0),
        },
        recap: {
          enabled: Boolean(data?.recap?.enabled),
          pendingGates,
          unlockedEpisodeIds,
        },
      };
    },
    options
  );
}

export async function startWatchPetRest(
  token: string,
  options: PlayFeaturesRequestOptions = {}
): Promise<WatchPetStatus> {
  const url = `${options.baseUrl || API}/users/play-features/watch-pet/rest`;
  return runPlayFeaturesRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa bắt đầu giờ nghỉ mắt cùng Dieppie được.');
      return normalizeWatchPet(data);
    },
    options
  );
}

export async function fetchWatchPetMedicineChallenge(
  token: string,
  options: PlayFeaturesRequestOptions = {}
): Promise<WatchPetMedicineChallenge> {
  const url = `${options.baseUrl || API}/users/play-features/watch-pet/medicine`;
  return runPlayFeaturesRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'GET',
        headers: authHeaders(token),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa lấy được câu hỏi để xem tiếp cùng Dieppie.');
      const quizOptions = normalizeQuizOptions(data?.options);
      const challengeToken = typeof data?.token === 'string' ? data.token.trim() : '';
      const question = typeof data?.question === 'string' ? data.question.trim() : '';
      if (!challengeToken || !question || quizOptions.length !== 4) {
        throw new PlayFeaturesApiError('Dữ liệu câu hỏi của Dieppie chưa hợp lệ.', 502);
      }
      return {
        token: challengeToken,
        question,
        options: quizOptions,
        subject: typeof data?.subject === 'string' ? data.subject : 'TOAN',
        subjectLabel: typeof data?.subjectLabel === 'string' ? data.subjectLabel : 'Toán',
        grade: typeof data?.grade === 'string' ? data.grade : 'LOP_1',
        gradeLabel: typeof data?.gradeLabel === 'string' ? data.gradeLabel : 'Lớp 1',
        setName: typeof data?.setName === 'string' ? data.setName : '',
        expiresAt: typeof data?.expiresAt === 'string' ? data.expiresAt : '',
        expiresInSeconds: nonNeg(data?.expiresInSeconds, 120),
      };
    },
    options
  );
}

export async function submitWatchPetMedicineAnswer(
  token: string,
  challengeToken: string,
  option: QuizOptionKey,
  options: PlayFeaturesRequestOptions = {}
): Promise<WatchPetMedicineAnswerResult> {
  const url = `${options.baseUrl || API}/users/play-features/watch-pet/medicine`;
  return runPlayFeaturesRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ token: challengeToken, option }),
        signal,
      });
      const { data, message } = await parseEnvelope(response, 'Chưa kiểm tra được câu trả lời cùng Dieppie.');
      return {
        correct: Boolean(data?.correct),
        correctOption: normalizeOptionKey(data?.correctOption),
        correctText: typeof data?.correctText === 'string' ? data.correctText : null,
        explanation: typeof data?.explanation === 'string' && data.explanation.trim() ? data.explanation : null,
        message,
        watchPet: normalizeWatchPet(data?.watchPet),
      };
    },
    options
  );
}

export async function spawnLootDrop(
  token: string,
  options: PlayFeaturesRequestOptions = {}
): Promise<LootDropSpawn> {
  const url = `${options.baseUrl || API}/users/play-features/loot/spawn`;
  return runPlayFeaturesRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa có rương báu mới.');
      return {
        dropToken: typeof data?.dropToken === 'string' ? data.dropToken : '',
        visibleSeconds: nonNeg(data?.visibleSeconds, 15) || 15,
        expiresAt: typeof data?.expiresAt === 'string' ? data.expiresAt : '',
        combo: nonNeg(data?.combo, 0),
      };
    },
    options
  );
}

export async function openLootDrop(
  token: string,
  dropToken: string,
  options: PlayFeaturesRequestOptions = {}
): Promise<LootDropQuiz> {
  const url = `${options.baseUrl || API}/users/play-features/loot/open`;
  return runPlayFeaturesRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ dropToken }),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Rương báu đã bay mất rồi.');
      const quizOptions = normalizeQuizOptions(data?.options);
      const quizToken = typeof data?.token === 'string' ? data.token.trim() : '';
      const question = typeof data?.question === 'string' ? data.question.trim() : '';
      if (!quizToken || !question || quizOptions.length !== 4) {
        throw new PlayFeaturesApiError('Dữ liệu câu hỏi rương báu chưa hợp lệ.', 502);
      }
      return {
        token: quizToken,
        question,
        options: quizOptions,
        subject: typeof data?.subject === 'string' ? data.subject : 'TOAN',
        subjectLabel: typeof data?.subjectLabel === 'string' ? data.subjectLabel : 'Toán',
        grade: typeof data?.grade === 'string' ? data.grade : 'LOP_1',
        gradeLabel: typeof data?.gradeLabel === 'string' ? data.gradeLabel : 'Lớp 1',
        setName: typeof data?.setName === 'string' ? data.setName : '',
        timerSeconds: nonNeg(data?.timerSeconds, 20) || 20,
        expiresAt: typeof data?.expiresAt === 'string' ? data.expiresAt : '',
        combo: nonNeg(data?.combo, 0),
        baseRewardMinutes: nonNeg(data?.baseRewardMinutes, 3) || 3,
        potentialComboBonusMinutes: nonNeg(data?.potentialComboBonusMinutes, 0),
      };
    },
    options
  );
}

export async function submitLootDropAnswer(
  token: string,
  answerToken: string,
  option: QuizOptionKey,
  options: PlayFeaturesRequestOptions = {}
): Promise<LootDropAnswerResult> {
  const url = `${options.baseUrl || API}/users/play-features/loot/answer`;
  return runPlayFeaturesRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ token: answerToken, option }),
        signal,
      });
      const { data, message } = await parseEnvelope(response, 'Chưa mở thưởng rương báu được.');
      return {
        correct: Boolean(data?.correct),
        correctOption: normalizeOptionKey(data?.correctOption),
        correctText: typeof data?.correctText === 'string' ? data.correctText : null,
        explanation: typeof data?.explanation === 'string' && data.explanation.trim() ? data.explanation : null,
        earnedSeconds: nonNeg(data?.earnedSeconds, 0),
        earnedMinutes: nonNeg(data?.earnedMinutes, 0),
        combo: nonNeg(data?.combo, 0),
        comboBonusMinutes: nonNeg(data?.comboBonusMinutes, 0),
        bonusCapReached: Boolean(data?.bonusCapReached),
        message,
        status: normalizeScreenTimeStatus(data?.status),
      };
    },
    options
  );
}

export async function missLootDrop(
  token: string,
  dropOrAnswerToken: string,
  options: PlayFeaturesRequestOptions = {}
): Promise<LootStatus> {
  const url = `${options.baseUrl || API}/users/play-features/loot/miss`;
  return runPlayFeaturesRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify({ token: dropOrAnswerToken }),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa cập nhật trạng thái rương báu.');
      return {
        enabled: Boolean(data?.enabled),
        ready: Boolean(data?.ready),
        combo: nonNeg(data?.combo, 0),
      };
    },
    options
  );
}

export async function fetchRecapQuiz(
  token: string,
  episodeId: string,
  options: PlayFeaturesRequestOptions = {}
): Promise<RecapQuiz> {
  const url = `${options.baseUrl || API}/users/play-features/recap/quiz?episodeId=${encodeURIComponent(episodeId)}`;
  return runPlayFeaturesRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'GET',
        headers: authHeaders(token),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa tải được câu hỏi ôn tập.');
      const items: RecapQuizItem[] = Array.isArray(data?.items)
        ? data.items
            .filter((item: any) => item && typeof item.id === 'string' && typeof item.text === 'string')
            .map((item: any) => ({ id: item.id, text: item.text }))
        : [];
      return {
        token: typeof data?.token === 'string' ? data.token : '',
        episodeId: typeof data?.episodeId === 'string' ? data.episodeId : episodeId,
        episodeTitle: typeof data?.episodeTitle === 'string' ? data.episodeTitle : '',
        targetEpisodeId: typeof data?.targetEpisodeId === 'string' && data.targetEpisodeId ? data.targetEpisodeId : null,
        quizType: normalizeQuizType(data?.quizType),
        prompt: typeof data?.prompt === 'string' ? data.prompt : 'Tập vừa rồi tên là gì?',
        items,
        expiresAt: typeof data?.expiresAt === 'string' ? data.expiresAt : '',
        expiresInSeconds: nonNeg(data?.expiresInSeconds, 600),
      };
    },
    options
  );
}

export async function submitRecapQuizAnswer(
  token: string,
  payload: { token: string; orderedIds?: string[]; choiceId?: string },
  options: PlayFeaturesRequestOptions = {}
): Promise<RecapQuizAnswerResult> {
  const url = `${options.baseUrl || API}/users/play-features/recap/answer`;
  return runPlayFeaturesRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'POST',
        headers: authHeaders(token, true),
        body: JSON.stringify(payload),
        signal,
      });
      const { data, message } = await parseEnvelope(response, 'Chưa kiểm tra được câu trả lời ôn tập.');
      return {
        correct: Boolean(data?.correct),
        episodeId: typeof data?.episodeId === 'string' ? data.episodeId : '',
        targetEpisodeId: typeof data?.targetEpisodeId === 'string' && data.targetEpisodeId ? data.targetEpisodeId : null,
        unlocked: Boolean(data?.unlocked),
        message,
      };
    },
    options
  );
}

export async function fetchParentPlaySettings(
  token: string,
  options: PlayFeaturesRequestOptions = {}
): Promise<ChildProfilePlaySettings[]> {
  const url = `${options.baseUrl || API}/users/profiles/play-features`;
  return runPlayFeaturesRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'GET',
        headers: authHeaders(token),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa tải được cài đặt tính năng xem phim.');
      if (!Array.isArray(data)) return [];
      return data
        .map(normalizeChildSettings)
        .filter((item: ChildProfilePlaySettings | null): item is ChildProfilePlaySettings => item !== null);
    },
    options
  );
}

export async function updateParentPlaySettings(
  token: string,
  profileId: number,
  payload: UpdatePlaySettingsPayload,
  options: PlayFeaturesRequestOptions = {}
): Promise<ChildProfilePlaySettings> {
  const url = `${options.baseUrl || API}/users/profiles/${encodeURIComponent(String(profileId))}/play-features`;
  return runPlayFeaturesRequest(
    async (signal) => {
      const response = await apiFetch(url, {
        method: 'PUT',
        headers: authHeaders(token, true),
        body: JSON.stringify(payload),
        signal,
      });
      const { data } = await parseEnvelope(response, 'Chưa lưu được cài đặt tính năng xem phim.');
      const normalized = normalizeChildSettings(data);
      if (!normalized) {
        throw new PlayFeaturesApiError('Dữ liệu cài đặt tính năng xem phim chưa hợp lệ.', 502);
      }
      return normalized;
    },
    options
  );
}
