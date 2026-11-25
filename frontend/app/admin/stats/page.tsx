import React, { useEffect, useState } from 'react';
import {
  BarChart3,
  Users,
  UserCheck,
  UserPlus,
  Film,
  Tv,
  PlayCircle,
  CheckCircle2,
  Star,
  RefreshCw,
} from 'lucide-react';
import { useAuth } from '@/lib/context/AuthContext';
import { getAdminStats, AdminStatsResponse } from '@/lib/services/adminService';
import { formatCount, formatPercentage, formatRating } from '@/features/admin/adminFormat';
import { AdminNav } from '@/features/admin/components/AdminNav';

export default function AdminStatsPage() {
  const { status, user, token, openAuthModal, retryVerify } = useAuth();
  const isAdmin = status === 'authenticated' && user?.role === 'ROLE_ADMIN' && !!token;

  const [stats, setStats] = useState<AdminStatsResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [refreshNonce, setRefreshNonce] = useState(0);

  useEffect(() => {
    if (!isAdmin || !token) return;
    const controller = new AbortController();
    setIsLoading(true);
    setLoadError(null);

    getAdminStats(token, { signal: controller.signal })
      .then((data) => {
        if (controller.signal.aborted) return;
        setStats(data);
        setIsLoading(false);
      })
      .catch((err: any) => {
        if (controller.signal.aborted || err?.isCanceled) return;
        setLoadError(err?.message || 'Không thể tải dữ liệu thống kê.');
        setIsLoading(false);
      });

    return () => controller.abort();
  }, [isAdmin, token, refreshNonce]);

  if (status === 'checking') return <div role="status" className="p-8 text-sm">Đang kiểm tra tài khoản…</div>;
  if (status === 'unavailable') return <div role="alert" className="p-8 text-sm">Không thể kiểm tra tài khoản. <button className="underline" onClick={() => retryVerify()}>Thử lại</button></div>;
  if (status === 'guest') return <div className="p-8 text-sm">Vui lòng đăng nhập để xem thống kê hệ thống. <button data-testid="admin-sign-in" className="underline" onClick={() => openAuthModal('login')}>Đăng nhập</button></div>;
  if (!isAdmin) return <div data-testid="admin-forbidden" role="alert" className="p-8 text-sm">Tài khoản này không có quyền xem thống kê hệ thống.</div>;

  const maxViewers = stats && stats.topWatchedEpisodes.length > 0
    ? Math.max(...stats.topWatchedEpisodes.map((ep) => ep.viewers), 1)
    : 1;

  const completionPercentClamped = stats
    ? Math.max(0, Math.min(100, Math.round(stats.completionRate * 100)))
    : 0;

  return (
    <main className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      <AdminNav />

      <div className="mb-8 flex flex-wrap items-center justify-between gap-4 border-b border-surface-border pb-6">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-brand-dark">
            <BarChart3 size={14} aria-hidden="true" />
            <span>Quản trị hệ thống</span>
          </div>
          <h1 className="mt-1 text-2xl font-extrabold text-ink sm:text-3xl">
            Thống kê tổng quan
          </h1>
          <p className="mt-1 text-sm text-muted">
            Theo dõi số lượng người dùng, kho nội dung và mức độ tương tác trên toàn nền tảng.
          </p>
        </div>

        <button
          type="button"
          data-testid="admin-stats-refresh"
          onClick={() => setRefreshNonce((n) => n + 1)}
          disabled={isLoading}
          className="btn-secondary flex items-center gap-1.5 text-xs font-medium text-muted disabled:opacity-50"
        >
          <RefreshCw size={13} className={isLoading ? 'animate-spin' : ''} aria-hidden="true" />
          <span>Làm mới</span>
        </button>
      </div>

      <div aria-live="polite" className="sr-only">
        {isLoading ? 'Đang tải dữ liệu thống kê.' : loadError ? loadError : 'Đã tải dữ liệu thống kê.'}
      </div>

      {isLoading ? (
        <div role="status" className="flex items-center justify-center rounded-xl border border-surface-border bg-surface py-16 text-xs text-muted shadow-sm">
          <RefreshCw size={16} className="mr-2 animate-spin text-brand-dark" aria-hidden="true" />
          <span>Đang tải dữ liệu thống kê...</span>
        </div>
      ) : loadError ? (
        <div role="alert" data-testid="admin-stats-error" className="rounded-xl border border-red-500/30 bg-red-500/10 p-8 text-center">
          <p className="text-xs text-red-900">{loadError}</p>
          <button
            type="button"
            onClick={() => setRefreshNonce((n) => n + 1)}
            className="btn-secondary mt-3 text-xs font-semibold"
          >
            Thử lại
          </button>
        </div>
      ) : !stats ? (
        <div className="rounded-xl border border-surface-border bg-surface p-10 text-center text-xs text-muted">
          Chưa có dữ liệu thống kê.
        </div>
      ) : (
        <div className="space-y-8">
          <section aria-label="Chỉ số tổng quan" className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <KpiCard
              label="Tổng người dùng"
              value={formatCount(stats.totalUsers)}
              Icon={Users}
            />
            <KpiCard
              label="Người dùng hoạt động"
              value={formatCount(stats.activeUsers)}
              Icon={UserCheck}
            />
            <KpiCard
              label="Người dùng mới (7 ngày)"
              value={formatCount(stats.newUsersLast7Days)}
              Icon={UserPlus}
            />
            <KpiCard
              label="Tổng số phim"
              value={formatCount(stats.totalMovies)}
              Icon={Film}
            />
            <KpiCard
              label="Tập đã phát hành"
              value={formatCount(stats.publishedEpisodes)}
              Icon={Tv}
            />
            <KpiCard
              label="Phiên xem"
              value={formatCount(stats.totalWatchSessions)}
              subtext={`${formatCount(stats.completedWatchSessions)} phiên hoàn thành`}
              Icon={PlayCircle}
            />
            <div className="rounded-xl border border-surface-border bg-surface p-5 shadow-sm sm:col-span-2">
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs font-semibold text-muted">Tỷ lệ hoàn thành</span>
                <div className="grid h-8 w-8 place-items-center rounded-lg bg-brand/15 text-brand-dark">
                  <CheckCircle2 size={16} aria-hidden="true" />
                </div>
              </div>
              <p className="mt-2 text-2xl font-extrabold text-ink">
                {formatPercentage(stats.completionRate)}
              </p>
              <div
                role="progressbar"
                aria-label="Tỷ lệ hoàn thành phiên xem"
                aria-valuenow={completionPercentClamped}
                aria-valuemin={0}
                aria-valuemax={100}
                className="mt-3 h-2.5 w-full overflow-hidden rounded-full border border-surface-border/40 bg-surface-elevated"
              >
                <div
                  className="h-full rounded-full bg-brand transition-all duration-300"
                  style={{ width: `${completionPercentClamped}%` }}
                />
              </div>
              <p className="mt-2 text-[11px] text-muted">
                {formatCount(stats.completedWatchSessions)} / {formatCount(stats.totalWatchSessions)} phiên xem hoàn tất
              </p>
            </div>
          </section>

          <div className="grid grid-cols-1 gap-8 lg:grid-cols-2">
            <section className="rounded-xl border border-surface-border bg-surface p-6 shadow-sm">
              <div className="flex items-center justify-between border-b border-surface-border pb-3">
                <div className="flex items-center gap-2">
                  <PlayCircle size={18} className="text-brand-dark" aria-hidden="true" />
                  <h2 className="text-base font-bold text-ink">
                    Tập phim được xem nhiều nhất
                  </h2>
                </div>
                <span className="text-xs text-muted">Theo số người xem</span>
              </div>

              {stats.topWatchedEpisodes.length === 0 ? (
                <p className="py-10 text-center text-xs text-muted">
                  Chưa có dữ liệu lượt xem tập phim.
                </p>
              ) : (
                <ol className="mt-4 space-y-4">
                  {stats.topWatchedEpisodes.map((ep, idx) => {
                    const widthPct = ep.viewers > 0
                      ? Math.max(4, Math.round((ep.viewers / maxViewers) * 100))
                      : 0;
                    return (
                      <li key={ep.episodeId} className="space-y-1.5">
                        <div className="flex items-baseline justify-between gap-3 text-xs">
                          <div className="min-w-0">
                            <span className="mr-2 font-mono font-bold text-brand-dark">#{idx + 1}</span>
                            <span className="font-semibold text-ink">{ep.title}</span>
                            <span className="ml-2 font-mono text-[10px] text-muted">[{ep.episodeId}]</span>
                          </div>
                          <span className="shrink-0 font-mono text-xs font-bold text-ink">
                            {formatCount(ep.viewers)} người xem
                          </span>
                        </div>
                        <div
                          role="progressbar"
                          aria-label={`Số người xem của ${ep.title}`}
                          aria-valuenow={ep.viewers}
                          aria-valuemin={0}
                          aria-valuemax={maxViewers}
                          className="h-3 w-full overflow-hidden rounded-full border border-surface-border/40 bg-surface-elevated"
                        >
                          <div
                            className="h-full rounded-full bg-brand"
                            style={{ width: `${widthPct}%` }}
                          />
                        </div>
                      </li>
                    );
                  })}
                </ol>
              )}
            </section>

            <section className="rounded-xl border border-surface-border bg-surface p-6 shadow-sm">
              <div className="flex items-center justify-between border-b border-surface-border pb-3">
                <div className="flex items-center gap-2">
                  <Star size={18} className="text-brand-dark" aria-hidden="true" />
                  <h2 className="text-base font-bold text-ink">
                    Phim được đánh giá cao nhất
                  </h2>
                </div>
                <span className="text-xs text-muted">Điểm trung bình & lượt đánh giá</span>
              </div>

              {stats.topRatedMovies.length === 0 ? (
                <p className="py-10 text-center text-xs text-muted">
                  Chưa có dữ liệu đánh giá phim.
                </p>
              ) : (
                <div className="mt-4 overflow-x-auto">
                  <table className="w-full border-collapse text-left text-xs">
                    <thead>
                      <tr className="border-b border-surface-border bg-surface-elevated/70 text-[11px] font-bold uppercase tracking-wide text-muted">
                        <th scope="col" className="px-3 py-2.5">Hạng</th>
                        <th scope="col" className="px-3 py-2.5">Phim</th>
                        <th scope="col" className="px-3 py-2.5 text-right">Điểm TB</th>
                        <th scope="col" className="px-3 py-2.5 text-right">Lượt đánh giá</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-surface-border">
                      {stats.topRatedMovies.map((movie, idx) => (
                        <tr key={movie.movieId} className="transition-colors hover:bg-surface-hover/50">
                          <td className="px-3 py-3 font-mono font-bold text-brand-dark">#{idx + 1}</td>
                          <td className="px-3 py-3">
                            <div className="font-semibold text-ink">{movie.title}</div>
                            <div className="font-mono text-[10px] text-muted">{movie.movieId}</div>
                          </td>
                          <td className="px-3 py-3 text-right">
                            <span className="inline-flex items-center gap-1 rounded border border-amber-500/30 bg-amber-500/10 px-2 py-0.5 font-mono text-[11px] font-bold text-amber-900">
                              <Star size={11} aria-hidden="true" />
                              <span>{formatRating(movie.averageRating)}</span>
                            </span>
                          </td>
                          <td className="px-3 py-3 text-right font-mono text-muted">
                            {formatCount(movie.ratingCount)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          </div>
        </div>
      )}
    </main>
  );
}

function KpiCard({
  label,
  value,
  subtext,
  Icon,
}: {
  label: string;
  value: string;
  subtext?: string;
  Icon: React.ComponentType<{ size?: number; 'aria-hidden'?: boolean }>;
}) {
  return (
    <div className="rounded-xl border border-surface-border bg-surface p-5 shadow-sm">
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-semibold text-muted">{label}</span>
        <div className="grid h-8 w-8 place-items-center rounded-lg bg-brand/15 text-brand-dark">
          <Icon size={16} aria-hidden={true} />
        </div>
      </div>
      <p className="mt-2 text-2xl font-extrabold text-ink">{value}</p>
      {subtext && <p className="mt-1 text-[11px] text-muted">{subtext}</p>}
    </div>
  );
}
