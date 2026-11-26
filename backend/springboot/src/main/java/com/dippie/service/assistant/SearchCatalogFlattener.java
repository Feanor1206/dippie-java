package com.dippie.service.assistant;

import com.dippie.common.text.UnicodeText;
import com.dippie.model.assistant.SearchDocument;
import com.dippie.model.assistant.SearchDocument.Placement;
import com.dippie.model.assistant.SearchDocument.Text;
import com.dippie.model.assistant.SearchDocument.Timecode;
import com.dippie.model.catalog.CatalogDisplayText;
import com.dippie.model.catalog.EpisodeRuntime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// trải phẳng cây JSON danh mục (bộ, mùa, tập, phân đoạn, cảnh với phụ đề) thành danh sách SearchDocument cho LexicalIndex
// lọc sẵn bản ghi chưa xuất bản với file media không công khai hay bị tắt ngay lúc đọc kẻo lộ phim ẩn qua tìm kiếm
public final class SearchCatalogFlattener {

    private static final long DIALOGUE_STRIDE = 22_500;
    private static final String TYPE_SEGMENT = "segment";
    private static final String TYPE_SCENE = "scene";

    private final Map<String, JsonNode> series;
    private final Map<String, JsonNode> seasons;
    private final Map<String, JsonNode> episodes;
    private final Map<String, JsonNode> assets;
    private final Map<String, List<String>> genres;
    private final Map<String, String> genreLabels;
    private final Map<String, List<String>> characters;
    private final Map<String, String> firstAssetByEpisode = new HashMap<>();
    private final Map<String, List<String>> episodesByPrimaryAsset = new HashMap<>();
    private final Map<String, List<Coverage>> coverageByAsset = new HashMap<>();
    private final Map<String, List<Cue>> cuesByAsset = new HashMap<>();
    private final List<TrackCues> trackCues;

    private SearchCatalogFlattener(JsonNode catalog) {
        series = records(catalog, "series");
        seasons = records(catalog, "seasons");
        episodes = records(catalog, "episodes");
        episodes.values().removeIf(episode -> !visibleParent(episode, "series_id", series)
                || !visibleParent(episode, "season_id", seasons));
        assets = records(catalog, "media_assets");
        assets.values().removeIf(asset -> !"public".equals(text(asset, "access_policy", "public")) || !enabled(asset));
        List<JsonNode> tracks = records(catalog, "text_tracks").values().stream().filter(SearchCatalogFlattener::enabled)
                .filter(track -> assets.containsKey(text(track, "media_asset_id"))).toList();
        genres = new HashMap<>();
        genreLabels = new HashMap<>();
        records(catalog, "genres").forEach((id, row) -> {
            List<String> names = new ArrayList<>(List.of(id));
            JsonNode labels = row.path("labels");
            if (labels.isObject()) {
                labels.valueStream().filter(JsonNode::isTextual).map(JsonNode::textValue).forEach(names::add);
                addVietnameseLabel(id, labels.path("vi").textValue());
            } else {
                labels.forEach(label -> {
                    String value = text(label, "text");
                    add(names, value);
                    if ("vi".equals(text(label, "language"))) {
                        addVietnameseLabel(id, value);
                    }
                });
            }
            names.addAll(aliases(row));
            genres.put(id, distinct(names));
        });
        characters = new HashMap<>();
        records(catalog, "characters").forEach((id, row) -> {
            List<String> names = new ArrayList<>();
            add(names, text(row, "name", id));
            names.addAll(aliases(row));
            characters.put(id, names);
        });
        assets.forEach((asset, row) -> row.path("coverage").forEach(part -> {
            String episode = text(part, "episode_id");
            if (!empty(episode)) {
                firstAssetByEpisode.putIfAbsent(episode, asset);
            }
        }));
        episodes.forEach((episodeId, row) -> {
            String primary = primaryAsset(episodeId, row);
            if (!empty(primary)) {
                episodesByPrimaryAsset.computeIfAbsent(primary, ignored -> new ArrayList<>()).add(episodeId);
            }
        });
        assets.forEach(this::collectCoverage);
        List<TrackCues> parsedTracks = new ArrayList<>(tracks.size());
        for (JsonNode track : tracks) {
            String assetId = text(track, "media_asset_id");
            List<Cue> parsed = cues(track);
            parsedTracks.add(new TrackCues(track, assetId, parsed));
            cuesByAsset.computeIfAbsent(assetId, ignored -> new ArrayList<>()).addAll(parsed);
        }
        trackCues = List.copyOf(parsedTracks);
    }

    // trải hết cây JSON xuất ra thành danh sách SearchDocument phẳng để đẩy thẳng vào chỉ mục
    public static List<SearchDocument> flatten(JsonNode catalog) {
        return new SearchCatalogFlattener(catalog).documents(catalog);
    }

    private List<SearchDocument> documents(JsonNode catalog) {
        // gom lần lượt bộ, tập, phân đoạn với cảnh có cha công khai vào danh sách tài liệu
        List<SearchDocument> documents = new ArrayList<>();
        series.values().forEach(row -> documents.add(series(row)));
        Map<String, SearchDocument> episodeDocuments = new HashMap<>();
        episodes.forEach((id, row) -> {
            SearchDocument episode = episode(row);
            episodeDocuments.put(id, episode);
            documents.add(episode);
        });
        Map<String, JsonNode> segments = records(catalog, "segments");
        segments.values().removeIf(row -> !visibleParent(row, "episode_id", episodes));
        segments.values().forEach(row -> documents.add(segment(row)));
        for (JsonNode row : records(catalog, "scenes").values()) {
            if (visibleParent(row, "episode_id", episodes) && visibleParent(row, "segment_id", segments)
                    && assets.containsKey(text(row, "media_asset_id"))) {
                documents.add(scene(row));
            }
        }
        // cắt phụ đề từng track theo đúng đoạn coverage của mỗi tập
        for (TrackCues track : trackCues) {
            for (Coverage coverage : coverageByAsset.getOrDefault(track.assetId(), List.of())) {
                addDialogue(documents, episodeDocuments.get(coverage.episodeId()), track, coverage);
            }
        }
        return List.copyOf(documents);
    }

    private SearchDocument series(JsonNode row) {
        String title = text(row, "title", "");
        Integer year = nonzero(integer(row, "release_year_start"), year(text(row, "release_date")));
        List<String> ids = strings(row, "genre_ids");
        var placement = new Placement(null, null, null, null, null, null, null, null, null, year,
                distinct(ids), resolve(ids, genres), localizedGenres(ids));
        var names = new ArrayList<>(aliases(row));
        add(names, text(row, "original_title"));
        return document(row, "series", placement, title, distinct(names), null, null, null,
                CatalogDisplayText.series(title), CatalogDisplayText.SERIES, null, null, null, Timecode.NONE);
    }

    private SearchDocument episode(JsonNode row) {
        String id = text(row, "id");
        String title = text(row, "title", "");
        String seriesId = text(row, "series_id", "");
        String seasonId = text(row, "season_id", "");
        JsonNode parentSeries = lookup(series, seriesId);
        JsonNode parentSeason = lookup(seasons, seasonId);
        String airDate = airDate(row);
        Integer year = episodeYear(parentSeason, parentSeries, airDate);
        Integer number = integer(row, "number");
        Integer season = integer(parentSeason, "number");
        List<String> aliases = new ArrayList<>(aliases(row));
        if (number != null) {
            aliases.addAll(List.of("Tập " + number, "Tập " + pad(number), "Episode " + number));
            if (season != null) {
                aliases.addAll(List.of("Mùa " + season + " Tập " + number, "Mùa " + season + " - Tập " + number,
                        "Season " + season + " Episode " + number, "S" + pad(season) + "E" + pad(number)));
            }
        }
        Long duration = EpisodeRuntime.known(number(row, "duration_ms"));
        var genreIds = inheritedGenreIds(row, parentSeries);
        var placement = new Placement(seriesId, text(parentSeries, "title", ""), seasonId, season, null, null, number,
                null, airDate, year, distinct(genreIds), resolve(genreIds, genres), localizedGenres(genreIds));
        return document(row, "episode", placement, title, distinct(aliases), plotFacts(row), null, null,
                CatalogDisplayText.episode(number, title), EpisodeRuntime.label(duration), duration,
                primaryAsset(id, row), null, Timecode.NONE);
    }

    private SearchDocument segment(JsonNode row) {
        Placement placement = childPlacement(row, null);
        String title = text(row, "title", "");
        List<String> aliases = new ArrayList<>(aliases(row));
        if (!empty(placement.episodeTitle())) {
            aliases.addAll(List.of("Tập " + placement.episodeTitle(), placement.episodeTitle()));
        }
        if (placement.episodeNumber() != null) {
            aliases.addAll(List.of("Tập " + placement.episodeNumber(), "Tập " + pad(placement.episodeNumber())));
        }
        Long start = number(row, "episode_start_ms");
        if (start == null) {
            start = number(row, "start_ms");
        }
        Long end = number(row, "episode_end_ms");
        if (end == null) {
            end = number(row, "end_ms");
        }
        return document(row, TYPE_SEGMENT, placement, title, distinct(aliases), plotFacts(row), null, null,
                "Segment: " + title, runtime(start, end, "Segment"), null, null, null, Timecode.unshifted(start, end));
    }

    private SearchDocument scene(JsonNode row) {
        Placement placement = childPlacement(row, text(row, "segment_id"));
        String asset = text(row, "media_asset_id");
        String title = nonempty(text(row, "title"), "Scene " + text(row, "id"));
        String visual = nonempty(text(row, "visual_description"),
                nonempty(text(row, "summary"), text(row, "visual_summary", "")));
        Long start = number(row, "start_ms");
        Long end = number(row, "end_ms");
        String dialogue = text(row, "dialogue_summary");
        if (empty(dialogue) && start != null && end != null) {
            dialogue = cuesByAsset.getOrDefault(asset, List.of()).stream()
                    .filter(cue -> between(cue.start(), start, end) || between(cue.end(), start, end))
                    .map(Cue::text).collect(Collectors.joining(" "));
        }
        dialogue = nonempty(dialogue, "");
        String description = nonempty(visual, text(row, "summary", ""));
        if (!dialogue.isEmpty()) {
            description = UnicodeText.strip(description + " | Thoại: \"" + dialogue + "\"");
        }
        Timecode position = Timecode.unshifted(start, end);
        if (start != null) {
            List<Coverage> matches = coverageByAsset.getOrDefault(asset, List.of()).stream()
                    .filter(entry -> entry.episodeId().equals(placement.episodeId()) && entry.contains(start)).toList();
            position = matches.size() == 1 ? matches.getFirst().timecode(start, end)
                    : new Timecode(start, end, null, null);
        }
        return document(row, TYPE_SCENE, placement, title, null, null, visual, dialogue,
                nonempty(description, "Scene: " + title), runtime(start, end, "Scene"), null, null, asset, position);
    }

    private Placement childPlacement(JsonNode row, String segmentId) {
        String episodeId = text(row, "episode_id", "");
        JsonNode parent = lookup(episodes, episodeId);
        String seriesId = text(parent, "series_id", "");
        JsonNode parentSeries = lookup(series, seriesId);
        JsonNode parentSeason = lookup(seasons, text(parent, "season_id"));
        Integer year = episodeYear(parentSeason, parentSeries, airDate(parent));
        var genreIds = inheritedGenreIds(parent, parentSeries);
        return new Placement(seriesId, text(parentSeries, "title", ""), null, integer(parentSeason, "number"),
                episodeId, text(parent, "title", ""), integer(parent, "number"), segmentId, null, year,
                distinct(genreIds), resolve(genreIds, genres), localizedGenres(genreIds));
    }

    private SearchDocument document(JsonNode row, String type, Placement placement, String title, List<String> aliases,
            List<String> plotFacts, String visual, String dialogue, String description, String runtime, Long duration,
            String primaryAsset, String asset, Timecode position) {
        String synopsis = type.equals(TYPE_SCENE) ? null : text(row, "synopsis", "");
        var ids = strings(row, "character_ids");
        List<String> names = ids.stream().map(characters::get).filter(values -> values != null && !values.isEmpty())
                .map(List::getFirst).distinct().toList();
        var text = new Text(title, synopsis, nonempty(synopsis, description), aliases,
                resolve(ids, characters), plotFacts, strings(row, "keywords"), strings(row, "themes"), visual, dialogue,
                type.equals(TYPE_SCENE) ? strings(row, "sound_events") : null, names);
        return new SearchDocument(text(row, "id"), type, placement, text, runtime, duration, primaryAsset, asset,
                position, attributes(row), type.equals(TYPE_SEGMENT) ? integer(row, "position") : null);
    }

    private void collectCoverage(String assetId, JsonNode asset) {
        Long duration = number(asset, "duration_ms");
        List<Coverage> mappings = new ArrayList<>();
        JsonNode coverage = asset.path("coverage");
        for (JsonNode part : coverage) {
            String episode = text(part, "episode_id");
            Long start = number(part, "source_in_ms");
            Long end = number(part, "source_out_ms");
            if (end == null || end == 0) {
                end = duration;
            }
            Long episodeStart = number(part, "episode_in_ms");
            if (episodes.containsKey(episode) && start != null && start >= 0
                    && (end == null || end > start) && (episodeStart == null || episodeStart >= 0)) {
                mappings.add(new Coverage(episode, start, end, episodeStart));
            }
        }
        if (coverage.isEmpty() && (duration == null || duration > 0)) {
            for (String episodeId : episodesByPrimaryAsset.getOrDefault(assetId, List.of())) {
                mappings.add(new Coverage(episodeId, 0, duration, 0L));
            }
        }
        coverageByAsset.put(assetId, mappings);
    }

    private String primaryAsset(String episode, JsonNode row) {
        return firstAssetByEpisode.getOrDefault(episode, text(row, "primary_media_asset_id"));
    }

    private static void addDialogue(List<SearchDocument> documents, SearchDocument episode, TrackCues track,
            Coverage coverage) {
        // giữ mấy câu phụ đề giao với khoảng thời gian của tập trên file media rồi sắp tăng dần
        List<Cue> cues = track.cues().stream().filter(cue -> cue.start() >= 0 && cue.start() < cue.end()
                        && cue.end() > coverage.sourceIn() && (coverage.sourceOut() == null || cue.start() < coverage.sourceOut()))
                .sorted(Comparator.comparingLong(Cue::start).thenComparingLong(Cue::end)).toList();
        if (cues.isEmpty()) {
            return;
        }
        // trượt cửa sổ 45 giây bước 22.5 giây để câu thoại vắt qua mép vẫn gom chung được, thêm tập seen khỏi nhân đôi cửa sổ giống hệt
        long first = cues.getFirst().start() / DIALOGUE_STRIDE * DIALOGUE_STRIDE;
        long last = cues.stream().mapToLong(Cue::end).max().orElseThrow();
        Set<List<Cue>> seen = new HashSet<>();
        int cursor = 0;
        for (long start = first; start < last;) {
            while (cursor < cues.size() && cues.get(cursor).end() <= start) {
                cursor++;
            }
            List<Cue> selected = new ArrayList<>();
            long next = last;
            for (int i = cursor; i < cues.size(); i++) {
                Cue cue = cues.get(i);
                if (cue.start() - start >= 45_000) {
                    next = Math.min(next, cue.start() / DIALOGUE_STRIDE * DIALOGUE_STRIDE - DIALOGUE_STRIDE);
                    break;
                }
                if (cue.end() > start) {
                    selected.add(cue);
                    long finalWindow = (cue.end() - 1) / DIALOGUE_STRIDE * DIALOGUE_STRIDE;
                    if (finalWindow < last - DIALOGUE_STRIDE) {
                        next = Math.min(next, finalWindow + DIALOGUE_STRIDE);
                    }
                }
            }
            if (!selected.isEmpty() && seen.add(selected)) {
                documents.add(dialogue(episode, track.track(), track.assetId(), coverage, start, selected));
            }
            start = next;
        }
    }

    private static SearchDocument dialogue(SearchDocument episode, JsonNode track, String asset, Coverage coverage,
            long start, List<Cue> selected) {
        long sourceStart = Math.max(selected.getFirst().start(), coverage.sourceIn());
        long sourceEnd = selected.stream().mapToLong(Cue::end).max().orElseThrow();
        if (coverage.sourceOut() != null) {
            sourceEnd = Math.min(sourceEnd, coverage.sourceOut());
        }
        String dialogue = selected.stream().map(Cue::text).collect(Collectors.joining(" "));
        var parent = episode.placement();
        var placement = new Placement(parent.seriesId(), parent.seriesTitle(), parent.seasonId(), parent.seasonNumber(),
                episode.id(), parent.episodeTitle(), parent.episodeNumber(), null, parent.airDate(), parent.year(),
                parent.genres(), parent.genreTerms(), parent.genreLabels());
        var text = episode.text();
        var window = new Text(text.title(), text.synopsis(), dialogue, text.aliases(), text.characters(), text.plotFacts(),
                text.keywords(), text.themes(), null, dialogue, null, text.characterNames());
        String id = "dialogue:" + text(track, "id", asset) + ":" + episode.id() + ":" + start;
        return new SearchDocument(id, "dialogue", placement, window, episode.runtime(), episode.durationMs(),
                episode.primaryMediaAssetId(), asset, coverage.timecode(sourceStart, sourceEnd),
                episode.comparativeAttributes(), null);
    }

    private static List<String> inheritedGenreIds(JsonNode episode, JsonNode parentSeries) {
        List<String> ids = strings(episode, "genre_ids");
        return ids.isEmpty() ? strings(parentSeries, "genre_ids") : ids;
    }

    private void addVietnameseLabel(String id, String label) {
        if (label != null && !label.isBlank()) {
            genreLabels.putIfAbsent(id, label);
        }
    }

    private List<String> localizedGenres(List<String> ids) {
        return ids.stream().map(genreLabels::get).filter(label -> label != null).distinct().toList();
    }

    // chỉ lấy bản ghi status ready hoặc data cũ chưa có record.status, mấy bản draft, pending, hidden bỏ hết
    private static Map<String, JsonNode> records(JsonNode catalog, String field) {
        Map<String, JsonNode> records = new LinkedHashMap<>();
        for (JsonNode row : catalog.path(field)) {
            String id = text(row, "id");
            String status = text(row.path("record"), "status", "");
            boolean published = status == null || status.isEmpty() || "ready".equalsIgnoreCase(status);
            if (id != null && published) {
                records.put(id, row);
            }
        }
        return records;
    }

    private static List<Cue> cues(JsonNode track) {
        List<Cue> cues = new ArrayList<>();
        for (JsonNode row : track.path("cues")) {
            Long start = number(row, "start_ms");
            Long end = number(row, "end_ms");
            String text = text(row, "text");
            if (start != null && end != null && text != null && !UnicodeText.strip(text).isEmpty()) {
                cues.add(new Cue(start, end, text));
            }
        }
        return cues;
    }

    private static List<String> aliases(JsonNode row) {
        return distinct(stripTexts(row.path("aliases"), "text", null));
    }

    private static List<String> plotFacts(JsonNode row) {
        return stripTexts(row.path("plot_facts"), "text", "fact");
    }

    private static List<String> stripTexts(JsonNode rows, String key, String fallback) {
        List<String> result = new ArrayList<>();
        for (JsonNode row : rows) {
            String value = row.isTextual() ? row.textValue() : text(row, key);
            if (empty(value) && fallback != null) {
                value = text(row, fallback);
            }
            if (value != null) {
                String stripped = UnicodeText.strip(value);
                if (!stripped.isEmpty()) {
                    result.add(stripped);
                }
            }
        }
        return result;
    }

    private static Map<String, Object> attributes(JsonNode row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        for (JsonNode attribute : row.path("attributes")) {
            String scale = text(attribute, "scale_id");
            if (scale != null) {
                attributes.put(scale, attributeValue(attribute.path("value")));
            }
        }
        return Collections.unmodifiableMap(attributes);
    }

    private static Object attributeValue(JsonNode value) {
        if (value.isNumber() || value.isBoolean()) {
            return value.isBoolean() ? (value.booleanValue() ? 1.0 : 0.0) : value.doubleValue();
        }
        if (value.isTextual()) {
            try {
                return Double.parseDouble(value.textValue());
            } catch (NumberFormatException ignored) {
                return value.textValue();
            }
        }
        return immutableValue(value);
    }

    private static Object immutableValue(JsonNode value) {
        if (value.isObject()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            value.fields().forEachRemaining(field -> fields.put(field.getKey(), immutableValue(field.getValue())));
            return Collections.unmodifiableMap(fields);
        }
        if (value.isArray()) {
            return value.valueStream().map(SearchCatalogFlattener::immutableValue).toList();
        }
        if (value.isNumber()) {
            return value.numberValue();
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        return value.textValue();
    }

    private static JsonNode lookup(Map<String, JsonNode> rows, String id) {
        return rows.getOrDefault(id, MissingNode.getInstance());
    }

    private static boolean visibleParent(JsonNode row, String field, Map<String, JsonNode> rows) {
        String id = text(row, field);
        return empty(id) || rows.containsKey(id);
    }

    private static boolean enabled(JsonNode row) {
        return !row.has("enabled") || row.path("enabled").booleanValue();
    }

    private static List<String> resolve(List<String> ids, Map<String, List<String>> names) {
        return ids.stream().flatMap(id -> names.getOrDefault(id, List.of(id)).stream()).distinct().toList();
    }

    private static List<String> strings(JsonNode row, String key) {
        return row.path(key).valueStream()
                .filter(JsonNode::isTextual).map(JsonNode::textValue).toList();
    }

    private static List<String> distinct(List<String> values) {
        return values.stream().distinct().toList();
    }

    private static void add(List<String> names, String value) {
        if (value != null) {
            names.add(value);
        }
    }

    private static Integer year(String date) {
        if (date == null || date.length() < 4 || (date.length() > 4 && date.charAt(4) != '-')) {
            return null;
        }
        try {
            return Integer.valueOf(date.substring(0, 4));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String airDate(JsonNode episode) {
        return nonempty(text(episode, "release_date"), text(episode, "air_date"));
    }

    private static Integer episodeYear(JsonNode season, JsonNode series, String airDate) {
        Integer seasonYear = nonzero(integer(season, "release_year_start"), year(text(season, "release_date")));
        return nonzero(year(airDate), nonzero(seasonYear, integer(series, "release_year_start")));
    }

    private static String pad(int number) {
        return String.format(Locale.ROOT, "%02d", number);
    }

    private static boolean between(long value, long start, long end) {
        return value >= start && value <= end;
    }

    private static String runtime(Long start, Long end, String fallback) {
        return start != null && end != null && end - start >= 1_000 ? (end - start) / 1_000 + "s" : fallback;
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }

    private static String nonempty(String value, String fallback) {
        return empty(value) ? fallback : value;
    }

    private static Integer nonzero(Integer value, Integer fallback) {
        return value == null || value == 0 ? fallback : value;
    }

    private static String text(JsonNode row, String key) {
        return row.path(key).textValue();
    }

    private static String text(JsonNode row, String key, String fallback) {
        return row.path(key).isMissingNode() ? fallback : text(row, key);
    }

    private static Integer integer(JsonNode row, String key) {
        JsonNode node = row.path(key);
        return node.isIntegralNumber() && node.canConvertToInt() ? node.intValue() : null;
    }

    private static Long number(JsonNode row, String key) {
        JsonNode node = row.path(key);
        return node.isIntegralNumber() && node.canConvertToLong() ? node.longValue() : null;
    }

    // khoảng ánh xạ thời gian giữa file video nguồn với timeline của 1 tập phim
    private record Coverage(String episodeId, long sourceIn, Long sourceOut, Long episodeIn) {

        boolean contains(long start) {
            return start >= sourceIn && (sourceOut == null || start < sourceOut);
        }

        Timecode timecode(Long start, Long end) {
            return new Timecode(start, end, episodeTime(start), episodeTime(end));
        }

        private Long episodeTime(Long time) {
            return episodeIn == null || time == null ? null : time - sourceIn + episodeIn;
        }
    }

    // gom một đường phụ đề kèm mã media với danh sách câu đã đọc xong
    private record TrackCues(JsonNode track, String assetId, List<Cue> cues) {
    }

    // một câu phụ đề lẻ gồm mốc đầu, mốc cuối và chữ gốc chưa cắt khoảng trắng
    private record Cue(long start, long end, String rawText) {

        String text() {
            return UnicodeText.strip(rawText);
        }
    }
}
