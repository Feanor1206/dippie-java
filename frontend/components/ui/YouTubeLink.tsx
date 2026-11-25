import React, { useState } from 'react';
import { safeHttpUrl } from '@/lib/safeUrl';
import { SiteDialog } from '@/components/ui/SiteDialog';

export const YOUTUBE_PARENT_WARNING =
  'Bạn sắp rời Dippie để xem trên YouTube. YouTube có thể hiện quảng cáo và video khác không dành cho trẻ em.';

export function YouTubeLink({
  url,
  children,
  className,
  testId,
}: {
  url?: string | null;
  children: React.ReactNode;
  className?: string;
  testId?: string;
}) {
  const [open, setOpen] = useState(false);
  const safe = safeHttpUrl(url);
  if (!safe) return null;
  const destination = new URL(safe);
  if (destination.protocol !== 'https:' || destination.hostname !== 'www.youtube.com' || destination.pathname !== '/watch') {
    return null;
  }
  return (
    <>
      <button type="button" className={className} data-testid={testId} onClick={() => setOpen(true)}>
        {children}
      </button>
      {open && (
        <SiteDialog title="Mở trên YouTube" tone="warning" onClose={() => setOpen(false)}>
          <div className="space-y-3">
            <div className="flex items-start gap-3 rounded-xl border border-amber-700/35 bg-[#f2d18c]/30 p-3.5 text-sm leading-relaxed text-ink">
              <svg
                width={20}
                height={20}
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth={2}
                className="mt-0.5 shrink-0 text-amber-900"
                aria-hidden="true"
              >
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10" />
                <line x1="12" x2="12" y1="8" y2="12" />
                <line x1="12" x2="12.01" y1="16" y2="16" />
              </svg>
              <p>{YOUTUBE_PARENT_WARNING}</p>
            </div>
            <p className="text-xs leading-relaxed text-muted">
              Video sẽ mở ở thẻ mới. Ba mẹ nhớ xem cùng bé khi rời khỏi Dippie.
            </p>
          </div>
          <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <button
              type="button"
              autoFocus
              className="btn-secondary w-full sm:w-auto text-xs"
              onClick={() => setOpen(false)}
            >
              Ở lại Dippie
            </button>
            <a
              href={safe}
              target="_blank"
              rel="noopener noreferrer"
              className="btn-primary w-full sm:w-auto text-xs"
              onClick={() => setOpen(false)}
            >
              <span>Mở YouTube</span>
              <svg
                width={14}
                height={14}
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth={2}
                aria-hidden="true"
              >
                <path d="M15 3h6v6" />
                <path d="M10 14 21 3" />
                <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
              </svg>
            </a>
          </div>
        </SiteDialog>
      )}
    </>
  );
}
