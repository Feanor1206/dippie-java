package com.dippie.dto.catalog;

import com.dippie.common.json.JsonText;
import com.dippie.common.rating.RatingSummary;
import com.dippie.model.catalog.CatalogGenre;
import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.catalog.SeriesArtwork;
import com.dippie.model.catalog.SeriesEpisode;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// thẻ phim bộ cho trang duyệt, gom sẵn số mùa, số tập công khai, thể loại, ảnh bìa với điểm
public record SeriesCard(String id, String title, String originalTitle, String synopsis, Integer releaseYearStart,
        Integer releaseYearEnd, List<Genre> genres, int seasonCount, int episodeCount, String posterUrl,
        String backdropUrl, @JsonUnwrapped RatingSummary ratings) {

    public SeriesCard {
        genres = genres == null ? List.of() : List.copyOf(genres);
    }

    private static final Comparator<Integer> NUMBER_ORDER = Comparator.nullsLast(Comparator.naturalOrder());
    private static final Comparator<SeriesEpisode> EPISODE_ORDER = Comparator
            .comparing(SeriesEpisode::seasonNumber, NUMBER_ORDER)
            .thenComparing(SeriesEpisode::episodeNumber, NUMBER_ORDER)
            .thenComparing(SeriesEpisode::id);

    // dựng thẻ từ bộ phim với mấy tập đã đăng, thiếu ảnh bộ thì mượn tạm ảnh tập sớm nhất theo mùa/tập
    public static SeriesCard from(CatalogSeries series, List<SeriesEpisode> episodes, Map<String, Genre> genresById) {
        List<Genre> genres = JsonText.stringList(series.getGenreIdsJson()).stream()
                .map(genresById::get).filter(Objects::nonNull).toList();
        int seasonCount = Math.toIntExact(episodes.stream().map(SeriesEpisode::seasonId).filter(Objects::nonNull)
                .distinct().count());
        SeriesArtwork youtube = episodes.stream().filter(episode -> episode.artwork().posterUrl() != null)
                .min(EPISODE_ORDER).map(SeriesEpisode::artwork).orElse(SeriesArtwork.EMPTY);
        SeriesArtwork artwork = SeriesArtwork.fromSeries(series.getArtworksJson(), youtube);
        return new SeriesCard(series.getId(), series.getTitle(), series.getOriginalTitle(), series.getSynopsis(),
                series.getReleaseYearStart(), series.getReleaseYearEnd(), genres, seasonCount, episodes.size(),
                artwork.posterUrl(), artwork.backdropUrl(), RatingSummary.EMPTY);
    }

    public SeriesArtwork artwork() {
        return new SeriesArtwork(posterUrl, backdropUrl);
    }

    public SeriesCard withRatings(RatingSummary summary) {
        return new SeriesCard(id, title, originalTitle, synopsis, releaseYearStart, releaseYearEnd, genres,
                seasonCount, episodeCount, posterUrl, backdropUrl, summary);
    }

    // mã thể loại kèm nhãn ngắn hiện trên thẻ phim
    public record Genre(String id, String label) {
        public static Genre from(CatalogGenre genre) {
            return new Genre(genre.getId(), genre.preferredLabel());
        }
    }
}
