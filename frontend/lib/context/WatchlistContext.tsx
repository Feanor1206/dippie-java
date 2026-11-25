import React, {
  createContext,
  useContext,
  useEffect,
  useState,
  useRef,
  useCallback,
  useMemo,
  lazy,
  Suspense,
  ReactNode,
} from 'react';
import { useAuth } from './AuthContext';
import { GuestSyncDialog } from '@/features/library/components/GuestSyncDialog';
import {
  clearGuestSyncData,
  deleteUserRating,
  fetchUserLibrary,
  hasGuestSyncData,
  isUncertainNetworkError,
  LibraryApiError,
  updateLibraryList,
  notifyRatingChanged,
  notifyWatchProgressSynced,
  RATING_CHANGED_EVENT,
  readGuestLibrary,
  readGuestSyncSummary,
  reconcileLibrarySnapshot,
  saveUserRating,
  syncGuestDataToAccount,
  WATCH_PROGRESS_SYNCED_EVENT,
  writeGuestLibrary,
  type GuestLibraryData,
  type GuestSyncSummary,
  type LibraryActionIntent,
  type LibraryActionKind,
  type OperationRecord,
} from '@/lib/services/libraryService';

export { WATCH_PROGRESS_SYNCED_EVENT, RATING_CHANGED_EVENT };

const CineBot = lazy(() =>
  import('@/features/chat/components/CineBot').then(({ CineBot }) => ({ default: CineBot }))
);
const AuthModal = lazy(() =>
  import('@/features/auth/components/AuthModal').then(({ AuthModal }) => ({ default: AuthModal }))
);

const TOAST_DURATION_MS = 3800;
const MAX_VISIBLE_TOASTS = 4;

export type Toast = {
  id: string;
  message: string;
  type?: 'success' | 'info' | 'error';
};

type WatchlistContextType = {
  watchlist: string[];
  favorites: string[];
  ratings: Record<string, number>;
  toggleWatchlist: (id: string, title?: string, poster?: string) => Promise<boolean>;
  toggleFavorite: (id: string, title?: string, poster?: string) => Promise<boolean>;
  rateMovie: (id: string, rating: number, title?: string) => Promise<boolean>;
  removeRating: (id: string, title?: string) => Promise<boolean>;
  isWatchlisted: (id: string) => boolean;
  isFavorite: (id: string) => boolean;
  getRating: (id: string) => number | undefined;
  isActionPending: (id: string, action?: LibraryActionKind) => boolean;
  isActionUnconfirmed: (id: string, action?: LibraryActionKind) => boolean;
  getActionIntent: (id: string, action?: LibraryActionKind) => LibraryActionIntent | null;
  getUnconfirmedRating: (id: string) => number | undefined;
  isLibraryLoading: boolean;
  libraryError: string | null;
  refetchLibrary: () => Promise<void>;
  isGuestMode: boolean;
  guestSyncPrompt: GuestSyncSummary | null;
  isGuestSyncing: boolean;
  confirmGuestSync: () => Promise<void>;
  declineGuestSync: () => void;
  dismissGuestSyncPrompt: () => void;
  toasts: Toast[];
  showToast: (message: string, type?: 'success' | 'info' | 'error') => void;
  dismissToast: (id: string) => void;
  isChatOpen: boolean;
  openChat: () => void;
  closeChat: () => void;
  toggleChat: () => void;
};

type LibrarySnapshot = GuestLibraryData & { ownerKey: string };

const WatchlistContext = createContext<WatchlistContextType | undefined>(undefined);

function getActorKey(status: string, user: { id: number } | null, token: string | null): string {
  if (status === 'authenticated' && user && token) return `user:${user.id}:${token}`;
  if (status === 'guest') return 'guest';
  return `status:${status}`;
}

function abortSafe(controller: AbortController | null): void {
  if (!controller) return;
  try {
    controller.abort();
  } catch {}
}

export function WatchlistProvider({ children }: { children: ReactNode }) {
  const { user, token, status, isAuthenticated, isAuthModalOpen } = useAuth();
  const currentActorKey = getActorKey(status, user, token);

  const [snapshot, setSnapshot] = useState<LibrarySnapshot>(() => {
    if (typeof window !== 'undefined' && status === 'guest') {
      return { ownerKey: 'guest', ...readGuestLibrary() };
    }
    return { ownerKey: currentActorKey, watchlist: [], favorites: [], ratings: {} };
  });

  const [isLibraryLoading, setIsLibraryLoading] = useState(true);
  const [libraryError, setLibraryError] = useState<string | null>(null);
  const [, setPendingUpdateTick] = useState(0);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [isChatOpen, setIsChatOpen] = useState(false);
  const [hasOpenedChat, setHasOpenedChat] = useState(false);
  const [guestSyncPrompt, setGuestSyncPrompt] = useState<GuestSyncSummary | null>(null);
  const [isGuestSyncing, setIsGuestSyncing] = useState(false);

  const sessionVersionRef = useRef(0);
  const readVersionRef = useRef(0);
  const activeActorKeyRef = useRef(currentActorKey);
  const promptedSyncActorRef = useRef<string | null>(null);
  const approvedSyncActorRef = useRef<string | null>(null);
  const serverLibraryRef = useRef<{ actorKey: string; data: GuestLibraryData } | null>(null);
  const operationCounterRef = useRef(0);
  const journalRef = useRef<Map<string, OperationRecord>>(new Map());
  const pendingLocksRef = useRef<Set<string>>(new Set());
  const mutationRequestsRef = useRef<Map<number, AbortController>>(new Map());
  const readRequestRef = useRef<AbortController | null>(null);
  const guestSyncRequestRef = useRef<AbortController | null>(null);
  const toastTimersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

  useEffect(() => {
    if (activeActorKeyRef.current === currentActorKey) return;
    sessionVersionRef.current += 1;
    activeActorKeyRef.current = currentActorKey;
    journalRef.current.clear();
    pendingLocksRef.current.clear();
    readVersionRef.current = 0;
    serverLibraryRef.current = null;
    approvedSyncActorRef.current = null;
    promptedSyncActorRef.current = null;
    for (const controller of mutationRequestsRef.current.values()) abortSafe(controller);
    mutationRequestsRef.current.clear();
    abortSafe(readRequestRef.current);
    readRequestRef.current = null;
    abortSafe(guestSyncRequestRef.current);
    guestSyncRequestRef.current = null;
    setGuestSyncPrompt(null);
    setIsGuestSyncing(false);
  }, [currentActorKey]);

  const visibleLibrary: GuestLibraryData = useMemo(() => {
    if (snapshot.ownerKey === currentActorKey) {
      return {
        watchlist: snapshot.watchlist,
        favorites: snapshot.favorites,
        ratings: snapshot.ratings,
      };
    }
    if (status === 'guest' && typeof window !== 'undefined') {
      return readGuestLibrary();
    }
    return { watchlist: [], favorites: [], ratings: {} };
  }, [snapshot, currentActorKey, status]);

  const { watchlist: visibleWatchlist, favorites: visibleFavorites, ratings: visibleRatings } = visibleLibrary;

  const dismissToast = useCallback((id: string) => {
    const timer = toastTimersRef.current.get(id);
    if (timer !== undefined) {
      clearTimeout(timer);
      toastTimersRef.current.delete(id);
    }
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  }, []);

  const showToast = useCallback((message: string, type: 'success' | 'info' | 'error' = 'success') => {
    const id = `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
    setToasts((prev) => [...prev.slice(-(MAX_VISIBLE_TOASTS - 1)), { id, message, type }]);
    const timer = setTimeout(() => {
      toastTimersRef.current.delete(id);
      setToasts((current) => current.filter((toast) => toast.id !== id));
    }, TOAST_DURATION_MS);
    toastTimersRef.current.set(id, timer);
  }, []);

  useEffect(() => {
    const timers = toastTimersRef.current;
    return () => {
      for (const timer of timers.values()) clearTimeout(timer);
      timers.clear();
    };
  }, []);

  const syncGuestLibrary = useCallback(
    async (
      authToken: string,
      baseServerLibrary: GuestLibraryData,
      actorKey: string,
      sessionVersion: number,
      readVersion: number,
      signal: AbortSignal
    ): Promise<boolean> => {
      const isCurrent = () =>
        sessionVersionRef.current === sessionVersion && activeActorKeyRef.current === actorKey;
      const workingLibrary: GuestLibraryData = {
        watchlist: [...baseServerLibrary.watchlist],
        favorites: [...baseServerLibrary.favorites],
        ratings: { ...baseServerLibrary.ratings },
      };

      const syncResult = await syncGuestDataToAccount(authToken, workingLibrary, signal, isCurrent);
      if (syncResult.aborted || !isCurrent()) return false;

      if (syncResult.hasGuestItems) {
        if (syncResult.mergedProgressCount > 0) notifyWatchProgressSynced();
        if (syncResult.mergedRatingsCount > 0) notifyRatingChanged();

        serverLibraryRef.current = { actorKey, data: workingLibrary };
        setSnapshot((prev) => {
          if (!isCurrent()) return prev;
          const reconciled = reconcileLibrarySnapshot(workingLibrary, journalRef.current, readVersion);
          return { ownerKey: actorKey, ...reconciled };
        });

        if (syncResult.authExpired) {
          setLibraryError('Phiên đăng nhập đã hết hạn. Bạn đăng nhập lại nhé.');
          return true;
        }
        if (syncResult.failedCount > 0) {
          const total = syncResult.syncedWriteCount + syncResult.failedCount;
          if (syncResult.syncedWriteCount > 0) {
            showToast(
              `Đã đồng bộ một phần (${syncResult.syncedWriteCount}/${total} mục) từ máy này. Còn ${syncResult.failedCount} mục chưa chuyển được vẫn được giữ lại.`,
              'info'
            );
          } else {
            showToast(
              `Chưa thể đồng bộ ${syncResult.failedCount} mục từ máy này vào tài khoản. Dữ liệu vẫn được giữ lại để thử lại sau.`,
              'error'
            );
          }
        } else {
          showToast('Đã đồng bộ thư viện và lịch sử xem từ máy này vào tài khoản.', 'success');
        }
      }
      return true;
    },
    [showToast]
  );

  const loadLibrary = useCallback(
    async (options?: { isBackgroundReconcile?: boolean }) => {
      const actorKey = currentActorKey;
      const sessionVersion = sessionVersionRef.current;
      readVersionRef.current += 1;
      const readVersion = readVersionRef.current;
      const isCurrent = () =>
        sessionVersionRef.current === sessionVersion && activeActorKeyRef.current === actorKey;

      if (!isAuthenticated || !token || !user) {
        if (status === 'checking') {
          setIsLibraryLoading(true);
          return;
        }
        if (status === 'unavailable') {
          setIsLibraryLoading(false);
          setLibraryError('Chưa kết nối được máy chủ tài khoản.');
          setSnapshot({ ownerKey: actorKey, watchlist: [], favorites: [], ratings: {} });
          return;
        }
        const guestData = readGuestLibrary();
        if (isCurrent()) {
          setSnapshot({ ownerKey: 'guest', ...guestData });
          setIsLibraryLoading(false);
          setLibraryError(null);
        }
        return;
      }

      if (!options?.isBackgroundReconcile) setIsLibraryLoading(true);

      abortSafe(readRequestRef.current);
      const controller = new AbortController();
      readRequestRef.current = controller;

      try {
        const serverLibrary = await fetchUserLibrary(token, controller.signal);
        if (!isCurrent()) return;

        serverLibraryRef.current = {
          actorKey,
          data: {
            watchlist: [...serverLibrary.watchlist],
            favorites: [...serverLibrary.favorites],
            ratings: { ...serverLibrary.ratings },
          },
        };

        setSnapshot((prev) => {
          if (!isCurrent()) return prev;
          const reconciled = reconcileLibrarySnapshot(serverLibrary, journalRef.current, readVersion);
          return { ownerKey: actorKey, ...reconciled };
        });

        if (!options?.isBackgroundReconcile && hasGuestSyncData()) {
          if (approvedSyncActorRef.current === actorKey) {
            setIsGuestSyncing(true);
            try {
              await syncGuestLibrary(
                token,
                serverLibrary,
                actorKey,
                sessionVersion,
                readVersion,
                controller.signal
              );
            } finally {
              if (isCurrent()) setIsGuestSyncing(false);
            }
          } else if (promptedSyncActorRef.current !== actorKey) {
            promptedSyncActorRef.current = actorKey;
            setGuestSyncPrompt(readGuestSyncSummary());
          }
        }

        if (!isCurrent()) return;
        setIsLibraryLoading(false);
        setLibraryError(null);
      } catch (err: unknown) {
        if (!isCurrent()) return;
        setIsLibraryLoading(false);
        if (err instanceof LibraryApiError) {
          setLibraryError(err.message);
        } else if (err instanceof Error && err.name === 'TimeoutError') {
          setLibraryError(err.message);
        } else {
          setLibraryError('Chưa tải được thư viện của bạn từ máy chủ.');
        }
      } finally {
        if (readRequestRef.current === controller) {
          readRequestRef.current = null;
        }
      }
    },
    [isAuthenticated, token, user, status, currentActorKey, syncGuestLibrary]
  );

  useEffect(() => {
    void loadLibrary();
    return () => {
      abortSafe(readRequestRef.current);
      abortSafe(guestSyncRequestRef.current);
      for (const controller of mutationRequestsRef.current.values()) abortSafe(controller);
      mutationRequestsRef.current.clear();
    };
  }, [loadLibrary]);

  const confirmGuestSync = useCallback(async () => {
    if (!isAuthenticated || !token || !user || guestSyncRequestRef.current) return;
    const actorKey = currentActorKey;
    const sessionVersion = sessionVersionRef.current;
    const isCurrent = () =>
      sessionVersionRef.current === sessionVersion && activeActorKeyRef.current === actorKey;

    approvedSyncActorRef.current = actorKey;
    promptedSyncActorRef.current = actorKey;

    const controller = new AbortController();
    guestSyncRequestRef.current = controller;
    setIsGuestSyncing(true);

    try {
      let baseLibrary: GuestLibraryData;
      if (serverLibraryRef.current?.actorKey === actorKey) {
        baseLibrary = serverLibraryRef.current.data;
      } else {
        baseLibrary = await fetchUserLibrary(token, controller.signal);
        if (!isCurrent()) return;
      }

      readVersionRef.current += 1;
      await syncGuestLibrary(token, baseLibrary, actorKey, sessionVersion, readVersionRef.current, controller.signal);
      if (isCurrent()) {
        setGuestSyncPrompt(null);
      }
    } catch {
      if (isCurrent()) {
        showToast('Chưa thể đồng bộ dữ liệu tạm lúc này. Dữ liệu vẫn được giữ lại để thử lại sau.', 'error');
        setGuestSyncPrompt(null);
      }
    } finally {
      if (guestSyncRequestRef.current === controller) {
        guestSyncRequestRef.current = null;
      }
      if (isCurrent()) {
        setIsGuestSyncing(false);
      }
    }
  }, [isAuthenticated, token, user, currentActorKey, syncGuestLibrary, showToast]);

  const declineGuestSync = useCallback(() => {
    if (guestSyncRequestRef.current) return;
    promptedSyncActorRef.current = currentActorKey;
    if (approvedSyncActorRef.current === currentActorKey) {
      approvedSyncActorRef.current = null;
    }
    clearGuestSyncData();
    setGuestSyncPrompt(null);
    showToast('Đã xóa dữ liệu tạm trên máy này.', 'info');
  }, [currentActorKey, showToast]);

  const dismissGuestSyncPrompt = useCallback(() => {
    if (guestSyncRequestRef.current) return;
    promptedSyncActorRef.current = currentActorKey;
    setGuestSyncPrompt(null);
  }, [currentActorKey]);

  const isActionPending = useCallback(
    (id: string, action?: LibraryActionKind) => {
      const prefix = `${sessionVersionRef.current}:${currentActorKey}`;
      if (!action) {
        return (
          pendingLocksRef.current.has(`${prefix}:watchlist:${id}`) ||
          pendingLocksRef.current.has(`${prefix}:favorite:${id}`) ||
          pendingLocksRef.current.has(`${prefix}:rating:${id}`)
        );
      }
      return pendingLocksRef.current.has(`${prefix}:${action}:${id}`);
    },
    [currentActorKey]
  );

  const isActionUnconfirmed = useCallback((id: string, action: LibraryActionKind = 'watchlist') => {
    return journalRef.current.get(`${action}:${id}`)?.status === 'uncertain';
  }, []);

  const getActionIntent = useCallback(
    (id: string, action: LibraryActionKind = 'watchlist'): LibraryActionIntent | null => {
      const operation = journalRef.current.get(`${action}:${id}`);
      if (!operation || operation.status !== 'uncertain') return null;
      if (operation.action === 'rating') {
        return typeof operation.desiredValue === 'number' ? 'rate' : 'remove_rating';
      }
      return operation.desiredValue ? 'add' : 'remove';
    },
    []
  );

  const getUnconfirmedRating = useCallback((id: string): number | undefined => {
    const operation = journalRef.current.get(`rating:${id}`);
    return operation && operation.action === 'rating' && operation.status === 'uncertain' && typeof operation.desiredValue === 'number'
      ? operation.desiredValue
      : undefined;
  }, []);

  const runLibraryMutation = useCallback(
    async (config: {
      action: LibraryActionKind;
      movieId: string;
      desiredValue: boolean | number | undefined;
      guestNext: () => GuestLibraryData;
      guestToast: { message: string; type: 'success' | 'info' };
      optimisticUpdate: (base: GuestLibraryData) => GuestLibraryData;
      rollbackUpdate: (base: GuestLibraryData) => GuestLibraryData;
      request: (authToken: string, signal: AbortSignal) => Promise<void>;
      successToast: { message: string; type: 'success' | 'info' };
      failureToastMessage: string;
      notifyRating?: boolean;
    }): Promise<boolean> => {
      const { action, movieId } = config;
      const actorKey = currentActorKey;
      const sessionVersion = sessionVersionRef.current;
      const lockKey = `${sessionVersion}:${actorKey}:${action}:${movieId}`;
      const operationKey = `${action}:${movieId}`;

      if (pendingLocksRef.current.has(lockKey)) return false;
      pendingLocksRef.current.add(lockKey);
      setPendingUpdateTick((tick) => tick + 1);

      const releaseLock = () => {
        pendingLocksRef.current.delete(lockKey);
        setPendingUpdateTick((tick) => tick + 1);
      };

      const isStillCurrent = () =>
        sessionVersionRef.current === sessionVersion && activeActorKeyRef.current === actorKey;

      if (status === 'checking') {
        showToast('Đang kiểm tra tài khoản, bạn chờ chút nhé…', 'info');
        releaseLock();
        return false;
      }
      if (status === 'unavailable') {
        showToast('Chưa kết nối được máy chủ tài khoản. Bạn thử lại sau nhé.', 'error');
        releaseLock();
        return false;
      }
      if (status === 'guest' || !isAuthenticated || !token) {
        const nextGuest = config.guestNext();
        if (!writeGuestLibrary(nextGuest)) {
          showToast('Chưa lưu được vào bộ nhớ máy này.', 'error');
          releaseLock();
          return false;
        }
        setSnapshot({ ownerKey: 'guest', ...nextGuest });
        showToast(config.guestToast.message, config.guestToast.type);
        releaseLock();
        return true;
      }

      const operationId = ++operationCounterRef.current;
      const record: OperationRecord =
        action === 'rating'
          ? {
              id: operationId,
              action: 'rating',
              movieId,
              readVersionAtStart: readVersionRef.current,
              committedAtReadVersion: null,
              status: 'pending',
              desiredValue: config.desiredValue as number | undefined,
            }
          : {
              id: operationId,
              action,
              movieId,
              readVersionAtStart: readVersionRef.current,
              committedAtReadVersion: null,
              status: 'pending',
              desiredValue: Boolean(config.desiredValue),
            };
      journalRef.current.set(operationKey, record);

      setSnapshot((prev) => {
        const base: GuestLibraryData =
          prev.ownerKey === actorKey
            ? { watchlist: prev.watchlist, favorites: prev.favorites, ratings: prev.ratings }
            : { watchlist: [], favorites: [], ratings: {} };
        return { ownerKey: actorKey, ...config.optimisticUpdate(base) };
      });

      const controller = new AbortController();
      mutationRequestsRef.current.set(operationId, controller);

      try {
        await config.request(token, controller.signal);
        if (!isStillCurrent()) return false;
        const operation = journalRef.current.get(operationKey);
        if (operation && operation.id === operationId) {
          operation.status = 'committed';
          operation.committedAtReadVersion = readVersionRef.current;
        }
        if (config.notifyRating) notifyRatingChanged(movieId);
        showToast(config.successToast.message, config.successToast.type);
        return true;
      } catch (err: unknown) {
        if (isStillCurrent()) {
          if (isUncertainNetworkError(err)) {
            const operation = journalRef.current.get(operationKey);
            if (operation && operation.id === operationId) operation.status = 'uncertain';
            showToast('Mạng chập chờn hoặc hết thời gian chờ. Dippie đang kiểm tra lại…', 'error');
            void loadLibrary({ isBackgroundReconcile: true });
          } else {
            const operation = journalRef.current.get(operationKey);
            if (operation && operation.id === operationId) journalRef.current.delete(operationKey);
            setSnapshot((prev) => {
              if (prev.ownerKey !== actorKey) return prev;
              return { ownerKey: actorKey, ...config.rollbackUpdate(prev) };
            });
            if (!(err instanceof LibraryApiError && (err.status === 401 || err.status === 403))) {
              showToast(config.failureToastMessage, 'error');
            }
          }
        }
        return false;
      } finally {
        mutationRequestsRef.current.delete(operationId);
        releaseLock();
      }
    },
    [currentActorKey, status, isAuthenticated, token, showToast, loadLibrary]
  );

  const toggleLibraryItem = useCallback(
    (action: 'watchlist' | 'favorite', id: string, title?: string, poster?: string): Promise<boolean> => {
      const isWatchlist = action === 'watchlist';
      const listKey = isWatchlist ? 'watchlist' : 'favorites';
      const listName = isWatchlist ? 'Xem sau' : 'Yêu thích';
      const visibleItems = isWatchlist ? visibleWatchlist : visibleFavorites;
      const existingOperation = journalRef.current.get(`${action}:${id}`);
      const isAdding =
        existingOperation && existingOperation.action !== 'rating' && existingOperation.status === 'uncertain'
          ? existingOperation.desiredValue
          : !visibleItems.includes(id);
      const alreadyInList = !isAdding;
      const displayTitle = title || id;

      return runLibraryMutation({
        action,
        movieId: id,
        desiredValue: isAdding,
        guestNext: () => ({
          watchlist: visibleWatchlist,
          favorites: visibleFavorites,
          ratings: visibleRatings,
          [listKey]: alreadyInList ? visibleItems.filter((existingId) => existingId !== id) : [...visibleItems, id],
        }),
        guestToast: alreadyInList
          ? { message: `Đã bỏ ${displayTitle} khỏi ${listName} (trên máy này)`, type: 'info' }
          : { message: `Đã thêm ${displayTitle} vào ${listName} (trên máy này)`, type: 'success' },
        optimisticUpdate: (base) => ({
          ...base,
          [listKey]: isAdding
            ? base[listKey].includes(id) ? base[listKey] : [...base[listKey], id]
            : base[listKey].filter((existingId) => existingId !== id),
        }),
        rollbackUpdate: (base) => ({
          ...base,
          [listKey]: alreadyInList
            ? base[listKey].includes(id) ? base[listKey] : [...base[listKey], id]
            : base[listKey].filter((existingId) => existingId !== id),
        }),
        request: (authToken, signal) =>
          updateLibraryList(listKey, authToken, id, {
            remove: alreadyInList,
            title: displayTitle,
            poster: poster || '',
            signal,
          }),
        successToast: alreadyInList
          ? { message: `Đã bỏ ${displayTitle} khỏi ${listName}`, type: 'info' }
          : { message: `Đã thêm ${displayTitle} vào ${listName}`, type: 'success' },
        failureToastMessage: `Chưa cập nhật được mục ${listName}. Bạn thử lại nhé.`,
      });
    },
    [visibleWatchlist, visibleFavorites, visibleRatings, runLibraryMutation]
  );

  const toggleWatchlist = useCallback(
    (id: string, title?: string, poster?: string) => toggleLibraryItem('watchlist', id, title, poster),
    [toggleLibraryItem]
  );

  const toggleFavorite = useCallback(
    (id: string, title?: string, poster?: string) => toggleLibraryItem('favorite', id, title, poster),
    [toggleLibraryItem]
  );

  const rateMovie = useCallback(
    (id: string, rating: number, title?: string): Promise<boolean> => {
      const validRating = Math.max(1, Math.min(10, Math.round(rating)));
      const displayTitle = title || id;
      const prevRating = visibleRatings[id];
      return runLibraryMutation({
        action: 'rating',
        movieId: id,
        desiredValue: validRating,
        guestNext: () => ({
          watchlist: visibleWatchlist,
          favorites: visibleFavorites,
          ratings: { ...visibleRatings, [id]: validRating },
        }),
        guestToast: { message: `Đã chấm ${displayTitle} ${validRating}/10 điểm (trên máy này)`, type: 'success' },
        optimisticUpdate: (base) => ({ ...base, ratings: { ...base.ratings, [id]: validRating } }),
        rollbackUpdate: (base) => {
          const nextRatings = { ...base.ratings };
          if (prevRating !== undefined) nextRatings[id] = prevRating;
          else delete nextRatings[id];
          return { ...base, ratings: nextRatings };
        },
        request: (authToken, signal) => saveUserRating(authToken, id, validRating, signal),
        successToast: { message: `Đã chấm ${displayTitle} ${validRating}/10 điểm`, type: 'success' },
        failureToastMessage: 'Chưa lưu được điểm. Bạn thử lại nhé.',
        notifyRating: true,
      });
    },
    [visibleWatchlist, visibleFavorites, visibleRatings, runLibraryMutation]
  );

  const removeRating = useCallback(
    (id: string, title?: string): Promise<boolean> => {
      const displayTitle = title || id;
      const prevRating = visibleRatings[id];
      return runLibraryMutation({
        action: 'rating',
        movieId: id,
        desiredValue: undefined,
        guestNext: () => {
          const nextRatings = { ...visibleRatings };
          delete nextRatings[id];
          return { watchlist: visibleWatchlist, favorites: visibleFavorites, ratings: nextRatings };
        },
        guestToast: { message: `Đã xóa điểm của ${displayTitle} (trên máy này)`, type: 'info' },
        optimisticUpdate: (base) => {
          const nextRatings = { ...base.ratings };
          delete nextRatings[id];
          return { ...base, ratings: nextRatings };
        },
        rollbackUpdate: (base) => {
          const nextRatings = { ...base.ratings };
          if (prevRating !== undefined) nextRatings[id] = prevRating;
          return { ...base, ratings: nextRatings };
        },
        request: (authToken, signal) => deleteUserRating(authToken, id, signal),
        successToast: { message: `Đã xóa điểm của ${displayTitle}`, type: 'info' },
        failureToastMessage: `Chưa xóa được điểm của ${displayTitle}. Bạn thử lại nhé.`,
        notifyRating: true,
      });
    },
    [visibleWatchlist, visibleFavorites, visibleRatings, runLibraryMutation]
  );

  const isWatchlisted = useCallback((id: string) => visibleWatchlist.includes(id), [visibleWatchlist]);
  const isFavorite = useCallback((id: string) => visibleFavorites.includes(id), [visibleFavorites]);
  const getRating = useCallback((id: string) => visibleRatings[id], [visibleRatings]);
  const refetchLibrary = useCallback(() => loadLibrary(), [loadLibrary]);
  const openChat = useCallback(() => {
    setHasOpenedChat(true);
    setIsChatOpen(true);
  }, []);
  const closeChat = useCallback(() => setIsChatOpen(false), []);
  const toggleChat = useCallback(() => {
    setHasOpenedChat(true);
    setIsChatOpen((prev) => !prev);
  }, []);

  const contextValue = useMemo<WatchlistContextType>(
    () => ({
      watchlist: visibleWatchlist,
      favorites: visibleFavorites,
      ratings: visibleRatings,
      toggleWatchlist,
      toggleFavorite,
      rateMovie,
      removeRating,
      isWatchlisted,
      isFavorite,
      getRating,
      isActionPending,
      isActionUnconfirmed,
      getActionIntent,
      getUnconfirmedRating,
      isLibraryLoading,
      libraryError,
      refetchLibrary,
      isGuestMode: status === 'guest',
      guestSyncPrompt,
      isGuestSyncing,
      confirmGuestSync,
      declineGuestSync,
      dismissGuestSyncPrompt,
      toasts,
      showToast,
      dismissToast,
      isChatOpen,
      openChat,
      closeChat,
      toggleChat,
    }),
    [
      visibleWatchlist,
      visibleFavorites,
      visibleRatings,
      toggleWatchlist,
      toggleFavorite,
      rateMovie,
      removeRating,
      isWatchlisted,
      isFavorite,
      getRating,
      isActionPending,
      isActionUnconfirmed,
      getActionIntent,
      getUnconfirmedRating,
      isLibraryLoading,
      libraryError,
      refetchLibrary,
      status,
      guestSyncPrompt,
      isGuestSyncing,
      confirmGuestSync,
      declineGuestSync,
      dismissGuestSyncPrompt,
      toasts,
      showToast,
      dismissToast,
      isChatOpen,
      openChat,
      closeChat,
      toggleChat,
    ]
  );

  return (
    <WatchlistContext.Provider value={contextValue}>
      {children}
      <Suspense fallback={null}>
        {isAuthModalOpen && <AuthModal />}
        {hasOpenedChat && <CineBot isOpen={isChatOpen} onClose={closeChat} />}
      </Suspense>
      {guestSyncPrompt && (
        <GuestSyncDialog
          summary={guestSyncPrompt}
          busy={isGuestSyncing}
          onConfirm={() => void confirmGuestSync()}
          onDecline={declineGuestSync}
          onDismiss={dismissGuestSyncPrompt}
        />
      )}
      <div
        role="region"
        aria-label="Thông báo"
        data-testid="toast-region"
        className={`fixed z-50 flex flex-col gap-2 pointer-events-none sm:max-w-md ${
          isChatOpen
            ? 'top-20 left-4 right-4 sm:left-auto sm:right-5'
            : 'bottom-5 left-4 right-4 sm:left-auto sm:right-5'
        }`}
      >
        {toasts.map((toast) => (
          <div
            key={toast.id}
            role="status"
            className={`pointer-events-auto flex items-center justify-between gap-2 rounded-lg border-2 px-4 py-2 text-xs sm:text-sm font-medium shadow-brand-sm animate-slide-up transition-all ${
              toast.type === 'error'
                ? 'border-red-800 bg-paper-white text-red-900'
                : toast.type === 'success'
                ? 'border-ink bg-mint text-ink'
                : 'border-ink bg-paper-white text-ink'
            }`}
          >
            <span className="min-w-0 flex-1 break-words py-1">{toast.message}</span>
            <button
              type="button"
              onClick={() => dismissToast(toast.id)}
              className="inline-flex h-11 w-11 min-h-[44px] min-w-[44px] shrink-0 items-center justify-center rounded-md text-muted hover:bg-surface-elevated hover:text-ink cursor-pointer"
              aria-label="Đóng thông báo"
            >
              ✕
            </button>
          </div>
        ))}
      </div>
    </WatchlistContext.Provider>
  );
}

export function useWatchlist() {
  const context = useContext(WatchlistContext);
  if (!context) {
    throw new Error('useWatchlist must be used within a WatchlistProvider');
  }
  return context;
}
