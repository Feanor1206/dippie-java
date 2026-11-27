import { lazy, Suspense, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router';
import { Footer } from '@/components/layout/Footer';
import { OfflineBanner } from '@/components/layout/OfflineBanner';
import { Navbar } from '@/components/layout/Navbar';
import { PostSignupFamilyModal } from '@/features/account/components/PostSignupFamilyModal';
import { ProfilePickerModal } from '@/features/profiles/components/ProfilePickerModal';
import { AuthProvider, useAuth } from '@/lib/context/AuthContext';
import { ScreenTimeProvider } from '@/lib/context/ScreenTimeContext';
import { WatchlistProvider } from '@/lib/context/WatchlistContext';
import { initLargeTextPreference, initMotionPreference, isMotionReduced } from '@/lib/motion';
import { scrollToHashTarget } from '@/lib/hashScroll';
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
const FamilyPage = lazy(() => import('./gia-dinh/page'));
const WatchHistoryPage = lazy(() => import('./lich-su/page'));
const AdminReportsPage = lazy(() => import('./admin/reports/page'));
const TermsPage = lazy(() => import('./legal/page').then(({ TermsPage }) => ({ default: TermsPage })));
const PrivacyPage = lazy(() => import('./legal/page').then(({ PrivacyPage }) => ({ default: PrivacyPage })));
const ResetPasswordPage = lazy(() => import('./dat-lai-mat-khau/page'));
const ForgotPasswordPage = lazy(() =>
  import('./dat-lai-mat-khau/page').then(({ ForgotPasswordPage }) => ({ default: ForgotPasswordPage }))
);

function App() {
  const location = useLocation();
  const { pathname, hash } = location;
  const [renderedLocation, setRenderedLocation] = useState(location);
  const prevPathnameRef = useRef(location.pathname);

  useEffect(() => {
    const cleanupMotion = initMotionPreference();
    const cleanupLargeText = initLargeTextPreference();
    return () => {
      cleanupMotion();
      cleanupLargeText();
    };
  }, []);

  useLayoutEffect(() => {
    if (prevPathnameRef.current === location.pathname) {
      if (renderedLocation !== location) {
        setRenderedLocation(location);
      }
      return;
    }
    prevPathnameRef.current = location.pathname;
    const doc =
      typeof document !== 'undefined'
        ? (document as Document & {
            startViewTransition?: (cb: () => void) => { finished?: Promise<unknown> };
          })
        : null;
    if (!doc || typeof doc.startViewTransition !== 'function' || isMotionReduced()) {
      setRenderedLocation(location);
      return;
    }
    try {
      const transition = doc.startViewTransition(() => {
        flushSync(() => {
          setRenderedLocation(location);
        });
      });
      transition?.finished?.catch(() => {});
    } catch {
      setRenderedLocation(location);
    }
  }, [location, renderedLocation]);

  useEffect(() => {
    if (typeof document !== 'undefined') {
      document.title = resolveRouteTitle(pathname);
    }
    if (hash && typeof document !== 'undefined') {
      return scrollToHashTarget(hash.replace(/^#/, ''));
    }
    window.scrollTo(0, 0);
  }, [pathname, hash]);

  const effectiveLocation = location.pathname === renderedLocation.pathname ? location : renderedLocation;

  return (
    <>
      <a href="#main-content" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-3 focus:z-[80] focus:rounded-lg focus:bg-surface focus:px-4 focus:py-3 focus:font-bold focus:text-ink">
        Đến nội dung chính
      </a>
      <Navbar />
      <ProfilePickerModal />
      <PostSignupFamilyModal />
      <OfflineBanner />
      <Suspense fallback={<main id="main-content" tabIndex={-1} className="page-shell comic-loading"><span role="status">Đang mở trang…</span></main>}>
        <Routes location={effectiveLocation}>
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
          <Route path="/gia-dinh" element={<FamilyPage />} />
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
  return (
    <ScreenTimeProvider>
      <WatchlistProvider key={sessionEpoch}><App /></WatchlistProvider>
    </ScreenTimeProvider>
  );
}

createRoot(document.getElementById('root')!).render(
  <BrowserRouter>
    <AuthProvider><SessionApp /></AuthProvider>
  </BrowserRouter>,
);
