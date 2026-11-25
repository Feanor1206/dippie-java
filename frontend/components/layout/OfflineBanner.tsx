import React, { useEffect, useState } from 'react';

export function OfflineBanner() {
  const [online, setOnline] = useState(() => navigator.onLine);
  useEffect(() => {
    const update = () => setOnline(navigator.onLine);
    window.addEventListener('online', update);
    window.addEventListener('offline', update);
    update();
    return () => {
      window.removeEventListener('online', update);
      window.removeEventListener('offline', update);
    };
  }, []);

  return (
    <div
      role="status"
      className={
        online
          ? 'sr-only'
          : 'border-b-2 border-ink bg-[#f2d18c] px-4 py-2.5 text-center text-xs font-semibold text-ink sm:text-sm'
      }
    >
      {!online && (
        <span className="inline-flex items-center justify-center gap-2">
          <svg
            width={16}
            height={16}
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            className="shrink-0"
            aria-hidden="true"
          >
            <line x1="2" x2="22" y1="2" y2="22" />
            <path d="M8.5 16.5a5 5 0 0 1 7 0" />
            <path d="M2 8.82a15 15 0 0 1 4.17-2.65" />
            <path d="M10.66 5c4.01-.36 8.14.9 11.34 3.76" />
            <path d="M16.85 11.25a10 10 0 0 1 2.22 1.68" />
            <path d="M5 12.859a10 10 0 0 1 5.17-2.69" />
            <line x1="12" x2="12.01" y1="20" y2="20" />
          </svg>
          <span>Đang mất mạng. Một số tính năng sẽ hoạt động lại khi có kết nối.</span>
        </span>
      )}
    </div>
  );
}
