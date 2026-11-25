package com.dippie.catalog.domain;

import com.dippie.common.json.JsonText;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record SeriesArtwork(String posterUrl, String backdropUrl) {

    public static final SeriesArtwork EMPTY = new SeriesArtwork(null, null);

    public static SeriesArtwork fromSeries(String artworksJson, SeriesArtwork youtube) {
        Map<String, String> urls = new HashMap<>();
        JsonText.parse(artworksJson).filter(JsonNode::isArray).ifPresent(items -> items.forEach(item -> {
            String uri = item.path("uri").textValue();
            if (isHttpUri(uri)) {
                urls.putIfAbsent(item.path("role").asText(), uri);
            }
        }));
        String poster = urls.getOrDefault("poster", urls.getOrDefault("thumbnail", youtube.posterUrl()));
        String backdrop = urls.getOrDefault("backdrop", urls.getOrDefault("banner",
                Objects.toString(youtube.backdropUrl(), poster)));
        return new SeriesArtwork(poster, backdrop);
    }

    public SeriesArtwork fillMissing(String poster, String backdrop) {
        return new SeriesArtwork(Objects.toString(poster, posterUrl), Objects.toString(backdrop, backdropUrl));
    }

    private static boolean isHttpUri(String value) {
        if (value == null) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return uri.getRawAuthority() != null && ("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
