import { lazy, Suspense, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router';
import { Footer } from '@/components/layout/Footer';
import { OfflineBanner } from '@/components/layout/OfflineBanner';
import { Navbar } from '@/components/layout/Navbar';
import { AuthProvider, useAuth } from '@/lib/context/AuthContext';
import { WatchlistProvider } from '@/lib/context/WatchlistContext';
import { resolveRouteTitle } from '@/lib/routeTitle';
import HomePage from './page';
import NotFound from './not-found';
import './globals.css';

const SearchPage = lazy(() => import('./search/page'));
const ProfilePage = lazy(() => import('./profile/page'));
const MoviePage = lazy(() => import('./movies/[id]/page'));
const SeriesPage = lazy(() => import('./series/[id]/page'));
const SeriesBrowsePage = lazy(() => import('./series/page'));
const ImportPage = lazy(() => import('./admin/import/page'));
const AdminUsersPage = lazy(() => import('./admin/users/page'));
const AdminStatsPage = lazy(() => import('./admin/stats/page'));
const AdminAuditPage = lazy(() => import('./admin/audit/page'));
const AccountPage = lazy(() => import('./account/page'));
const WatchHistoryPage = lazy(() => import('./lich-su/page'));
const AdminReportsPage = lazy(() => import('./admin/reports/page'));
const TermsPage = lazy(() => import('./legal/page').then(({ TermsPage }) => ({ default: TermsPage })));
const PrivacyPage = lazy(() => import('./legal/page').then(({ PrivacyPage }) => ({ default: PrivacyPage })));
const ResetPasswordPage = lazy(() => import('./dat-lai-mat-khau/page'));
const ForgotPasswordPage = lazy(() =>
  import('./dat-lai-mat-khau/page').then(({ ForgotPasswordPage }) => ({ default: ForgotPasswordPage }))
);

function App() {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo(0, 0);
    if (typeof document !== 'undefined') {
      document.title = resolveRouteTitle(pathname);
    }
  }, [pathname]);

  return (
    <>
      <a href="#main-content" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-3 focus:z-[80] focus:rounded-lg focus:bg-surface focus:px-4 focus:py-3 focus:font-bold focus:text-ink">
        Đến nội dung chính
      </a>
      <Navbar />
      <OfflineBanner />
      <Suspense fallback={<main id="main-content" tabIndex={-1} className="page-shell comic-loading"><span role="status">Đang mở trang…</span></main>}>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/movies" element={<Navigate to="/search" replace />} />
          <Route path="/movies/:id" element={<MoviePage />} />
          <Route path="/series" element={<SeriesBrowsePage />} />
          <Route path="/series/:id" element={<SeriesPage />} />
          <Route path="/admin/import" element={<ImportPage />} />
          <Route path="/admin/users" element={<AdminUsersPage />} />
          <Route path="/admin/stats" element={<AdminStatsPage />} />
          <Route path="/admin/audit" element={<AdminAuditPage />} />
          <Route path="/account" element={<AccountPage />} />
          <Route path="/lich-su" element={<WatchHistoryPage />} />
          <Route path="/admin/reports" element={<AdminReportsPage />} />
          <Route path="/dieu-khoan" element={<TermsPage />} />
          <Route path="/quyen-rieng-tu" element={<PrivacyPage />} />
          <Route path="/dat-lai-mat-khau" element={<ResetPasswordPage />} />
          <Route path="/quen-mat-khau" element={<ForgotPasswordPage />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Suspense>
      <Footer />
    </>
  );
}

function SessionApp() {
  const { sessionEpoch } = useAuth();
  return <WatchlistProvider key={sessionEpoch}><App /></WatchlistProvider>;
}

createRoot(document.getElementById('root')!).render(
  <BrowserRouter>
    <AuthProvider><SessionApp /></AuthProvider>
  </BrowserRouter>,
);
