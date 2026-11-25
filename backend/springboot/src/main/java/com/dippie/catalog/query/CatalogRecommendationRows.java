package com.dippie.catalog.query;

import com.dippie.catalog.domain.CatalogCharacter;
import com.dippie.catalog.domain.CatalogDisplayText;
import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogGenre;
import com.dippie.catalog.domain.CatalogSeason;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.catalog.domain.EpisodeRuntime;
import com.dippie.catalog.domain.MoviePresentationLookup;
import com.dippie.catalog.domain.SeriesArtwork;
import com.dippie.catalog.query.CatalogReadModel.EpisodeProfile;
import com.dippie.common.json.JsonText;
import jakarta.persistence.EntityManager;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

// Nạp dữ liệu theo lô (batch JPQL) để dựng danh sách EpisodeProfile cho bộ gợi ý, tránh query lặp N+1 từng tập một.
final class CatalogRecommendationRows {

    private final EntityManager entities;
    private final MoviePresentationLookup moviePresentations;

    CatalogRecommendationRows(EntityManager entities, MoviePresentationLookup moviePresentations) {
        this.entities = entities;
        this.moviePresentations = moviePresentations;
    }

    // Nạp sẵn trình chiếu, thể loại và nhân vật theo từng lô rồi ghép thành hồ sơ gợi ý cho các tập đủ điều kiện.
    List<EpisodeProfile> load(List<CatalogEpisode> episodes, Map<String, CatalogSeries> series,
            Map<String, CatalogSeason> seasons, Predicate<CatalogEpisode> isEligible,
            Function<String, SeriesArtwork> artworkById, Set<String> episodesWithSegments) {
        // Lọc trước danh sách tập hợp lệ rồi tải một lượt điểm đánh giá và ảnh từ bảng trình chiếu.
        var eligible = episodes.stream().filter(isEligible).toList();
        if (eligible.isEmpty()) {
            return List.of();
        }
        Map<String, MoviePresentationLookup.Presentation> movies = moviePresentations.findForReadyEpisodes();

        // Tải gom thể loại của tập, bộ phim và nhân vật của từng tập bằng 3 câu JPQL duy nhất.
        var episodeGenres = genres("""
                select link.episodeId, genre from CatalogEpisodeGenre link
                join CatalogGenre genre on genre.id = link.genreId order by link.genreId
                """);
        var seriesGenres = genres("""
                select link.seriesId, genre from CatalogSeriesGenre link
                join CatalogGenre genre on genre.id = link.genreId order by link.genreId
                """);
        var characters = characters();
        return eligible.stream()
                .map(episode -> toProfile(
                        episode,
                        series.get(episode.getSeriesId()),
                        episode.getSeasonId() == null ? null : seasons.get(episode.getSeasonId()),
                        movies.get(episode.getId()),
                        episodeGenres.getOrDefault(episode.getId(),
                                seriesGenres.getOrDefault(episode.getSeriesId(), GenreTags.EMPTY)),
                        characters.getOrDefault(episode.getId(), Set.of()),
                        artworkById.apply(episode.getId()),
                        episodesWithSegments.contains(episode.getId())))
                .toList();
    }

    // Chấm điểm summaryQuality (0 đến 2) dựa vào việc tập có tóm tắt và phân đoạn hay chưa; năm phát hành ưu tiên tập, mùa rồi mới đến bộ.
    private static EpisodeProfile toProfile(CatalogEpisode episode, CatalogSeries series, CatalogSeason season,
            MoviePresentationLookup.Presentation presentation, GenreTags genres,
            Set<String> characters, SeriesArtwork fallback, boolean hasSegments) {
        Long duration = EpisodeRuntime.known(episode.getDurationMs());
        String description = episode.getSynopsis();
        int summaryQuality = (description == null || description.isBlank() ? 0 : 1) + (hasSegments ? 1 : 0);
        if (description == null || description.isBlank()) {
            description = CatalogDisplayText.episode(episode.getNumber(), episode.getTitle());
        }
        SeriesArtwork artwork = presentation == null ? fallback
                : fallback.fillMissing(presentation.poster(), presentation.backdrop());
        Integer year = episode.getReleaseDate() != null ? Integer.valueOf(episode.getReleaseDate().getYear())
                : season != null && season.getReleaseYearStart() != null ? season.getReleaseYearStart()
                        : series.getReleaseYearStart();
        return new EpisodeProfile(episode.getId(), episode.getTitle(), episode.getSeriesId(),
                episode.getSeasonId(), episode.getNumber(), year,
                presentation == null ? 0 : presentation.rating(),
                EpisodeRuntime.label(duration), artwork.posterUrl(), artwork.backdropUrl(),
                description, List.copyOf(genres.ids()), List.copyOf(genres.searchTerms()),
                JsonText.stringList(episode.getThemesJson()), JsonText.stringList(episode.getKeywordsJson()),
                List.copyOf(characters), duration, series.getTitle(),
                season == null ? null : season.getNumber(), summaryQuality);
    }

    private Map<String, GenreTags> genres(String query) {
        Map<String, GenreTags> values = new HashMap<>();
        for (Object[] row : entities.createQuery(query, Object[].class).getResultList()) {
            values.computeIfAbsent((String) row[0], id -> new GenreTags()).add((CatalogGenre) row[1]);
        }
        return values;
    }

    private Map<String, Set<String>> characters() {
        Map<String, Set<String>> values = new HashMap<>();
        for (Object[] row : entities.createQuery("""
                select link.episodeId, character from CatalogEpisodeCharacter link
                join CatalogCharacter character on character.id = link.characterId order by link.characterId
                """, Object[].class).getResultList()) {
            CatalogCharacter character = (CatalogCharacter) row[1];
            Set<String> names = values.computeIfAbsent((String) row[0], id -> new LinkedHashSet<>());
            names.add(character.getName());
            names.addAll(CatalogSearchNames.aliases(character.getAliasesJson()));
        }
        return values;
    }

    // Nhóm mã thể loại cùng toàn bộ từ khóa tìm kiếm mở rộng của một tập hoặc bộ phim.
    private record GenreTags(Set<String> ids, Set<String> searchTerms) {

        private static final GenreTags EMPTY = new GenreTags(Set.of(), Set.of());

        private GenreTags() {
            this(new LinkedHashSet<>(), new LinkedHashSet<>());
        }

        void add(CatalogGenre genre) {
            ids.add(genre.getId());
            searchTerms.addAll(genre.allSearchTerms());
        }
    }
}
