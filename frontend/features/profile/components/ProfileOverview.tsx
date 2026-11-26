import React, { useMemo, useState } from 'react';
import { Link } from 'react-router';
import {
  AlertCircle,
  ArrowRight,
  Bookmark,
  Film,
  Heart,
  Loader2,
  RefreshCw,
  Star,
  Trash2,
  User,
} from 'lucide-react';
import { MovieCard } from '@/features/movies/components/MovieCard';
import { ChildAvatarBadge } from '@/features/profiles/components/ChildAvatarBadge';
import { useWatchlist } from '@/lib/context/WatchlistContext';
import { useAuth } from '@/lib/context/AuthContext';
import { genreLabel, getUserInitials, normalizeGenreLabels } from '@/lib/labels';
import type { LibraryActionIntent, LibraryActionKind } from '@/lib/services/libraryService';
import {
  clearProfileMetadataCache,
  pruneProfileMetadataCache,
  useProfileMetadata,
  type HydratedMovieItem,
} from '@/features/profile/useProfileMetadata';

export { clearProfileMetadataCache };

type Tab = 'watchlist' | 'favorites' | 'ratings';
const PROFILE_TABS: readonly Tab[] = ['watchlist', 'favorites', 'ratings'];

const TAB_CONFIG: Record<
  Tab,
  {
    action: LibraryActionKind;
    emptyTitle: string;
    emptyDescription: string;
    removeLabel: string;
    addAria: string;
    removeAria: string;
    addTitle: string;
    removeTitle: string;
    retryAddLabel: string;
    retryRemoveLabel: string;
  }
> = {
  watchlist: {
    action: 'watchlist',
    emptyTitle: 'Chưa có phim nào trong Xem sau',
    emptyDescription:
      'Nhấn nút Xem sau trên thẻ phim hoặc trang xem phim để lưu các tập muốn mở lại sau.',
    removeLabel: 'Bỏ khỏi Xem sau',
    addAria: 'Chưa lưu được vào Xem sau, nhấn để thử lại',
    removeAria: 'Chưa bỏ được khỏi Xem sau, nhấn để thử lại',
    addTitle: 'Chưa lưu được, nhấn để thử lại',
    removeTitle: 'Chưa bỏ được, nhấn để thử lại',
    retryAddLabel: 'Thử lưu lại',
    retryRemoveLabel: 'Thử bỏ lại',
  },
  favorites: {
    action: 'favorite',
    emptyTitle: 'Chưa có phim Yêu thích',
    emptyDescription:
      'Nhấn nút Yêu thích trên thẻ phim hoặc trang xem phim để lưu lại những phim cả nhà thích.',
    removeLabel: 'Bỏ khỏi Yêu thích',
    addAria: 'Chưa thêm được vào Yêu thích, nhấn để thử lại',
    removeAria: 'Chưa bỏ được khỏi Yêu thích, nhấn để thử lại',
    addTitle: 'Chưa thêm được, nhấn để thử lại',
    removeTitle: 'Chưa bỏ được, nhấn để thử lại',
    retryAddLabel: 'Thử thêm lại',
    retryRemoveLabel: 'Thử bỏ lại',
  },
  ratings: {
    action: 'rating',
    emptyTitle: 'Chưa chấm điểm phim nào',
    emptyDescription:
      'Chọn điểm từ 1 đến 10 trên trang xem phim để lưu điểm cho phim bộ hoặc từng tập.',
    removeLabel: 'Xóa điểm',
    addAria: 'Chưa lưu được điểm, nhấn để thử lại',
    removeAria: 'Chưa xóa được điểm, nhấn để thử lại',
    addTitle: 'Chưa lưu được điểm, nhấn để thử lại',
    removeTitle: 'Chưa xóa được điểm, nhấn để thử lại',
    retryAddLabel: 'Thử lưu lại',
    retryRemoveLabel: 'Thử xóa lại',
  },
};

const GRID_CLASS = 'grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-4 lg:grid-cols-5 xl:grid-cols-6';

type ResolvedEntry = HydratedMovieItem & { id: string; rating?: number };

function MissingItemCard({
  item,
  tab,
  pending,
  unconfirmed,
  intent,
  onRetryFetch,
  onRemove,
}: {
  item: ResolvedEntry;
  tab: Tab;
  pending: boolean;
  unconfirmed: boolean;
  intent: LibraryActionIntent | null;
  onRetryFetch: () => void;
  onRemove: () => void;
}) {
  const tabConfig = TAB_CONFIG[tab];
  const isError = item.status === 'error';
  const isAddIntent = intent === 'add' || intent === 'rate';

  const ariaLabel = pending
    ? 'Đang xử lý…'
    : unconfirmed
    ? isAddIntent
      ? tabConfig.addAria
      : tabConfig.removeAria
    : tabConfig.removeLabel;

  const title = unconfirmed ? (isAddIntent ? tabConfig.addTitle : tabConfig.removeTitle) : undefined;
  const buttonText = unconfirmed ? (isAddIntent ? tabConfig.retryAddLabel : tabConfig.retryRemoveLabel) : tabConfig.removeLabel;

  const removeButton = (
    <button
      disabled={pending}
      onClick={onRemove}
      className={`${isError ? '' : 'mt-3 '}flex min-h-[44px] items-center justify-center gap-1 rounded py-1.5 text-[11px] transition-colors cursor-pointer disabled:opacity-50 ${
        unconfirmed
          ? 'bg-amber-500/20 text-amber-900 hover:bg-amber-500/30 border border-amber-400/40'
          : 'bg-surface-elevated text-red-900 hover:bg-red-500/20'
      }`}
      aria-label={ariaLabel}
      title={title}
    >
      {pending ? (
        <Loader2 size={12} className="animate-spin" />
      ) : unconfirmed ? (
        <RefreshCw size={12} className="text-amber-900 stroke-[2.5]" />
      ) : (
        <Trash2 size={12} />
      )}{' '}
      {buttonText}
    </button>
  );

  return (
    <div
      className={`flex flex-col justify-between rounded-lg border ${
        isError ? 'border-amber-900/40' : 'border-ink/30'
      } bg-surface-muted p-4 text-xs`}
    >
      <div>
        <div className="flex items-center gap-1 text-[11px] text-amber-900 font-semibold mb-1">
          <AlertCircle size={12} /> {isError ? 'Lỗi kết nối' : 'Không còn trong kho phim'}
        </div>
        <p className="font-bold text-ink truncate">{item.id}</p>
        {item.rating !== undefined && (
          <p className="text-amber-900 font-semibold mt-1">Điểm bạn chấm: {item.rating}/10</p>
        )}
        <p className="text-[11px] text-muted mt-1">
          {isError
            ? 'Chưa kết nối được máy chủ để tải thông tin phim.'
            : 'Phim này không còn trong kho phim Dippie.'}
        </p>
      </div>
      {isError ? (
        <div className="mt-3 flex flex-col gap-1.5">
          <button
            onClick={onRetryFetch}
            className="flex min-h-[44px] items-center justify-center gap-1 rounded bg-surface-elevated py-1.5 text-[11px] text-muted hover:bg-surface-elevated transition-colors cursor-pointer"
          >
            <RefreshCw size={12} /> Thử tải lại
          </button>
          {removeButton}
        </div>
      ) : (
        removeButton
      )}
    </div>
  );
}

export function ProfileOverview() {
  const [activeTab, setActiveTab] = useState<Tab>('watchlist');
  const {
    watchlist,
    favorites,
    ratings,
    openChat,
    isLibraryLoading,
    libraryError,
    refetchLibrary,
    isGuestMode,
    toggleWatchlist,
    toggleFavorite,
    rateMovie,
    removeRating,
    isActionPending,
    isActionUnconfirmed,
    getActionIntent,
    getUnconfirmedRating,
    showToast,
  } = useWatchlist();

  const { user, status, isKidMode, activeProfile, openAuthModal, retryVerify } = useAuth();

  const allItemIds = useMemo(
    () => Array.from(new Set([...watchlist, ...favorites, ...Object.keys(ratings)])),
    [watchlist, favorites, ratings]
  );

  const { resolveItem, fetchMovie } = useProfileMetadata(allItemIds);

  const watchlistItems = useMemo<ResolvedEntry[]>(
    () => watchlist.map((id) => ({ id, ...resolveItem(id) })),
    [watchlist, resolveItem]
  );
  const favoriteItems = useMemo<ResolvedEntry[]>(
    () => favorites.map((id) => ({ id, ...resolveItem(id) })),
    [favorites, resolveItem]
  );
  const ratingItems = useMemo<ResolvedEntry[]>(
    () => Object.entries(ratings).map(([id, rating]) => ({ id, rating, ...resolveItem(id) })),
    [ratings, resolveItem]
  );

  const topGenres = useMemo(() => {
    const counts: Record<string, number> = {};
    for (const item of [...watchlistItems, ...favoriteItems, ...ratingItems]) {
      if (item.status !== 'found') continue;
      for (const label of normalizeGenreLabels(item.movie.genres)) {
        counts[label] = (counts[label] || 0) + 1;
      }
    }
    return Object.entries(counts)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 4);
  }, [watchlistItems, favoriteItems, ratingItems]);

  const handleRemoveItem = async (id: string, tab: Tab, title?: string) => {
    if (tab === 'watchlist') {
      await toggleWatchlist(id, title);
      return;
    }
    if (tab === 'favorites') {
      await toggleFavorite(id, title);
      return;
    }
    if (isActionUnconfirmed(id, 'rating') && getActionIntent(id, 'rating') === 'rate') {
      const score = getUnconfirmedRating(id);
      if (typeof score === 'number' && Number.isFinite(score) && score >= 1 && score <= 10) {
        await rateMovie(id, score, title);
      } else {
        showToast('Chưa tìm thấy điểm hợp lệ để lưu lại.', 'error');
      }
      return;
    }
    await removeRating(id, title);
  };

  const handleTabKeyDown = (event: React.KeyboardEvent<HTMLButtonElement>, tab: Tab) => {
    const idx = PROFILE_TABS.indexOf(tab);
    if (idx < 0) return;
    const keyMap: Record<string, Tab> = {
      ArrowRight: PROFILE_TABS[(idx + 1) % PROFILE_TABS.length],
      ArrowLeft: PROFILE_TABS[(idx - 1 + PROFILE_TABS.length) % PROFILE_TABS.length],
      Home: PROFILE_TABS[0],
      End: PROFILE_TABS[PROFILE_TABS.length - 1],
    };
    const nextTab = keyMap[event.key];
    if (!nextTab) return;
    event.preventDefault();
    setActiveTab(nextTab);
    if (typeof document !== 'undefined') {
      document.getElementById(`profile-tab-${nextTab}`)?.focus();
    }
  };

  const ratingsCount = Object.keys(ratings).length;
  const tabMeta: Array<{ id: Tab; label: string; count: number; icon: React.ReactNode }> = [
    { id: 'watchlist', label: 'Xem sau', count: watchlist.length, icon: <Bookmark size={15} /> },
    { id: 'favorites', label: 'Yêu thích', count: favorites.length, icon: <Heart size={15} /> },
    { id: 'ratings', label: 'Chấm điểm', count: ratingsCount, icon: <Star size={15} /> },
  ];

  const activeItems =
    activeTab === 'watchlist' ? watchlistItems : activeTab === 'favorites' ? favoriteItems : ratingItems;
  const activeTabConfig = TAB_CONFIG[activeTab];
  const isInitialLoading = isLibraryLoading && watchlist.length === 0 && favorites.length === 0 && ratingsCount === 0;

  return (
    <main id="main-content" tabIndex={-1} className="page-shell min-h-[75vh] py-8 sm:py-12">
      {status === 'unavailable' && (
        <div className="mb-6 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 text-amber-900">
          <div className="flex items-center gap-3">
            <AlertCircle size={20} className="shrink-0 text-amber-900" />
            <div className="text-xs sm:text-sm">
              <span className="font-bold">Chưa kết nối được máy chủ:</span> Thư viện chưa đồng bộ được lúc này.
            </div>
          </div>
          <button
            onClick={() => retryVerify()}
            className="flex min-h-[44px] items-center gap-1.5 rounded-md bg-amber-500/20 px-3 py-1.5 text-xs font-semibold text-amber-900 hover:bg-amber-500/30 transition-colors cursor-pointer"
          >
            <RefreshCw size={13} /> Thử lại
          </button>
        </div>
      )}

      <div className="rounded-xl border border-surface-border bg-surface p-6 sm:p-8">
        <div className="flex flex-col gap-6 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-4">
            {!isGuestMode && isKidMode && activeProfile ? (
              <ChildAvatarBadge avatarKey={activeProfile.avatarKey} size="md" />
            ) : (
              <div className="grid h-12 w-12 place-items-center rounded-md bg-brand text-base font-bold text-zinc-950 shadow-brand-sm">
                {isGuestMode ? <User size={22} /> : getUserInitials(user?.fullName || user?.username, 'K')}
              </div>
            )}
            <div>
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-brand-dark">
                  {isGuestMode ? 'Chế độ khách' : isKidMode ? 'Hồ sơ trẻ em' : 'Tài khoản Dippie'}
                </span>
                <span className="text-muted">•</span>
                <span className="text-xs text-muted">
                  {isGuestMode
                    ? 'Lưu trên máy này'
                    : isKidMode
                    ? 'Thư viện riêng của bé'
                    : user?.email || 'Đã đồng bộ theo tài khoản'}
                </span>
              </div>
              <h1 className="text-xl sm:text-2xl font-bold text-ink mt-0.5">
                {isGuestMode ? 'Khách' : isKidMode && activeProfile ? activeProfile.name : user?.fullName || user?.username}
              </h1>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            {isGuestMode ? (
              <button
                type="button"
                onClick={() => openAuthModal('register')}
                className="btn-primary min-h-[44px] text-xs w-full sm:w-auto"
              >
                Tạo tài khoản
              </button>
            ) : (
              <>
                {!isKidMode && (
                  <Link
                    to="/account"
                    data-testid="profile-account-link"
                    className="btn-secondary min-h-[44px] text-xs w-full sm:w-auto"
                  >
                    Cài đặt tài khoản
                  </Link>
                )}
                <Link to="/lich-su" className="btn-secondary min-h-[44px] text-xs w-full sm:w-auto">
                  Lịch sử xem
                </Link>
                <button
                  type="button"
                  onClick={openChat}
                  className="btn-secondary min-h-[44px] text-xs w-full sm:w-auto"
                >
                  Hỏi CineBot
                </button>
              </>
            )}
          </div>
        </div>

        <div className="mt-8 grid grid-cols-2 gap-3 sm:grid-cols-4 sm:gap-4 border-t border-surface-border pt-6">
          {[
            { label: 'Xem sau', value: watchlist.length, icon: <Bookmark size={14} className="text-brand-dark" /> },
            { label: 'Yêu thích', value: favorites.length, icon: <Heart size={14} className="text-brand-dark" /> },
            { label: 'Chấm điểm', value: ratingsCount, icon: <Star size={14} className="text-brand-dark" /> },
          ].map((stat) => (
            <div key={stat.label} className="rounded-lg border border-surface-border bg-surface-muted p-3 sm:p-4">
              <div className="flex items-center gap-2 text-muted text-xs">
                {stat.icon}
                <span>{stat.label}</span>
              </div>
              <p className="mt-1 text-lg sm:text-2xl font-bold text-ink">{stat.value}</p>
            </div>
          ))}

          <div className="rounded-lg border border-surface-border bg-surface-muted p-3 sm:p-4">
            <div className="flex items-center gap-2 text-muted text-xs">
              <Film size={14} className="text-brand-dark" />
              <span>Thể loại hay xem</span>
            </div>
            <p className="mt-1 text-xs sm:text-sm font-semibold text-ink truncate">
              {topGenres.length > 0 ? topGenres.map((g) => genreLabel(g[0])).join(', ') : 'Chưa có'}
            </p>
          </div>
        </div>
      </div>

      <section className="mt-8">
        <div
          role="tablist"
          aria-label="Thư viện"
          className="flex overflow-x-auto whitespace-nowrap border-b border-surface-border"
        >
          {tabMeta.map((tab) => (
            <button
              key={tab.id}
              type="button"
              id={`profile-tab-${tab.id}`}
              role="tab"
              aria-selected={activeTab === tab.id}
              aria-controls={`profile-panel-${tab.id}`}
              tabIndex={activeTab === tab.id ? 0 : -1}
              onClick={() => setActiveTab(tab.id)}
              onKeyDown={(event) => handleTabKeyDown(event, tab.id)}
              className={`flex shrink-0 min-h-[44px] items-center gap-2 border-b-2 px-4 py-3 text-xs sm:text-sm font-bold transition-colors cursor-pointer ${
                activeTab === tab.id
                  ? 'border-brand text-brand-dark'
                  : 'border-transparent text-muted hover:text-ink'
              }`}
            >
              {tab.icon} {tab.label} ({tab.count})
            </button>
          ))}
        </div>

        {isGuestMode && (
          <div className="my-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-surface-border bg-surface-muted px-4 py-3 text-xs text-muted">
            <span>Thư viện đang lưu tạm trên máy này.</span>
            <button
              type="button"
              onClick={() => openAuthModal('login')}
              className="inline-flex min-h-[44px] items-center px-2 font-bold text-brand-dark hover:underline cursor-pointer shrink-0"
            >
              Đăng nhập
            </button>
          </div>
        )}

        {libraryError && (
          <div
            data-testid="library-error-banner"
            className="my-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-red-500/30 bg-red-500/10 px-4 py-3 text-xs text-red-900"
          >
            <div className="flex items-center gap-2">
              <AlertCircle size={16} className="text-red-900 shrink-0" />
              <span>{libraryError}</span>
            </div>
            <button
              type="button"
              onClick={() => {
                pruneProfileMetadataCache();
                void refetchLibrary();
              }}
              className="flex min-h-[44px] items-center gap-1 rounded bg-red-500/20 px-3 py-1 font-semibold text-red-900 hover:bg-red-500/30 transition-colors cursor-pointer shrink-0"
              aria-label="Thử tải lại thư viện"
            >
              <RefreshCw size={12} /> Thử tải lại
            </button>
          </div>
        )}

        <div
          role="tabpanel"
          id={`profile-panel-${activeTab}`}
          aria-labelledby={`profile-tab-${activeTab}`}
          className="mt-6"
        >
          {isInitialLoading ? (
            <div role="status" className="space-y-3">
              <p className="text-xs text-muted">Đang tải thư viện của bạn…</p>
              <div className={GRID_CLASS}>
                {Array.from({ length: 6 }).map((_, index) => (
                  <div key={index} className="overflow-hidden rounded-lg border border-surface-border/40 bg-paper-white animate-pulse">
                    <div className="aspect-[2/3] w-full bg-surface-elevated" />
                    <div className="space-y-2 p-3">
                      <div className="h-3.5 w-3/4 rounded bg-surface-elevated" />
                      <div className="h-3 w-1/2 rounded bg-surface-elevated" />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : activeItems.length > 0 ? (
            <div className={GRID_CLASS}>
              {activeItems.map((item) => {
                const action = activeTabConfig.action;
                const pending = isActionPending(item.id, action);
                const unconfirmed = isActionUnconfirmed(item.id, action);
                const intent = unconfirmed ? getActionIntent(item.id, action) : null;

                if (item.status === 'loading') {
                  return (
                    <div
                      key={item.id}
                      className="flex h-56 flex-col items-center justify-center rounded-lg border border-ink/30 bg-surface-muted p-4 text-xs"
                    >
                      <Loader2 size={18} className="animate-spin text-brand-dark mb-2" />
                      <span className="text-muted text-[11px]">Đang tải…</span>
                    </div>
                  );
                }

                if (item.status !== 'found') {
                  return (
                    <MissingItemCard
                      key={item.id}
                      item={item}
                      tab={activeTab}
                      pending={pending}
                      unconfirmed={unconfirmed}
                      intent={intent}
                      onRetryFetch={() => void fetchMovie(item.id)}
                      onRemove={() => void handleRemoveItem(item.id, activeTab)}
                    />
                  );
                }

                if (activeTab !== 'ratings') {
                  return (
                    <MovieCard
                      key={item.id}
                      movie={item.movie}
                      fluid
                      actionMode={activeTab === 'favorites' ? 'favorite' : 'watchlist'}
                    />
                  );
                }

                const isRateIntent = intent === 'rate';
                return (
                  <div key={item.id} className="relative group">
                    <MovieCard movie={item.movie} fluid />
                    <div className="mt-1 flex items-center justify-between px-1 text-xs">
                      <span className="text-muted text-[11px]">Điểm của bạn:</span>
                      <div className="flex items-center gap-2">
                        <span className="flex items-center gap-0.5 font-bold text-amber-900">
                          <Star size={11} className="fill-amber-400" /> {item.rating}/10
                        </span>
                        {unconfirmed && (
                          <span
                            data-testid="rating-unconfirmed-badge"
                            className="rounded bg-amber-500/20 px-1 py-0.5 text-[9px] font-bold text-amber-900 border border-amber-400/30"
                            title={isRateIntent ? 'Chưa lưu được điểm' : 'Chưa xóa được điểm'}
                          >
                            {isRateIntent ? 'Chưa lưu' : 'Chưa xóa'}
                          </span>
                        )}
                        <button
                          type="button"
                          disabled={pending}
                          onClick={() => void handleRemoveItem(item.id, 'ratings', item.movie.title)}
                          className={`inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-md transition-colors cursor-pointer disabled:opacity-50 ${
                            unconfirmed ? 'text-amber-900 hover:text-amber-900' : 'text-muted hover:text-red-900'
                          }`}
                          title={
                            unconfirmed
                              ? isRateIntent
                                ? 'Chưa lưu được điểm, nhấn để thử lại'
                                : 'Chưa xóa được điểm, nhấn để thử lại'
                              : 'Xóa điểm'
                          }
                          aria-label={
                            pending
                              ? 'Đang xử lý…'
                              : unconfirmed
                              ? isRateIntent
                                ? 'Chưa lưu được điểm, nhấn để thử lại'
                                : 'Chưa xóa được điểm, nhấn để thử lại'
                              : 'Xóa điểm'
                          }
                        >
                          {pending ? (
                            <Loader2 size={14} className="animate-spin" />
                          ) : unconfirmed ? (
                            <RefreshCw size={14} className="stroke-[2.5]" />
                          ) : (
                            <Trash2 size={14} />
                          )}
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          ) : libraryError ? (
            <div className="flex flex-col items-center justify-center rounded-xl border border-red-500/20 bg-red-500/5 py-12 px-4 text-center">
              <AlertCircle size={28} className="text-red-900 mb-2" />
              <p className="text-sm font-bold text-red-900">{libraryError}</p>
              <p className="text-xs text-muted mt-1 mb-4">
                Chưa tải được thư viện từ máy chủ. Bạn kiểm tra lại mạng rồi thử lại nhé.
              </p>
              <button onClick={() => void refetchLibrary()} className="btn-secondary text-xs flex items-center gap-1.5">
                <RefreshCw size={13} /> Thử lại
              </button>
            </div>
          ) : (
            <EmptyState
              title={activeTabConfig.emptyTitle}
              description={activeTabConfig.emptyDescription}
              openChat={openChat}
            />
          )}
        </div>
      </section>
    </main>
  );
}

function EmptyState({
  title,
  description,
  openChat,
}: {
  title: string;
  description: string;
  openChat: () => void;
}) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-surface-border bg-surface px-6 py-14 text-center">
      <Film className="text-muted mb-2.5" size={28} />
      <h3 className="text-base font-bold text-ink">{title}</h3>
      <p className="mt-1 max-w-md text-xs text-muted">{description}</p>
      <div className="mt-4 flex flex-wrap justify-center gap-2">
        <Link to="/" className="btn-primary text-xs">
          Chọn phim <ArrowRight size={13} />
        </Link>
        <button type="button" onClick={openChat} className="btn-secondary text-xs">
          Hỏi CineBot
        </button>
      </div>
    </div>
  );
}
