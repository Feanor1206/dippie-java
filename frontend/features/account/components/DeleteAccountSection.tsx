import React, { useEffect, useId, useRef, useState } from 'react';
import { SiteDialog } from '@/components/ui/SiteDialog';
import { useAuth } from '@/lib/context/AuthContext';
import { deleteAccount } from '@/lib/services/accountService';

export function DeleteAccountSection() {
  const { token, finishAccountDeletion } = useAuth();
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const request = useRef<AbortController | null>(null);
  const passwordId = useId();

  useEffect(() => () => request.current?.abort(), []);

  const close = () => {
    if (busy) return;
    setPassword('');
    setShowPassword(false);
    setError('');
    setOpen(false);
  };

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!token || !password || request.current) return;
    const controller = new AbortController();
    request.current = controller;
    setBusy(true);
    setError('');
    try {
      await deleteAccount(token, password, controller.signal);
      setPassword('');
      setShowPassword(false);
      setError('');
      setOpen(false);
      finishAccountDeletion(token);
    } catch (failure) {
      if (!controller.signal.aborted) {
        setPassword('');
        setError(failure instanceof Error ? failure.message : 'Chưa xóa được tài khoản. Bạn thử lại nhé.');
      }
    } finally {
      request.current = null;
      if (!controller.signal.aborted) setBusy(false);
    }
  };

  return (
    <section
      className="rounded-xl border-2 border-red-800/35 bg-red-50/60 p-6 sm:p-8"
      aria-labelledby="account-delete-title"
    >
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-2.5">
          <div className="inline-flex items-center gap-1.5 rounded border border-red-800/35 bg-paper-white px-2.5 py-0.5 text-xs font-bold text-red-900">
            <svg width={13} height={13} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden="true">
              <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3" />
              <line x1="12" x2="12" y1="9" y2="13" />
              <line x1="12" x2="12.01" y1="17" y2="17" />
            </svg>
            <span>Lưu ý quan trọng</span>
          </div>

          <h2 id="account-delete-title" className="text-lg font-bold text-red-950 sm:text-xl">
            Xóa tài khoản
          </h2>

          <p className="max-w-2xl text-sm leading-relaxed text-ink/85">
            Tài khoản và toàn bộ dữ liệu dưới đây sẽ bị xóa hẳn, không lấy lại được:
          </p>
          <ul className="list-disc space-y-1 pl-5 text-xs leading-relaxed text-muted">
            <li>Các phim trong Xem sau, Yêu thích và điểm đã chấm</li>
            <li>Lịch sử xem và mọi mốc xem dở trên các thiết bị</li>
            <li>Tài khoản sẽ đăng xuất khỏi Dippie ngay</li>
          </ul>
        </div>

        <button
          type="button"
          data-testid="open-delete-account-dialog"
          onClick={() => setOpen(true)}
          className="inline-flex min-h-[44px] w-full shrink-0 items-center justify-center gap-2 border-2 border-red-900 bg-paper-white px-5 py-2.5 text-xs font-bold text-red-900 shadow-[3px_3px_0_#7f1d1d] transition-transform duration-150 hover:-translate-y-0.5 hover:bg-red-100 cursor-pointer sm:w-auto"
        >
          <svg width={14} height={14} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden="true">
            <path d="M3 6h18" />
            <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6" />
            <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2" />
          </svg>
          <span>Xóa tài khoản của tôi</span>
        </button>
      </div>

      {open && (
        <SiteDialog title="Xác nhận xóa tài khoản" tone="danger" onClose={close} busy={busy}>
          <p className="mb-4 text-sm leading-relaxed text-muted">
            Nhập <strong>mật khẩu hiện tại</strong> để xác nhận bạn muốn xóa hẳn tài khoản Dippie và toàn bộ dữ liệu cá nhân.
          </p>
          <form onSubmit={submit} className="space-y-4">
            <div className="space-y-1.5">
              <label htmlFor={passwordId} className="block text-xs font-semibold text-ink">
                Mật khẩu hiện tại
              </label>
              <div className="flex items-center rounded-lg border border-surface-border bg-surface-muted pl-3 pr-1 focus-within:border-ink focus-within:bg-paper-white">
                <input
                  id={passwordId}
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  required
                  value={password}
                  disabled={busy}
                  placeholder="Nhập mật khẩu để xác nhận"
                  onChange={(event) => {
                    setPassword(event.target.value);
                    if (error) setError('');
                  }}
                  className="w-full bg-transparent py-2.5 text-base sm:text-sm text-ink placeholder:text-muted focus:outline-none focus-visible:shadow-none disabled:opacity-50"
                />
                <button
                  type="button"
                  disabled={busy}
                  aria-pressed={showPassword}
                  aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                  onClick={() => setShowPassword((prev) => !prev)}
                  className="inline-flex min-h-[44px] min-w-[44px] shrink-0 items-center justify-center rounded-md px-2 text-xs font-semibold text-muted hover:bg-surface-elevated hover:text-ink cursor-pointer"
                >
                  {showPassword ? 'Ẩn' : 'Hiện'}
                </button>
              </div>
            </div>

            {error && (
              <div role="alert" className="rounded-lg border border-red-800/35 bg-red-50 px-3.5 py-2.5 text-xs leading-relaxed text-red-900">
                {error}
              </div>
            )}

            <div className="flex flex-col-reverse gap-2 pt-1 sm:flex-row sm:justify-end">
              <button
                type="button"
                autoFocus
                className="btn-secondary w-full sm:w-auto text-xs"
                disabled={busy}
                onClick={close}
              >
                Giữ lại tài khoản
              </button>
              <button
                type="submit"
                className="inline-flex min-h-[44px] w-full sm:w-auto items-center justify-center gap-2 border-2 border-ink bg-red-800 px-5 py-2.5 text-xs font-bold text-paper-white shadow-brand-sm transition-transform duration-150 hover:-translate-y-0.5 hover:bg-red-900 disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer"
                disabled={busy || !password}
              >
                {busy ? 'Đang xóa…' : 'Xóa hẳn tài khoản'}
              </button>
            </div>
          </form>
        </SiteDialog>
      )}
    </section>
  );
}
