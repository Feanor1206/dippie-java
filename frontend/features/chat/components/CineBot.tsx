import { YouTubeLink } from '@/components/ui/YouTubeLink';
import { safeImageUrl } from '@/lib/safeUrl';
import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { sendChatMessage, type ChatMessage, type ChatServiceResponse } from '@/lib/services/chatService';
import { useWatchlist } from '@/lib/context/WatchlistContext';
import { useAuth } from '@/lib/context/AuthContext';
import { buildMediaItemHref } from '@/lib/services/mediaRouting';
import { isMediaExternalOnly, resolveExternalYoutubeWatchUrl } from '@/lib/services/playbackSource';
import { chatGenres, cleanMediaTitle, formatRuntime, formatYear, resolveRatingSummary } from '@/lib/labels';
import type { Movie } from '@/types/movie';

const QUICK_PROMPTS = [
  'Jadoo sợ tăng cân',
  'Larva mùa 1 tập 1',
  'Công chúa Ori mùa 1 tập 1',
];

const MAX_MESSAGE_LENGTH = 4000;
const FOCUS_DELAY_MS = 50;
const HEADER_BUTTON_CLASS =
  'flex min-h-[44px] min-w-[44px] items-center justify-center rounded-md px-3 text-xs text-muted hover:bg-surface-elevated hover:text-ink cursor-pointer';
const FOCUSABLE_SELECTOR =
  'button:not([disabled]), input:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])';
const TIME_FORMAT = new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit' });

function newConversationId() {
  return globalThis.crypto?.randomUUID?.() ?? `chat-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function createWelcomeMessage(id: string, text: string): ChatMessage {
  return { id, sender: 'bot', text, timestamp: 'Vừa xong', followUps: QUICK_PROMPTS.slice(0, 3) };
}

function ChatMovieCard({
  movie,
  inWatchlist,
  onToggleWatchlist,
  onClose,
}: {
  movie: Movie;
  inWatchlist: boolean;
  onToggleWatchlist: () => void;
  onClose: () => void;
}) {
  const mediaHref = buildMediaItemHref(movie);
  const hasPoster = Boolean(movie.poster && movie.poster.trim());
  const externalOnly =
    typeof isMediaExternalOnly === 'function'
      ? isMediaExternalOnly(movie)
      : Boolean(movie.externalOnly || movie.external_only || movie.canEmbed === false || movie.can_embed === false);
  const externalYoutubeUrl = externalOnly
    ? typeof resolveExternalYoutubeWatchUrl === 'function'
      ? resolveExternalYoutubeWatchUrl(movie)
      : movie.youtubeWatchUrl || movie.youtube_watch_url || null
    : null;
  const ratingSummary = resolveRatingSummary(movie);
  const metaText = [
    formatYear(movie.year),
    formatRuntime(movie.runtime),
    chatGenres(movie.genres || []),
    ratingSummary.hasRating ? `★ ${ratingSummary.compactLabel}` : ratingSummary.label,
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <div className="flex gap-3 rounded-lg border border-surface-border bg-surface-muted p-2.5 transition-colors hover:border-ink/30">
      {hasPoster && (
        <img
          src={safeImageUrl(movie.poster)}
          alt={movie.title}
          loading="lazy"
          referrerPolicy="no-referrer"
          className="h-20 w-14 shrink-0 rounded border border-surface-border object-cover"
        />
      )}
      <div className="flex flex-col justify-between flex-1 min-w-0">
        <div>
          <div className="flex items-center justify-between gap-1">
            <Link
              to={mediaHref}
              onClick={onClose}
              className="line-clamp-2 break-words text-sm font-semibold text-ink hover:text-brand-dark"
            >
              {cleanMediaTitle(movie.title)}
            </Link>
          </div>
          {externalOnly && (
            <span
              data-testid="cinebot-external-badge"
              className="mt-1 inline-flex w-fit items-center rounded border border-amber-500/40 bg-amber-500/10 px-2 py-0.5 text-[11px] font-semibold text-amber-900"
            >
              Chỉ mở trên YouTube
            </span>
          )}
          <p className="mt-1 text-xs text-muted">{metaText}</p>
        </div>

        <div className="mt-2 flex flex-wrap items-center gap-2">
          {externalOnly && externalYoutubeUrl ? (
            <YouTubeLink
              url={externalYoutubeUrl}
              testId="cinebot-open-youtube-link"
              className="inline-flex min-h-[44px] items-center rounded bg-brand px-3 text-xs font-bold text-ink hover:bg-brand-hover"
            >
              Mở trên YouTube
            </YouTubeLink>
          ) : (
            <Link
              to={mediaHref}
              onClick={onClose}
              className="inline-flex min-h-[44px] items-center rounded bg-brand px-3 text-xs font-bold text-ink hover:bg-brand-hover"
            >
              {externalOnly ? 'Xem chi tiết' : movie.episodeId || movie.episode_id ? 'Xem tập' : 'Xem phim'}
            </Link>
          )}

          <button
            onClick={onToggleWatchlist}
            type="button"
            aria-pressed={inWatchlist}
            className="inline-flex min-h-[44px] items-center rounded border border-ink/30 px-3 text-xs font-medium text-ink hover:bg-surface-elevated cursor-pointer"
          >
            {inWatchlist ? 'Đã lưu Xem sau' : 'Xem sau'}
          </button>
        </div>
      </div>
    </div>
  );
}

export function CineBot({ isOpen, onClose }: { isOpen: boolean; onClose: () => void }) {
  const { status, user, token } = useAuth();
  const actorKey = status === 'authenticated' && user && token ? `user:${user.id}:${token}` : `status:${status}`;
  return <CineBotConversation key={actorKey} isOpen={isOpen} onClose={onClose} />;
}

function CineBotConversation({ isOpen, onClose }: { isOpen: boolean; onClose: () => void }) {
  const { isWatchlisted, toggleWatchlist } = useWatchlist();
  const [messages, setMessages] = useState<ChatMessage[]>(() => [
    createWelcomeMessage('welcome', 'Hôm nay nhà mình muốn xem gì? Nhập tên phim, nhân vật hoặc cảnh bé nhớ nhé.'),
  ]);
  const [input, setInput] = useState('');
  const [inputError, setInputError] = useState('');
  const [isTyping, setIsTyping] = useState(false);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const cinebotRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const openerRef = useRef<HTMLElement | null>(null);
  const conversationRef = useRef(0);
  const sessionIdRef = useRef('');
  const requestRef = useRef<AbortController | null>(null);
  if (!sessionIdRef.current) sessionIdRef.current = newConversationId();

  useEffect(
    () => () => {
      conversationRef.current += 1;
      requestRef.current?.abort();
    },
    []
  );

  useEffect(() => {
    if (!isOpen) return;
    const isReduced =
      typeof window !== 'undefined' &&
      Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches);
    messagesEndRef.current?.scrollIntoView({ behavior: isReduced ? 'auto' : 'smooth' });
  }, [messages, isOpen]);

  useEffect(() => {
    if (!isOpen) return;
    if (document.activeElement && (!cinebotRef.current || !cinebotRef.current.contains(document.activeElement))) {
      openerRef.current = document.activeElement as HTMLElement | null;
    }

    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const timer = setTimeout(() => {
      inputRef.current?.focus();
    }, FOCUS_DELAY_MS);

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
        return;
      }
      if (e.key === 'Tab' && cinebotRef.current) {
        const focusable = cinebotRef.current.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR);
        if (focusable.length === 0) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (
          !cinebotRef.current.contains(document.activeElement) ||
          (e.shiftKey ? document.activeElement === first : document.activeElement === last)
        ) {
          e.preventDefault();
          (e.shiftKey ? last : first).focus();
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      clearTimeout(timer);
      document.body.style.overflow = originalOverflow;
      window.removeEventListener('keydown', handleKeyDown);
      openerRef.current?.focus();
    };
  }, [isOpen, onClose]);

  const handleSend = async (textToSend?: string) => {
    const text = (textToSend || input).trim();
    if (!text || requestRef.current) return;
    if (text.length > MAX_MESSAGE_LENGTH) {
      setInputError(`Tin nhắn dài tối đa ${MAX_MESSAGE_LENGTH} ký tự.`);
      return;
    }

    setInputError('');
    const conversation = conversationRef.current;
    const controller = new AbortController();
    requestRef.current = controller;

    const userMessage: ChatMessage = {
      id: `user-${Date.now()}`,
      sender: 'user',
      text,
      timestamp: TIME_FORMAT.format(new Date()),
    };

    const nextMessages = [...messages, userMessage];
    setMessages(nextMessages);
    setInput('');
    setIsTyping(true);

    const botId = `bot-${userMessage.id}`;
    const updateBot = (result: ChatServiceResponse) => {
      if (conversation !== conversationRef.current) return;
      const botMessage: ChatMessage = {
        id: botId,
        sender: 'bot',
        text: result.text,
        timestamp: TIME_FORMAT.format(new Date()),
        movies: result.recommendedMovies,
        followUps: result.followUps,
        structuredIntent: result.structuredIntent,
        pipelineStages: result.pipelineStages,
      };
      setMessages((prev) =>
        prev.some((item) => item.id === botId)
          ? prev.map((item) => (item.id === botId ? botMessage : item))
          : [...prev, botMessage]
      );
    };

    try {
      const result = await sendChatMessage(text, nextMessages, {
        sessionId: sessionIdRef.current,
        signal: controller.signal,
        onUpdate: updateBot,
      });
      if (conversation !== conversationRef.current) return;
      updateBot(result);
    } catch (err: unknown) {
      if (conversation !== conversationRef.current) return;
      console.error('Lỗi nhận phản hồi:', err);
      const errorMessage =
        err instanceof Error && err.message ? err.message : 'Chưa kết nối được CineBot. Bạn thử lại nhé.';
      const errorReply: ChatMessage = {
        id: `bot-err-${Date.now()}`,
        sender: 'bot',
        text: errorMessage,
        isError: true,
        timestamp: TIME_FORMAT.format(new Date()),
      };
      setMessages((prev) => [...prev.filter((item) => item.id !== botId), errorReply]);
    } finally {
      if (conversation === conversationRef.current) {
        requestRef.current = null;
        setIsTyping(false);
      }
    }
  };

  const resetChat = () => {
    conversationRef.current += 1;
    requestRef.current?.abort();
    requestRef.current = null;
    sessionIdRef.current = newConversationId();
    setIsTyping(false);
    setInput('');
    setInputError('');
    setMessages([createWelcomeMessage(`welcome-${Date.now()}`, 'Bạn muốn tìm phim nào cho bé?')]);
  };

  if (!isOpen) return null;

  return (
    <div
      ref={cinebotRef}
      role="dialog"
      aria-modal="true"
      aria-labelledby="cinebot-dialog-title"
      className="fixed inset-0 z-50 flex items-end justify-end p-0 sm:p-6 bg-black/60 backdrop-blur-sm animate-fade-in pointer-events-auto"
    >
      <div className="flex h-full w-full flex-col overflow-hidden bg-paper-white border-0 sm:border-2 sm:border-ink sm:rounded-xl sm:h-[620px] sm:w-[440px] sm:shadow-brand-lg">
        <div className="flex items-center justify-between border-b border-ink/20 bg-[#edf3e4] px-4 py-3">
          <div>
            <h2 id="cinebot-dialog-title" className="comic-heading text-xl font-bold text-ink">
              CineBot
            </h2>
            <p className="text-[11px] text-muted">Gợi ý phim hoạt hình cho cả nhà</p>
          </div>

          <div className="flex items-center gap-1">
            <button type="button" onClick={resetChat} title="Làm mới" aria-label="Làm mới cuộc trò chuyện" className={HEADER_BUTTON_CLASS}>
              Làm mới
            </button>
            <button type="button" onClick={onClose} title="Đóng" aria-label="Đóng CineBot" className={HEADER_BUTTON_CLASS}>
              Đóng
            </button>
          </div>
        </div>

        <div
          role="log"
          aria-live="polite"
          aria-label="Lịch sử trò chuyện CineBot"
          data-testid="cinebot-message-list"
          className="flex-1 overflow-y-auto p-4 space-y-4"
        >
          {messages.map((message) => (
            <div key={message.id} className={`flex flex-col ${message.sender === 'user' ? 'items-end' : 'items-start'}`}>
              <div
                role={message.isError ? 'alert' : undefined}
                className={`max-w-[88%] rounded-2xl px-4 py-2.5 text-xs sm:text-sm leading-relaxed shadow-sm ${
                  message.sender === 'user'
                    ? 'bg-brand text-ink font-medium rounded-br-sm border border-ink/25'
                    : message.isError
                    ? 'bg-amber-500/15 text-amber-950 border border-amber-700/40 rounded-bl-sm'
                    : 'bg-surface-elevated text-ink border border-surface-border/40 rounded-bl-sm'
                }`}
              >
                <p>
                  {message.text.split(/(\*\*[^*]+\*\*)/g).map((part, i) =>
                    part.startsWith('**') && part.endsWith('**') ? (
                      <strong key={i}>{part.slice(2, -2)}</strong>
                    ) : (
                      part
                    )
                  )}
                </p>
                <span
                  className={`mt-1 block text-[10px] ${
                    message.sender === 'user' ? 'text-zinc-950/70 text-right font-medium' : 'text-muted'
                  }`}
                >
                  {message.timestamp}
                </span>
              </div>

              {message.movies && message.movies.length > 0 && (
                <div className="mt-2.5 w-full space-y-2">
                  {message.movies.map((movie) => (
                    <ChatMovieCard
                      key={movie.id}
                      movie={movie}
                      inWatchlist={isWatchlisted(movie.id)}
                      onToggleWatchlist={() => void toggleWatchlist(movie.id, movie.title)}
                      onClose={onClose}
                    />
                  ))}
                </div>
              )}

              {message.followUps && message.followUps.length > 0 && (
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {message.followUps.map((prompt) => (
                    <button
                      key={prompt}
                      disabled={isTyping}
                      onClick={() => handleSend(prompt)}
                      className="inline-flex min-h-[44px] items-center rounded border border-ink/30 bg-mint px-3 py-2 text-xs text-muted transition-colors hover:border-ink hover:text-ink cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {prompt}
                    </button>
                  ))}
                </div>
              )}
            </div>
          ))}

          {isTyping && (
            <div role="status" className="text-xs text-muted bg-surface-muted border border-brand/40 rounded-md px-3 py-1.5 w-fit">
              Đang trả lời…
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        <div className="border-t border-surface-border bg-surface-muted p-3">
          <div className="flex items-center gap-2 rounded-md border border-surface-border bg-surface px-3 py-2 focus-within:border-brand">
            <input
              ref={inputRef}
              type="text"
              aria-label="Tin nhắn cho CineBot"
              maxLength={MAX_MESSAGE_LENGTH}
              value={input}
              disabled={isTyping}
              onChange={(e) => {
                setInput(e.target.value);
                setInputError('');
              }}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  void handleSend();
                }
              }}
              placeholder="Hỏi tên phim, nhân vật hoặc cảnh nhớ được…"
              className="w-full bg-transparent text-base sm:text-sm text-ink placeholder:text-muted focus:outline-none disabled:opacity-60"
            />
            <button
              onClick={() => handleSend()}
              disabled={!input.trim() || isTyping}
              className="h-11 shrink-0 rounded border border-ink bg-brand px-4 text-xs font-semibold text-ink hover:bg-brand-hover disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
              aria-label="Gửi tin nhắn"
            >
              Gửi
            </button>
          </div>
          {inputError && (
            <p role="alert" className="mt-2 text-xs text-red-800">
              {inputError}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
