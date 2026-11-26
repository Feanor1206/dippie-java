package com.dippie.service.assistant;

import com.dippie.model.assistant.SearchHit;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Sinh các nút gợi ý câu hỏi tiếp theo sau một lượt tìm kiếm.
// Việt nhớ đừng tự đổi câu chữ trong đây nếu chưa đối chiếu IntentParser, vì một số nút bấm được parser nhận diện trực tiếp.
public final class FollowUpSuggestions {

    private FollowUpSuggestions() {
    }

    public static List<String> build(List<SearchHit> results) {
        return build(results, results != null && results.size() > 1);
    }

    // Giữ thứ tự cố định (nhân vật, đổi tập khác, cảnh sau đó) bằng LinkedHashSet để không bị lặp gợi ý.
    public static List<String> build(List<SearchHit> results, boolean hasAlternatives) {
        if (results == null || results.isEmpty()) {
            return List.of("Gợi ý tập phim nổi bật", "Tìm phim hài gia đình");
        }
        SearchHit top = results.getFirst();
        Set<String> suggestions = new LinkedHashSet<>();
        if (top.characters() != null && !top.characters().isEmpty() && top.characters().getFirst() != null) {
            suggestions.add("Tìm thêm cảnh có " + top.characters().getFirst());
        }
        if (hasAlternatives) {
            suggestions.add("Không, tập khác");
        }
        if (top.effectiveStartMs() != null) {
            suggestions.add("Cảnh sau đó thì sao");
        }
        return List.copyOf(suggestions);
    }
}
