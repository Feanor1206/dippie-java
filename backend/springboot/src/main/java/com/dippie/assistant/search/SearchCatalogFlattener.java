package com.dippie.assistant.search;

import com.dippie.assistant.search.SearchDocument.Placement;
import com.dippie.assistant.search.SearchDocument.Text;
import com.dippie.assistant.search.SearchDocument.Timecode;
import com.dippie.common.text.UnicodeText;
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

final class SearchCatalogFlattener {

    private static final long DIALOGUE_STRIDE = 22_500;
    private static final long DIALOGUE_WINDOW = 45_000;

    private final Map<String, JsonNode> series;
    private final Map<String, JsonNode> seasons;
    private final Map<String, JsonNode> episodes;
    private final Map<String, JsonNode> assets;
    private final Map<String, List<String>> genres;
    private final Map<String, List<String>> characters;
    private final Map<String, String> firstAssetByEpisode = new HashMap<>();
    private final Map<String, List<Coverage>> coverageByAsset = new HashMap<>();
    private final Map<String, List<Cue>> cuesByAsset = new HashMap<>();
    private final List<JsonNode> tracks;

    private SearchCatalogFlattener(JsonNode catalog) {
        series = records(catalog, "series");
        seasons = records(catalog, "seasons");
        episodes = records(catalog, "episodes");
        episodes.values().removeIf(episode -> !visibleParent(episode, "series_id", series)
                || !visibleParent(episode, "season_id", seasons));
        assets = records(catalog, "media_assets");
        assets.values().removeIf(asset -> !"public".equals(text(asset, "access_policy", "public")) || !enabled(asset));
        tracks = records(catalog, "text_tracks").values().stream().filter(SearchCatalogFlattener::enabled)
                .filter(track -> assets.containsKey(text(track, "media_asset_id"))).toList();
        genres = new HashMap<>();
        records(catalog, "genres").forEach((id, row) -> {
            List<String> names = new ArrayList<>(List.of(id));
            row.path("labels").forEach(label -> add(names, text(label, "text")));
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
        assets.forEach(this::collectCoverage);
        tracks.forEach(track -> cuesByAsset.computeIfAbsent(text(track, "media_asset_id"), ignored -> new ArrayList<>())
                .addAll(cues(track)));
    }

    static List<SearchDocument> flatten(JsonNode catalog) {
        return new SearchCatalogFlattener(catalog).documents(catalog);
    }

    private List<SearchDocument> documents(JsonNode catalog) {
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
        for (JsonNode track : tracks) {
            String asset = text(track, "media_asset_id");
            for (Coverage coverage : coverageByAsset.getOrDefault(asset, List.of())) {
                addDialogue(documents, episodeDocuments.get(coverage.episodeId()), track, asset, coverage);
            }
        }
        return List.copyOf(documents);
    }

    private SearchDocument series(JsonNode row) {
        String title = text(row, "title", "");
        Integer year = nonzero(integer(row, "release_year_start"), year(text(row, "release_date")));
        var placement = new Placement(null, null, null, null, null, null, null, null, null, year,
                resolve(strings(row, "genre_ids"), genres));
        return document(row, "series", placement, title, aliases(row), null, null, null,
                "Animation series: " + title, "Series", null, null, null, Timecode.NONE);
    }

    private SearchDocument episode(JsonNode row) {
        String id = text(row, "id");
        String title = text(row, "title", "");
        String seriesId = text(row, "series_id", "");
        String seasonId = text(row, "season_id", "");
        JsonNode parentSeries = lookup(series, seriesId);
        JsonNode parentSeason = lookup(seasons, seasonId);
        String airDate = text(row, "release_date");
        Integer year = nonzero(year(airDate),
                nonzero(integer(parentSeason, "release_year_start"), integer(parentSeries, "release_year_start")));
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
        Long duration = number(row, "duration_ms");
        var placement = new Placement(seriesId, text(parentSeries, "title", ""), seasonId, season, null, null, number,
                null, airDate, year, inheritedGenres(row, parentSeries));
        return document(row, "episode", placement, title, distinct(aliases), plotFacts(row), null, null,
                "Episode " + (number == null ? "None" : number) + ": " + title,
                duration != null && duration >= 60_000 ? duration / 60_000 + "m" : "Episode", duration,
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
        return document(row, "segment", placement, title, distinct(aliases), plotFacts(row), null, null,
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
        return document(row, "scene", placement, title, null, null, visual, dialogue,
                nonempty(description, "Scene: " + title), runtime(start, end, "Scene"), null, null, asset, position);
    }

    private Placement childPlacement(JsonNode row, String segmentId) {
        String episodeId = text(row, "episode_id", "");
        JsonNode parent = lookup(episodes, episodeId);
        String seriesId = text(parent, "series_id", "");
        JsonNode parentSeries = lookup(series, seriesId);
        JsonNode parentSeason = lookup(seasons, text(parent, "season_id"));
        Integer date = year(text(parent, "release_date"));
        Integer seriesYear = integer(parentSeries, "release_year_start");
        Integer year = nonzero(date, seriesYear);
        if (year == null) {
            year = integer(parentSeason, "release_year_start");
        }
        return new Placement(seriesId, text(parentSeries, "title", ""), null, integer(parentSeason, "number"),
                episodeId, text(parent, "title", ""), integer(parent, "number"), segmentId, null, year,
                inheritedGenres(parent, parentSeries));
    }

    private SearchDocument document(JsonNode row, String type, Placement placement, String title, List<String> aliases,
            List<String> plotFacts, String visual, String dialogue, String description, String runtime, Long duration,
            String primaryAsset, String asset, Timecode position) {
        String synopsis = type.equals("scene") ? null : text(row, "synopsis", "");
        var text = new Text(title, synopsis, nonempty(synopsis, description), aliases,
                resolve(strings(row, "character_ids"), characters), plotFacts, strings(row, "keywords"),
                strings(row, "themes"), visual, dialogue, type.equals("scene") ? strings(row, "sound_events") : null);
        return new SearchDocument(text(row, "id"), type, placement, text, runtime, duration, primaryAsset, asset,
                position, attributes(row));
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
            episodes.forEach((episodeId, row) -> {
                if (assetId.equals(primaryAsset(episodeId, row))) {
                    mappings.add(new Coverage(episodeId, 0, duration, 0L));
                }
            });
        }
        coverageByAsset.put(assetId, mappings);
    }

    private String primaryAsset(String episode, JsonNode row) {
        return firstAssetByEpisode.getOrDefault(episode, text(row, "primary_media_asset_id"));
    }

    private static void addDialogue(List<SearchDocument> documents, SearchDocument episode, JsonNode track,
            String asset, Coverage coverage) {
        List<Cue> cues = cues(track).stream().filter(cue -> cue.start() >= 0 && cue.start() < cue.end()
                        && cue.end() > coverage.sourceIn() && (coverage.sourceOut() == null || cue.start() < coverage.sourceOut()))
                .sorted(Comparator.comparingLong(Cue::start).thenComparingLong(Cue::end)).toList();
        if (cues.isEmpty()) {
            return;
        }
        long first = cues.getFirst().start() / DIALOGUE_STRIDE * DIALOGUE_STRIDE;
        long last = cues.stream().mapToLong(Cue::end).max().orElseThrow();
        Set<List<Cue>> seen = new HashSet<>();
        for (long start = first; start < last;) {
            List<Cue> selected = new ArrayList<>();
            long next = last;
            for (Cue cue : cues) {
                if (cue.start() - start >= DIALOGUE_WINDOW) {
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
                documents.add(dialogue(episode, track, asset, coverage, start, selected));
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
                episode.id(), parent.episodeTitle(), parent.episodeNumber(), null, parent.airDate(), parent.year(), parent.genres());
        var text = episode.text();
        var window = new Text(text.title(), text.synopsis(), dialogue, text.aliases(), text.characters(), text.plotFacts(),
                text.keywords(), text.themes(), null, dialogue, null);
        String id = "dialogue:" + text(track, "id", asset) + ":" + episode.id() + ":" + start;
        return new SearchDocument(id, "dialogue", placement, window, episode.runtime(), episode.durationMs(),
                episode.primaryMediaAssetId(), asset, coverage.timecode(sourceStart, sourceEnd), episode.comparativeAttributes());
    }

    private List<String> inheritedGenres(JsonNode episode, JsonNode parentSeries) {
        List<String> ids = strings(episode, "genre_ids");
        return resolve(ids.isEmpty() ? strings(parentSeries, "genre_ids") : ids, genres);
    }

    private static Map<String, JsonNode> records(JsonNode catalog, String field) {
        Map<String, JsonNode> records = new LinkedHashMap<>();
        for (JsonNode row : catalog.path(field)) {
            String id = text(row, "id");
            String status = text(row.path("record"), "status", "");
            if (id != null && !status.equals("draft") && !status.equals("archived")) {
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
        JsonNode value = row.path(key);
        return value.isIntegralNumber() && value.canConvertToInt() ? value.intValue() : null;
    }

    private static Long number(JsonNode row, String key) {
        JsonNode value = row.path(key);
        return value.isIntegralNumber() && value.canConvertToLong() ? value.longValue() : null;
    }

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

    private record Cue(long start, long end, String rawText) {

        String text() {
            return UnicodeText.strip(rawText);
        }
    }
}
