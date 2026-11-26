package com.dippie.service.catalog;

import com.dippie.dto.catalog.CatalogSeriesView;
import com.dippie.dto.catalog.SeriesCard;
import com.dippie.model.catalog.SeriesEpisode;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// bản chụp bất biến của cả danh mục nằm sẵn trong RAM, tách hẳn bảng ghi JPA cho request đọc chạy nhanh khỏi join DB lại
// mấy collection bên trong copy bất biến hết lúc dựng nên nhiều luồng đọc cùng lúc không cần khóa
public final class CatalogReadModel {

    private final List<CatalogSeriesView> series;
    private final Set<String> visibleSeriesIds;
    private final Set<String> visibleEpisodeIds;
    private final Set<String> hiddenMovieIds;
    private final CatalogSearchService.SearchIndex searchIndex;
    private final List<EpisodeProfile> episodeProfiles;
    private final Map<String, List<SeriesEpisode>> seriesEpisodes;
    private final List<SeriesCard> seriesCards;
    private final Map<String, SeriesCard> cardsById;
    private final Map<String, SeriesEpisode> episodesById;
    private final Map<String, List<String>> externalEpisodeAssetIds;
    private final List<GenreEntry> genres;

    public CatalogReadModel(List<CatalogSeriesView> series, Set<String> visibleSeriesIds,
            Set<String> visibleEpisodeIds, Set<String> hiddenMovieIds,
            List<CatalogSearchService.SearchRow> searchRows, List<EpisodeProfile> episodeProfiles,
            Map<String, List<SeriesEpisode>> seriesEpisodes, List<SeriesCard> seriesCards,
            Map<String, SeriesEpisode> episodesById, Map<String, List<String>> externalEpisodeAssetIds,
            List<GenreEntry> genres) {
        this.series = List.copyOf(series);
        this.visibleSeriesIds = Set.copyOf(visibleSeriesIds);
        this.visibleEpisodeIds = Set.copyOf(visibleEpisodeIds);
        this.hiddenMovieIds = Set.copyOf(hiddenMovieIds);
        this.searchIndex = CatalogSearchService.SearchIndex.from(searchRows);
        this.episodeProfiles = List.copyOf(episodeProfiles);
        this.seriesEpisodes = seriesEpisodes.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        this.seriesCards = List.copyOf(seriesCards);
        this.cardsById = seriesCards.stream().collect(Collectors.toUnmodifiableMap(SeriesCard::id, Function.identity()));
        this.episodesById = Map.copyOf(episodesById);
        this.externalEpisodeAssetIds = externalEpisodeAssetIds.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        this.genres = List.copyOf(genres);
    }

    // admin thấy cả bộ nháp hay ẩn, còn người xem thường chỉ nhận mấy bộ đã xuất bản
    public List<CatalogSeriesView> series(boolean admin) {
        return admin ? series : series.stream().filter(row -> visibleSeriesIds.contains(row.id())).toList();
    }

    public Set<String> visibleSeriesIds() {
        return visibleSeriesIds;
    }

    public Set<String> visibleEpisodeIds() {
        return visibleEpisodeIds;
    }

    public Set<String> hiddenMovieIds() {
        return hiddenMovieIds;
    }

    public CatalogSearchService.SearchIndex searchIndex() {
        return searchIndex;
    }

    // gom mức khớp cao nhất cho từng mã bộ hay tập lúc lọc danh sách phim theo từ khóa
    public Map<String, SearchRank> movieSearchRanks(String query) {
        return searchIndex.ranked(query, false, false)
                .filter(row -> row.hit().titleLevel())
                .collect(Collectors.toMap(row -> row.hit().id(), CatalogSearchService.RankedRow::rank,
                        (first, second) -> first.compareTo(second) <= 0 ? first : second));
    }

    // thứ tự khai báo enum chính là thứ tự ưu tiên sort luôn (từ khớp chuẩn tên bộ phim xuống dần tới khớp mờ)
    public enum SearchRank {
        SERIES_EXACT, SERIES_PARTIAL, EXACT, SERIES_FUZZY, FUZZY;

        public boolean fuzzy() {
            return this == SERIES_FUZZY || this == FUZZY;
        }
    }

    public List<EpisodeProfile> episodeProfiles() {
        return episodeProfiles;
    }

    public List<GenreEntry> genres() {
        return genres;
    }

    // bảng externalEpisodeAssetIds chỉ lưu mấy tập bị khóa nhúng YouTube thôi, Việt đừng tưởng nó chứa hết mọi tập nha
    // tập thường không trong map thì qua luôn, tập khóa nhúng cần ít nhất 1 file video public ngoài đĩa mới lên khám phá với gợi ý
    public Predicate<String> playableFilter(MediaFileAvailability files) {
        if (externalEpisodeAssetIds.isEmpty()) {
            return id -> true;
        }
        return id -> {
            List<String> assetIds = externalEpisodeAssetIds.get(id);
            return assetIds == null || (!assetIds.isEmpty() && !files.availableAssetIds(assetIds).isEmpty());
        };
    }

    public List<SeriesEpisode> seriesEpisodes(String seriesId) {
        return seriesEpisodes.getOrDefault(seriesId, List.of());
    }

    public List<SeriesCard> seriesCards() {
        return seriesCards;
    }

    public SeriesCard seriesCard(String seriesId) {
        return cardsById.get(seriesId);
    }

    public SeriesEpisode episode(String episodeId) {
        return episodesById.get(episodeId);
    }

    // lấy ảnh riêng của tập, thiếu thì lùi về poster của bộ phim cha
    public String episodeThumbnail(String episodeId, String seriesId) {
        SeriesEpisode episode = episode(episodeId);
        SeriesCard series = seriesCard(seriesId);
        return Objects.toString(episode == null ? null : episode.artwork().posterUrl(),
                series == null ? null : series.posterUrl());
    }


    // hồ sơ tổng hợp của 1 tập phim để đẩy thẳng vào bộ chấm điểm gợi ý
    public record EpisodeProfile(
            String id, String title, String seriesId, String seasonId, Integer episodeNumber, Integer year,
            double rating, String runtime, String poster, String backdrop, String description,
            List<String> genres, List<String> genreTerms, List<String> themes, List<String> keywords,
            List<String> characters, Long durationMs, String seriesTitle, Integer seasonNumber, int summaryQuality
    ) {
    }

    // mã thể loại kèm nhãn hiển thị tiếng Việt với tiếng Anh
    public record GenreEntry(String id, String viLabel, String enLabel) {
    }
}
