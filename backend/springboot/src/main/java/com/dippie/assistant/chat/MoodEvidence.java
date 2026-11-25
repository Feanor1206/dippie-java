package com.dippie.assistant.chat;

import com.dippie.assistant.search.SearchEvidence;
import com.dippie.assistant.search.SearchHit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MoodEvidence {

    private static final Pattern CHEERFUL_RE = Pattern.compile(
            "\\b(?:vui|hai huoc|hai|comedy|funny|cheerful|humor|humour|laugh|tich cuc|am ap)\\b",
            Pattern.UNICODE_CHARACTER_CLASS
    );
    private static final Pattern SAD_RE = Pattern.compile(
            "\\b(?:buon|sad|sadness|melancholy|bi kich|tragic|tragedy)\\b",
            Pattern.UNICODE_CHARACTER_CLASS
    );
    private static final Set<String> EVIDENCE_TYPES = Set.of("scene", "segment", "dialogue");
    private static final List<String> EXTRA_MOOD_FIELDS = List.of("mood", "moods", "emotion", "emotions");

    private MoodEvidence() {
    }

    public static int score(SearchHit item, String target) {
        if (item == null) {
            return 0;
        }
        List<String> values = new ArrayList<>();
        appendStrings(values, item.genres());
        appendStrings(values, item.keywords());
        appendStrings(values, item.themes());
        appendExtraFields(values, item.diagnostics());
        appendExtraFields(values, item.comparativeAttributes());
        appendStrings(values, item.plotFacts());

        String title = item.title() == null ? "" : item.title();
        appendWithoutTitle(values, item.description(), title);
        appendWithoutTitle(values, CatalogReplies.attributeText(item, "synopsis"), title);
        appendWithoutTitle(values, item.visualSummary(), title);
        appendWithoutTitle(values, item.dialogueSummary(), title);

        if (item.evidence() != null) {
            for (SearchEvidence evidence : item.evidence()) {
                if (evidence != null
                        && evidence.itemType() != null
                        && EVIDENCE_TYPES.contains(evidence.itemType())
                        && evidence.text() != null) {
                    values.add(evidence.text());
                }
            }
        }

        Pattern pattern = "sad".equals(target) ? SAD_RE : CHEERFUL_RE;
        Matcher matcher = pattern.matcher(TextFolding.fold(String.join(" ", values)));
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static void appendStrings(List<String> values, List<String> entries) {
        if (entries == null) {
            return;
        }
        for (String entry : entries) {
            if (entry != null) {
                values.add(entry);
            }
        }
    }

    private static void appendExtraFields(List<String> values, Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return;
        }
        for (String field : EXTRA_MOOD_FIELDS) {
            Object raw = map.get(field);
            if (raw instanceof String text) {
                values.add(text);
            } else if (raw instanceof Collection<?> collection) {
                for (Object element : collection) {
                    if (element instanceof String text) {
                        values.add(text);
                    }
                }
            }
        }
    }

    private static void appendWithoutTitle(List<String> values, String value, String title) {
        if (value == null) {
            return;
        }
        values.add(title.isEmpty() ? value : value.replace(title, ""));
    }
}
