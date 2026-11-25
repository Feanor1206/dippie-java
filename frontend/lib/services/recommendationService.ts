import { apiFetch } from '@/lib/services/apiRequest';
import type { Movie } from '@/types/movie';

const API = '/api';

export type MoodPresetId =
  | 'funny'
  | 'family'
  | 'bedtime'
  | 'adventure'
  | 'friendship'
  | 'short';

export const MOOD_PRESETS: readonly { id: MoodPresetId; label: string }[] = [
  { id: 'funny', label: 'Vui nhộn' },
  { id: 'family', label: 'Ấm áp gia đình' },
  { id: 'bedtime', label: 'Nhẹ nhàng trước giờ ngủ' },
  { id: 'adventure', label: 'Phiêu lưu khám phá' },
  { id: 'friendship', label: 'Tình bạn' },
  { id: 'short', label: 'Tập ngắn dưới 10 phút' },
];

export type RecommendationOptions = {
  moodPreset?: MoodPresetId | null;
  mood?: string | null;
};

export async function getRecommendations(
  token: string | null,
  signal?: AbortSignal,
  likedMovieIds: string[] = [],
  options?: RecommendationOptions
): Promise<{ movies: Movie[]; reasoning: string }> {
  const payload: Record<string, unknown> = { limit: 6, likedMovieIds };
  if (options?.moodPreset) {
    payload.moodPreset = options.moodPreset;
  }
  if (typeof options?.mood === 'string' && options.mood.trim()) {
    payload.mood = options.mood.trim();
  }
  const response = await apiFetch(`${API}/recommend`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(payload),
    signal,
  });
  if (!response.ok) {
    let errorMessage = 'Chưa tải được gợi ý phim.';
    try {
      const errBody = await response.json();
      if (typeof errBody?.message === 'string' && errBody.message.trim()) {
        errorMessage = errBody.message.trim();
      }
    } catch {
    }
    throw new Error(errorMessage);
  }
  const responseBody = await response.json();
  if (!responseBody || !Array.isArray(responseBody.recommendedMovies) || typeof responseBody.reasoning !== 'string' ||
      !responseBody.recommendedMovies.every((movie: Movie) => movie && typeof movie.id === 'string' && typeof movie.title === 'string')) {
    throw new Error('Dữ liệu gợi ý chưa hợp lệ.');
  }
  return { movies: responseBody.recommendedMovies, reasoning: responseBody.reasoning };
}
