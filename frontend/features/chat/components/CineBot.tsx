import { YouTubeLink } from '@/components/ui/YouTubeLink';
import { ArtworkImage } from '@/features/series/components/ArtworkImage';
import { foldVietnamese } from '@/features/series/seriesBrowseUtils';
import { useSeriesCards } from '@/lib/hooks/useCatalogData';
import type { SeriesCard } from '@/lib/services/seriesCardService';
import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { sendChatMessage, type ChatMessage, type ChatServiceResponse } from '@/lib/services/chatService';
import { useWatchlist } from '@/lib/context/WatchlistContext';
import { useAuth } from '@/lib/context/AuthContext';
import { buildMediaItemHref } from '@/lib/services/mediaRouting';
import { isMediaExternalOnly, resolveExternalYoutubeWatchUrl } from '@/lib/services/playbackSource';
import {
  chatGenres,
  cleanMediaTitle,
  formatRuntime,
  formatYear,
  resolveRatingSummary,
  toCompactCardArtworkUrl,
} from '@/lib/labels';
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

function extractYoutubeVideoId(rawUrl?: string | null): string | null {
  if (!rawUrl || typeof rawUrl !== 'string') return null;
  const watchMatch = rawUrl.match(/[?&]v=([A-Za-z0-9_-]{6,})/);
  if (watchMatch) return watchMatch[1];
  const shortMatch = rawUrl.match(/youtu\.be\/([A-Za-z0-9_-]{6,})/);
  if (shortMatch) return shortMatch[1];
  const embedMatch = rawUrl.match(/youtube(?:-nocookie)?\.com\/embed\/([A-Za-z0-9_-]{6,})/);
  if (embedMatch) return embedMatch[1];
  return null;
}

function resolveChatCardArtwork(movie: Movie, seriesCards: SeriesCard[]): string {
  const direct = movie.poster || movie.thumbnailUrl || movie.backdrop || '';
  if (direct && direct.trim()) return direct.trim();

  const targetSeriesId = (movie.series_id || movie.seriesId || '').trim();
  const targetSeriesTitle = (movie.series_title || movie.seriesTitle || '').trim();
  const foldedSeriesTitle = targetSeriesTitle && typeof foldVietnamese === 'function' ? foldVietnamese(targetSeriesTitle) : '';

  for (const card of seriesCards) {
    if (targetSeriesId && card.id === targetSeriesId) {
      const matchedArt = card.posterUrl || card.backdropUrl || '';
      if (matchedArt) return matchedArt;
    }
    if (
      foldedSeriesTitle &&
      typeof foldVietnamese === 'function' &&
      foldVietnamese(card.title) === foldedSeriesTitle
    ) {
      const matchedArt = card.posterUrl || card.backdropUrl || '';
      if (matchedArt) return matchedArt;
    }
  }

  const ytUrl =
    (typeof resolveExternalYoutubeWatchUrl === 'function' ? resolveExternalYoutubeWatchUrl(movie) : null) ||
    movie.youtubeWatchUrl ||
    movie.youtube_watch_url ||
    null;
  const ytId = extractYoutubeVideoId(ytUrl);
  if (ytId) {
    return `https://i.ytimg.com/vi/${ytId}/hqdefault.jpg`;
  }
  return '';
}

function ChatMovieCard({
  movie,
  seriesCards = [],
  inWatchlist,
  onToggleWatchlist,
  onClose,
  isKidMode = false,
}: {
  movie: Movie;
  seriesCards?: SeriesCard[];
  inWatchlist: boolean;
  onToggleWatchlist: () => void;
  onClose: () => void;
  isKidMode?: boolean;
}) {
  const mediaHref = buildMediaItemHref(movie);
  const rawArt = resolveChatCardArtwork(movie, seriesCards);
  const compactArt = typeof toCompactCardArtworkUrl === 'function' ? toCompactCardArtworkUrl(rawArt) : rawArt;
  const seriesTitle = (movie.series_title || movie.seriesTitle || '').trim();
  const displayTitle = cleanMediaTitle(movie.title);
  const showSeriesName =
    Boolean(seriesTitle) &&
    movie.item_type !== 'series' &&
    seriesTitle.toLocaleLowerCase('vi') !== displayTitle.toLocaleLowerCase('vi');
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
    ratingSummary.hasRating ? `★ ${ratingSummary.compactLabel}` : '',
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <div className="flex gap-3 rounded-lg border border-surface-border bg-surface-muted p-2.5 transition-colors hover:border-ink/30">
      <ArtworkImage
        src={compactArt || null}
        alt={displayTitle}
        title={seriesTitle || displayTitle}
        seed={movie.series_id || movie.seriesId || movie.id}
        aspectClassName="h-20 w-16"
        className="h-20 w-16 shrink-0 rounded border border-surface-border"
      />
      <div className="flex flex-col justify-between flex-1 min-w-0">
        <div>
          {showSeriesName && (
            <p className="truncate text-[0.6875rem] font-semibold text-brand-dark">{seriesTitle}</p>
          )}
          <div className="flex items-center justify-between gap-1">
            <Link
              to={mediaHref}
              onClick={onClose}
              className="line-clamp-2 break-words text-sm font-semibold text-ink hover:text-brand-dark"
            >
              {displayTitle}
            </Link>
          </div>
          {externalOnly && !isKidMode && (
            <span
              data-testid="cinebot-external-badge"
              className="mt-1 inline-flex w-fit items-center rounded border border-amber-500/40 bg-amber-500/10 px-2 py-0.5 text-[0.6875rem] font-semibold text-amber-900"
            >
              Chỉ mở trên YouTube
            </span>
          )}
          <p className="mt-1 text-xs text-muted">{metaText}</p>
        </div>

        <div className="mt-2 flex flex-wrap items-center gap-2">
          {externalOnly && !isKidMode && externalYoutubeUrl ? (
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

export function CineBot({
  isOpen,
  onClose,
  initialPrompt = null,
  onConsumeInitialPrompt,
}: {
  isOpen: boolean;
  onClose: () => void;
  initialPrompt?: string | null;
  onConsumeInitialPrompt?: () => void;
}) {
  const { status, user, token, isKidMode } = useAuth();
  const actorKey = status === 'authenticated' && user && token ? `user:${user.id}:${token}` : `status:${status}`;
  return (
    <CineBotConversation
      key={actorKey}
      isOpen={isOpen}
      onClose={onClose}
      isKidMode={Boolean(isKidMode)}
      initialPrompt={initialPrompt}
      onConsumeInitialPrompt={onConsumeInitialPrompt}
    />
  );
}

function CineBotConversation({
  isOpen,
  onClose,
  isKidMode = false,
  initialPrompt = null,
  onConsumeInitialPrompt,
}: {
  isOpen: boolean;
  onClose: () => void;
  isKidMode?: boolean;
  initialPrompt?: string | null;
  onConsumeInitialPrompt?: () => void;
}) {
  const { isWatchlisted, toggleWatchlist } = useWatchlist();
  const seriesCardsData = typeof useSeriesCards === 'function' ? useSeriesCards(isOpen) : null;
  const seriesCards = Array.isArray(seriesCardsData?.cards) ? seriesCardsData.cards : [];
  const [messages, setMessages] = useState<ChatMessage[]>(() => [
    createWelcomeMessage('welcome', 'Hôm nay bạn muốn xem gì? Nhập tên phim, nhân vật hoặc cảnh phim bạn nhớ nhé.'),
  ]);
  const [input, setInput] = useState('');
  const [inputError, setInputError] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const [lastRetryPrompt, setLastRetryPrompt] = useState('');

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const cinebotRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const openerRef = useRef<HTMLElement | null>(null);
  const conversationRef = useRef(0);
  const sessionIdRef = useRef('');
  const requestRef = useRef<AbortController | null>(null);
  const activePromptRef = useRef('');
  const streamedMoviesRef = useRef<Movie[] | undefined>(undefined);
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
      (typeof document !== 'undefined' && document.documentElement?.dataset?.motion === 'reduce') ||
      (typeof window !== 'undefined' &&
        Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches));
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
      const isCompactOrTouch =
        typeof window !== 'undefined' &&
        ((typeof window.innerWidth === 'number' && window.innerWidth < 768) ||
          Boolean(window.matchMedia?.('(pointer: coarse)').matches));
      if (isCompactOrTouch) {
        const firstBtn = cinebotRef.current?.querySelector<HTMLElement>('button:not([disabled])');
        firstBtn?.focus();
      } else {
        inputRef.current?.focus();
      }
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
    setLastRetryPrompt('');
    activePromptRef.current = text;
    streamedMoviesRef.current = undefined;
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
      if (result.recommendedMovies && result.recommendedMovies.length > 0) {
        streamedMoviesRef.current = result.recommendedMovies;
      }
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
      if (controller.signal.aborted) {
        setLastRetryPrompt(text);
        return;
      }
      console.error('Lỗi nhận phản hồi:', err);
      setLastRetryPrompt(text);
      const errorMessage =
        err instanceof Error && err.message ? err.message : 'Chưa kết nối được CineBot. Bạn thử lại nhé.';
      const partialMovies = streamedMoviesRef.current;
      const errorReply: ChatMessage = {
        id: `bot-err-${Date.now()}`,
        sender: 'bot',
        text: errorMessage,
        isError: true,
        movies: partialMovies,
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

  useEffect(() => {
    if (!isOpen || !initialPrompt || !initialPrompt.trim()) return;
    const promptText = initialPrompt.trim();
    onConsumeInitialPrompt?.();
    if (!requestRef.current) {
      void handleSend(promptText);
    }
  }, [isOpen, initialPrompt, onConsumeInitialPrompt]);

  const handleStopGenerating = () => {
    if (!requestRef.current) return;
    const stoppedPrompt = activePromptRef.current;
    requestRef.current.abort();
    requestRef.current = null;
    setIsTyping(false);
    setLastRetryPrompt(stoppedPrompt);
    setMessages((prev) => {
      const last = prev.at(-1);
      if (last && last.sender === 'bot') {
        return prev;
      }
      return [
        ...prev,
        {
          id: `bot-stopped-${Date.now()}`,
          sender: 'bot',
          text: 'Đã dừng trả lời. Bạn có thể bấm Thử lại hoặc hỏi câu khác nhé.',
          movies: streamedMoviesRef.current,
          timestamp: TIME_FORMAT.format(new Date()),
        },
      ];
    });
  };

  const resetChat = () => {
    conversationRef.current += 1;
    requestRef.current?.abort();
    requestRef.current = null;
    sessionIdRef.current = newConversationId();
    setIsTyping(false);
    setInput('');
    setInputError('');
    setLastRetryPrompt('');
    setMessages([createWelcomeMessage(`welcome-${Date.now()}`, 'Bạn muốn tìm bộ phim hay tập phim nào?')]);
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
      <div className="motion-sheet-enter flex h-full w-full flex-col overflow-hidden bg-paper-white border-0 sm:border-2 sm:border-ink sm:rounded-xl sm:h-[620px] sm:w-[440px] sm:shadow-brand-lg">
        <div className="flex items-center justify-between border-b border-ink/20 bg-[#edf3e4] px-4 py-3">
          <div>
            <h2 id="cinebot-dialog-title" className="comic-heading text-xl font-bold text-ink">
              CineBot
            </h2>
            <p className="text-[0.6875rem] text-muted">Gợi ý phim hoạt hình cho cả nhà</p>
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
          {messages.map((message, messageIndex) => {
            const isWelcomeMsg = message.id.startsWith('welcome');
            const showEmptySearchLink =
              message.sender === 'bot' &&
              !isWelcomeMsg &&
              !message.isError &&
              (!message.movies || message.movies.length === 0) &&
              !(isTyping && messageIndex === messages.length - 1);
            const matchedUserPrompt = showEmptySearchLink
              ? messages
                  .slice(0, messageIndex)
                  .reverse()
                  .find((item) => item.sender === 'user')
                  ?.text.trim() || ''
              : '';

            return (
            <div key={message.id} className={`flex flex-col ${message.sender === 'user' ? 'items-end' : 'items-start'}`}>
              <div
                role={message.isError ? 'alert' : undefined}
                className={`max-w-[88%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed shadow-sm ${
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
                  className={`mt-1 block text-[0.625rem] ${
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
                      seriesCards={seriesCards}
                      inWatchlist={isWatchlisted(movie.id)}
                      onToggleWatchlist={() => void toggleWatchlist(movie.id, movie.title)}
                      onClose={onClose}
                      isKidMode={isKidMode}
                    />
                  ))}
                </div>
              )}

              {showEmptySearchLink && (
                <div className="mt-2">
                  <Link
                    to={matchedUserPrompt ? `/search?q=${encodeURIComponent(matchedUserPrompt)}` : '/search'}
                    data-testid="cinebot-empty-search-link"
                    onClick={onClose}
                    className="btn-secondary min-h-[44px] px-3.5 text-xs font-semibold"
                  >
                    Tìm trong Kho phim
                  </Link>
                </div>
              )}

              {message.followUps && message.followUps.length > 0 && (
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {message.followUps.map((prompt) => (
                    <button
                      key={prompt}
                      type="button"
                      disabled={isTyping}
                      onClick={() => handleSend(prompt)}
                      className="inline-flex min-h-[44px] items-center rounded border border-ink/30 bg-mint px-3 py-2 text-xs font-medium text-ink transition-colors hover:border-ink hover:bg-mint/80 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {prompt}
                    </button>
                  ))}
                </div>
              )}
            </div>
            );
          })}

          {isTyping && (
            <div className="flex flex-wrap items-center gap-2">
              <div role="status" className="text-xs text-muted bg-surface-muted border border-brand/40 rounded-md px-3 py-2 w-fit">
                Đang trả lời…
              </div>
              <button
                type="button"
                data-testid="cinebot-stop-button"
                aria-label="Dừng trả lời"
                onClick={handleStopGenerating}
                className="inline-flex min-h-[44px] items-center rounded-lg border border-ink bg-paper-white px-3.5 text-xs font-bold text-ink hover:bg-surface-elevated cursor-pointer"
              >
                Dừng
              </button>
            </div>
          )}

          {!isTyping && lastRetryPrompt && (
            <div className="flex flex-wrap items-center gap-2">
              <button
                type="button"
                data-testid="cinebot-retry-button"
                onClick={() => handleSend(lastRetryPrompt)}
                className="btn-primary min-h-[44px] px-3.5 text-xs font-bold"
              >
                Thử lại
              </button>
              <Link
                to={`/search?q=${encodeURIComponent(lastRetryPrompt)}`}
                data-testid="cinebot-fallback-search-link"
                onClick={onClose}
                className="btn-secondary min-h-[44px] px-3.5 text-xs font-semibold"
              >
                Tìm trong Kho phim
              </Link>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        <div className="border-t border-surface-border bg-surface-muted p-3">
          <div className="input-shell flex items-center gap-2 rounded-md border border-surface-border bg-surface px-3 py-2">
            <input
              ref={inputRef}
              id="cinebot-message-input"
              name="message"
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
              placeholder="Hỏi tên phim, nhân vật, cảnh nhớ…"
              className="w-full bg-transparent text-base sm:text-sm text-ink placeholder:text-muted focus:outline-none disabled:opacity-60"
            />
            {isTyping ? (
              <button
                type="button"
                onClick={handleStopGenerating}
                className="h-11 min-h-[44px] shrink-0 rounded border border-ink bg-paper-white px-4 text-xs font-bold text-ink hover:bg-surface-elevated cursor-pointer"
              >
                Dừng
              </button>
            ) : (
              <button
                type="button"
                onClick={() => handleSend()}
                disabled={!input.trim() || isTyping}
                className="h-11 min-h-[44px] shrink-0 rounded border border-ink bg-brand px-4 text-xs font-semibold text-ink hover:bg-brand-hover disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
                aria-label="Gửi tin nhắn"
              >
                Gửi
              </button>
            )}
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
