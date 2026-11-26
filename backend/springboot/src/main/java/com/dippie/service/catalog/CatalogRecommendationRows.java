package com.dippie.service.catalog;

import com.dippie.common.json.JsonText;
import com.dippie.model.catalog.CatalogCharacter;
import com.dippie.model.catalog.CatalogDisplayText;
import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogGenre;
import com.dippie.model.catalog.CatalogSeason;
import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.catalog.EpisodeRuntime;
import com.dippie.model.catalog.SeriesArtwork;
import com.dippie.service.catalog.CatalogReadModel.EpisodeProfile;

import jakarta.persistence.EntityManager;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

// kéo dữ liệu theo lô bằng batch JPQL để dựng EpisodeProfile cho bộ gợi ý, khỏi bị N+1 query từng tập một
public final class CatalogRecommendationRows {

    private final EntityManager entities;
    private final MoviePresentationLookup moviePresentations;

    public CatalogRecommendationRows(EntityManager entities, MoviePresentationLookup moviePresentations) {
        this.entities = entities;
        this.moviePresentations = moviePresentations;
    }

    // nạp sẵn thông tin trình chiếu, thể loại với nhân vật theo lô rồi ghép thành hồ sơ gợi ý cho mấy tập đủ điều kiện
    public List<EpisodeProfile> load(List<CatalogEpisode> episodes, Map<String, CatalogSeries> series,
            Map<String, CatalogSeason> seasons, Predicate<CatalogEpisode> isEligible,
            Function<String, SeriesArtwork> artworkById, Set<String> episodesWithSegments) {
        // lọc trước mấy tập đạt chuẩn rồi kéo 1 lượt rating với ảnh từ bảng trình chiếu
        var eligible = episodes.stream().filter(isEligible).toList();
        if (eligible.isEmpty()) {
            return List.of();
        }
        Map<String, MoviePresentationLookup.Presentation> movies = moviePresentations.findForReadyEpisodes();

        // gom thể loại của tập, bộ phim với nhân vật từng tập trong đúng 3 câu JPQL thôi
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

    // chấm summaryQuality (0 tới 2) tùy tập đã có tóm tắt với phân đoạn chưa, năm phát hành thì ưu tiên tập, mùa rồi mới tới bộ
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

    // gom mã thể loại kèm hết từ khóa tìm kiếm mở rộng của 1 tập hay bộ phim
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
