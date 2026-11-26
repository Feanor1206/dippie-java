package com.dippie.model.catalog;

import com.dippie.common.json.JsonText;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

// Cặp URL poster và backdrop của một phim bộ, kèm quy tắc chọn ảnh ưu tiên và lọc link hợp lệ.
public record SeriesArtwork(String posterUrl, String backdropUrl) {

    public static final SeriesArtwork EMPTY = new SeriesArtwork(null, null);

    // Trích ảnh từ artworksJson theo thứ tự ưu tiên poster > thumbnail > YouTube cho ảnh dọc, backdrop > banner > YouTube > chính poster cho ảnh ngang; bỏ qua link không phải http/https.
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

    // Việt chú ý chiều gọi hàm này: this là ảnh dự phòng, còn poster và backdrop truyền vào là giá trị đang có, thiếu trường nào (null) thì mới lấy từ this bù sang.
    public SeriesArtwork fillMissing(String poster, String backdrop) {
        return new SeriesArtwork(Objects.toString(poster, posterUrl), Objects.toString(backdrop, backdropUrl));
    }

    // Chỉ nhận URL http hoặc https có host rõ ràng, chặn mấy chuỗi kiểu javascript: hay file:///.
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
