package com.dippie.catalog.query;

import com.dippie.catalog.domain.CatalogGenre;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.catalog.domain.SeriesArtwork;
import com.dippie.catalog.query.CatalogReadModel.SeriesEpisode;
import com.dippie.common.json.JsonText;
import com.dippie.common.rating.RatingSummary;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Thẻ tóm tắt bất biến cho trang duyệt bộ phim, gom sẵn số mùa, số tập công khai, thể loại, ảnh bìa và điểm đánh giá.
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

    // Dựng thẻ từ bộ phim và các tập đã xuất bản; nếu bộ phim chưa có ảnh riêng thì lấy tạm ảnh của tập sớm nhất theo thứ tự mùa/tập.
    static SeriesCard from(CatalogSeries series, List<SeriesEpisode> episodes, Map<String, Genre> genresById) {
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

    SeriesCard withRatings(RatingSummary summary) {
        return new SeriesCard(id, title, originalTitle, synopsis, releaseYearStart, releaseYearEnd, genres,
                seasonCount, episodeCount, posterUrl, backdropUrl, summary);
    }

    // Mã thể loại đi kèm nhãn hiển thị rút gọn trên thẻ phim.
    public record Genre(String id, String label) {
        static Genre from(CatalogGenre genre) {
            return new Genre(genre.getId(), genre.preferredLabel());
        }
    }
}
