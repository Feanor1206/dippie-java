import { withRequestTimeout } from '@/lib/services/apiRequest';
import type { Movie } from '@/types/movie';

export interface StructuredIntent {
  intent_type: 'DISCOVERY' | 'CONVERSATIONAL_PATCH' | 'CLARIFICATION' | 'NO_OP' | string;
  semantic_query?: string | null;
  reference_films?: string[];
  include_genres?: string[];
  exclude_genres?: string[];
  year_min?: number | null;
  year_max?: number | null;
  requested_k?: number | null;
  requires_clarification?: boolean;
  clarification_reason_code?: string | null;
  no_op?: boolean;
}

export interface ChatMessage {
  id: string;
  sender: 'user' | 'bot';
  text: string;
  timestamp: string;
  isError?: boolean;
  movies?: Movie[];
  followUps?: string[];
  structuredIntent?: StructuredIntent | null;
  pipelineStages?: Record<string, unknown>;
}

export interface ChatServiceResponse {
  text: string;
  recommendedMovies: Movie[];
  followUps: string[];
  structuredIntent?: StructuredIntent | null;
  pipelineStages?: Record<string, unknown>;
}

function parseResponse(value: unknown, requireText: boolean): ChatServiceResponse {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Phản hồi CineBot không hợp lệ.');
  const payload = value as Record<string, unknown>;
  if ((requireText && (typeof payload.text !== 'string' || !payload.text.trim()))
    || (payload.text !== undefined && typeof payload.text !== 'string')
    || (payload.recommendedMovies !== undefined && !Array.isArray(payload.recommendedMovies))
    || (payload.followUps !== undefined && (!Array.isArray(payload.followUps) || !payload.followUps.every((item) => typeof item === 'string')))
    || (payload.structuredIntent !== undefined && payload.structuredIntent !== null && (typeof payload.structuredIntent !== 'object' || Array.isArray(payload.structuredIntent)))
    || (payload.pipelineStages !== undefined && payload.pipelineStages !== null && (typeof payload.pipelineStages !== 'object' || Array.isArray(payload.pipelineStages)))) {
    throw new Error('Phản hồi CineBot không hợp lệ.');
  }
  return {
    text: typeof payload.text === 'string' ? payload.text : '',
    recommendedMovies: (payload.recommendedMovies as Movie[] | undefined) || [],
    followUps: (payload.followUps as string[] | undefined) || [],
    structuredIntent: (payload.structuredIntent as StructuredIntent | undefined) || null,
    pipelineStages: (payload.pipelineStages as Record<string, unknown> | undefined) || {},
  };
}

function buildHistoryPayload(history: ChatMessage[]) {
  return history.slice(-20).map((entry) => {
    if (entry.sender === 'user') {
      return {
        role: 'user',
        content: entry.text,
      };
    }
    const movies = Array.isArray(entry.movies) ? entry.movies : [];
    const itemIds: string[] = [];
    const documentIds: string[] = [];
    for (const movie of movies) {
      const movieId = typeof movie?.id === 'string' ? movie.id.trim() : '';
      if (movieId) {
        itemIds.push(movieId);
      }
      if (Array.isArray(movie?.evidence)) {
        for (const evidenceItem of movie.evidence) {
          const documentId = typeof evidenceItem?.documentId === 'string' ? evidenceItem.documentId.trim() : '';
          if (documentId && !documentIds.includes(documentId)) {
            documentIds.push(documentId);
          }
        }
      }
    }
    return {
      role: 'assistant',
      content: entry.text,
      ...(itemIds.length > 0 ? { itemIds, documentIds } : {}),
    };
  });
}

export async function sendChatMessage(
  message: string,
  history: ChatMessage[] = [],
  options: { sessionId?: string; signal?: AbortSignal; onUpdate?: (response: ChatServiceResponse) => void } = {}
): Promise<ChatServiceResponse> {
  return withRequestTimeout((signal) => readChatMessage(message, history, { ...options, signal }),
    { signal: options.signal, timeoutMs: 75_000 }, 'CineBot phản hồi chậm. Bạn thử lại nhé.');
}

async function readChatMessage(
  message: string,
  history: ChatMessage[],
  options: { sessionId?: string; signal: AbortSignal; onUpdate?: (response: ChatServiceResponse) => void },
): Promise<ChatServiceResponse> {
  const response = await fetch('/api/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    signal: options.signal,
    body: JSON.stringify({
      message,
      sessionId: options.sessionId,
      messages: buildHistoryPayload(history),
    }),
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    const fallback = response.status === 429
      ? 'CineBot đang bận. Bạn chờ chút rồi hỏi lại nhé.'
      : 'Chưa kết nối được CineBot. Bạn thử lại nhé.';
    if (response.status === 429) {
      const detail = typeof errorData?.detail === 'string' ? errorData.detail.trim() : '';
      throw new Error(detail || fallback);
    }
    const serverMessage =
      (typeof errorData?.text === 'string' && errorData.text.trim()) ||
      (typeof errorData?.detail === 'string' && errorData.detail.trim()) ||
      (typeof errorData?.message === 'string' && errorData.message.trim()) ||
      '';
    throw new Error(serverMessage || fallback);
  }

  if (!response.headers?.get('Content-Type')?.toLowerCase().includes('text/event-stream')) {
    const result = parseResponse(await response.json(), true);
    options.onUpdate?.(result);
    return result;
  }
  if (!response.body) throw new Error('CineBot không có luồng phản hồi.');
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let result: ChatServiceResponse = { text: '', recommendedMovies: [], followUps: [] };
  let completed = false;
  const onAbort = () => { void reader.cancel(options.signal?.reason).catch(() => {}); };
  options.signal?.addEventListener('abort', onAbort, { once: true });
  try {
    while (!completed) {
      options.signal?.throwIfAborted();
      const { value, done } = await reader.read();
      options.signal?.throwIfAborted();
      buffer += decoder.decode(value, { stream: !done });
      let boundary;
      while ((boundary = /\r?\n\r?\n/.exec(buffer)) !== null) {
        const event = buffer.slice(0, boundary.index);
        buffer = buffer.slice(boundary.index + boundary[0].length);
        if (event.length > 256_000) throw new Error('Phản hồi CineBot vượt giới hạn.');
        let type = 'message';
        const lines: string[] = [];
        for (const line of event.split(/\r?\n/)) {
          if (line.startsWith('event:')) type = line.slice(6).trim();
          else if (line.startsWith('data:')) lines.push(line.slice(5).replace(/^ /, ''));
        }
        if (!lines.length) continue;
        let payload;
        try { payload = JSON.parse(lines.join('\n')); } catch { throw new Error('Phản hồi CineBot không hợp lệ.'); }
        if (type === 'metadata') {
          result = { ...parseResponse(payload, false), text: result.text };
        } else if (type === 'token') {
          if (!payload || typeof payload.text !== 'string') throw new Error('Phản hồi CineBot không hợp lệ.');
          result = { ...result, text: result.text + payload.text };
          if (result.text.length > 128_000) throw new Error('Phản hồi CineBot vượt giới hạn.');
        } else if (type === 'done') {
          result = parseResponse(payload, true);
          completed = true;
        } else if (type === 'error') {
          const streamErrorText =
            (typeof payload?.text === 'string' && payload.text.trim()) ||
            (typeof payload?.detail === 'string' && payload.detail.trim()) ||
            '';
          throw new Error(streamErrorText || 'CineBot chưa trả lời trọn vẹn. Bạn thử lại nhé.');
        } else continue;
        options.onUpdate?.(result);
        if (completed) break;
      }
      if (buffer.length > 256_000) throw new Error('Phản hồi CineBot vượt giới hạn.');
      if (done && !completed) throw new Error('Kết nối với CineBot bị gián đoạn. Bạn hỏi lại nhé.');
    }
    return result;
  } finally {
    options.signal?.removeEventListener('abort', onAbort);
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}
