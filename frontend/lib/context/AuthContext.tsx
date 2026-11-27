import React, {
  createContext,
  useContext,
  useState,
  useEffect,
  useRef,
  useCallback,
  ReactNode,
} from 'react';

import {
  formatRateLimitMessage,
  observeSessionResponses,
  PERMISSION_MESSAGE,
  SESSION_EXPIRED_MESSAGE,
  withRequestTimeout,
} from '@/lib/services/apiRequest';
import {
  decodeJwtProfileId,
  exitChildProfile,
  fetchProfilesState,
  resetParentPinAndExit,
  switchToChildProfile,
  type ChildProfile,
  type ProfilesState,
} from '@/lib/services/profileService';
import { AUTH_TOKEN_KEY, AUTH_USER_KEY, clearPrivateStorage, getStoredAuthToken, notifySessionReset } from '@/lib/services/sessionStorage';

export type AuthUser = {
  id: number;
  username: string;
  email: string;
  fullName?: string;
  role: string;
};

export type AuthStatus = 'guest' | 'checking' | 'authenticated' | 'unavailable';

export type AuthModalMode = 'login' | 'register' | 'forgot';

type AuthContextType = {
  user: AuthUser | null;
  token: string | null;
  status: AuthStatus;
  isAuthenticated: boolean;
  isLoading: boolean;
  isUnavailable: boolean;
  errorMessage: string | null;
  isAuthModalOpen: boolean;
  authModalMode: AuthModalMode;
  openAuthModal: (mode?: AuthModalMode) => void;
  closeAuthModal: () => void;
  login: (usernameOrEmail: string, password: string) => Promise<{ success: boolean; message?: string }>;
  register: (username: string, email: string, password: string, fullName: string | undefined, acceptedTerms: boolean) => Promise<{ success: boolean; message?: string }>;
  logout: () => Promise<void>;
  finishAccountDeletion: (deletedToken: string) => void;
  sessionEpoch: number;
  retryVerify: () => Promise<void>;
  applySession: (token: string, user: AuthUser) => void;
  updateUser: (user: AuthUser) => void;
  parentModeEnabled: boolean;
  childProfiles: ChildProfile[];
  activeProfile: ChildProfile | null;
  isKidMode: boolean;
  showProfilePicker: boolean;
  openProfilePicker: () => void;
  closeProfilePicker: () => void;
  refreshProfiles: () => Promise<ProfilesState | null>;
  selectChildProfile: (profileId: number) => Promise<{ success: boolean; message?: string }>;
  exitKidMode: (pin: string) => Promise<{ success: boolean; message?: string }>;
  resetPinAndExitKidMode: (password: string, newPin: string) => Promise<{ success: boolean; message?: string }>;
  showPostSignupFamilyOffer: boolean;
  dismissPostSignupFamilyOffer: () => void;
};

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const API = '/api';
const REQUEST_TIMEOUT_MS = 8000;
export const PICKER_LOCK_STORAGE_KEY = 'dippie_picker_locked';

function readPickerLock(): boolean {
  if (typeof sessionStorage === 'undefined') return false;
  try {
    return sessionStorage.getItem(PICKER_LOCK_STORAGE_KEY) === '1';
  } catch {
    return false;
  }
}

function writePickerLock(locked: boolean): void {
  if (typeof sessionStorage === 'undefined') return;
  try {
    if (locked) {
      sessionStorage.setItem(PICKER_LOCK_STORAGE_KEY, '1');
    } else {
      sessionStorage.removeItem(PICKER_LOCK_STORAGE_KEY);
    }
  } catch {}
}

function validateAuthPayload(payload: any, fallbackToken?: string): { user: AuthUser; token: string } | null {
  if (!payload || typeof payload !== 'object') return null;

  const rawToken = typeof payload.token === 'string' && payload.token.trim().length > 0 ? payload.token.trim() : fallbackToken;
  if (!rawToken || typeof rawToken !== 'string' || rawToken.trim().length === 0) {
    return null;
  }
  let numericId: number | null = null;
  if (typeof payload.id === 'number' && Number.isFinite(payload.id) && payload.id > 0) {
    numericId = payload.id;
  } else if (typeof payload.id === 'string' && /^\d+$/.test(payload.id.trim())) {
    const parsed = parseInt(payload.id.trim(), 10);
    if (parsed > 0) numericId = parsed;
  }
  if (numericId === null) {
    return null;
  }

  if (typeof payload.username !== 'string' || payload.username.trim().length === 0) {
    return null;
  }

  if (typeof payload.email !== 'string' || !payload.email.includes('@')) {
    return null;
  }

  const user: AuthUser = {
    id: numericId,
    username: payload.username.trim(),
    email: payload.email.trim(),
    fullName: typeof payload.fullName === 'string' && payload.fullName.trim().length > 0 ? payload.fullName.trim() : undefined,
    role: typeof payload.role === 'string' && payload.role.trim().length > 0 ? payload.role.trim() : 'ROLE_USER',
  };

  return { user, token: rawToken };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [status, setStatus] = useState<AuthStatus>('checking');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
  const [authModalMode, setAuthModalMode] = useState<AuthModalMode>('login');
  const [sessionEpoch, setSessionEpoch] = useState(0);
  const [notice, setNotice] = useState<{ text: string; kind: 'info' | 'success' } | null>(null);
  const [parentModeEnabled, setParentModeEnabled] = useState(false);
  const [childProfiles, setChildProfiles] = useState<ChildProfile[]>([]);
  const [activeProfile, setActiveProfile] = useState<ChildProfile | null>(null);
  const [showProfilePicker, setShowProfilePicker] = useState<boolean>(() => readPickerLock());
  const [showPostSignupFamilyOffer, setShowPostSignupFamilyOffer] = useState(false);
  const activeToken = useRef<string | null>(null);
  const activeUserId = useRef<number | null>(null);
  const logoutRequest = useRef<Promise<void> | null>(null);
  const generationRef = useRef<number>(0);
  const activeRequestRef = useRef<AbortController | null>(null);

  const abortActiveRequest = useCallback(() => {
    if (activeRequestRef.current) {
      activeRequestRef.current.abort();
      activeRequestRef.current = null;
    }
  }, []);

  const applyVerifiedSession = useCallback((authToken: string, authUser: AuthUser, clean: boolean) => {
    if (clean && activeUserId.current !== authUser.id) {
      clearPrivateStorage({ keepToken: true, keepNonEmptyGuest: activeUserId.current === null });
      notifySessionReset();
      setSessionEpoch((epoch) => epoch + 1);
    }
    activeToken.current = authToken;
    activeUserId.current = authUser.id;
    try {
      localStorage.setItem(AUTH_USER_KEY, JSON.stringify(authUser));
      localStorage.setItem(AUTH_TOKEN_KEY, authToken);
    } catch {}
    setUser(authUser);
    setToken(authToken);
    setStatus('authenticated');
    setErrorMessage(null);
  }, []);

  const endSession = useCallback((message?: string, kind: 'info' | 'success' = 'info', clearStorage = true) => {
    generationRef.current += 1;
    abortActiveRequest();
    activeToken.current = null;
    activeUserId.current = null;
    writePickerLock(false);
    clearPrivateStorage({ clearLocal: clearStorage });
    notifySessionReset();
    setSessionEpoch((epoch) => epoch + 1);
    setUser(null);
    setToken(null);
    setParentModeEnabled(false);
    setChildProfiles([]);
    setActiveProfile(null);
    setShowProfilePicker(false);
    setShowPostSignupFamilyOffer(false);
    setStatus('guest');
    setIsAuthModalOpen(false);
    setErrorMessage(null);
    setNotice(message ? { text: message, kind } : null);
  }, [abortActiveRequest]);

  const loadProfilesSilently = useCallback(
    async (authToken: string, generation: number, openPickerIfEnabled = false): Promise<ProfilesState | null> => {
      try {
        const res = await fetch(`${API}/users/profiles`, {
          headers: {
            Authorization: `Bearer ${authToken}`,
            Accept: 'application/json',
          },
          cache: 'no-store',
        });
        if (generation !== generationRef.current || activeToken.current !== authToken) {
          return null;
        }
        if (!res.ok) return null;
        const json = await res.json().catch(() => null);
        if (generation !== generationRef.current || activeToken.current !== authToken) {
          return null;
        }
        if (!json?.success || !json?.data) return null;
        const data = json.data;
        const profiles: ChildProfile[] = Array.isArray(data.profiles) ? data.profiles : [];
        const active: ChildProfile | null = data.activeProfile && typeof data.activeProfile === 'object'
          ? data.activeProfile
          : null;
        const enabled = Boolean(data.parentModeEnabled);
        setParentModeEnabled(enabled);
        setChildProfiles(profiles);
        setActiveProfile(active);
        const shouldOpenPicker = (openPickerIfEnabled || readPickerLock()) && enabled && !active;
        if (shouldOpenPicker) {
          writePickerLock(true);
          setShowProfilePicker(true);
        } else if (!enabled || active !== null) {
          writePickerLock(false);
          setShowProfilePicker(false);
        }
        return {
          parentModeEnabled: enabled,
          activeProfileId: active ? active.id : null,
          activeProfile: active,
          profiles,
          availableAvatars: Array.isArray(data.availableAvatars) ? data.availableAvatars : [],
        };
      } catch {
        return null;
      }
    },
    []
  );

  const verifySession = useCallback(
    async (authToken: string, generation: number, background = false) => {
      abortActiveRequest();
      const controller = new AbortController();
      activeRequestRef.current = controller;
      const startedFromStorage = getStoredAuthToken() === authToken;
      const isCurrent = () => generation === generationRef.current
        && (!startedFromStorage || getStoredAuthToken() === authToken);
      let timeoutId: ReturnType<typeof setTimeout> | undefined;

      try {
        timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

        const res = await fetch(`${API}/auth/me`, {
          headers: {
            Authorization: `Bearer ${authToken}`,
            Accept: 'application/json',
          },
          signal: controller.signal,
          cache: 'no-store',
        });
        if (!isCurrent()) {
          return;
        }

        if (res.ok) {
          let json: any = null;
          try {
            json = await res.json();
          } catch {
            if (!isCurrent()) return;
            if (!background) {
              setStatus('unavailable');
              setErrorMessage('Máy chủ trả về dữ liệu chưa đúng định dạng.');
            }
            return;
          }
          if (!isCurrent()) {
            return;
          }

          const validated = validateAuthPayload(json?.data, authToken);
          if (json?.success && validated) {
            const wasPickerLocked = readPickerLock();
            applyVerifiedSession(validated.token, validated.user, false);
            if (!background) {
              await loadProfilesSilently(validated.token, generation, wasPickerLocked);
            }
          } else if (!background) {
            endSession();
          }
          return;
        }

        if (res.status === 401 || res.status === 403) {
          const hadActiveSession = activeToken.current === authToken;
          endSession(hadActiveSession ? (res.status === 401 ? SESSION_EXPIRED_MESSAGE : PERMISSION_MESSAGE) : undefined);
        } else if (!background) {
          setStatus('unavailable');
          setErrorMessage('Máy chủ tạm thời không phản hồi. Bạn thử lại nhé.');
        }
      } catch (err: unknown) {
        if (!isCurrent() || background) return;
        setStatus('unavailable');
        setErrorMessage(err instanceof DOMException && err.name === 'AbortError'
          ? 'Máy chủ phản hồi chậm. Bạn thử lại nhé.'
          : 'Chưa kết nối được máy chủ. Bạn kiểm tra lại mạng nhé.');
      } finally {
        if (timeoutId) {
          clearTimeout(timeoutId);
        }
        if (activeRequestRef.current === controller) {
          activeRequestRef.current = null;
        }
      }
    },
    [abortActiveRequest, applyVerifiedSession, endSession]
  );
  useEffect(() => {
    const savedToken = getStoredAuthToken();
    if (savedToken) {
      generationRef.current += 1;
      setStatus('checking');
      void verifySession(savedToken, generationRef.current);
    } else {
      setStatus('guest');
    }

    const onStorage = (event: StorageEvent) => {
      if (event.storageArea !== localStorage || (event.key !== AUTH_TOKEN_KEY && event.key !== null)) return;
      const saved = getStoredAuthToken();
      if (saved === activeToken.current && activeToken.current !== null) return;
      endSession(undefined, 'info', false);
      if (saved) {
        setStatus('checking');
        void verifySession(saved, generationRef.current);
      }
    };
    if (typeof window !== 'undefined') window.addEventListener?.('storage', onStorage);
    return () => {
      if (typeof window !== 'undefined') window.removeEventListener?.('storage', onStorage);
      generationRef.current += 1;
      abortActiveRequest();
    };
  }, [abortActiveRequest, endSession, verifySession]);

  useEffect(() => observeSessionResponses((httpStatus, requestToken) => {
    if (activeToken.current !== requestToken || (getStoredAuthToken() ?? requestToken) !== requestToken || logoutRequest.current) return;
    if (httpStatus === 401) {
      endSession(SESSION_EXPIRED_MESSAGE);
    } else {
      setNotice((current) => current?.text === PERMISSION_MESSAGE ? current : { text: PERMISSION_MESSAGE, kind: 'info' });
      if (!activeRequestRef.current) void verifySession(requestToken, generationRef.current, true);
    }
  }), [endSession, verifySession]);

  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(() => setNotice(null), 5000);
    return () => clearTimeout(timer);
  }, [notice]);

  const retryVerify = async () => {
    const savedToken = activeToken.current ?? getStoredAuthToken();
    if (!savedToken || logoutRequest.current) return;
    generationRef.current += 1;
    setStatus('checking');
    setErrorMessage(null);
    await verifySession(savedToken, generationRef.current);
  };

  const openAuthModal = (mode: AuthModalMode = 'login') => {
    setAuthModalMode(mode);
    setIsAuthModalOpen(true);
  };

  const closeAuthModal = () => {
    setIsAuthModalOpen(false);
  };

  const login = async (
    usernameOrEmail: string,
    password: string
  ): Promise<{ success: boolean; message?: string }> => {
    if (logoutRequest.current) return { success: false, message: 'Đang đăng xuất, bạn chờ chút nhé.' };
    const cleanUsername = usernameOrEmail.trim();
    const rawPassword = password;

    if (!cleanUsername) {
      return { success: false, message: 'Bạn nhập tên đăng nhập hoặc email nhé.' };
    }
    if (!rawPassword) {
      return { success: false, message: 'Bạn nhập mật khẩu nhé.' };
    }

    abortActiveRequest();
    generationRef.current += 1;
    const requestGeneration = generationRef.current;

    const controller = new AbortController();
    activeRequestRef.current = controller;
    let timeoutId: any = null;

    try {
      timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

      const res = await fetch(`${API}/auth/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
        body: JSON.stringify({
          usernameOrEmail: cleanUsername,
          password: rawPassword,
        }),
        signal: controller.signal,
      });
      if (requestGeneration !== generationRef.current) {
        return { success: false, message: 'Yêu cầu đã được hủy.' };
      }

      let json: any = null;
      try {
        json = await res.json();
      } catch {
        if (requestGeneration !== generationRef.current) {
          return { success: false, message: 'Yêu cầu đã được hủy.' };
        }
        return { success: false, message: 'Máy chủ trả về dữ liệu chưa đúng định dạng.' };
      }
      if (requestGeneration !== generationRef.current) {
        return { success: false, message: 'Yêu cầu đã được hủy.' };
      }

      const validated = validateAuthPayload(json?.data);
      if (res.ok && json?.success && validated) {
        if (requestGeneration !== generationRef.current) {
          return { success: false, message: 'Yêu cầu đã được hủy.' };
        }

        applyVerifiedSession(validated.token, validated.user, true);
        setNotice({ text: 'Đã đăng nhập.', kind: 'success' });
        closeAuthModal();
        await loadProfilesSilently(validated.token, requestGeneration, true);
        return { success: true };
      } else {
        const retryHeader = res.headers?.get?.('Retry-After');
        const retryAfter = retryHeader && /^\d+$/.test(retryHeader) ? parseInt(retryHeader, 10) : undefined;
        const serverMessage =
          (typeof json?.detail === 'string' && json.detail.trim()) ||
          (typeof json?.message === 'string' && json.message.trim()) ||
          '';
        const errorText = res.status === 401
          ? 'Tên đăng nhập hoặc mật khẩu chưa đúng.'
          : res.status === 429
          ? formatRateLimitMessage(
              serverMessage,
              'Bạn vừa thử đăng nhập quá nhiều lần. Chờ vài phút rồi thử lại nhé.',
              retryAfter
            )
          : serverMessage || 'Chưa đăng nhập được. Bạn thử lại nhé.';
        return { success: false, message: errorText };
      }
    } catch (err: unknown) {
      if (requestGeneration !== generationRef.current) {
        return { success: false, message: 'Yêu cầu đã được hủy.' };
      }

      if (err instanceof DOMException && err.name === 'AbortError') {
        return { success: false, message: 'Máy chủ phản hồi chậm. Bạn thử lại nhé.' };
      }
      return {
        success: false,
        message: 'Không kết nối được với Dippie. Bạn kiểm tra lại mạng rồi thử lại nhé.',
      };
    } finally {
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
      if (activeRequestRef.current === controller) {
        activeRequestRef.current = null;
      }
    }
  };

  const register = async (
    username: string,
    email: string,
    password: string,
    fullName: string | undefined,
    acceptedTerms: boolean
  ): Promise<{ success: boolean; message?: string }> => {
    if (logoutRequest.current) return { success: false, message: 'Đang đăng xuất, bạn chờ chút nhé.' };
    if (acceptedTerms !== true) {
      return { success: false, message: 'Bạn đánh dấu đồng ý với Điều khoản sử dụng và Chính sách quyền riêng tư trước nhé.' };
    }
    const cleanUsername = username.trim();
    const cleanEmail = email.trim();
    const rawPassword = password;

    if (!cleanUsername || cleanUsername.length < 3) {
      return { success: false, message: 'Tên đăng nhập cần có ít nhất 3 ký tự.' };
    }
    if (!cleanEmail || !cleanEmail.includes('@')) {
      return { success: false, message: 'Email chưa đúng định dạng.' };
    }
    if (!rawPassword || rawPassword.length < 6) {
      return { success: false, message: 'Mật khẩu cần có ít nhất 6 ký tự.' };
    }
    if (!rawPassword.trim()) {
      return { success: false, message: 'Mật khẩu không được chỉ có khoảng trắng.' };
    }
    const passwordByteLength =
      typeof TextEncoder !== 'undefined'
        ? new TextEncoder().encode(rawPassword).length
        : unescape(encodeURIComponent(rawPassword)).length;
    if (passwordByteLength > 72) {
      return { success: false, message: 'Mật khẩu quá dài. Bạn đặt ngắn gọn hơn (dưới 50 ký tự) nhé.' };
    }

    abortActiveRequest();
    generationRef.current += 1;
    const requestGeneration = generationRef.current;

    const controller = new AbortController();
    activeRequestRef.current = controller;
    let timeoutId: any = null;

    try {
      timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

      const res = await fetch(`${API}/auth/register`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
        body: JSON.stringify({
          username: cleanUsername,
          email: cleanEmail,
          password: rawPassword,
          fullName: fullName?.trim() || undefined,
          acceptedTerms: true,
        }),
        signal: controller.signal,
      });
      if (requestGeneration !== generationRef.current) {
        return { success: false, message: 'Yêu cầu đã được hủy.' };
      }

      let json: any = null;
      try {
        json = await res.json();
      } catch {
        if (requestGeneration !== generationRef.current) {
          return { success: false, message: 'Yêu cầu đã được hủy.' };
        }
        return { success: false, message: 'Máy chủ trả về dữ liệu chưa đúng định dạng.' };
      }
      if (requestGeneration !== generationRef.current) {
        return { success: false, message: 'Yêu cầu đã được hủy.' };
      }

      const validated = validateAuthPayload(json?.data);
      if (res.ok && json?.success && validated) {
        if (requestGeneration !== generationRef.current) {
          return { success: false, message: 'Yêu cầu đã được hủy.' };
        }

        applyVerifiedSession(validated.token, validated.user, true);
        setNotice({ text: 'Đã tạo tài khoản.', kind: 'success' });
        closeAuthModal();
        setShowPostSignupFamilyOffer(true);
        return { success: true };
      } else {
        const retryHeader = res.headers?.get?.('Retry-After');
        const retryAfter = retryHeader && /^\d+$/.test(retryHeader) ? parseInt(retryHeader, 10) : undefined;
        const serverMessage =
          (typeof json?.detail === 'string' && json.detail.trim()) ||
          (typeof json?.message === 'string' && json.message.trim()) ||
          '';
        const errorText =
          res.status === 429
            ? formatRateLimitMessage(
                serverMessage,
                'Bạn vừa gửi quá nhiều yêu cầu đăng ký. Chờ vài phút rồi thử lại nhé.',
                retryAfter
              )
            : serverMessage || 'Chưa tạo được tài khoản. Bạn thử lại nhé.';
        return { success: false, message: errorText };
      }
    } catch (err: unknown) {
      if (requestGeneration !== generationRef.current) {
        return { success: false, message: 'Yêu cầu đã được hủy.' };
      }

      if (err instanceof DOMException && err.name === 'AbortError') {
        return { success: false, message: 'Máy chủ phản hồi chậm. Bạn thử lại nhé.' };
      }
      return {
        success: false,
        message: 'Không kết nối được với Dippie. Bạn kiểm tra lại mạng rồi thử lại nhé.',
      };
    } finally {
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
      if (activeRequestRef.current === controller) {
        activeRequestRef.current = null;
      }
    }
  };

  const logout = useCallback((): Promise<void> => {
    if (logoutRequest.current) return logoutRequest.current;
    const currentToken = activeToken.current ?? getStoredAuthToken();
    if (!currentToken) {
      endSession();
      return Promise.resolve();
    }
    generationRef.current += 1;
    abortActiveRequest();
    setUser(null);
    setToken(null);
    setStatus('checking');
    const pending = withRequestTimeout((signal) => fetch('/api/auth/logout', {
      method: 'POST',
      headers: { Authorization: `Bearer ${currentToken}` },
      signal,
    }), { timeoutMs: REQUEST_TIMEOUT_MS }).catch(() => {}).then(() => {
      logoutRequest.current = null;
      const stored = getStoredAuthToken();
      if (stored && stored !== currentToken) return;
      endSession();
    });
    logoutRequest.current = pending;
    return pending;
  }, [abortActiveRequest, endSession]);

  const finishAccountDeletion = useCallback((deletedToken: string) => {
    if (activeToken.current === deletedToken && (getStoredAuthToken() ?? deletedToken) === deletedToken) {
      endSession('Đã xóa tài khoản Dippie.', 'success');
    }
  }, [endSession]);

  const applySession = useCallback((nextToken: string, nextUser: AuthUser) => {
    if (logoutRequest.current) return;
    abortActiveRequest();
    generationRef.current += 1;
    applyVerifiedSession(nextToken, nextUser, true);
  }, [abortActiveRequest, applyVerifiedSession]);

  const updateUser = useCallback((nextUser: AuthUser) => {
    const currentToken = activeToken.current;
    if (!logoutRequest.current && currentToken && activeUserId.current === nextUser.id) {
      applyVerifiedSession(currentToken, nextUser, false);
    }
  }, [applyVerifiedSession]);

  const openProfilePicker = useCallback(() => {
    if (parentModeEnabled && !activeProfile) {
      writePickerLock(true);
      setShowProfilePicker(true);
    }
  }, [parentModeEnabled, activeProfile]);

  const closeProfilePicker = useCallback(() => {
    writePickerLock(false);
    setShowProfilePicker(false);
  }, []);

  const dismissPostSignupFamilyOffer = useCallback(() => {
    setShowPostSignupFamilyOffer(false);
  }, []);

  const refreshProfiles = useCallback(async (): Promise<ProfilesState | null> => {
    const currentToken = activeToken.current;
    if (!currentToken || logoutRequest.current) return null;
    try {
      const state = await fetchProfilesState(currentToken);
      if (activeToken.current !== currentToken) return null;
      setParentModeEnabled(state.parentModeEnabled);
      setChildProfiles(state.profiles);
      setActiveProfile(state.activeProfile);
      if (!state.parentModeEnabled || state.activeProfile) {
        writePickerLock(false);
        setShowProfilePicker(false);
      }
      return state;
    } catch {
      return null;
    }
  }, []);

  const applyProfileSwitch = useCallback((nextToken: string, nextUser: AuthUser, nextProfile: ChildProfile | null) => {
    abortActiveRequest();
    generationRef.current += 1;
    writePickerLock(false);
    clearPrivateStorage({ keepToken: true, keepNonEmptyGuest: false });
    notifySessionReset();
    setSessionEpoch((epoch) => epoch + 1);
    activeToken.current = nextToken;
    activeUserId.current = nextUser.id;
    try {
      localStorage.setItem(AUTH_USER_KEY, JSON.stringify(nextUser));
      localStorage.setItem(AUTH_TOKEN_KEY, nextToken);
    } catch {}
    setUser(nextUser);
    setToken(nextToken);
    setActiveProfile(nextProfile);
    setShowProfilePicker(false);
    setShowPostSignupFamilyOffer(false);
    setStatus('authenticated');
    setErrorMessage(null);
  }, [abortActiveRequest]);

  const selectChildProfile = useCallback(async (profileId: number): Promise<{ success: boolean; message?: string }> => {
    const currentToken = activeToken.current;
    if (!currentToken || logoutRequest.current) {
      return { success: false, message: 'Bạn cần đăng nhập trước.' };
    }
    try {
      const result = await switchToChildProfile(currentToken, profileId);
      applyProfileSwitch(result.token, result.user, result.activeProfile);
      setNotice({
        text: result.activeProfile ? `Đang xem với hồ sơ ${result.activeProfile.name}.` : result.message,
        kind: 'success',
      });
      return { success: true };
    } catch (err: unknown) {
      return {
        success: false,
        message: err instanceof Error ? err.message : 'Chưa chuyển được sang hồ sơ trẻ em.',
      };
    }
  }, [applyProfileSwitch]);

  const exitKidMode = useCallback(async (pin: string): Promise<{ success: boolean; message?: string }> => {
    const currentToken = activeToken.current;
    if (!currentToken || logoutRequest.current) {
      return { success: false, message: 'Bạn cần đăng nhập trước.' };
    }
    try {
      const result = await exitChildProfile(currentToken, pin);
      applyProfileSwitch(result.token, result.user, null);
      setNotice({ text: result.message || 'Đã quay lại tài khoản chính.', kind: 'success' });
      void loadProfilesSilently(result.token, generationRef.current, false);
      return { success: true };
    } catch (err: unknown) {
      return {
        success: false,
        message: err instanceof Error ? err.message : 'Mã PIN phụ huynh chưa đúng.',
      };
    }
  }, [applyProfileSwitch, loadProfilesSilently]);

  const resetPinAndExitKidMode = useCallback(
    async (password: string, newPin: string): Promise<{ success: boolean; message?: string }> => {
      const currentToken = activeToken.current;
      if (!currentToken || logoutRequest.current) {
        return { success: false, message: 'Bạn cần đăng nhập trước.' };
      }
      try {
        const result = await resetParentPinAndExit(currentToken, password, newPin);
        applyProfileSwitch(result.token, result.user, null);
        setNotice({ text: result.message || 'Đã đặt lại mã PIN và quay lại tài khoản chính.', kind: 'success' });
        void loadProfilesSilently(result.token, generationRef.current, false);
        return { success: true };
      } catch (err: unknown) {
        return {
          success: false,
          message: err instanceof Error ? err.message : 'Chưa đặt lại được mã PIN.',
        };
      }
    },
    [applyProfileSwitch, loadProfilesSilently]
  );

  const isKidMode = status === 'authenticated' && (activeProfile !== null || decodeJwtProfileId(token) !== null);

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        status,
        isAuthenticated: status === 'authenticated',
        isLoading: status === 'checking',
        isUnavailable: status === 'unavailable',
        errorMessage,
        isAuthModalOpen,
        authModalMode,
        openAuthModal,
        closeAuthModal,
        login,
        register,
        logout,
        retryVerify,
        finishAccountDeletion,
        sessionEpoch,
        applySession,
        updateUser,
        parentModeEnabled,
        childProfiles,
        activeProfile,
        isKidMode,
        showProfilePicker,
        openProfilePicker,
        closeProfilePicker,
        refreshProfiles,
        selectChildProfile,
        exitKidMode,
        resetPinAndExitKidMode,
        showPostSignupFamilyOffer,
        dismissPostSignupFamilyOffer,
      }}
    >
      {children}
      {notice && (
        <div
          role="status"
          className={`fixed bottom-5 left-4 right-4 z-[70] flex items-center justify-between gap-2 rounded-lg border-2 px-4 py-2 text-sm font-medium shadow-brand-sm animate-slide-up sm:left-auto sm:right-5 sm:max-w-md ${
            notice.kind === 'success' ? 'border-ink bg-mint text-ink' : 'border-ink bg-paper-white text-ink'
          }`}
        >
          {notice.text}
          <button
            type="button"
            onClick={() => setNotice(null)}
            aria-label="Đóng thông báo"
            className="inline-flex h-11 w-11 min-h-[44px] min-w-[44px] shrink-0 items-center justify-center rounded-md text-muted hover:bg-surface-elevated hover:text-ink cursor-pointer"
          >
            ✕
          </button>
        </div>
      )}
    </AuthContext.Provider>
  );
}

export function useOptionalAuth(): AuthContextType | null {
  return useContext(AuthContext) ?? null;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
