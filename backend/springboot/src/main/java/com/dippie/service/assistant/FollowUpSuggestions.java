package com.dippie.service.assistant;

import com.dippie.model.assistant.SearchHit;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// tạo mấy nút gợi ý câu hỏi tiếp theo sau mỗi lượt tìm
// Việt đừng tự đổi câu chữ ở đây khi chưa dò lại IntentParser nha, có mấy nút parser bắt trực tiếp đó
public final class FollowUpSuggestions {

    private FollowUpSuggestions() {
    }

    public static List<String> build(List<SearchHit> results) {
        return build(results, results != null && results.size() > 1);
    }

    // giữ đúng thứ tự (nhân vật, tập khác, cảnh sau đó) bằng LinkedHashSet cho khỏi lặp gợi ý
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
