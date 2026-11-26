package com.dippie.service.assistant;

import com.dippie.common.text.TextFolding;
import com.dippie.model.assistant.ChatHistoryMessage;
import com.dippie.model.assistant.SearchHit;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Bắt các câu hỏi nối tiếp như "cảnh sau đó", "tập thứ hai" hay "tập khác" để tra ngược từ lịch sử hội thoại.
public record ChatFollowUp(boolean nextScene, int itemIndex) {

    private static final Pattern NEXT_SCENE = Pattern.compile(
            "\\b(?:canh sau do|doan sau do|canh tiep theo|tiep theo thi sao)\\b");
    private static final Pattern PREVIOUS_ITEM = Pattern.compile(
            "\\b(?:(?:tap|phim|bo)(?: phim)? (?:do|ay|dau tien)|tom tat (?:no|cai do)|vua goi y)\\b");
    private static final Pattern ORDINAL = Pattern.compile(
            "(?:^|\\b(?:tap|phim|bo|goi y) )thu (\\d{1,3}|nhat|hai|ba|tu|bon|nam|sau|bay|tam|chin|muoi)\\b");
    private static final Pattern ALTERNATE = Pattern.compile(
            "(?:(?:khong|cho minh|goi y|con)[,.!\\s]+)*(?:tap|phim|bo phim) khac(?: (?:di|nua|khong|nhe))*[.!? ]*");
    private static final Map<String, Integer> ORDINALS = Map.ofEntries(
            Map.entry("nhat", 1), Map.entry("hai", 2), Map.entry("ba", 3), Map.entry("tu", 4), Map.entry("bon", 4),
            Map.entry("nam", 5), Map.entry("sau", 6), Map.entry("bay", 7), Map.entry("tam", 8),
            Map.entry("chin", 9), Map.entry("muoi", 10));

    // Nhận diện yêu cầu xem cảnh tiếp theo hoặc tham chiếu mục phim theo số thứ tự (chuyển về chỉ số từ 0).
    public static Optional<ChatFollowUp> parse(String query) {
        String folded = TextFolding.foldNormalized(query);
        if (NEXT_SCENE.matcher(folded).find()) {
            return Optional.of(new ChatFollowUp(true, 0));
        }
        var ordinal = ORDINAL.matcher(folded);
        if (ordinal.find()) {
            String token = ordinal.group(1);
            int position = Character.isDigit(token.charAt(0)) ? Integer.parseInt(token) : ORDINALS.get(token);
            return Optional.of(new ChatFollowUp(false, position - 1));
        }
        return PREVIOUS_ITEM.matcher(folded).find() ? Optional.of(new ChatFollowUp(false, 0)) : Optional.empty();
    }

    public static boolean asksForAlternate(String query) {
        return ALTERNATE.matcher(TextFolding.foldNormalized(query)).matches();
    }

    public static Optional<ChatHistoryMessage> lastAssistant(List<ChatHistoryMessage> history) {
        return history == null ? Optional.empty() : history.reversed().stream()
                .filter(message -> message != null && "assistant".equals(message.role())).findFirst();
    }

    // Gom hết itemIds CineBot từng trả trong phiên để tránh lặp lại khi người dùng xin tập khác.
    public static Set<String> shownItems(List<ChatHistoryMessage> history) {
        return history == null ? Set.of() : history.stream()
                .filter(message -> message != null && "assistant".equals(message.role()))
                .flatMap(message -> message.itemIds().stream()).collect(Collectors.toUnmodifiableSet());
    }

    // Với "cảnh sau đó" thì lần theo documentId cuối cùng, còn hỏi theo thứ tự thì lấy thẳng từ itemIds lượt trả lời gần nhất.
    public List<SearchHit> resolve(List<ChatHistoryMessage> history, CatalogSearch search) {
        return lastAssistant(history).flatMap(message -> resolve(message, search)).stream().toList();
    }

    private Optional<SearchHit> resolve(ChatHistoryMessage message, CatalogSearch search) {
        if (nextScene) {
            return message.documentIds().isEmpty() ? Optional.empty()
                    : search.nextSegment(message.documentIds().getLast());
        }
        return itemIndex >= 0 && itemIndex < message.itemIds().size()
                ? search.item(message.itemIds().get(itemIndex)) : Optional.empty();
    }
}
