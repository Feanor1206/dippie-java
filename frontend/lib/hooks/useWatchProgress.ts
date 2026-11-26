import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '@/lib/context/AuthContext';
import { parseTimestamp } from '@/lib/timestamps';
import {
  getGuestWatchProgress,
  getWatchProgress,
  listWatchProgress,
  notifyWatchProgressUpdated,
  saveGuestWatchProgress,
  saveWatchProgress as saveWatchProgressRequest,
  WatchProgressError,
  WATCH_HISTORY_CHANGED_EVENT,
  WATCH_PROGRESS_LIMIT,
  WATCH_PROGRESS_UPDATED_EVENT,
  type WatchProgressDraft,
  type WatchProgressRecord,
  type WatchProgressUpdateDetail,
} from '@/lib/services/watchProgressService';

export type WatchProgressSaveReason = 'interval' | 'pause' | 'ended' | 'episode-change' | 'leave';

type ProgressState = {
  scopeId: string;
  records: WatchProgressRecord[];
  loading: boolean;
  error: string | null;
};

type ScopeIdentity = {
  id: string;
  generation: number;
  status: string;
  token: string | null;
};

type QueuedSave = {
  draft: WatchProgressDraft;
  scope: ScopeIdentity;
  reason: WatchProgressSaveReason;
  resolve: (record: WatchProgressRecord | null) => void;
};

type InFlightSave = {
  assetId: string;
  positionMs: number;
};

type CoordinatorApi = {
  getWatchProgress: typeof getWatchProgress;
  saveWatchProgress: typeof saveWatchProgressRequest;
};

type CoordinatorHooks = {
  isCurrentScope: (scope: ScopeIdentity) => boolean;
  onRecordLoaded: (scope: ScopeIdentity, record: WatchProgressRecord) => void;
  onRecordSaved: (scope: ScopeIdentity, record: WatchProgressRecord) => void;
  onSaveError: (scope: ScopeIdentity, error: unknown, reason: WatchProgressSaveReason) => void;
};

class ScopedJobError {
  constructor(
    public readonly error: unknown,
    public readonly job: QueuedSave
  ) {}
}

const INTERVAL_THROTTLE_MS = 14_000;
const CONFLICT_RETRY_FAILED_MESSAGE =
  'Bạn vừa xem tập này ở cửa sổ khác. Dippie đã giữ mốc mới nhất.';

function getErrorInfo(err: unknown): { status?: number; isCanceled: boolean; message?: string } {
  if (!err || typeof err !== 'object') return { isCanceled: false };
  const candidate = err as { status?: unknown; isCanceled?: unknown; name?: unknown; message?: unknown };
  return {
    status: typeof candidate.status === 'number' ? candidate.status : undefined,
    isCanceled: Boolean(candidate.isCanceled) || candidate.name === 'AbortError',
    message: typeof candidate.message === 'string' && candidate.message ? candidate.message : undefined,
  };
}

function upsertRecord(records: WatchProgressRecord[], record: WatchProgressRecord): WatchProgressRecord[] {
  return [record, ...records.filter((item) => item.episodeId !== record.episodeId)]
    .sort((a, b) => parseTimestamp(b.updatedAt) - parseTimestamp(a.updatedAt))
    .slice(0, WATCH_PROGRESS_LIMIT);
}

export class EpisodeProgressCoordinator {
  private readonly readControllers = new Set<AbortController>();
  private readonly writeControllers = new Set<AbortController>();
  private readonly pending = new Map<string, QueuedSave>();
  private readonly workers = new Map<string, Promise<void>>();
  private readonly versions = new Map<string, number>();
  private readonly inFlight = new Map<string, InFlightSave>();
  private readonly lastIntervalSave = new Map<string, number>();
  private activeScopeId = '';

  constructor(
    private readonly api: CoordinatorApi,
    private hooks: CoordinatorHooks
  ) {}

  updateHooks(hooks: CoordinatorHooks): void {
    this.hooks = hooks;
  }

  trackController(controller: AbortController): void {
    this.readControllers.add(controller);
  }

  releaseController(controller: AbortController): void {
    this.readControllers.delete(controller);
  }

  recordVersion(scopeId: string, episodeId: string, version: number): void {
    const key = `${scopeId}:${episodeId}`;
    const existing = this.versions.get(key);
    if (existing === undefined || version >= existing) {
      this.versions.set(key, version);
    }
  }

  syncVersions(scopeId: string, records: WatchProgressRecord[]): void {
    const prefix = `${scopeId}:`;
    for (const key of [...this.versions.keys()]) {
      if (key.startsWith(prefix) && !this.inFlight.has(key) && !this.pending.has(key)) {
        this.versions.delete(key);
      }
    }
    for (const record of records) {
      const key = `${scopeId}:${record.episodeId}`;
      if (!this.inFlight.has(key) && !this.pending.has(key)) {
        this.versions.set(key, record.version);
      } else {
        this.recordVersion(scopeId, record.episodeId, record.version);
      }
    }
  }

  abortReadRequests(): void {
    this.readControllers.forEach((controller) => controller.abort());
    this.readControllers.clear();
  }

  abortActiveRequests(): void {
    this.abortReadRequests();
    this.writeControllers.forEach((controller) => controller.abort());
    this.writeControllers.clear();
  }

  resetForScope(scopeId: string): void {
    if (this.activeScopeId !== scopeId) {
      this.abortActiveRequests();
      this.pending.forEach((job) => job.resolve(null));
      this.pending.clear();
      this.workers.clear();
      this.versions.clear();
      this.inFlight.clear();
      this.lastIntervalSave.clear();
      this.activeScopeId = scopeId;
    } else {
      this.abortReadRequests();
    }
  }

  shouldThrottleInterval(scopeId: string, episodeId: string, now = Date.now()): boolean {
    const key = `${scopeId}:${episodeId}`;
    const previous = this.lastIntervalSave.get(key) || 0;
    if (now - previous < INTERVAL_THROTTLE_MS) return true;
    this.lastIntervalSave.set(key, now);
    return false;
  }

  enqueueSave(
    draft: WatchProgressDraft,
    scope: ScopeIdentity,
    reason: WatchProgressSaveReason
  ): Promise<WatchProgressRecord | null> {
    const queueKey = `${scope.id}:${draft.episodeId}`;
    const running = this.workers.has(queueKey);
    const active = this.inFlight.get(queueKey);
    if (
      reason === 'leave' &&
      running &&
      !this.pending.has(queueKey) &&
      active &&
      active.assetId === draft.assetId &&
      active.positionMs === draft.positionMs
    ) {
      return Promise.resolve(null);
    }

    return new Promise((resolve) => {
      this.pending.get(queueKey)?.resolve(null);
      this.pending.set(queueKey, { draft, scope, reason, resolve });
      if (running) return;

      const worker = this.drainQueue(queueKey);
      this.workers.set(queueKey, worker);
      void worker.finally(() => {
        if (this.workers.get(queueKey) === worker) this.workers.delete(queueKey);
      });
    });
  }

  private async drainQueue(queueKey: string): Promise<void> {
    while (this.pending.has(queueKey)) {
      const job = this.pending.get(queueKey)!;
      this.pending.delete(queueKey);
      if (!this.hooks.isCurrentScope(job.scope) || !job.scope.token) {
        job.resolve(null);
        continue;
      }

      const controller = job.reason === 'leave' ? null : new AbortController();
      if (controller) this.writeControllers.add(controller);
      this.inFlight.set(queueKey, { assetId: job.draft.assetId, positionMs: job.draft.positionMs });

      let activeJob = job;
      try {
        const result = await this.executeJob(queueKey, job, controller?.signal);
        if (!result) continue;
        activeJob = result.job;
        if (!this.hooks.isCurrentScope(activeJob.scope)) {
          activeJob.resolve(null);
          continue;
        }
        this.versions.set(`${activeJob.scope.id}:${activeJob.draft.episodeId}`, result.record.version);
        this.hooks.onRecordSaved(activeJob.scope, result.record);
        activeJob.resolve(result.record);
      } catch (err: unknown) {
        const failedJob = err instanceof ScopedJobError ? err.job : activeJob;
        const rawError = err instanceof ScopedJobError ? err.error : err;
        const info = getErrorInfo(rawError);
        if (this.hooks.isCurrentScope(failedJob.scope) && !info.isCanceled) {
          this.hooks.onSaveError(failedJob.scope, rawError, failedJob.reason);
        }
        failedJob.resolve(null);
      } finally {
        this.inFlight.delete(queueKey);
        if (controller) this.writeControllers.delete(controller);
      }
    }
  }

  private async executeJob(
    queueKey: string,
    job: QueuedSave,
    signal?: AbortSignal
  ): Promise<{ job: QueuedSave; record: WatchProgressRecord } | null> {
    const versionKey = `${job.scope.id}:${job.draft.episodeId}`;
    let expectedVersion = this.versions.get(versionKey);
    if (expectedVersion === undefined) {
      if (job.reason === 'leave') {
        expectedVersion = -1;
      } else {
        const existing = await this.api.getWatchProgress(job.scope.token!, job.draft.episodeId, { signal });
        if (!this.hooks.isCurrentScope(job.scope)) {
          job.resolve(null);
          return null;
        }
        expectedVersion = existing?.version ?? -1;
        if (existing) {
          this.versions.set(versionKey, existing.version);
          this.hooks.onRecordLoaded(job.scope, existing);
        }
      }
    }

    const requestOptions = job.reason === 'leave' ? { keepalive: true } : { signal };
    try {
      const record = await this.api.saveWatchProgress(
        job.scope.token!,
        job.draft.episodeId,
        { assetId: job.draft.assetId, positionMs: job.draft.positionMs },
        expectedVersion,
        requestOptions
      );
      return { job, record };
    } catch (err: unknown) {
      if (getErrorInfo(err).status !== 409) throw err;
      return await this.refreshVersionAndRetryOnce(queueKey, versionKey, job, signal);
    }
  }

  private async refreshVersionAndRetryOnce(
    queueKey: string,
    versionKey: string,
    job: QueuedSave,
    signal?: AbortSignal
  ): Promise<{ job: QueuedSave; record: WatchProgressRecord } | null> {
    const options = job.reason === 'leave' ? { keepalive: true } : { signal };
    const latest = await this.api.getWatchProgress(job.scope.token!, job.draft.episodeId, options);
    if (!this.hooks.isCurrentScope(job.scope)) {
      job.resolve(null);
      return null;
    }

    const retryVersion = latest?.version ?? -1;
    this.versions.set(versionKey, retryVersion);
    if (latest) this.hooks.onRecordLoaded(job.scope, latest);

    let activeJob = job;
    const newer = this.pending.get(queueKey);
    if (newer) {
      this.pending.delete(queueKey);
      job.resolve(null);
      if (!this.hooks.isCurrentScope(newer.scope) || !newer.scope.token) {
        newer.resolve(null);
        return null;
      }
      activeJob = newer;
      this.inFlight.set(queueKey, { assetId: activeJob.draft.assetId, positionMs: activeJob.draft.positionMs });
    }

    const retryOptions = activeJob.reason === 'leave' ? { keepalive: true } : { signal };
    try {
      const record = await this.api.saveWatchProgress(
        activeJob.scope.token!,
        activeJob.draft.episodeId,
        { assetId: activeJob.draft.assetId, positionMs: activeJob.draft.positionMs },
        retryVersion,
        retryOptions
      );
      return { job: activeJob, record };
    } catch (retryErr: unknown) {
      if (getErrorInfo(retryErr).status !== 409) {
        throw new ScopedJobError(retryErr, activeJob);
      }
      const refreshed = await this.api
        .getWatchProgress(activeJob.scope.token!, activeJob.draft.episodeId, retryOptions)
        .catch(() => latest);
      if (!this.hooks.isCurrentScope(activeJob.scope)) {
        activeJob.resolve(null);
        return null;
      }
      const serverRecord = refreshed ?? latest;
      this.versions.set(versionKey, serverRecord?.version ?? -1);
      if (serverRecord) this.hooks.onRecordLoaded(activeJob.scope, serverRecord);
      this.pending.get(queueKey)?.resolve(null);
      this.pending.delete(queueKey);
      throw new ScopedJobError(new WatchProgressError(CONFLICT_RETRY_FAILED_MESSAGE, 409), activeJob);
    }
  }
}

export function useWatchProgress() {
  const { status, token, user } = useAuth();
  const scopeId =
    status === 'guest'
      ? 'guest'
      : status === 'authenticated' && user && token
      ? `account:${user.id}:${token}`
      : status;
  const [state, setState] = useState<ProgressState>({ scopeId: '', records: [], loading: true, error: null });
  const [retryCount, setRetryCount] = useState(0);
  const mountedRef = useRef(true);
  const recentUpdatesRef = useRef<{ scopeId: string; byEpisode: Map<string, WatchProgressRecord> }>({
    scopeId: '',
    byEpisode: new Map(),
  });
  const identityRef = useRef<ScopeIdentity>({ id: scopeId, generation: 0, status, token });
  if (identityRef.current.id !== scopeId) {
    identityRef.current = { id: scopeId, generation: identityRef.current.generation + 1, status, token };
  }
  if (recentUpdatesRef.current.scopeId !== scopeId) {
    recentUpdatesRef.current = { scopeId, byEpisode: new Map() };
  }

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const isCurrentScope = useCallback(
    (scope: ScopeIdentity) =>
      identityRef.current.id === scope.id && identityRef.current.generation === scope.generation,
    []
  );

  const hooks: CoordinatorHooks = {
    isCurrentScope,
    onRecordLoaded: (scope, record) => {
      if (!mountedRef.current) return;
      setState((prev) => (prev.scopeId === scope.id ? { ...prev, records: upsertRecord(prev.records, record) } : prev));
    },
    onRecordSaved: (scope, record) => {
      if (recentUpdatesRef.current.scopeId === scope.id) {
        recentUpdatesRef.current.byEpisode.set(record.episodeId, record);
      }
      if (typeof notifyWatchProgressUpdated === 'function') {
        notifyWatchProgressUpdated(scope.id, record);
      }
      if (!mountedRef.current) return;
      setState((prev) =>
        prev.scopeId === scope.id ? { ...prev, records: upsertRecord(prev.records, record), error: null } : prev
      );
    },
    onSaveError: (scope, err, reason) => {
      if (!mountedRef.current) return;
      const info = getErrorInfo(err);
      if (reason === 'leave' && info.status !== 409 && info.status !== 401) return;
      setState((prev) =>
        prev.scopeId === scope.id ? { ...prev, error: info.message || 'Chưa lưu được mốc xem.' } : prev
      );
    },
  };

  const coordinatorRef = useRef<EpisodeProgressCoordinator | null>(null);
  if (!coordinatorRef.current) {
    coordinatorRef.current = new EpisodeProgressCoordinator(
      { getWatchProgress, saveWatchProgress: saveWatchProgressRequest },
      hooks
    );
  } else {
    coordinatorRef.current.updateHooks(hooks);
  }

  useEffect(() => {
    const coordinator = coordinatorRef.current!;
    coordinator.resetForScope(scopeId);
    const scope = identityRef.current;
    setState({
      scopeId: scope.id,
      records: [],
      loading: status === 'checking' || status === 'authenticated',
      error: status === 'unavailable' ? 'Chưa kết nối được máy chủ tài khoản.' : null,
    });

    if (status === 'checking' || status === 'unavailable') {
      return () => coordinator.abortReadRequests();
    }
    if (status === 'guest') {
      const records = getGuestWatchProgress();
      if (isCurrentScope(scope)) setState({ scopeId: scope.id, records, loading: false, error: null });
      return () => coordinator.abortReadRequests();
    }
    if (status !== 'authenticated' || !token || !user) {
      return () => coordinator.abortReadRequests();
    }

    const controller = new AbortController();
    coordinator.trackController(controller);
    listWatchProgress(token, { signal: controller.signal })
      .then((records) => {
        if (!isCurrentScope(scope)) return;
        let merged = records;
        if (recentUpdatesRef.current.scopeId === scope.id && recentUpdatesRef.current.byEpisode.size > 0) {
          for (const [episodeId, recent] of [...recentUpdatesRef.current.byEpisode.entries()]) {
            const existing = merged.find((item) => item.episodeId === episodeId);
            const isRecentNewer =
              !existing ||
              recent.version > existing.version ||
              (recent.version === existing.version &&
                parseTimestamp(recent.updatedAt) > parseTimestamp(existing.updatedAt));
            if (isRecentNewer) {
              merged = upsertRecord(merged, recent);
            } else {
              recentUpdatesRef.current.byEpisode.delete(episodeId);
            }
          }
        }
        coordinator.syncVersions(scope.id, merged);
        if (mountedRef.current) {
          setState({ scopeId: scope.id, records: merged, loading: false, error: null });
        }
      })
      .catch((err: unknown) => {
        const info = getErrorInfo(err);
        if (!isCurrentScope(scope) || info.isCanceled || !mountedRef.current) return;
        setState({
          scopeId: scope.id,
          records: [],
          loading: false,
          error: info.message || 'Chưa tải được lịch sử xem.',
        });
      })
      .finally(() => coordinator.releaseController(controller));

    return () => coordinator.abortReadRequests();
  }, [scopeId, status, token, user?.id, retryCount, isCurrentScope]);

  const saveProgress = useCallback(
    (draft: WatchProgressDraft, reason: WatchProgressSaveReason = 'pause'): Promise<WatchProgressRecord | null> => {
      const scope = identityRef.current;
      const coordinator = coordinatorRef.current!;
      if (reason === 'interval' && coordinator.shouldThrottleInterval(scope.id, draft.episodeId)) {
        return Promise.resolve(null);
      }
      if (scope.status === 'guest') {
        try {
          const records = saveGuestWatchProgress(draft);
          const record = records.find((item) => item.episodeId === draft.episodeId) || null;
          if (record && typeof notifyWatchProgressUpdated === 'function') {
            notifyWatchProgressUpdated(scope.id, record);
          }
          if (isCurrentScope(scope) && mountedRef.current) {
            setState({ scopeId: scope.id, records, loading: false, error: null });
          }
          return Promise.resolve(record);
        } catch (err: unknown) {
          const info = getErrorInfo(err);
          if (isCurrentScope(scope) && mountedRef.current) {
            setState((prev) => ({ ...prev, error: info.message || 'Chưa lưu được mốc xem trên máy này.' }));
          }
          return Promise.reject(err);
        }
      }
      if (scope.status !== 'authenticated' || !scope.token) return Promise.resolve(null);
      return coordinator.enqueueSave(draft, scope, reason);
    },
    [isCurrentScope]
  );

  const reload = useCallback(() => setRetryCount((value) => value + 1), []);

  useEffect(() => {
    if (
      (status !== 'authenticated' && status !== 'guest') ||
      typeof window === 'undefined' ||
      typeof window.addEventListener !== 'function'
    ) {
      return;
    }
    const historyEventName =
      typeof WATCH_HISTORY_CHANGED_EVENT === 'string' ? WATCH_HISTORY_CHANGED_EVENT : 'dippie:watch-history-changed';
    const progressEventName =
      typeof WATCH_PROGRESS_UPDATED_EVENT === 'string' ? WATCH_PROGRESS_UPDATED_EVENT : 'dippie:watch-progress-updated';
    const handleHistoryChanged = () => {
      recentUpdatesRef.current.byEpisode.clear();
      reload();
    };
    const handleProgressUpdated = (event: Event) => {
      const detail = (event as CustomEvent<WatchProgressUpdateDetail> | null)?.detail;
      if (!detail || detail.scopeId !== scopeId || !detail.record) return;
      const record = detail.record;
      recentUpdatesRef.current.byEpisode.set(record.episodeId, record);
      coordinatorRef.current?.recordVersion(scopeId, record.episodeId, record.version);
      if (!mountedRef.current) return;
      setState((prev) =>
        prev.scopeId === scopeId
          ? { ...prev, records: upsertRecord(prev.records, record), error: null }
          : prev
      );
    };
    window.addEventListener(historyEventName, handleHistoryChanged);
    window.addEventListener(progressEventName, handleProgressUpdated);
    if (status === 'authenticated') {
      window.addEventListener('dippie:watch-progress-synced', handleHistoryChanged);
    }
    return () => {
      if (typeof window.removeEventListener === 'function') {
        window.removeEventListener(historyEventName, handleHistoryChanged);
        window.removeEventListener(progressEventName, handleProgressUpdated);
        if (status === 'authenticated') {
          window.removeEventListener('dippie:watch-progress-synced', handleHistoryChanged);
        }
      }
    };
  }, [status, scopeId, reload]);

  const visibleState =
    state.scopeId === scopeId
      ? state
      : {
          scopeId,
          records: [] as WatchProgressRecord[],
          loading: status === 'checking' || status === 'authenticated',
          error: null,
        };

  return {
    records: visibleState.records,
    loading: visibleState.loading,
    error: visibleState.error,
    saveProgress,
    reload,
    isGuest: status === 'guest',
  };
}
