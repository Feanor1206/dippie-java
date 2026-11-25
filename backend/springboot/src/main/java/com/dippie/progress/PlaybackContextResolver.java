package com.dippie.progress;

import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogEpisodeRepository;
import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.catalog.domain.CatalogMediaAssetRepository;
import com.dippie.catalog.domain.CatalogSeason;
import com.dippie.catalog.domain.CatalogSeasonRepository;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.catalog.domain.CatalogSeriesRepository;
import com.dippie.catalog.domain.PublicationStatus;
import com.dippie.catalog.query.YoutubeSource;
import com.dippie.media.MediaAsset;
import com.dippie.media.MediaAssetRepository;
import com.dippie.media.MediaFileResolver;
import com.dippie.security.Viewer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

// Kiểm tra tập phim và nguồn phát (YouTube hoặc file nội bộ) có đủ điều kiện mở cho người dùng hiện tại không.
// Mình cố tình tách bước đọc DB (loadCandidate/validateRow) khỏi bước kiểm tra file trên đĩa (verifyPlayable) để không giữ giao dịch DB khi chạm ổ cứng.
@Component
class PlaybackContextResolver {

    private static final String VIDEO_MIME_PREFIX = "video/";

    private final CatalogEpisodeRepository episodeRepository;
    private final CatalogSeriesRepository seriesRepository;
    private final CatalogSeasonRepository seasonRepository;
    private final CatalogMediaAssetRepository catalogMediaAssetRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaFileResolver mediaFiles;

    public PlaybackContextResolver(CatalogEpisodeRepository episodeRepository, CatalogSeriesRepository seriesRepository,
            CatalogSeasonRepository seasonRepository, CatalogMediaAssetRepository catalogMediaAssetRepository,
            MediaAssetRepository mediaAssetRepository, MediaFileResolver mediaFiles) {
        this.episodeRepository = episodeRepository;
        this.seriesRepository = seriesRepository;
        this.seasonRepository = seasonRepository;
        this.catalogMediaAssetRepository = catalogMediaAssetRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.mediaFiles = mediaFiles;
    }

    // Dùng sealed interface của Java 21 để ép mọi chỗ xử lý assetId phải rẽ đủ hai nhánh YouTube và file nội bộ.
    sealed interface PlaybackAssetId permits PlaybackAssetId.Youtube, PlaybackAssetId.File {

        static PlaybackAssetId parse(String assetId) {
            return assetId.startsWith(YoutubeSource.PROGRESS_PREFIX) ? new Youtube(assetId) : new File(assetId);
        }

        // Mã nguồn phát dạng youtube:<videoId>.
        record Youtube(String rawId) implements PlaybackAssetId {}

        // File video cục bộ trỏ theo ID trong bảng media_assets.
        record File(String rawId) implements PlaybackAssetId {}
    }

    // Kết quả sau bước kiểm tra DB, giữ kèm fileToVerify để bước gọi ngoài giao dịch xác minh tệp trên đĩa nếu cần.
    record PendingPlayback(PlaybackContext context, MediaAsset fileToVerify) {}

    // Tải đủ tập, bộ, mùa và nguồn video từ DB rồi kiểm tra quyền truy cập của người xem.
    Optional<PendingPlayback> loadCandidate(String episodeId, String assetId, Viewer viewer) {
        CatalogEpisode episode = episodeRepository.findById(episodeId).orElse(null);
        CatalogSeries series = episode == null ? null : seriesRepository.findById(episode.getSeriesId()).orElse(null);
        CatalogSeason season = episode == null || episode.getSeasonId() == null ? null
                : seasonRepository.findById(episode.getSeasonId()).orElse(null);
        PlaybackAssetId parsed = PlaybackAssetId.parse(assetId);
        CatalogMediaAsset catalogAsset = parsed instanceof PlaybackAssetId.File file
                ? catalogMediaAssetRepository.findById(file.rawId()).orElse(null) : null;
        MediaAsset mediaAsset = parsed instanceof PlaybackAssetId.File file
                ? mediaAssetRepository.findById(file.rawId()).orElse(null) : null;
        return validateMetadata(episode, series, season, catalogAsset, mediaAsset, parsed, viewer);
    }

    // Dùng lại dữ liệu đã join sẵn trong ContextRow của listRecent để đỡ tốn thêm câu query lẻ cho từng dòng.
    Optional<PendingPlayback> validateRow(WatchProgressRepository.ContextRow row, Viewer viewer) {
        return validateMetadata(row.episode(), row.series(), row.season(), row.catalogAsset(), row.mediaAsset(),
                PlaybackAssetId.parse(row.progress().getAssetId()), viewer);
    }

    // Tìm các tệp video nội bộ mà user được xem của tập đó (dùng khi cần kiểm tra tập có file thay thế YouTube hay không).
    List<MediaAsset> findCandidateVideoFiles(String episodeId, String assetId, Viewer viewer) {
        if (!(PlaybackAssetId.parse(assetId) instanceof PlaybackAssetId.Youtube)) {
            return List.of();
        }
        List<String> ids = catalogMediaAssetRepository.findByEpisodeId(episodeId).stream()
                .filter(asset -> canPlayFile(asset, episodeId, viewer))
                .map(CatalogMediaAsset::getId)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return mediaAssetRepository.findAllById(ids).stream()
                .filter(PlaybackContextResolver::isVideo)
                .toList();
    }

    // Chạy ngoài giao dịch DB: nếu tập không cho nhúng YouTube và dựa vào file nội bộ thì kiểm tra file còn nằm trên đĩa.
    Optional<PlaybackContext> verifyPlayable(PendingPlayback pending) {
        if (pending.fileToVerify() != null && mediaFiles.resolve(pending.fileToVerify()).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(pending.context());
    }

    // Kiểm tra xem có ít nhất một tệp ứng viên thực sự tồn tại trên ổ đĩa hay không.
    boolean anyFileExists(List<MediaAsset> candidates) {
        return candidates.stream().anyMatch(asset -> mediaFiles.resolve(asset).isPresent());
    }

    // Kiểm tra cả chuỗi tập, bộ và mùa đều đã xuất bản, sau đó rẽ nhánh bằng switch pattern matching theo loại nguồn phát.
    private Optional<PendingPlayback> validateMetadata(
            CatalogEpisode episode,
            CatalogSeries series,
            CatalogSeason season,
            CatalogMediaAsset catalogAsset,
            MediaAsset mediaAsset,
            PlaybackAssetId assetId,
            Viewer viewer
    ) {
        // Chỉ cần một mắt xích (tập, bộ hoặc mùa) đang là nháp thì coi như không phát được
        if (episode == null || !PublicationStatus.of(episode.getRecordJson()).isPublished()) {
            return Optional.empty();
        }
        if (series == null || !PublicationStatus.of(series.getRecordJson()).isPublished()) {
            return Optional.empty();
        }
        if (episode.getSeasonId() != null && !episode.getSeasonId().isEmpty()) {
            if (season == null || !PublicationStatus.of(season.getRecordJson()).isPublished()) {
                return Optional.empty();
            }
        }
        // Khớp nguồn phát theo nhánh YouTube hoặc file nội bộ; chỉ cần kiểm tra file trên đĩa khi tập đã tắt nhúng YouTube
        return switch (assetId) {
            case PlaybackAssetId.Youtube youtube -> YoutubeSource.matchesProgressAsset(episode.getRawJson(), youtube.rawId())
                    ? Optional.of(new PendingPlayback(new PlaybackContext(episode, series, season, youtube.rawId(), null), null))
                    : Optional.empty();
            case PlaybackAssetId.File ignored -> {
                if (!canPlayFile(catalogAsset, episode.getId(), viewer) || !isVideo(mediaAsset)) {
                    yield Optional.empty();
                }
                MediaAsset toVerify = YoutubeSource.embeddingDisabled(episode.getRawJson()) ? mediaAsset : null;
                yield Optional.of(new PendingPlayback(
                        new PlaybackContext(episode, series, season, catalogAsset.getId(), catalogAsset.getDurationMs()),
                        toVerify));
            }
        };
    }

    // File nội bộ hợp lệ khi đúng quyền truy cập của viewer, có vai trò video tập phim và phủ trọn tập (không tính bản cắt partial).
    private static boolean canPlayFile(CatalogMediaAsset asset, String episodeId, Viewer viewer) {
        return asset != null && asset.isAccessibleBy(viewer != null, viewer != null && viewer.isAdmin())
                && asset.isEpisodeVideo() && asset.coversFullEpisode(episodeId);
    }

    private static boolean isVideo(MediaAsset asset) {
        return asset != null && asset.isAvailable() && asset.getMimeType() != null
                && asset.getMimeType().toLowerCase(Locale.ROOT).startsWith(VIDEO_MIME_PREFIX);
    }
}
