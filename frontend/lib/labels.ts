const GENRE_LABELS: Record<string, string> = {
  action: 'Hành động',
  'hành động': 'Hành động',
  '액션': 'Hành động',
  adventure: 'Phiêu lưu',
  'phiêu lưu': 'Phiêu lưu',
  '모험': 'Phiêu lưu',
  animation: 'Hoạt hình',
  'hoạt hình': 'Hoạt hình',
  '애니메이션': 'Hoạt hình',
  children: 'Thiếu nhi',
  kids: 'Thiếu nhi',
  'thiếu nhi': 'Thiếu nhi',
  '어린이': 'Thiếu nhi',
  comedy: 'Hài hước',
  'hài': 'Hài hước',
  'hài hước': 'Hài hước',
  '코미디': 'Hài hước',
  crime: 'Tội phạm',
  'tội phạm': 'Tội phạm',
  documentary: 'Tài liệu',
  'tài liệu': 'Tài liệu',
  drama: 'Chính kịch',
  'chính kịch': 'Chính kịch',
  '드라마': 'Chính kịch',
  educational: 'Giáo dục',
  'giáo dục': 'Giáo dục',
  family: 'Gia đình',
  'gia đình': 'Gia đình',
  '가족': 'Gia đình',
  fantasy: 'Giả tưởng',
  'giả tưởng': 'Giả tưởng',
  '판타지': 'Giả tưởng',
  folklore: 'Dân gian',
  'dân gian': 'Dân gian',
  historical: 'Lịch sử',
  'lịch sử': 'Lịch sử',
  horror: 'Kinh dị',
  'kinh dị': 'Kinh dị',
  music: 'Âm nhạc',
  'âm nhạc': 'Âm nhạc',
  '음악': 'Âm nhạc',
  musical: 'Nhạc kịch',
  'nhạc kịch': 'Nhạc kịch',
  mystery: 'Bí ẩn',
  'bí ẩn': 'Bí ẩn',
  '미스터리': 'Bí ẩn',
  romance: 'Lãng mạn',
  'lãng mạn': 'Lãng mạn',
  school: 'Học đường',
  'học đường': 'Học đường',
  '학원': 'Học đường',
  'sci-fi': 'Khoa học viễn tưởng',
  sci_fi: 'Khoa học viễn tưởng',
  'sci fi': 'Khoa học viễn tưởng',
  science_fiction: 'Khoa học viễn tưởng',
  'science fiction': 'Khoa học viễn tưởng',
  'khoa học viễn tưởng': 'Khoa học viễn tưởng',
  slice_of_life: 'Đời thường',
  'slice-of-life': 'Đời thường',
  'slice of life': 'Đời thường',
  'đời thường': 'Đời thường',
  '일상': 'Đời thường',
  sport: 'Thể thao',
  sports: 'Thể thao',
  'thể thao': 'Thể thao',
  thriller: 'Giật gân',
  'giật gân': 'Giật gân',
};

export type GenreInput = string | { id?: string | null; label?: string | null } | null | undefined;

export function genreLabel(raw: string): string {
  const trimmed = typeof raw === 'string' ? raw.trim() : '';
  if (!trimmed) return '';
  const directKey = trimmed.toLowerCase();
  if (Object.prototype.hasOwnProperty.call(GENRE_LABELS, directKey)) {
    return GENRE_LABELS[directKey];
  }
  const normalizedKey = directKey.replace(/[\s-]+/g, '_');
  if (Object.prototype.hasOwnProperty.call(GENRE_LABELS, normalizedKey)) {
    return GENRE_LABELS[normalizedKey];
  }
  const spacedKey = directKey.replace(/[_-]+/g, ' ').replace(/\s+/g, ' ');
  if (Object.prototype.hasOwnProperty.call(GENRE_LABELS, spacedKey)) {
    return GENRE_LABELS[spacedKey];
  }
  return trimmed;
}

export function resolveGenreLabel(entry: GenreInput): string {
  if (typeof entry === 'string') return genreLabel(entry);
  if (!entry || typeof entry !== 'object') return '';
  const id = typeof entry.id === 'string' ? entry.id.trim() : '';
  const label = typeof entry.label === 'string' ? entry.label.trim() : '';
  if (id) {
    const mappedId = genreLabel(id);
    if (mappedId !== id) return mappedId;
  }
  if (label) return genreLabel(label);
  return id ? genreLabel(id) : '';
}

export function normalizeGenreLabels(genres?: ReadonlyArray<GenreInput> | null): string[] {
  if (!Array.isArray(genres) || genres.length === 0) return [];
  const seen = new Set<string>();
  const result: string[] = [];
  for (const genre of genres) {
    const label = resolveGenreLabel(genre);
    if (!label) continue;
    const dedupeKey = label.toLocaleLowerCase('vi');
    if (seen.has(dedupeKey)) continue;
    seen.add(dedupeKey);
    result.push(label);
  }
  return result;
}

export function formatGenreList(genres?: ReadonlyArray<GenreInput> | null, limit?: number): string {
  const labels = normalizeGenreLabels(genres);
  const bounded = typeof limit === 'number' && limit > 0 ? labels.slice(0, limit) : labels;
  return bounded.join(', ');
}

export function cleanMediaTitle(title?: string | null): string {
  if (!title) return '';
  return title
    .replace(/\s*\[[^\]]+\](?=\.(?:mp4|mkv|webm)$)/i, '')
    .replace(/\.(?:mp4|mkv|webm)$/i, '')
    .replace(/\s*⧸\s*/g, ' / ')
    .replace(/\s+/g, ' ')
    .trim();
}

export function chatGenres(genres: string[]): string {
  return formatGenreList(genres);
}

export function formatYear(year?: number | null): string {
  if (typeof year !== 'number' || !Number.isFinite(year) || year <= 0) return '';
  return String(Math.trunc(year));
}

export function formatDurationMs(ms?: number | null): string {
  if (!ms || ms <= 0 || !Number.isFinite(ms)) return '00:00';
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

export function formatRuntime(runtime = ''): string {
  const trimmed = runtime.trim();
  if (!trimmed) return '';
  if (trimmed.toLowerCase() === 'series') return 'Phim bộ';
  const match = trimmed.match(/^(?:(\d+)h)?\s*(?:(\d+)m)?\s*(?:(\d+)s)?$/i);
  if (!match || !match.slice(1).some(Boolean)) return trimmed;
  return [
    match[1] && `${Number(match[1])} giờ`,
    match[2] && `${Number(match[2])} phút`,
    match[3] && `${Number(match[3])} giây`,
  ]
    .filter(Boolean)
    .join(' ');
}

export function getUserInitials(name?: string, fallback = 'U'): string {
  const trimmed = typeof name === 'string' ? name.trim() : '';
  if (!trimmed) return fallback;
  const parts = trimmed.split(/\s+/);
  if (parts.length >= 2) return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
  return trimmed.slice(0, 2).toUpperCase();
}

const DEMO_SYNOPSIS = new Map<string, string>([
  ['Tập phim hoạt hình Shin Cậu Bé Bút Chì được phát và giải mã trực tiếp từ Google Drive storage.', 'Tập phim Shin Cậu Bé Bút Chì phát từ Google Drive.'],
  ['A lone astronaut discovers that the signal guiding her home may be the final memory of a world already gone.', 'Một nữ phi hành gia lần theo tín hiệu dẫn cô về nhà. Đó có thể là ký ức cuối cùng của một thế giới đã mất.'],
  ['A young girl wanders into a magical bathhouse ruled by spirits and witches, where she must find the courage to free her parents.', 'Một cô bé lạc vào nhà tắm của các linh hồn và phù thủy. Cô phải tìm cách cứu cha mẹ.'],
  ['Two teenagers share a profound, magical connection upon discovering they are swapping bodies across time and space.', 'Hai thiếu niên phát hiện họ có thể hoán đổi thân xác dù sống ở những nơi và thời điểm khác nhau.'],
  ['Teen Miles Morales becomes the Spider-Man of his universe and crosses paths with five counterparts from other dimensions.', 'Miles Morales trở thành Người Nhện và gặp năm Người Nhện đến từ các chiều không gian khác.'],
  ['Set in dystopian Japan, a young boy journeys to Trash Island in a stop-motion quest to rescue his exiled dog Spots.', 'Một cậu bé ở Nhật Bản đến đảo rác để tìm Spots, chú chó bị đày khỏi thành phố.'],
  ['An adventurous 11-year-old girl finds another world that is a strangely idealized version of her frustrating home, but it has sinister secrets.', 'Coraline, 11 tuổi, tìm thấy một thế giới giống nhà mình đến kỳ lạ. Nơi tưởng như êm ấm ấy lại giấu nhiều bí mật đáng sợ.'],
  ['A timeless collection of animated interpretations of great works of Western classical music.', 'Tuyển tập hoạt hình lấy cảm hứng từ những bản nhạc cổ điển phương Tây.'],
  ['In a city where night never ends, a photographer follows one impossible beam of sunlight.', 'Trong thành phố chìm trong đêm, một nhiếp ảnh gia lần theo tia nắng duy nhất.'],
  ['A brilliant architect is asked to design a city that no one is meant to leave.', 'Một kiến trúc sư được giao thiết kế một thành phố mà cư dân không thể rời đi.'],
  ['Two estranged sisters cross an unmapped ocean in search of their father.', 'Hai chị em xa cách cùng vượt qua vùng biển chưa có trên bản đồ để tìm cha.'],
  ['A composer returns to the town she fled and finds an old song waiting for her.', 'Một nữ nhạc sĩ trở về thị trấn từng bỏ đi và tìm lại một ca khúc cũ.'],
  ['A detective investigates a string of elegant crimes committed at midnight.', 'Một thám tử điều tra chuỗi vụ án xảy ra đúng nửa đêm.'],
]);

export function movieSynopsis(text: string): string {
  return DEMO_SYNOPSIS.get(text) ?? text;
}

export type RatingDisplaySummary = {
  hasRating: boolean;
  scoreText: string | null;
  count: number;
  label: string;
  compactLabel: string;
};

const EMPTY_RATING_SUMMARY: RatingDisplaySummary = {
  hasRating: false,
  scoreText: null,
  count: 0,
  label: 'Chưa có điểm',
  compactLabel: 'Chưa có điểm',
};

export function resolveRatingSummary(item?: {
  averageRating?: number | null;
  ratingCount?: number | null;
  rating?: number | null;
} | null): RatingDisplaySummary {
  const count =
    typeof item?.ratingCount === 'number' && Number.isFinite(item.ratingCount) && item.ratingCount > 0
      ? Math.floor(item.ratingCount)
      : 0;
  const average =
    typeof item?.averageRating === 'number' && Number.isFinite(item.averageRating) && item.averageRating > 0
      ? item.averageRating
      : null;

  if (count > 0 && average !== null) {
    const scoreText = average.toFixed(1);
    return {
      hasRating: true,
      scoreText,
      count,
      label: `${scoreText}/10 (${count} lượt chấm)`,
      compactLabel: `${scoreText} (${count})`,
    };
  }

  return EMPTY_RATING_SUMMARY;
}
