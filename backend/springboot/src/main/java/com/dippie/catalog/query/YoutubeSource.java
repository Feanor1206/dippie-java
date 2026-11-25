package com.dippie.catalog.query;

import com.dippie.catalog.domain.SeriesArtwork;
import com.dippie.common.json.JsonText;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

public record YoutubeSource(String videoId, boolean canEmbed) {

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final String THUMBNAIL_ONLY_NAMESPACE = "youtube_video_id";

    static SeriesArtwork artworkFrom(String episodeJson) {
        String videoId = Optional.ofNullable(from(episodeJson))
                .map(YoutubeSource::videoId)
                .orElseGet(() -> thumbnailOnlyVideoId(episodeJson));
        if (videoId == null) {
            return SeriesArtwork.EMPTY;
        }
        String base = "https://i.ytimg.com/vi/" + videoId + "/";
        return new SeriesArtwork(base + "hqdefault.jpg", base + "maxresdefault.jpg");
    }

    private static String thumbnailOnlyVideoId(String episodeJson) {
        return externalIds(episodeJson).stream()
                .flatMap(ids -> StreamSupport.stream(ids.spliterator(), false))
                .filter(item -> THUMBNAIL_ONLY_NAMESPACE.equals(item.path("namespace").textValue()))
                .map(item -> item.path("value").textValue())
                .filter(id -> id != null && VIDEO_ID.matcher(id).matches())
                .findFirst()
                .orElse(null);
    }

    private static Optional<JsonNode> externalIds(String episodeJson) {
        return JsonText.parse(episodeJson)
                .map(record -> record.path("external_ids"))
                .filter(JsonNode::isArray);
    }

    public static YoutubeSource from(String episodeJson) {
        JsonNode externalIds = externalIds(episodeJson).orElse(null);
        if (externalIds == null) {
            return null;
        }

        String watchId = null;
        for (JsonNode item : externalIds) {
            String id = item.path("value").textValue();
            if (id == null || !VIDEO_ID.matcher(id).matches()) {
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
}
