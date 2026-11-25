package com.dippie.catalog.query;

import com.dippie.catalog.domain.CatalogSeriesView;
import com.dippie.catalog.domain.MediaFileAvailability;
import com.dippie.catalog.domain.SeriesArtwork;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// Bản chụp bất biến của toàn bộ danh mục trong bộ nhớ, tách riêng khỏi các bảng ghi JPA để request đọc của người dùng chạy nhanh không cần join lại CSDL.
// Mọi collection bên trong đều được sao chép bất biến lúc dựng nên nhiều luồng đọc đồng thời không cần khóa.
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

    CatalogReadModel(List<CatalogSeriesView> series, Set<String> visibleSeriesIds,
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

    // Quản trị viên thấy cả bộ phim nháp/ẩn, còn người xem thường chỉ nhận các bộ phim đã xuất bản.
    List<CatalogSeriesView> series(boolean admin) {
        return admin ? series : series.stream().filter(row -> visibleSeriesIds.contains(row.id())).toList();
    }

    public Set<String> visibleSeriesIds() {
        return visibleSeriesIds;
    }

    Set<String> visibleEpisodeIds() {
        return visibleEpisodeIds;
    }

    public Set<String> hiddenMovieIds() {
        return hiddenMovieIds;
    }

    CatalogSearchService.SearchIndex searchIndex() {
        return searchIndex;
    }

    // Gom mức khớp cao nhất cho từng mã bộ phim hoặc tập phim khi lọc danh sách phim theo từ khóa.
    public Map<String, SearchRank> movieSearchRanks(String query) {
        return searchIndex.ranked(query, false, false)
                .filter(row -> row.hit().isTitle())
                .collect(Collectors.toMap(row -> row.hit().id(), CatalogSearchService.RankedRow::rank,
                        (first, second) -> first.compareTo(second) <= 0 ? first : second));
    }

    // Thứ tự khai báo enum cũng là thứ tự ưu tiên sắp xếp (từ khớp chính xác tên bộ phim giảm dần tới khớp mờ).
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

    // Bảng externalEpisodeAssetIds chỉ lưu mấy tập bị khóa nhúng YouTube, nên Việt đừng tưởng nó chứa hết mọi tập nhé.
    // Tập bình thường không có trong map sẽ qua luôn, còn tập bị khóa nhúng phải có ít nhất một tệp video công khai trên ổ đĩa mới được lên khám phá và gợi ý.
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

    List<SeriesCard> seriesCards() {
        return seriesCards;
    }

    public SeriesCard seriesCard(String seriesId) {
        return cardsById.get(seriesId);
    }

    public SeriesEpisode episode(String episodeId) {
        return episodesById.get(episodeId);
    }

    // Lấy ảnh đại diện riêng của tập, nếu tập chưa có thì lùi về dùng poster của bộ phim cha.
    public String episodeThumbnail(String episodeId, String seriesId) {
        SeriesEpisode episode = episode(episodeId);
        SeriesCard series = seriesCard(seriesId);
        return Objects.toString(episode == null ? null : episode.artwork().posterUrl(),
                series == null ? null : series.posterUrl());
    }

    // Thông tin rút gọn của một tập phim dùng cho thẻ bộ phim và màn chi tiết.
    public record SeriesEpisode(String id, String title, Integer seasonNumber, Integer episodeNumber, Long durationMs,
            boolean memberAccessible, String seriesId, String seasonId, SeriesArtwork artwork) {
    }

    // Hồ sơ tổng hợp của một tập phim để nạp thẳng vào bộ tính điểm gợi ý.
    public record EpisodeProfile(
            String id, String title, String seriesId, String seasonId, Integer episodeNumber, Integer year,
            double rating, String runtime, String poster, String backdrop, String description,
            List<String> genres, List<String> genreTerms, List<String> themes, List<String> keywords,
            List<String> characters, Long durationMs, String seriesTitle, Integer seasonNumber, int summaryQuality
    ) {
    }

    // Cặp mã thể loại kèm tên hiển thị tiếng Việt và tiếng Anh.
    public record GenreEntry(String id, String viLabel, String enLabel) {
    }
}
