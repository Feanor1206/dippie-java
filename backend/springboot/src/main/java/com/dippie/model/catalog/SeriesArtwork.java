package com.dippie.model.catalog;

import com.dippie.common.json.JsonText;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

// cặp link poster với backdrop của phim bộ, kèm luật chọn ảnh ưu tiên và lọc URL
public record SeriesArtwork(String posterUrl, String backdropUrl) {

    public static final SeriesArtwork EMPTY = new SeriesArtwork(null, null);

    // rút ảnh từ artworksJson ưu tiên poster > thumbnail > YouTube cho ảnh dọc, backdrop > banner > YouTube > poster cho ảnh ngang
    // link nào không phải http/https thì bỏ qua hết
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

    // Việt để ý chiều gọi nha: this là ảnh dự phòng còn tham số là ảnh đang có, cái nào null mới lấy từ this bù sang
    public SeriesArtwork fillMissing(String poster, String backdrop) {
        return new SeriesArtwork(Objects.toString(poster, posterUrl), Objects.toString(backdrop, backdropUrl));
    }

    // chỉ nhận http/https có host đàng hoàng, chặn mấy chuỗi kiểu javascript: hay file:///
    private static boolean isHttpUri(String rawUri) {
        if (rawUri == null) {
            return false;
        }
        try {
            URI uri = URI.create(rawUri);
            return uri.getRawAuthority() != null && ("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
