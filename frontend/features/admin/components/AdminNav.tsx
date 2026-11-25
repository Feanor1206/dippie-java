import React from 'react';
import { Link, useLocation } from 'react-router';
import { Database, Users, BarChart3, ClipboardList, Flag } from 'lucide-react';

const ADMIN_NAV_ITEMS = [
  { to: '/admin/import', label: 'Nhập dữ liệu', Icon: Database },
  { to: '/admin/users', label: 'Người dùng', Icon: Users },
  { to: '/admin/stats', label: 'Thống kê', Icon: BarChart3 },
  { to: '/admin/audit', label: 'Nhật ký', Icon: ClipboardList },
  { to: '/admin/reports', label: 'Báo lỗi video', Icon: Flag },
] as const;

export function AdminNav() {
  const { pathname } = useLocation();

  return (
    <nav
      aria-label="Điều hướng quản trị"
      className="mb-6 flex flex-wrap items-center gap-2 rounded-xl border border-surface-border bg-surface p-1.5 shadow-sm"
    >
      {ADMIN_NAV_ITEMS.map(({ to, label, Icon }) => {
        const isActive = pathname === to || pathname.startsWith(`${to}/`);
        return (
          <Link
            key={to}
            to={to}
            aria-current={isActive ? 'page' : undefined}
            className={`inline-flex min-h-[44px] items-center gap-2 rounded-lg border px-3.5 py-2 text-xs transition-colors ${
              isActive
                ? 'border-brand/40 bg-brand/15 font-bold text-brand-dark'
                : 'border-transparent font-medium text-muted hover:bg-surface-hover hover:text-ink'
            }`}
          >
            <Icon size={14} aria-hidden="true" />
            <span>{label}</span>
          </Link>
        );
      })}
    </nav>
  );
}
