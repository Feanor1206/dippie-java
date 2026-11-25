package com.dippie.assistant.search;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FollowUpSuggestions {

    private FollowUpSuggestions() {
    }

    public static List<String> build(List<SearchHit> results) {
        if (results == null || results.isEmpty()) {
            return List.of("Gợi ý tập phim nổi bật", "Tìm phim hài gia đình");
        }
        SearchHit top = results.getFirst();
        Set<String> suggestions = new LinkedHashSet<>();
        if (top.characters() != null && !top.characters().isEmpty() && top.characters().getFirst() != null) {
            suggestions.add("Tìm thêm cảnh có " + top.characters().getFirst());
        }
        if (results.size() > 1) {
            suggestions.add("Không, tập khác");
        }
        if (top.effectiveStartMs() != null) {
            suggestions.add("Cảnh sau đó thì sao");
        }
        return List.copyOf(suggestions);
    }
}
