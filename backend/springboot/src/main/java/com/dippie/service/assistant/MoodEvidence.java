package com.dippie.service.assistant;

import com.dippie.common.text.TextFolding;
import com.dippie.model.assistant.SearchEvidence;
import com.dippie.model.assistant.SearchHit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Chấm điểm mức độ khớp tâm trạng (vui hay buồn) cho từng kết quả phim dựa trên metadata và lời thoại.
public final class MoodEvidence {

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

    // Gỡ tiêu đề khỏi phần mô tả trước khi đếm từ khóa để tránh một tập chỉ chứa chữ "vui" ở tiêu đề bị tính điểm ảo.
    public static int score(SearchHit item, String target) {
        if (item == null) {
            return 0;
        }
        List<String> texts = new ArrayList<>();
        appendStrings(texts, item.genres());
        appendStrings(texts, item.keywords());
        appendStrings(texts, item.themes());
        appendExtraFields(texts, item.diagnostics());
        appendExtraFields(texts, item.comparativeAttributes());
        appendStrings(texts, item.plotFacts());

        String title = item.title() == null ? "" : item.title();
        appendWithoutTitle(texts, item.description(), title);
        appendWithoutTitle(texts, CatalogReplies.attributeText(item, "synopsis"), title);
        appendWithoutTitle(texts, item.visualSummary(), title);
        appendWithoutTitle(texts, item.dialogueSummary(), title);

        // Chỉ đếm trích đoạn cấp scene, segment hoặc dialogue để không tính lặp mô tả chung của cả tập.
        if (item.evidence() != null) {
            for (SearchEvidence evidence : item.evidence()) {
                if (evidence != null
                        && evidence.itemType() != null
                        && EVIDENCE_TYPES.contains(evidence.itemType())
                        && evidence.text() != null) {
                    texts.add(evidence.text());
                }
            }
        }

        Pattern pattern = "sad".equals(target) ? SAD_RE : CHEERFUL_RE;
        Matcher matcher = pattern.matcher(TextFolding.fold(String.join(" ", texts)));
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static void appendStrings(List<String> texts, List<String> entries) {
        if (entries == null) {
            return;
        }
        for (String entry : entries) {
            if (entry != null) {
                texts.add(entry);
            }
        }
    }

    private static void appendExtraFields(List<String> texts, Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        for (String field : EXTRA_MOOD_FIELDS) {
            Object value = attributes.get(field);
            if (value instanceof String text) {
                texts.add(text);
            } else if (value instanceof Collection<?> collection) {
                for (Object element : collection) {
                    if (element instanceof String text) {
                        texts.add(text);
                    }
                }
            }
        }
    }

    private static void appendWithoutTitle(List<String> texts, String text, String title) {
        if (text == null) {
            return;
        }
        texts.add(title.isEmpty() ? text : text.replace(title, ""));
    }
}
