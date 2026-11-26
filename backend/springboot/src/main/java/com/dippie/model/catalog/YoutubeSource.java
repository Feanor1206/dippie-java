package com.dippie.model.catalog;

import com.dippie.common.json.JsonText;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.regex.Pattern;

// Mã video YouTube của tập phim kèm cờ cho phép nhúng trình phát.
// Khi đọc mảng external_ids, lớp ưu tiên theo thứ tự youtube_playback (nhúng được), youtube_watch (chỉ mở ngoài) rồi mới tới youtube_video_id.
public record YoutubeSource(String videoId, boolean canEmbed) {

    public static final String PROGRESS_PREFIX = "youtube:";
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final String PLAYBACK_NAMESPACE = "youtube_playback";
    private static final String WATCH_NAMESPACE = "youtube_watch";
    private static final String THUMBNAIL_ONLY_NAMESPACE = "youtube_video_id";

    // Đọc một lượt ảnh bìa, trạng thái khóa nhúng và tên gốc từ rawJson của tập phim để dùng khi dựng CatalogReadModel.
    public static EpisodeMetadata parseEpisodeMetadata(String episodeJson) {
        JsonNode root = JsonText.parse(episodeJson).orElse(null);
        if (root == null) {
            return EpisodeMetadata.EMPTY;
        }
        String originalTitle = root.path("original_title").textValue();
        boolean hasPrimaryMediaAsset = !root.path("primary_media_asset_id").asText("").isEmpty();
        ParsedIds ids = parseExternalIds(root.path("external_ids"));
        String artworkId = ids.playbackId() != null ? ids.playbackId()
                : ids.watchId() != null ? ids.watchId() : ids.thumbnailId();
        SeriesArtwork artwork = artworkId == null ? SeriesArtwork.EMPTY : artworkFor(artworkId);
        boolean embeddingDisabled = ids.playbackId() == null && ids.watchId() != null;
        return new EpisodeMetadata(artwork, embeddingDisabled, originalTitle, hasPrimaryMediaAsset);
    }

    // Tách nguồn chính và nguồn dự phòng: chỉ trả fallback từ youtube_video_id khi tập không có cả playback lẫn watch.
    public static Detail resolveDetail(String episodeJson) {
        JsonNode root = JsonText.parse(episodeJson).orElse(null);
        if (root == null) {
            return new Detail(null, null);
        }
        ParsedIds ids = parseExternalIds(root.path("external_ids"));
        YoutubeSource primary = ids.playbackId() != null ? new YoutubeSource(ids.playbackId(), true)
                : ids.watchId() != null ? new YoutubeSource(ids.watchId(), false) : null;
        YoutubeSource fallback = primary == null && ids.thumbnailId() != null
                ? new YoutubeSource(ids.thumbnailId(), true) : null;
        return new Detail(primary, fallback);
    }

    public static SeriesArtwork artworkFrom(String episodeJson) {
        return parseEpisodeMetadata(episodeJson).artwork();
    }

    public static YoutubeSource from(String episodeJson) {
        return resolveDetail(episodeJson).primary();
    }

    // Kiểm tra mã asset lưu tiến độ xem (dạng "youtube:<videoId>") có khớp đúng nguồn nhúng được của tập không.
    public static boolean matchesProgressAsset(String episodeJson, String assetId) {
        YoutubeSource source = embeddableFrom(episodeJson);
        return source != null && assetId.equals(PROGRESS_PREFIX + source.videoId());
    }

    // Việt đừng sửa nhánh này thành fallback khi primary có canEmbed = false: tập đã gắn youtube_watch nghĩa là bị khóa nhúng, nên cố tình trả null.
    public static YoutubeSource embeddableFrom(String episodeJson) {
        Detail detail = resolveDetail(episodeJson);
        if (detail.primary() == null) {
            return detail.fallback();
        }
        return detail.primary().canEmbed() ? detail.primary() : null;
    }

    public static boolean embeddingDisabled(String episodeJson) {
        YoutubeSource source = from(episodeJson);
        return source != null && !source.canEmbed();
    }

    private static ParsedIds parseExternalIds(JsonNode externalIds) {
        if (externalIds == null || !externalIds.isArray()) {
            return ParsedIds.EMPTY;
        }
        String playbackId = null;
        String watchId = null;
        String thumbnailId = null;
        for (JsonNode item : externalIds) {
            String id = validVideoId(item);
            if (id == null) {
                continue;
            }
            String namespace = item.path("namespace").textValue();
            if (playbackId == null && PLAYBACK_NAMESPACE.equals(namespace)) {
                playbackId = id;
            } else if (watchId == null && WATCH_NAMESPACE.equals(namespace)) {
                watchId = id;
            } else if (thumbnailId == null && THUMBNAIL_ONLY_NAMESPACE.equals(namespace)) {
                thumbnailId = id;
            }
        }
        return new ParsedIds(playbackId, watchId, thumbnailId);
    }

    private static String validVideoId(JsonNode item) {
        String id = item.path("value").textValue();
        return id != null && VIDEO_ID.matcher(id).matches() ? id : null;
    }

    private static SeriesArtwork artworkFor(String videoId) {
        String base = "https://i.ytimg.com/vi/" + videoId + "/";
        return new SeriesArtwork(base + "hqdefault.jpg", base + "maxresdefault.jpg");
    }

    // Ba mã video YouTube đầu tiên hợp lệ tìm thấy cho từng namespace trong external_ids.
    private record ParsedIds(String playbackId, String watchId, String thumbnailId) {
        private static final ParsedIds EMPTY = new ParsedIds(null, null, null);
    }

    // Phần siêu dữ liệu YouTube bóc sẵn từ JSON của tập phim cho CatalogReadModelLoader.
    public record EpisodeMetadata(SeriesArtwork artwork, boolean embeddingDisabled, String originalTitle,
            boolean hasPrimaryMediaAsset) {
        public static final EpisodeMetadata EMPTY = new EpisodeMetadata(SeriesArtwork.EMPTY, false, null, false);
    }

    // Cặp nguồn YouTube chính và dự phòng gửi ra ở API chi tiết tập phim.
    public record Detail(YoutubeSource primary, YoutubeSource fallback) {
    }
}
