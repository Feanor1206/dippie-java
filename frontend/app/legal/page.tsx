import React from 'react';
import { Link } from 'react-router';
import { safeHttpUrl } from '@/lib/safeUrl';

const LAST_UPDATED_ISO = '2026-11-26';
const LAST_UPDATED_LABEL = '26/11/2026';
const INLINE_LINK =
  'font-semibold text-[#36563c] underline decoration-[#36563c]/60 underline-offset-4 hover:text-ink';
const PROJECT_CONTACT = 'https://github.com/Feanor1206/dippie-java/issues';
const CONTACT_URL = safeHttpUrl(import.meta.env.VITE_PRIVACY_CONTACT_URL);

type TocItem = {
  id: string;
  label: string;
};

const TERMS_TOC: readonly TocItem[] = [
  { id: 'dung-dippie-cung-gia-dinh', label: 'Dùng Dippie cùng gia đình' },
  { id: 'video-youtube-bao-loi', label: 'Video, YouTube và báo lỗi' },
  { id: 'quan-ly-va-xoa-tai-khoan', label: 'Quản lý và xóa tài khoản' },
  { id: 'lien-he-dippie', label: 'Liên hệ với Dippie' },
];

const PRIVACY_TOC: readonly TocItem[] = [
  { id: 'du-lieu-dippie-dung', label: 'Dippie dùng dữ liệu gì' },
  { id: 'thoi-gian-luu-giu', label: 'Dữ liệu được giữ bao lâu' },
  { id: 'du-lieu-cua-tre-em', label: 'Dữ liệu của trẻ em' },
  { id: 'xoa-lich-su-tai-khoan', label: 'Xóa lịch sử hoặc tài khoản' },
  { id: 'lien-he-dippie', label: 'Liên hệ với Dippie' },
];

function LegalLayout({
  active,
  title,
  intro,
  toc,
  children,
}: {
  active: 'terms' | 'privacy';
  title: string;
  intro: string;
  toc: readonly TocItem[];
  children: React.ReactNode;
}) {
  return (
    <main id="main-content" tabIndex={-1} className="page-shell max-w-5xl py-8 sm:py-12">
      <header className="rounded-xl border-2 border-ink bg-paper-white p-6 shadow-brand-sm sm:p-8">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <span className="comic-label">Dành cho ba mẹ và gia đình</span>
          <p className="text-xs font-medium text-muted">
            Cập nhật ngày{' '}
            <time dateTime={LAST_UPDATED_ISO} className="font-semibold text-ink">
              {LAST_UPDATED_LABEL}
            </time>
          </p>
        </div>

        <h1 className="mt-3 text-3xl font-bold text-ink sm:text-4xl">{title}</h1>
        <p className="mt-2 max-w-2xl text-[15px] leading-relaxed text-muted">{intro}</p>

        <nav aria-label="Chuyển giữa Điều khoản và Quyền riêng tư" className="mt-5 flex flex-wrap gap-2">
          <Link
            to="/dieu-khoan"
            aria-current={active === 'terms' ? 'page' : undefined}
            className={`series-filter-chip min-h-[44px] ${
              active === 'terms' ? 'series-filter-chip--active' : ''
            }`}
          >
            Điều khoản sử dụng
          </Link>
          <Link
            to="/quyen-rieng-tu"
            aria-current={active === 'privacy' ? 'page' : undefined}
            className={`series-filter-chip min-h-[44px] ${
              active === 'privacy' ? 'series-filter-chip--active' : ''
            }`}
          >
            Chính sách quyền riêng tư
          </Link>
        </nav>
      </header>

      <div className="mt-8 grid grid-cols-1 gap-6 lg:grid-cols-[240px_minmax(0,1fr)] lg:items-start">
        <aside className="rounded-xl border border-surface-border bg-paper-white p-4 lg:sticky lg:top-24">
          <p className="border-b border-surface-border/40 pb-2.5 text-xs font-bold text-brand-dark">
            Mục lục
          </p>
          <nav aria-label="Mục lục trang" className="mt-2">
            <ol className="space-y-0.5 text-sm">
              {toc.map((item, idx) => (
                <li key={item.id}>
                  <a
                    href={`#${item.id}`}
                    className="flex min-h-[44px] items-center gap-2 rounded-lg px-2.5 py-2 text-xs font-medium text-muted transition-colors hover:bg-surface-hover hover:text-ink"
                  >
                    <span className="font-mono text-[11px] font-bold text-brand-dark">
                      {idx + 1}.
                    </span>
                    <span>{item.label}</span>
                  </a>
                </li>
              ))}
            </ol>
          </nav>
        </aside>

        <article className="min-w-0 space-y-5 text-[15px] leading-7 text-ink/90">
          {children}
        </article>
      </div>
    </main>
  );
}

function LegalSection({
  id,
  heading,
  highlight = false,
  children,
}: {
  id: string;
  heading: string;
  highlight?: boolean;
  children: React.ReactNode;
}) {
  return (
    <section
      id={id}
      className={`scroll-mt-24 rounded-xl border p-6 sm:p-7 ${
        highlight
          ? 'border-2 border-ink bg-mint/35 shadow-brand-sm'
          : 'border-surface-border bg-paper-white'
      }`}
    >
      <h2 className="mb-3 text-xl font-bold text-ink">{heading}</h2>
      <div className="space-y-3">{children}</div>
    </section>
  );
}

function ContactSection() {
  return (
    <LegalSection id="lien-he-dippie" heading="Liên hệ với Dippie">
      <p>
        Nếu ba mẹ có câu hỏi về dữ liệu gia đình, hoặc muốn xem, sửa, xóa dữ liệu hay rút lại sự đồng ý, hãy nhắn cho Dippie qua{' '}
        <a
          className={INLINE_LINK}
          href={CONTACT_URL ?? PROJECT_CONTACT}
          target="_blank"
          rel="noopener noreferrer"
        >
          {CONTACT_URL ? 'kênh hỗ trợ Dippie' : 'trang hỗ trợ của dự án Dippie'}
        </a>
        .
      </p>
      {!CONTACT_URL && (
        <p className="rounded-lg border border-ink/25 bg-surface-muted px-4 py-3 text-xs leading-relaxed text-ink">
          Lưu ý: trang hỗ trợ của dự án hiển thị công khai. Ba mẹ chỉ cần để lại lời nhắn để nhóm Dippie mở kênh trao đổi riêng, đừng viết mật khẩu, email tài khoản hay thông tin cá nhân của bé ở đó.
        </p>
      )}
    </LegalSection>
  );
}

export function TermsPage() {
  return (
    <LegalLayout
      active="terms"
      title="Điều khoản sử dụng"
      intro="Những quy định cơ bản khi ba mẹ dùng Dippie để tìm và mở phim hoạt hình cho bé."
      toc={TERMS_TOC}
    >
      <LegalSection id="dung-dippie-cung-gia-dinh" heading="Dùng Dippie cùng gia đình">
        <p>
          Dippie giúp ba mẹ và người giám hộ tìm, lưu và mở các tập phim hoạt hình cho trẻ nhỏ. Người lớn nên trực tiếp tạo và quản lý tài khoản để chủ động chọn nội dung hợp với độ tuổi của bé.
        </p>
        <p className="text-muted">
          Ba mẹ giữ kín mật khẩu và nhớ bấm <strong>Đăng xuất</strong> khi dùng Dippie trên máy tính hoặc máy tính bảng dùng chung trong nhà.
        </p>
      </LegalSection>

      <LegalSection id="video-youtube-bao-loi" heading="Video, YouTube và báo lỗi">
        <p>
          Một số tập được phát qua khung nhúng YouTube hoặc mở trực tiếp trên YouTube. Trước khi rời Dippie sang YouTube, trang luôn hiện lời nhắc để ba mẹ biết và xem cùng bé, vì YouTube áp dụng điều khoản và chính sách riêng, đồng thời có thể hiện quảng cáo hoặc video gợi ý ngoài Dippie.
        </p>
        <p className="text-muted">
          Nếu gặp tập không phát được, sai tập, mất tiếng hoặc có cảnh chưa hợp với trẻ nhỏ, ba mẹ bấm nút <strong>Báo lỗi video</strong> ngay dưới khung phát để Dippie kiểm tra và xử lý.
        </p>
      </LegalSection>

      <LegalSection id="quan-ly-va-xoa-tai-khoan" heading="Quản lý và xóa tài khoản">
        <p>
          Ba mẹ có thể đổi tên, đổi email, đổi mật khẩu hoặc xóa vĩnh viễn tài khoản bất cứ lúc nào trong{' '}
          <Link className={INLINE_LINK} to="/account">
            Cài đặt tài khoản
          </Link>
          .
        </p>
        <p className="text-muted">
          Để biết Dippie lưu những dữ liệu nào và bảo vệ ra sao, ba mẹ xem thêm{' '}
          <Link className={INLINE_LINK} to="/quyen-rieng-tu">
            Chính sách quyền riêng tư
          </Link>
          .
        </p>
      </LegalSection>

      <ContactSection />
    </LegalLayout>
  );
}

export function PrivacyPage() {
  return (
    <LegalLayout
      active="privacy"
      title="Chính sách quyền riêng tư"
      intro="Dippie chỉ giữ những thông tin cần thiết để nhớ mục Xem sau, Yêu thích và mốc xem dở của gia đình bạn."
      toc={PRIVACY_TOC}
    >
      <LegalSection id="du-lieu-dippie-dung" heading="Dippie dùng dữ liệu gì">
        <p>
          Khi bạn tạo tài khoản, Dippie lưu tên đăng nhập, email, họ tên (nếu bạn điền) và thời điểm bạn đồng ý với chính sách. Mật khẩu được mã hóa một chiều, Dippie không bao giờ lưu mật khẩu gốc.
        </p>
        <p>
          Thư viện của bạn lưu mục Xem sau, Yêu thích, điểm đã chấm và mốc xem dở của từng tập để ba mẹ mở lại đúng đoạn bé đang xem. Khi chưa đăng nhập, trình duyệt chỉ lưu tạm những mục này trên máy đang dùng và luôn hỏi bạn trước khi chuyển vào tài khoản.
        </p>
        <p>
          Khi dùng CineBot, câu hỏi và vài tin nhắn gần đây trong phiên giúp CineBot gợi ý đúng tập phim. Khi bạn gửi báo lỗi video, Dippie chỉ nhận mã tập, nguồn phát, lý do và phần ghi chú bạn tự viết.
        </p>
        <p>
          Khi phát video nhúng hoặc mở liên kết sang YouTube, trình duyệt kết nối thẳng tới YouTube và tuân theo chính sách quyền riêng tư của họ.
        </p>
      </LegalSection>

      <LegalSection id="thoi-gian-luu-giu" heading="Dữ liệu được giữ bao lâu">
        <p>
          Thông tin tài khoản, thư viện đã lưu và lịch sử xem được giữ trong suốt thời gian gia đình dùng Dippie. Bạn có thể xóa từng tập hoặc xóa cả lịch sử xem bất cứ lúc nào.
        </p>
        <p className="text-muted">
          Khi bạn xác nhận xóa tài khoản, toàn bộ dữ liệu cá nhân gắn với tài khoản sẽ bị xóa hoặc ẩn danh theo quy định. Một số nhật ký bảo mật tối thiểu vẫn có thể được giữ lại trong thời hạn pháp luật yêu cầu. Cuộc trò chuyện với CineBot chỉ hiện trong phiên đang mở và sẽ làm mới khi bạn đổi tài khoản hoặc tải lại trang.
        </p>
      </LegalSection>

      <LegalSection id="du-lieu-cua-tre-em" heading="Dữ liệu của trẻ em" highlight>
        <p>
          Dippie làm cho gia đình, nên ba mẹ hoặc người giám hộ hãy trực tiếp quản lý tài khoản. Đừng nhập tên thật, địa chỉ nhà, tên trường hay thông tin riêng tư của bé vào khung trò chuyện CineBot hoặc ô ghi chú báo lỗi.
        </p>
        <p className="text-sm leading-relaxed text-ink/90">
          Trước đây, việc bảo vệ dữ liệu trẻ em áp dụng theo Nghị định 13/2023/NĐ-CP. Từ ngày 01/01/2026, việc này tuân theo{' '}
          <a
            className={INLINE_LINK}
            href="https://vanban.chinhphu.vn/?docid=214590&pageid=27160"
            target="_blank"
            rel="noopener noreferrer"
          >
            Luật Bảo vệ dữ liệu cá nhân
          </a>{' '}
          và{' '}
          <a
            className={INLINE_LINK}
            href="https://vanban.chinhphu.vn/?docid=216387&pageid=27160"
            target="_blank"
            rel="noopener noreferrer"
          >
            Nghị định 356/2025/NĐ-CP
          </a>
          . Khi pháp luật yêu cầu, việc xử lý hoặc công bố thông tin riêng tư của trẻ cần có sự đồng ý của cha mẹ hoặc người giám hộ, và của chính trẻ từ đủ 7 tuổi trở lên.
        </p>
      </LegalSection>

      <LegalSection id="xoa-lich-su-tai-khoan" heading="Xóa lịch sử hoặc tài khoản">
        <p>
          Bạn có thể vào{' '}
          <Link className={INLINE_LINK} to="/lich-su">
            Lịch sử xem
          </Link>{' '}
          để xóa từng tập hoặc xóa hết lịch sử xem của tài khoản.
        </p>
        <p className="text-muted">
          Để xóa hẳn tài khoản cùng toàn bộ dữ liệu cá nhân, bạn vào{' '}
          <Link className={INLINE_LINK} to="/account">
            Cài đặt tài khoản
          </Link>
          , chọn <strong>Xóa tài khoản của tôi</strong> và nhập mật khẩu hiện tại để xác nhận.
        </p>
      </LegalSection>

      <ContactSection />
    </LegalLayout>
  );
}
