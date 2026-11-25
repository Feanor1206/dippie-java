export const AUTH_TOKEN_KEY = 'dippie_auth_token';
export const AUTH_USER_KEY = 'dippie_auth_user';
export const SESSION_RESET_EVENT = 'dippie:session-reset';

const GUEST_LIBRARY_KEY = 'dippie_guest_library_v1';
const GUEST_PROGRESS_KEY = 'dippie_guest_watch_progress_v1';

const PRIVATE_KEYS = [
  AUTH_USER_KEY,
  'token',
  'auth_token',
  'auth_user',
  GUEST_LIBRARY_KEY,
  GUEST_PROGRESS_KEY,
  'dippie_movie_progress_v1',
  'dippie_auto_next_enabled_v1',
];

function hasNonEmptyGuestLibrary(raw: string | null): boolean {
  if (!raw) return false;
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return false;
    const hasId = (list: unknown) =>
      Array.isArray(list) && list.some((id) => typeof id === 'string' && id.trim().length > 0);
    if (hasId(parsed.watchlist) || hasId(parsed.favorites)) return true;
    if (parsed.ratings && typeof parsed.ratings === 'object' && !Array.isArray(parsed.ratings)) {
      return Object.entries(parsed.ratings).some(
        ([key, rating]) => key.trim().length > 0 && typeof rating === 'number' && Number.isFinite(rating) && rating >= 1 && rating <= 10
      );
    }
    return false;
  } catch {
    return false;
  }
}

function hasNonEmptyGuestProgress(raw: string | null): boolean {
  if (!raw) return false;
  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return false;
    return parsed.some((entry) => {
      if (!entry || typeof entry !== 'object' || Array.isArray(entry)) return false;
      const record = entry as Record<string, unknown>;
      return (
        typeof record.episodeId === 'string' &&
        record.episodeId.trim().length > 0 &&
        typeof record.assetId === 'string' &&
        record.assetId.trim().length > 0 &&
        typeof record.positionMs === 'number' &&
        Number.isSafeInteger(record.positionMs) &&
        record.positionMs >= 0
      );
    });
  } catch {
    return false;
  }
}

export function getStoredAuthToken(): string | null {
  try {
    return localStorage.getItem(AUTH_TOKEN_KEY)?.trim() || null;
  } catch {
    return null;
  }
}

export function clearPrivateStorage({
  keepToken = false,
  clearLocal = true,
  keepNonEmptyGuest = false,
} = {}): void {
  function clear(store: Storage, preserveToken: boolean, preserveGuest: boolean) {
    const preserved = new Set<string>();
    if (preserveGuest) {
      if (hasNonEmptyGuestLibrary(store.getItem(GUEST_LIBRARY_KEY))) {
        preserved.add(GUEST_LIBRARY_KEY);
      }
      if (hasNonEmptyGuestProgress(store.getItem(GUEST_PROGRESS_KEY))) {
        preserved.add(GUEST_PROGRESS_KEY);
      }
    }
    const keys = new Set(PRIVATE_KEYS);
    for (let index = 0; index < store.length; index += 1) {
      const key = store.key(index);
      if (key?.startsWith('dippie_') && key !== AUTH_TOKEN_KEY) keys.add(key);
    }
    for (const key of keys) {
      if (!preserved.has(key)) store.removeItem(key);
    }
    if (!preserveToken) store.removeItem(AUTH_TOKEN_KEY);
  }
  if (clearLocal) {
    try { clear(localStorage, keepToken, keepNonEmptyGuest); } catch {}
  }
  try { clear(sessionStorage, false, false); } catch {}
}

export function notifySessionReset(): void {
  if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
    window.dispatchEvent(new Event(SESSION_RESET_EVENT));
  }
}
