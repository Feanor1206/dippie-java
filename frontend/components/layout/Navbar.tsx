import React, { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router';
import { getUserInitials } from '@/lib/labels';
import * as motion from '@/lib/motion';
import { useWatchlist } from '@/lib/context/WatchlistContext';
import { useAuth } from '@/lib/context/AuthContext';
import { useOptionalScreenTime } from '@/lib/context/ScreenTimeContext';
import { ChildAvatarBadge } from '@/features/profiles/components/ChildAvatarBadge';
import { ExitKidModeDialog } from '@/features/profiles/components/ExitKidModeDialog';
import { ScreenTimeChip } from '@/features/profiles/components/ScreenTimeChip';

export function Navbar() {
  const [open, setOpen] = useState(false);
  const [userDropdownOpen, setUserDropdownOpen] = useState(false);
  const [exitDialogOpen, setExitDialogOpen] = useState(false);
  const [preferSiblingSwitch, setPreferSiblingSwitch] = useState(false);
  const [fallbackReducedMotion, setFallbackReducedMotion] = useState(false);

  const menuTriggerRef = useRef<HTMLButtonElement>(null);
  const mobileTriggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const openAtEnd = useRef(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const { pathname } = useLocation();
  const { watchlist, openChat } = useWatchlist();
  const {
    user,
    isAuthenticated,
    openAuthModal,
    logout,
    isKidMode,
    activeProfile,
    childProfiles = [],
    parentModeEnabled,
    openProfilePicker,
  } = useAuth();
  const motionPref = typeof motion.useReducedMotion === 'function' ? motion.useReducedMotion() : null;
  const isReducedMotion = motionPref ? motionPref.reduced : fallbackReducedMotion;
  const handleToggleMotion = () => {
    if (motionPref) {
      motionPref.toggle();
    } else {
      setFallbackReducedMotion((prev) => !prev);
    }
  };

  const kidMode = Boolean(isKidMode);
  const screenTime = typeof useOptionalScreenTime === 'function' ? useOptionalScreenTime() : null;
  const showAdmin = user?.role === 'ROLE_ADMIN' && !kidMode;
  const siblingCount = Array.isArray(childProfiles)
    ? childProfiles.filter((item) => item.id !== activeProfile?.id).length
    : 0;

  const primaryNavLinks = [
    { name: 'Trang chủ', href: '/' },
    { name: 'Phim bộ', href: '/series' },
    { name: 'Kho phim', href: '/search' },
    { name: 'Thư viện', href: '/profile', count: watchlist.length },
    ...(!kidMode ? [{ name: 'Gia đình', href: '/gia-dinh' }] : []),
  ];
  const adminLinks = showAdmin
    ? [
        { name: 'Báo lỗi video', href: '/admin/reports' },
        { name: 'Nhập dữ liệu', href: '/admin/import' },
        { name: 'Người dùng', href: '/admin/users' },
        { name: 'Thống kê', href: '/admin/stats' },
        { name: 'Nhật ký', href: '/admin/audit' },
      ]
    : [];
  const desktopNavLinks = showAdmin
    ? [...primaryNavLinks, { name: 'Quản trị', href: '/admin/reports' }]
    : primaryNavLinks;

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setUserDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const menuItems = () => Array.from(menuRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? []);

  useEffect(() => {
    if (!userDropdownOpen) return;
    const items = menuItems();
    (openAtEnd.current ? items.at(-1) : items[0])?.focus();
  }, [userDropdownOpen]);

  const onUserMenuKey = (event: React.KeyboardEvent) => {
    const items = menuItems();
    if (event.key === 'Escape') {
      event.preventDefault();
      setUserDropdownOpen(false);
      menuTriggerRef.current?.focus();
      return;
    }
    if (items.length === 0 || !['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const current = items.indexOf(document.activeElement as HTMLElement);
    const next = event.key === 'Home' ? 0
      : event.key === 'End' ? items.length - 1
      : (current + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
    items[next]?.focus();
  };

  const isActiveLink = (href: string) => pathname === href
    || (href === '/series' && pathname.startsWith('/series/'))
    || (href === '/search' && pathname.startsWith('/movies/'))
    || (href === '/admin/reports' && pathname.startsWith('/admin'));

  const userDisplayName = kidMode && activeProfile ? activeProfile.name : (user?.fullName || user?.username);
  const userInitials = getUserInitials(userDisplayName, 'U');

  const openExitDialog = (siblingFirst = false) => {
    setPreferSiblingSwitch(siblingFirst);
    setExitDialogOpen(true);
  };

  return (
    <header className="comic-navbar sticky top-0 z-40 border-b border-surface-border/25 bg-background">
      <nav aria-label="Điều hướng chính" className="page-shell flex h-[68px] items-center justify-between gap-1.5 sm:gap-2">
        <div className="flex min-w-0 items-center gap-3 lg:gap-7">
          <Link
            to="/"
            className="flex shrink-0 items-center gap-2 text-xl font-bold tracking-tight text-ink outline-none focus-visible:ring-2 focus-visible:ring-brand rounded px-0.5 sm:px-1"
          >
            <span className="comic-wordmark">Dippie</span>
          </Link>

          <div className="hidden lg:flex items-center gap-5">
            {desktopNavLinks.map((link) => {
              const isActive = isActiveLink(link.href);
              return (
                <Link
                  key={link.name}
                  to={link.href}
                  data-testid={link.href === '/gia-dinh' ? 'nav-family-link' : undefined}
                  aria-current={isActive ? 'page' : undefined}
                  className={`inline-flex min-h-[44px] items-center whitespace-nowrap text-sm font-medium transition-colors ${
                    isActive ? 'text-brand-dark font-semibold' : 'text-muted hover:text-ink'
                  }`}
                >
                  {link.name}
                  {link.count !== undefined && link.count > 0 && (
                    <span className="ml-1 rounded-full bg-surface-elevated px-1.5 py-0.5 text-[0.6875rem] font-semibold text-brand-dark">
                      {link.count}
                    </span>
                  )}
                </Link>
              );
            })}
          </div>
        </div>

        <div className="hidden lg:flex shrink-0 items-center gap-2.5">
          {isAuthenticated && !kidMode && parentModeEnabled && (
            <button
              type="button"
              data-testid="header-pick-child-button"
              onClick={() => openProfilePicker?.()}
              className="min-h-[44px] inline-flex items-center gap-1.5 rounded-lg border-2 border-ink bg-mint px-3.5 text-sm font-bold text-ink shadow-[2px_2px_0_#203328] transition-colors hover:bg-brand cursor-pointer"
            >
              Chọn bé
            </button>
          )}

          <button
            type="button"
            title="Nhờ CineBot gợi ý phim theo chủ đề hoặc nhân vật bé thích"
            onClick={openChat}
            className="min-h-[44px] min-w-[44px] inline-flex items-center gap-1.5 rounded-lg border-2 border-ink bg-[#f2d18c] px-3.5 text-sm font-bold text-ink shadow-[2px_2px_0_#203328] transition-colors hover:bg-[#f7dfa8] cursor-pointer"
          >
            Hỏi CineBot
          </button>

          {isAuthenticated && user ? (
            <div className="relative" ref={dropdownRef} onBlur={(event) => {
              if (!event.currentTarget.contains(event.relatedTarget)) setUserDropdownOpen(false);
            }}>
              <button
                type="button"
                ref={menuTriggerRef}
                data-testid="nav-user-dropdown-button"
                aria-haspopup="menu"
                aria-controls={userDropdownOpen ? 'account-menu' : undefined}
                aria-expanded={userDropdownOpen}
                onClick={() => {
                  openAtEnd.current = false;
                  setUserDropdownOpen((prev) => !prev);
                }}
                onKeyDown={(event) => {
                  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                    event.preventDefault();
                    openAtEnd.current = event.key === 'ArrowUp';
                    if (userDropdownOpen) {
                      const items = menuItems();
                      (openAtEnd.current ? items.at(-1) : items[0])?.focus();
                    } else setUserDropdownOpen(true);
                  } else if (event.key === 'Escape') setUserDropdownOpen(false);
                }}
                className="flex min-h-[44px] items-center gap-2 rounded-lg border border-surface-border bg-surface px-2.5 py-1 text-xs text-ink hover:border-ink transition-colors cursor-pointer"
              >
                {kidMode && activeProfile ? (
                  <ChildAvatarBadge avatarKey={activeProfile.avatarKey} size="sm" />
                ) : (
                  <div className="grid h-6 w-6 place-items-center rounded bg-brand text-[0.6875rem] font-bold text-ink border border-ink/30">
                    {userInitials}
                  </div>
                )}
                <span className="font-medium max-w-[110px] truncate">{userDisplayName}</span>
              </button>

              {userDropdownOpen && (
                <div
                  id="account-menu"
                  ref={menuRef}
                  role="menu"
                  onKeyDown={onUserMenuKey}
                  aria-label="Danh mục tài khoản"
                  className="motion-menu-enter absolute right-0 mt-2 w-60 rounded-xl border-2 border-ink bg-paper-white p-2 shadow-brand z-50"
                >
                  <div className="px-2.5 py-2 border-b border-surface-border/40">
                    <p className="text-xs font-bold text-ink truncate">{userDisplayName}</p>
                    <p className="text-[0.6875rem] text-muted truncate">
                      {kidMode ? 'Hồ sơ trẻ em' : user.email}
                    </p>
                  </div>

                  <div className="mt-1.5 space-y-0.5">
                    <Link
                      to="/profile"
                      role="menuitem"
                      tabIndex={-1}
                      onClick={() => setUserDropdownOpen(false)}
                      className="flex min-h-[44px] items-center gap-2 rounded-md px-2.5 py-1.5 text-xs font-medium text-ink hover:bg-mint/60 transition-colors"
                    >
                      Thư viện
                    </Link>
                    {!kidMode && (
                      <Link
                        to="/gia-dinh"
                        role="menuitem"
                        tabIndex={-1}
                        data-testid="nav-family-menu-item"
                        onClick={() => setUserDropdownOpen(false)}
                        className="flex min-h-[44px] items-center gap-2 rounded-md px-2.5 py-1.5 text-xs font-medium text-ink hover:bg-mint/60 transition-colors"
                      >
                        Gia đình &amp; Hồ sơ bé
                      </Link>
                    )}
                    {!kidMode && (
                      <Link
                        to="/account"
                        role="menuitem"
                        tabIndex={-1}
                        onClick={() => setUserDropdownOpen(false)}
                        className="flex min-h-[44px] items-center gap-2 rounded-md px-2.5 py-1.5 text-xs font-medium text-ink hover:bg-mint/60 transition-colors"
                      >
                        Cài đặt tài khoản
                      </Link>
                    )}
                    <Link
                      to="/lich-su"
                      role="menuitem"
                      tabIndex={-1}
                      onClick={() => setUserDropdownOpen(false)}
                      className="flex min-h-[44px] items-center rounded-md px-2.5 text-xs font-medium text-ink hover:bg-mint/60 transition-colors"
                    >
                      Lịch sử xem
                    </Link>
                    {!kidMode && parentModeEnabled && (
                      <button
                        type="button"
                        role="menuitem"
                        tabIndex={-1}
                        data-testid="nav-switch-profile-button"
                        onClick={() => {
                          setUserDropdownOpen(false);
                          openProfilePicker?.();
                        }}
                        className="flex min-h-[44px] w-full items-center gap-2 rounded-md px-2.5 py-1.5 text-xs font-medium text-ink hover:bg-mint/60 transition-colors cursor-pointer"
                      >
                        Chọn bé
                      </button>
                    )}
                    <button
                      type="button"
                      role="menuitem"
                      tabIndex={-1}
                      data-testid="nav-motion-reduce-toggle"
                      onClick={() => handleToggleMotion()}
                      className="flex min-h-[44px] w-full items-center justify-between gap-2 rounded-md px-2.5 py-1.5 text-xs font-medium text-ink hover:bg-mint/60 transition-colors cursor-pointer"
                    >
                      <span>Giảm chuyển động</span>
                      <span className="rounded border border-ink/30 bg-surface-muted px-1.5 py-0.5 text-[0.625rem] font-bold">
                        {isReducedMotion ? 'Đang bật' : 'Đang tắt'}
                      </span>
                    </button>
                  </div>

                  <div className="mt-1.5 pt-1.5 border-t border-surface-border/40">
                    {kidMode ? (
                      <button
                        type="button"
                        role="menuitem"
                        tabIndex={-1}
                        data-testid="nav-exit-kid-mode-menu-item"
                        onClick={() => {
                          setUserDropdownOpen(false);
                          openExitDialog(false);
                        }}
                        className="flex min-h-[44px] w-full items-center gap-2 rounded-md px-2.5 py-1.5 text-xs font-semibold text-brand-dark hover:bg-mint/60 transition-colors cursor-pointer"
                      >
                        Thoát hồ sơ trẻ em
                      </button>
                    ) : (
                      <button
                        type="button"
                        role="menuitem"
                        tabIndex={-1}
                        onClick={() => {
                          setUserDropdownOpen(false);
                          logout();
                        }}
                        className="flex min-h-[44px] w-full items-center gap-2 rounded-md px-2.5 py-1.5 text-xs font-semibold text-red-800 hover:bg-red-500/10 transition-colors cursor-pointer"
                      >
                        Đăng xuất
                      </button>
                    )}
                  </div>
                </div>
              )}
            </div>
          ) : (
            <button
              type="button"
              data-testid="nav-login-button"
              onClick={() => openAuthModal('login')}
              className="min-h-[44px] rounded-lg border-2 border-ink bg-brand px-3.5 text-sm font-bold text-ink shadow-[2px_2px_0_#203328] hover:bg-brand-hover active:bg-brand-active cursor-pointer"
            >
              Đăng nhập
            </button>
          )}
        </div>

        <div className="flex shrink-0 items-center gap-1 sm:gap-2 lg:hidden">
          {isAuthenticated && !kidMode && parentModeEnabled && (
            <button
              type="button"
              data-testid="tablet-pick-child-button"
              onClick={() => openProfilePicker?.()}
              className="hidden md:inline-flex min-h-[44px] items-center rounded-lg border-2 border-ink bg-mint px-3 text-xs font-bold text-ink shadow-[2px_2px_0_#203328] hover:bg-brand cursor-pointer"
            >
              Chọn bé
            </button>
          )}

          <Link
            to="/search"
            data-testid="mobile-search-link"
            aria-label="Tìm phim"
            onClick={() => setOpen(false)}
            className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center gap-1 rounded-lg border border-surface-border bg-surface px-2 sm:px-3 text-xs font-semibold text-ink hover:bg-surface-elevated"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <circle cx="11" cy="11" r="7" />
              <path d="m20 20-3.5-3.5" />
            </svg>
            <span>Tìm</span>
          </Link>

          <button
            type="button"
            data-testid="mobile-cinebot-launcher"
            aria-label="Mở CineBot"
            onClick={openChat}
            className="min-h-[44px] min-w-[44px] rounded-lg border-2 border-ink bg-[#f2d18c] px-2 sm:px-3 text-xs font-bold text-ink shadow-[2px_2px_0_#203328] hover:bg-[#f7dfa8] cursor-pointer"
          >
            CineBot
          </button>

          {isAuthenticated ? (
            <button
              type="button"
              data-testid="mobile-avatar-button"
              aria-label={open ? 'Đóng tài khoản và danh mục' : `Mở tài khoản ${userDisplayName || ''}`.trim()}
              aria-expanded={open}
              aria-controls={open ? 'mobile-navigation' : undefined}
              onClick={() => setOpen((prev) => !prev)}
              className="grid h-11 w-11 min-h-[44px] min-w-[44px] place-items-center rounded-lg border border-ink/40 bg-brand text-xs font-bold text-ink shadow-sm cursor-pointer"
            >
              {kidMode && activeProfile ? (
                <ChildAvatarBadge avatarKey={activeProfile.avatarKey} size="sm" />
              ) : (
                userInitials
              )}
            </button>
          ) : (
            <button
              type="button"
              onClick={() => openAuthModal('login')}
              className="min-h-[44px] rounded-lg border border-ink bg-brand px-2 sm:px-3 py-1.5 text-xs font-bold text-ink hover:bg-brand-hover active:bg-brand-active cursor-pointer shadow-sm"
            >
              Đăng nhập
            </button>
          )}

          <button
            ref={mobileTriggerRef}
            type="button"
            aria-label={open ? 'Đóng danh mục' : 'Mở danh mục'}
            aria-expanded={open}
            aria-controls={open ? 'mobile-navigation' : undefined}
            onKeyDown={(event) => { if (event.key === 'Escape') setOpen(false); }}
            onClick={() => setOpen((prev) => !prev)}
            className="min-h-[44px] min-w-[44px] rounded-lg border border-surface-border bg-surface px-2 sm:px-3 text-xs font-semibold text-ink hover:bg-surface-elevated cursor-pointer"
          >
            {open ? 'Đóng' : 'Danh mục'}
          </button>
        </div>
      </nav>

      {open && (
        <div id="mobile-navigation" onKeyDown={(event) => {
          if (event.key === 'Escape') {
            event.preventDefault();
            setOpen(false);
            mobileTriggerRef.current?.focus();
          }
        }} className="motion-accordion-open border-b-2 border-ink bg-paper-white px-5 py-5 lg:hidden">
          <div className="flex flex-col gap-2">
            {isAuthenticated && user && (
              <div className="mb-2 flex items-center justify-between gap-3 rounded-xl border border-surface-border bg-surface p-3">
                <div className="flex min-w-0 items-center gap-2.5">
                  {kidMode && activeProfile ? (
                    <ChildAvatarBadge avatarKey={activeProfile.avatarKey} size="sm" />
                  ) : (
                    <div className="grid h-8 w-8 shrink-0 place-items-center rounded-md border border-ink/30 bg-brand text-xs font-bold text-ink">
                      {userInitials}
                    </div>
                  )}
                  <div className="min-w-0">
                    <p className="truncate text-sm font-bold text-ink">{userDisplayName}</p>
                    <p className="truncate text-xs text-muted">
                      {kidMode ? 'Đang mở hồ sơ trẻ em' : user.email}
                    </p>
                  </div>
                </div>
                {!kidMode && parentModeEnabled && (
                  <button
                    type="button"
                    data-testid="mobile-switch-profile"
                    onClick={() => {
                      setOpen(false);
                      openProfilePicker?.();
                    }}
                    className="btn-primary min-h-[44px] shrink-0 px-3 text-xs font-bold"
                  >
                    Chọn bé
                  </button>
                )}
              </div>
            )}

            {primaryNavLinks.map((link) => {
              const isActive = isActiveLink(link.href);
              return (
                <Link
                  key={link.name}
                  to={link.href}
                  data-testid={link.href === '/gia-dinh' ? 'mobile-family-link' : undefined}
                  aria-current={isActive ? 'page' : undefined}
                  onClick={() => setOpen(false)}
                  className={`flex min-h-[44px] items-center justify-between rounded-lg px-3.5 py-2.5 text-sm font-medium ${
                    isActive
                      ? 'bg-mint/60 text-ink font-bold border border-ink/40'
                      : 'text-muted hover:bg-surface-hover hover:text-ink'
                  }`}
                >
                  <span>{link.name}</span>
                  {link.count !== undefined && link.count > 0 && (
                    <span className="text-xs text-brand-dark font-semibold">({link.count})</span>
                  )}
                </Link>
              );
            })}

            {adminLinks.length > 0 && (
              <div className="mt-2 border-t border-surface-border/40 pt-3">
                <p className="px-3.5 pb-1 text-xs font-bold text-muted">Quản trị</p>
                <div className="flex flex-col gap-1">
                  {adminLinks.map((link) => {
                    const isActive = pathname === link.href || pathname.startsWith(`${link.href}/`);
                    return (
                      <Link
                        key={link.href}
                        to={link.href}
                        aria-current={isActive ? 'page' : undefined}
                        onClick={() => setOpen(false)}
                        className={`flex min-h-[44px] items-center rounded-lg px-3.5 py-2 text-sm font-medium ${
                          isActive
                            ? 'bg-mint/60 text-ink font-bold border border-ink/40'
                            : 'text-muted hover:bg-surface-hover hover:text-ink'
                        }`}
                      >
                        {link.name}
                      </Link>
                    );
                  })}
                </div>
              </div>
            )}

            <div className="pt-3 mt-2 border-t border-surface-border/40 flex flex-col gap-2">
              <button
                type="button"
                onClick={() => {
                  setOpen(false);
                  openChat();
                }}
                className="btn-primary min-h-[44px] w-full justify-center text-sm"
              >
                Hỏi CineBot
              </button>

              <button
                type="button"
                data-testid="mobile-motion-reduce-toggle"
                onClick={() => handleToggleMotion()}
                className="btn-secondary min-h-[44px] w-full justify-between px-4 text-xs"
              >
                <span>Giảm chuyển động</span>
                <span className="font-bold">{isReducedMotion ? 'Đang bật' : 'Đang tắt'}</span>
              </button>

              {isAuthenticated && (
                <>
                  {!kidMode && (
                    <Link
                      to="/account"
                      data-testid="mobile-account-link"
                      onClick={() => setOpen(false)}
                      className="btn-secondary min-h-[44px] w-full justify-center text-xs"
                    >
                      Cài đặt tài khoản
                    </Link>
                  )}
                  <Link
                    to="/lich-su"
                    onClick={() => setOpen(false)}
                    className="btn-secondary min-h-[44px] w-full justify-center text-xs"
                  >
                    Lịch sử xem
                  </Link>
                  {kidMode ? (
                    <button
                      type="button"
                      data-testid="mobile-exit-kid-mode"
                      onClick={() => {
                        setOpen(false);
                        openExitDialog(false);
                      }}
                      className="btn-secondary min-h-[44px] w-full justify-center text-xs font-bold text-brand-dark"
                    >
                      Thoát hồ sơ trẻ em
                    </button>
                  ) : (
                    <button
                      type="button"
                      data-testid="mobile-logout"
                      onClick={() => {
                        setOpen(false);
                        logout();
                      }}
                      className="btn-secondary min-h-[44px] w-full justify-center text-xs text-red-800 hover:text-red-800"
                    >
                      Đăng xuất
                    </button>
                  )}
                </>
              )}
            </div>
          </div>
        </div>
      )}

      {isAuthenticated && kidMode && (
        <div data-testid="kid-mode-banner" className="border-t border-ink/20 bg-[#f2d18c]/45">
          <div className="page-shell flex flex-wrap items-center justify-between gap-2 py-2">
            <div className="flex min-w-0 items-center gap-2.5">
              <ChildAvatarBadge avatarKey={activeProfile?.avatarKey} size="sm" />
              <div className="flex min-w-0 flex-wrap items-center gap-2">
                <span className="inline-flex items-center rounded-full border border-ink/30 bg-paper-white px-2 py-0.5 text-[0.625rem] font-bold text-brand-dark">
                  Hồ sơ trẻ em
                </span>
                <span className="truncate text-xs sm:text-sm font-bold text-ink">
                  {activeProfile?.name || 'Chế độ trẻ em'}
                </span>
                {screenTime?.isLimited && (
                  <ScreenTimeChip
                    data-testid="profile-bar-screen-time-chip"
                    remainingSeconds={screenTime.remainingSeconds}
                    locked={screenTime.isLocked}
                    variant="bar"
                  />
                )}
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Link
                to="/#goc-cua-be"
                data-testid="kid-mode-dashboard-link"
                className="btn-secondary min-h-[44px] px-3 py-1.5 text-xs font-bold"
              >
                Nhiệm vụ &amp; Vé phim
              </Link>
              {siblingCount > 0 && (
                <button
                  type="button"
                  data-testid="kid-switch-sibling-button"
                  onClick={() => openExitDialog(true)}
                  className="btn-secondary min-h-[44px] px-3 py-1.5 text-xs font-bold"
                >
                  Đổi bé
                </button>
              )}
              <button
                type="button"
                data-testid="exit-kid-mode-button"
                onClick={() => openExitDialog(false)}
                className="btn-secondary min-h-[44px] px-3 py-1.5 text-xs font-bold"
              >
                Thoát hồ sơ trẻ em
              </button>
            </div>
          </div>
        </div>
      )}

      {exitDialogOpen && (
        <ExitKidModeDialog
          open={exitDialogOpen}
          preferSiblingSwitch={preferSiblingSwitch}
          onClose={() => {
            setExitDialogOpen(false);
            setPreferSiblingSwitch(false);
          }}
        />
      )}
    </header>
  );
}
