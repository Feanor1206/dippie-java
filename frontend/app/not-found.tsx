import { Link } from 'react-router';

export default function NotFound() {
  return (
    <main id="main-content" tabIndex={-1} className="page-shell grid min-h-[65vh] place-items-center py-12 text-center">
      <div className="w-full max-w-md rounded-2xl border-2 border-ink bg-paper-white p-8 shadow-brand">
        <span className="inline-flex items-center rounded-md border border-ink/25 bg-mint px-2.5 py-0.5 text-xs font-bold text-ink">
          404
        </span>
        <h1 className="comic-heading mt-3 text-3xl font-bold text-ink">Không tìm thấy trang</h1>
        <p className="mt-2 text-sm text-muted">
          Trang này không tồn tại hoặc đã được chuyển đi. Bạn quay về trang chủ để chọn phim cho bé nhé.
        </p>
        <div className="mt-6 flex flex-wrap items-center justify-center gap-3">
          <Link to="/" className="btn-primary text-xs">
            Về trang chủ
          </Link>
          <Link to="/series" className="btn-secondary text-xs">
            Xem phim bộ
          </Link>
        </div>
      </div>
    </main>
  );
}
