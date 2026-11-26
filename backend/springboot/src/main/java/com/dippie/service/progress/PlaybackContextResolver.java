package com.dippie.service.progress;

import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogMediaAsset;
import com.dippie.model.catalog.CatalogSeason;
import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.catalog.PublicationStatus;
import com.dippie.model.catalog.YoutubeSource;
import com.dippie.model.media.MediaAsset;
import com.dippie.model.progress.PlaybackContext;
import com.dippie.repository.catalog.CatalogEpisodeRepository;
import com.dippie.repository.catalog.CatalogMediaAssetRepository;
import com.dippie.repository.catalog.CatalogSeasonRepository;
import com.dippie.repository.catalog.CatalogSeriesRepository;
import com.dippie.repository.media.MediaAssetRepository;
import com.dippie.repository.progress.WatchProgressRepository;
import com.dippie.security.Viewer;
import com.dippie.service.media.MediaFileResolver;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

// check tập phim với nguồn phát (YouTube hay file nội bộ) có đủ điều kiện mở cho user hiện tại không
// tách đọc DB (loadCandidate/validateRow) khỏi check file (verifyPlayable) để khỏi ngâm transaction lúc đụng ổ cứng
@Component
public class PlaybackContextResolver {

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

    // xài sealed interface của Java 21 để ép chỗ nào xử lý assetId cũng phải rẽ đủ 2 nhánh YouTube với file nội bộ
    public sealed interface PlaybackAssetId permits PlaybackAssetId.Youtube, PlaybackAssetId.File {

        static PlaybackAssetId parse(String assetId) {
            return assetId.startsWith(YoutubeSource.PROGRESS_PREFIX) ? new Youtube(assetId) : new File(assetId);
        }

        // mã nguồn YouTube dạng youtube:<videoId>
        record Youtube(String rawId) implements PlaybackAssetId {}

        // file video nội bộ trỏ theo ID ở bảng media_assets
        record File(String rawId) implements PlaybackAssetId {}
    }

    // kết quả sau bước check DB, kẹp thêm fileToVerify để ra ngoài transaction check file trên đĩa nếu cần
    public record PendingPlayback(PlaybackContext context, MediaAsset fileToVerify) {}

    // kéo đủ tập, bộ, mùa với nguồn video từ DB rồi check quyền xem
    public Optional<PendingPlayback> loadCandidate(String episodeId, String assetId, Viewer viewer) {
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

    // xài lại dữ liệu đã join sẵn trong ContextRow của listRecent cho đỡ tốn query lẻ từng dòng
    public Optional<PendingPlayback> validateRow(WatchProgressRepository.ContextRow row, Viewer viewer) {
        return validateMetadata(row.episode(), row.series(), row.season(), row.catalogAsset(), row.mediaAsset(),
                PlaybackAssetId.parse(row.progress().getAssetId()), viewer);
    }

    // tìm mấy file video nội bộ của tập mà user được xem, dùng lúc cần biết tập có file dự phòng thay YouTube không
    public List<MediaAsset> findCandidateVideoFiles(String episodeId, String assetId, Viewer viewer) {
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

    // chạy ngoài transaction DB, tập nào tắt nhúng YouTube mà dựa vào file nội bộ thì check file còn trên đĩa không
    public Optional<PlaybackContext> verifyPlayable(PendingPlayback pending) {
        if (pending.fileToVerify() != null && mediaFiles.resolve(pending.fileToVerify()).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(pending.context());
    }

    // check xem có ít nhất 1 file ứng viên thật sự còn trên đĩa không
    public boolean anyFileExists(List<MediaAsset> candidates) {
        return candidates.stream().anyMatch(asset -> mediaFiles.resolve(asset).isPresent());
    }

    // check đủ tập, bộ với mùa đã xuất bản, rồi rẽ nhánh bằng switch pattern matching theo loại nguồn phát
    private Optional<PendingPlayback> validateMetadata(
            CatalogEpisode episode,
            CatalogSeries series,
            CatalogSeason season,
            CatalogMediaAsset catalogAsset,
            MediaAsset mediaAsset,
            PlaybackAssetId assetId,
            Viewer viewer
    ) {
        // chỉ cần 1 mắt xích (tập, bộ hay mùa) còn là nháp thì coi như không phát được
        if (episode == null || !PublicationStatus.of(episode.getRecordJson()).isPublished()) {
            return Optional.empty();
        }
        if (series == null || !PublicationStatus.of(series.getRecordJson()).isPublished()) {
            return Optional.empty();
        }
        if (episode.getSeasonId() != null && !episode.getSeasonId().isEmpty()) {
            if (season == null || !episode.getSeriesId().equals(season.getSeriesId())
                    || !PublicationStatus.of(season.getRecordJson()).isPublished()) {
                return Optional.empty();
            }
        }
        // khớp nguồn phát theo nhánh YouTube hay file nội bộ, chỉ cần check file trên đĩa khi tập đã tắt nhúng YouTube
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

    // file nội bộ chỉ hợp lệ khi đúng quyền của viewer, là video tập phim và phủ trọn tập (bỏ qua mấy bản cắt partial)
    private static boolean canPlayFile(CatalogMediaAsset asset, String episodeId, Viewer viewer) {
        return asset != null && asset.isAccessibleBy(viewer != null, viewer != null && viewer.isAdmin())
                && asset.isEpisodeVideo() && asset.coversFullEpisode(episodeId);
    }

    private static boolean isVideo(MediaAsset asset) {
        return asset != null && asset.isAvailable() && asset.getMimeType() != null
                && asset.getMimeType().toLowerCase(Locale.ROOT).startsWith("video/");
    }
}
