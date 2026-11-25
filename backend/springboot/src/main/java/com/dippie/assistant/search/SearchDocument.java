package com.dippie.assistant.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

record SearchDocument(String id, String itemType, Placement placement, Text text, String runtime, Long durationMs,
        String primaryMediaAssetId, String mediaAssetId, Timecode timecode, Map<String, Object> comparativeAttributes) {

    SearchDocument {
        comparativeAttributes = comparativeAttributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(comparativeAttributes));
    }

    record Placement(String seriesId, String seriesTitle, String seasonId, Integer seasonNumber, String episodeId,
            String episodeTitle, Integer episodeNumber, String segmentId, String airDate, Integer year, List<String> genres) {

        Placement {
            genres = List.copyOf(genres);
        }
    }

    record Timecode(Long sourceStart, Long sourceEnd, Long episodeStart, Long episodeEnd) {

        static final Timecode NONE = new Timecode(null, null, null, null);

        static Timecode unshifted(Long start, Long end) {
            return new Timecode(start, end, start, end);
        }
    }

    record Text(String title, String synopsis, String description, List<String> aliases, List<String> characters,
            List<String> plotFacts, List<String> keywords, List<String> themes, String visualSummary,
            String dialogueSummary, List<String> soundEvents) {

        Text {
            aliases = copy(aliases);
            characters = copy(characters);
            plotFacts = copy(plotFacts);
            keywords = copy(keywords);
            themes = copy(themes);
            soundEvents = copy(soundEvents);
        }

        String documentText(Placement placement) {
            return String.join("\n", new LinkedHashSet<>(values(title, placement.seriesTitle, placement.episodeTitle,
                    description, synopsis, visualSummary, dialogueSummary, aliases, characters, plotFacts, keywords,
                    themes, soundEvents)));
        }

        String highWeight(Placement placement) {
            return String.join(" ", values(title, placement.seriesTitle, placement.episodeTitle, aliases, characters));
        }

        String mediumWeight() {
            return String.join(" ", values(keywords, themes, plotFacts, soundEvents));
        }

        String evidenceText() {
            return values(dialogueSummary, visualSummary, description, synopsis, title).stream().findFirst().orElse("");
        }

        private static List<String> values(Object... fields) {
            List<String> values = new ArrayList<>();
            for (Object field : fields) {
                if (field instanceof String value && !value.isEmpty()) {
                    values.add(value);
                } else if (field instanceof List<?> list) {
                    for (Object element : list) {
                        if (element instanceof String value) {
                            values.add(value);
                        }
                    }
                }
            }
            return values;
        }

        private static List<String> copy(List<String> values) {
            return values == null ? null : List.copyOf(values);
        }
    }
}
