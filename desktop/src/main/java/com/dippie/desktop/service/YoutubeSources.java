package com.dippie.desktop.service;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

// tách link YouTube từ mảng external_ids trong raw_json của tập phim
// dùng possessive quantifier (++ và *+) trong regex kẻo JSON dài làm treo backtracking
final class YoutubeSources {
    private static final String JSON_STRING = "\"(?:[^\"\\\\]++|\\\\.)*+\"";
    private static final Pattern EXTERNAL_IDS = Pattern.compile(
            "\"external_ids\"\\s*:\\s*\\[((?:" + JSON_STRING + "|[^\"\\]]++)*+)\\]");
    private static final Pattern ITEM = Pattern.compile("\\{((?:" + JSON_STRING + "|[^\"{}]++)*+)\\}");
    private static final Pattern NAMESPACE = Pattern.compile(
            "\"namespace\"\\s*:\\s*\"(youtube_playback|youtube_watch|youtube_video_id)\"");
    private static final Pattern VIDEO_ID = Pattern.compile("\"value\"\\s*:\\s*\"([A-Za-z0-9_-]{11})\"");

    private YoutubeSources() {
    }

    // chọn nguồn theo thứ tự ưu tiên của enum Kind là youtube_playback, rồi youtube_watch, cuối cùng mới tới youtube_video_id
    static Optional<String> findUrl(String recordJson) {
        if (recordJson == null) {
            return Optional.empty();
        }
        var arrayMatcher = EXTERNAL_IDS.matcher(recordJson);
        if (!arrayMatcher.find()) {
            return Optional.empty();
        }

        Map<Kind, String> videoIds = new EnumMap<>(Kind.class);
        var itemMatcher = ITEM.matcher(arrayMatcher.group(1));
        while (itemMatcher.find()) {
            var entry = itemMatcher.group(1);
            var namespaceMatcher = NAMESPACE.matcher(entry);
            var videoIdMatcher = VIDEO_ID.matcher(entry);
            if (namespaceMatcher.find() && videoIdMatcher.find()) {
                videoIds.putIfAbsent(Kind.fromNamespace(namespaceMatcher.group(1)), videoIdMatcher.group(1));
            }
        }

        for (Kind kind : Kind.values()) {
            if (videoIds.containsKey(kind)) {
                return Optional.of("https://www.youtube.com/watch?v=" + videoIds.get(kind));
            }
        }
        return Optional.empty();
    }

    // thứ tự khai báo ở đây chính là độ ưu tiên của từng loại mã YouTube
    private enum Kind {
        PLAYBACK, WATCH, FALLBACK;

        static Kind fromNamespace(String namespace) {
            return switch (namespace) {
                case "youtube_playback" -> PLAYBACK;
                case "youtube_watch" -> WATCH;
                default -> FALLBACK;
            };
        }
    }
}
