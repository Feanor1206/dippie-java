import React, { useMemo } from 'react';
import { Link } from 'react-router';
import { type CatalogGenreOption } from '@/lib/services/catalogService';
import { useCatalogGenres } from '@/lib/hooks/useCatalogData';
import * as motion from '@/lib/motion';

const FALLBACK_FOOTER_GENRES: CatalogGenreOption[] = [
  { id: 'family', label: 'Phim gia đình', count: 1 },
  { id: 'comedy', label: 'Hài hước', count: 1 },
];

const FOOTER_LINK_CLASS = 'inline-flex min-h-[44px] items-center hover:text-ink transition-colors';

function formatFooterGenreLabel(option: CatalogGenreOption): string {
  if (option.id.toLowerCase() === 'family' || option.label.trim() === 'Gia đình') {
    return 'Phim gia đình';
  }
  return option.label;
}

export function Footer() {
  const fetchedGenres = useCatalogGenres();
  const motionPref = typeof motion.useReducedMotion === 'function' ? motion.useReducedMotion() : { reduced: false, toggle: () => {} };
  const largeTextPref = typeof motion.useLargeText === 'function' ? motion.useLargeText() : { largeText: false, toggle: () => {} };
  const genreLinks = useMemo(() => {
    if (!fetchedGenres || fetchedGenres.length === 0) return FALLBACK_FOOTER_GENRES;
    const nonDefault = fetchedGenres.filter((g) => g.count > 0 && g.id.toLowerCase() !== 'animation');
    const chosen = (nonDefault.length > 0 ? nonDefault : fetchedGenres.filter((g) => g.count > 0)).slice(0, 2);
    return chosen.length > 0 ? chosen : FALLBACK_FOOTER_GENRES;
  }, [fetchedGenres]);

  return (
    <footer className="comic-footer mt-auto border-t border-surface-border bg-surface">
      <div className="page-shell flex flex-col gap-6 py-8 sm:flex-row sm:items-center sm:justify-between text-ink/85">
        <Link
          to="/"
          className="comic-wordmark inline-flex min-h-[44px] items-center text-2xl font-bold text-ink hover:text-brand-dark transition-colors"
        >
          Dippie
        </Link>
        <nav aria-label="Liên kết cuối trang" className="flex flex-wrap gap-x-6 gap-y-0 text-xs font-medium">
          <Link to="/series" className={FOOTER_LINK_CLASS}>
            Phim bộ
          </Link>
          <Link to="/search" className={FOOTER_LINK_CLASS}>
            Kho phim
          </Link>
          {genreLinks.map((g) => (
            <Link key={g.id} to={`/search?genre=${encodeURIComponent(g.id)}`} className={FOOTER_LINK_CLASS}>
              {formatFooterGenreLabel(g)}
            </Link>
          ))}
          <Link to="/profile" className={FOOTER_LINK_CLASS}>
            Thư viện
          </Link>
          <Link to="/gia-dinh" className={FOOTER_LINK_CLASS}>
            Góc gia đình
          </Link>
          <Link to="/dieu-khoan" className={FOOTER_LINK_CLASS}>Điều khoản sử dụng</Link>
          <Link to="/quyen-rieng-tu" className={FOOTER_LINK_CLASS}>Chính sách quyền riêng tư</Link>
          <button
            type="button"
            data-testid="motion-reduce-toggle"
            aria-pressed={motionPref.reduced}
            onClick={motionPref.toggle}
            className={`${FOOTER_LINK_CLASS} cursor-pointer`}
          >
            {motionPref.reduced ? 'Đã giảm chuyển động' : 'Giảm chuyển động'}
          </button>
          <button
            type="button"
            data-testid="footer-large-text-toggle"
            aria-pressed={largeTextPref.largeText}
            onClick={largeTextPref.toggle}
            className={`${FOOTER_LINK_CLASS} cursor-pointer`}
          >
            {largeTextPref.largeText ? 'Đã bật cỡ chữ lớn' : 'Cỡ chữ lớn'}
          </button>
        </nav>
        <span className="text-xs text-ink/85">© Dippie 2026</span>
      </div>
    </footer>
  );
}
