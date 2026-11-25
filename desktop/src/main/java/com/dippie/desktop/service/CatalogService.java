package com.dippie.desktop.service;

import com.dippie.desktop.dao.EpisodeAssetDao;
import com.dippie.desktop.dao.EpisodeDao;
import com.dippie.desktop.dao.MediaAssetDao;
import com.dippie.desktop.dao.RatingDao;
import com.dippie.desktop.dao.SeasonDao;
import com.dippie.desktop.dao.SeriesDao;
import com.dippie.desktop.db.JdbcDatabase;
import com.dippie.desktop.model.SearchText;
import com.dippie.desktop.model.catalog.CatalogRows.Episode;
import com.dippie.desktop.model.catalog.CatalogRows.MediaAsset;
import com.dippie.desktop.model.catalog.CatalogRows.Season;
import com.dippie.desktop.model.catalog.EpisodeNode;
import com.dippie.desktop.model.catalog.SeasonNode;
import com.dippie.desktop.model.catalog.SeriesNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

// Service chỉ đọc, tải các bảng danh mục rồi lắp thành cây Composite (bộ, mùa, tập) để hiển thị và lọc.
public final class CatalogService {
    private final JdbcDatabase db;
    private final SeriesDao seriesDao;
    private final SeasonDao seasonDao;
    private final EpisodeDao episodeDao;
    private final MediaAssetDao mediaAssetDao;
    private final EpisodeAssetDao episodeAssetDao;
    private final RatingDao ratingDao;

    public CatalogService(JdbcDatabase db, SeriesDao seriesDao, SeasonDao seasonDao, EpisodeDao episodeDao,
                          MediaAssetDao mediaAssetDao, EpisodeAssetDao episodeAssetDao, RatingDao ratingDao) {
        this.db = db;
        this.seriesDao = seriesDao;
        this.seasonDao = seasonDao;
        this.episodeDao = episodeDao;
        this.mediaAssetDao = mediaAssetDao;
        this.episodeAssetDao = episodeAssetDao;
        this.ratingDao = ratingDao;
    }

    // Đọc mỗi bảng một lượt trong cùng kết nối rồi gom bằng Map theo khóa ngoại, tránh truy vấn lặp theo từng bộ hay từng tập.
    public List<SeriesNode> load() {
        return db.read(connection -> {
            var seasonsBySeries = seasonDao.findAll(connection).stream().collect(Collectors.groupingBy(Season::seriesId));
            var driveFiles = mediaAssetDao.findDriveAssets(connection).stream()
                    .collect(Collectors.toMap(MediaAsset::id, MediaAsset::driveFileId));
            Map<String, List<String>> driveSources = new HashMap<>();
            for (var link : episodeAssetDao.findAll(connection)) {
                String driveId = driveFiles.get(link.mediaAssetId());
                if (driveId != null) {
                    driveSources.computeIfAbsent(link.episodeId(), id -> new ArrayList<>()).add(driveId);
                }
            }
            var averages = ratingDao.summaries(connection).stream()
                    .collect(Collectors.toMap(RatingDao.Summary::movieId, RatingDao.Summary::average));
            var episodesBySeries = episodeDao.findAll(connection).stream().collect(Collectors.groupingBy(Episode::seriesId));

            return seriesDao.findAll(connection).stream().map(seriesRow -> new SeriesNode(
                    seriesRow.id(), seriesRow.title(), seriesRow.originalTitle(), seriesRow.synopsis(), averages.get(seriesRow.id()),
                    buildSeasons(seasonsBySeries.getOrDefault(seriesRow.id(), List.of()),
                            episodesBySeries.getOrDefault(seriesRow.id(), List.of()),
                            averages, driveSources))).toList();
        });
    }

    // Xếp tập và mùa theo số thứ tự (null đẩy xuống cuối), các tập chưa thuộc mùa nào thì gom vào nhóm Chưa xếp mùa.
    private static List<SeasonNode> buildSeasons(List<Season> seasons, List<Episode> episodes,
                                                 Map<String, Double> averages, Map<String, List<String>> driveSources) {
        Map<String, List<EpisodeNode>> episodesBySeason = new HashMap<>();
        var byEpisodeNumber = Comparator.comparing(Episode::number, Comparator.nullsLast(Comparator.naturalOrder()));
        for (Episode episode : episodes.stream().sorted(byEpisodeNumber).toList()) {
            List<String> sources = new ArrayList<>();
            YoutubeSources.findUrl(episode.recordJson()).ifPresent(sources::add);
            for (String driveId : driveSources.getOrDefault(episode.id(), List.of()).stream().distinct().toList()) {
                if (driveId.matches("[A-Za-z0-9_-]+")) {
                    sources.add("https://drive.google.com/file/d/" + driveId + "/view");
                }
            }
            var episodeNode = new EpisodeNode(episode.id(), episode.title(), episode.synopsis(), episode.durationMs(),
                    averages.get(episode.id()), sources);
            episodesBySeason.computeIfAbsent(episode.seasonId(), id -> new ArrayList<>()).add(episodeNode);
        }

        List<SeasonNode> seasonNodes = new ArrayList<>();
        var bySeasonNumber = Comparator.comparing(Season::number, Comparator.nullsLast(Comparator.naturalOrder()));
        for (Season season : seasons.stream().sorted(bySeasonNumber).toList()) {
            var children = episodesBySeason.remove(season.id());
            seasonNodes.add(new SeasonNode(season.id(), season.title(), season.synopsis(),
                    children == null ? List.of() : children));
        }

        var unassigned = episodesBySeason.values().stream().flatMap(List::stream).toList();
        if (!unassigned.isEmpty()) {
            seasonNodes.add(new SeasonNode(null, "Chưa xếp mùa", null, unassigned));
        }
        return List.copyOf(seasonNodes);
    }

    // Lọc mà không làm thay đổi cây gốc, luôn giữ lại nhánh bộ và mùa phía trên kết quả khớp.
    public static List<SeriesNode> filter(List<SeriesNode> tree, String query) {
        String term = SearchText.normalize(query);
        if (term.isEmpty()) {
            return tree;
        }
        return tree.stream().map(node -> node.filter(term)).filter(Objects::nonNull).toList();
    }
}
