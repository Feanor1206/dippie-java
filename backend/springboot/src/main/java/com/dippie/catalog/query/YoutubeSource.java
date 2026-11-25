package com.dippie.catalog.query;

import com.dippie.catalog.domain.SeriesArtwork;
import com.dippie.common.json.JsonText;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.regex.Pattern;

record YoutubeSource(String videoId, boolean canEmbed) {

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final String THUMBNAIL_ONLY_NAMESPACE = "youtube_video_id";

    static SeriesArtwork artworkFrom(String episodeJson) {
        JsonNode externalIds = externalIds(episodeJson);
        if (externalIds == null) {
            return SeriesArtwork.EMPTY;
        }
        String watchId = null;
        String thumbnailId = null;
        for (JsonNode item : externalIds) {
            String id = validVideoId(item);
            if (id == null) {
                continue;
            }
            String namespace = item.path("namespace").textValue();
            if ("youtube_playback".equals(namespace)) {
                return artworkFor(id);
            }
            if (watchId == null && "youtube_watch".equals(namespace)) {
                watchId = id;
            } else if (thumbnailId == null && THUMBNAIL_ONLY_NAMESPACE.equals(namespace)) {
                thumbnailId = id;
            }
        }
        String resolved = watchId != null ? watchId : thumbnailId;
        return resolved == null ? SeriesArtwork.EMPTY : artworkFor(resolved);
    }

    static YoutubeSource from(String episodeJson) {
        JsonNode externalIds = externalIds(episodeJson);
        if (externalIds == null) {
            return null;
        }
        String watchId = null;
        for (JsonNode item : externalIds) {
            String id = validVideoId(item);
            if (id == null) {
                continue;
            }
            String namespace = item.path("namespace").textValue();
            if ("youtube_playback".equals(namespace)) {
                return new YoutubeSource(id, true);
            }
            if (watchId == null && "youtube_watch".equals(namespace)) {
                watchId = id;
            }
        }
        return watchId == null ? null : new YoutubeSource(watchId, false);
    }

    private static JsonNode externalIds(String episodeJson) {
        return JsonText.parse(episodeJson)
                .map(record -> record.path("external_ids"))
                .filter(JsonNode::isArray)
                .orElse(null);
    }

    private static String validVideoId(JsonNode item) {
        String id = item.path("value").textValue();
        return id != null && VIDEO_ID.matcher(id).matches() ? id : null;
    }

    private static SeriesArtwork artworkFor(String videoId) {
        String base = "https://i.ytimg.com/vi/" + videoId + "/";
        return new SeriesArtwork(base + "hqdefault.jpg", base + "maxresdefault.jpg");
    }
}
