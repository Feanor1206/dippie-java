package com.dippie.model.catalog;

import com.dippie.common.json.JsonText;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.regex.Pattern;

// mã video YouTube của tập kèm cờ có nhúng player được không
// đọc external_ids ưu tiên youtube_playback (nhúng được), tới youtube_watch (chỉ mở ngoài) rồi mới tới youtube_video_id
public record YoutubeSource(String videoId, boolean canEmbed) {

    public static final String PROGRESS_PREFIX = "youtube:";
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    // bóc một lượt ảnh bìa, cờ khóa nhúng với tên gốc từ rawJson để dựng CatalogReadModel
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

    // tách nguồn chính với dự phòng, chỉ lấy fallback từ youtube_video_id khi thiếu cả playback lẫn watch
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

    // check mã asset lưu tiến độ ("youtube:<videoId>") có khớp đúng nguồn nhúng được của tập không
    public static boolean matchesProgressAsset(String episodeJson, String assetId) {
        YoutubeSource source = embeddableFrom(episodeJson);
        return source != null && assetId.equals(PROGRESS_PREFIX + source.videoId());
    }

    // Việt đừng sửa nhánh này rơi sang fallback khi primary có canEmbed = false nha, có youtube_watch là bị khóa nhúng nên cố tình trả null đó
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
            if (playbackId == null && "youtube_playback".equals(namespace)) {
                playbackId = id;
            } else if (watchId == null && "youtube_watch".equals(namespace)) {
                watchId = id;
            } else if (thumbnailId == null && "youtube_video_id".equals(namespace)) {
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

    // ba mã YouTube hợp lệ đầu tiên bắt được cho từng namespace trong external_ids
    private record ParsedIds(String playbackId, String watchId, String thumbnailId) {
        private static final ParsedIds EMPTY = new ParsedIds(null, null, null);
    }

    // metadata YouTube bóc sẵn từ JSON của tập cho CatalogReadModelLoader
    public record EpisodeMetadata(SeriesArtwork artwork, boolean embeddingDisabled, String originalTitle,
            boolean hasPrimaryMediaAsset) {
        public static final EpisodeMetadata EMPTY = new EpisodeMetadata(SeriesArtwork.EMPTY, false, null, false);
    }

    // cặp nguồn YouTube chính với dự phòng trả ra ở API chi tiết tập
    public record Detail(YoutubeSource primary, YoutubeSource fallback) {
    }
}
