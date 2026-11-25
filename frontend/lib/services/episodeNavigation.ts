import type { CatalogEpisode, CatalogSeason } from './catalogService';

function seasonNumber(season: CatalogSeason): number {
  return season.number ?? season.seasonNumber ?? Number.MAX_SAFE_INTEGER;
}

function episodeNumber(episode: CatalogEpisode): number {
  return episode.number ?? episode.episodeNumber ?? Number.MAX_SAFE_INTEGER;
}

export function sortCatalogSeasons(seasons: CatalogSeason[]): CatalogSeason[] {
  return [...seasons].sort((a, b) => seasonNumber(a) - seasonNumber(b) || a.id.localeCompare(b.id));
}

export function sortCatalogEpisodes(episodes: CatalogEpisode[]): CatalogEpisode[] {
  return [...episodes].sort((a, b) => episodeNumber(a) - episodeNumber(b) || a.id.localeCompare(b.id));
}

export function getEpisodesForSeason(
  episodes: CatalogEpisode[],
  seasonId: string | null,
  hasSeasons = true
): CatalogEpisode[] {
  const filtered = hasSeasons
    ? episodes.filter((episode) => episode.seasonId === seasonId)
    : episodes;
  return sortCatalogEpisodes(filtered);
}

export function getInitialSeasonId(
  seasons: CatalogSeason[],
  episodes: CatalogEpisode[],
  targetEpisodeId?: string | null
): string | null {
  const orderedSeasons = sortCatalogSeasons(seasons);
  if (targetEpisodeId) {
    const target = episodes.find((episode) => episode.id === targetEpisodeId);
    if (target?.seasonId && orderedSeasons.some((season) => season.id === target.seasonId)) {
      return target.seasonId;
    }
  }
  return orderedSeasons[0]?.id ?? null;
}

export function getInitialEpisode(
  episodes: CatalogEpisode[],
  seasonId: string | null,
  targetEpisodeId?: string | null,
  hasSeasons = true
): CatalogEpisode | null {
  const seasonEpisodes = getEpisodesForSeason(episodes, seasonId, hasSeasons);
  const target = targetEpisodeId && seasonEpisodes.find((episode) => episode.id === targetEpisodeId);
  return target || seasonEpisodes[0] || null;
}

export function getAdjacentEpisode(
  episodes: CatalogEpisode[],
  seasonId: string | null,
  currentEpisodeId: string | null,
  direction: -1 | 1,
  hasSeasons = true,
  seasons?: CatalogSeason[]
): CatalogEpisode | null {
  const seasonEpisodes = getEpisodesForSeason(episodes, seasonId, hasSeasons);
  const currentIndex = seasonEpisodes.findIndex((episode) => episode.id === currentEpisodeId);
  if (currentIndex < 0) return null;
  const inSeason = seasonEpisodes[currentIndex + direction];
  if (inSeason) return inSeason;
  if (!hasSeasons || !seasons || seasons.length <= 1) return null;

  const orderedSeasons = sortCatalogSeasons(seasons);
  const seasonIndex = orderedSeasons.findIndex((season) => season.id === seasonId);
  if (seasonIndex < 0) return null;

  for (
    let nextSeasonIndex = seasonIndex + direction;
    nextSeasonIndex >= 0 && nextSeasonIndex < orderedSeasons.length;
    nextSeasonIndex += direction
  ) {
    const candidateEpisodes = getEpisodesForSeason(episodes, orderedSeasons[nextSeasonIndex].id, true);
    if (candidateEpisodes.length > 0) {
      return direction === 1
        ? candidateEpisodes[0]
        : candidateEpisodes[candidateEpisodes.length - 1];
    }
  }
  return null;
}

export function formatAdjacentEpisodeLabel(
  targetEpisode: CatalogEpisode | null,
  currentSeasonId: string | null,
  seasons: CatalogSeason[],
  direction: -1 | 1
): string {
  const fallback = direction === 1 ? 'Tập tiếp theo' : 'Tập trước';
  if (!targetEpisode || !targetEpisode.seasonId || targetEpisode.seasonId === currentSeasonId) {
    return fallback;
  }
  const targetSeason = seasons.find((season) => season.id === targetEpisode.seasonId);
  const targetSeasonNum = targetSeason?.number ?? targetSeason?.seasonNumber;
  const seasonText =
    typeof targetSeasonNum === 'number' && Number.isFinite(targetSeasonNum)
      ? `Mùa ${targetSeasonNum}`
      : targetSeason?.title?.trim() || 'Mùa khác';
  const targetEpisodeNum = targetEpisode.number ?? targetEpisode.episodeNumber;
  const episodeText =
    typeof targetEpisodeNum === 'number' && Number.isFinite(targetEpisodeNum)
      ? `Tập ${targetEpisodeNum}`
      : '';
  const verb = direction === 1 ? 'Sang' : 'Về';
  return episodeText ? `${verb} ${seasonText} · ${episodeText}` : `${verb} ${seasonText}`;
}
