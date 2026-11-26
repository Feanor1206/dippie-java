package com.dippie.service.assistant;

import com.dippie.model.assistant.ChatIntent;
import com.dippie.model.assistant.SearchHit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// Strategy sinh câu trả lời bằng mẫu tiếng Việt dựng sẵn, dùng khi chưa bật LLM hoặc khi DeepSeek phải fallback.
public final class TemplateAnswerGenerator implements AnswerGenerator {

    private static final Pattern TOKEN_STREAM_RE = Pattern.compile("\\S+\\s*");
    private final Supplier<List<String>> availableSeries;

    public TemplateAnswerGenerator() {
        this(List::of);
    }

    public TemplateAnswerGenerator(Supplier<List<String>> availableSeries) {
        this.availableSeries = availableSeries;
    }

    // Ghép câu trả lời mẫu, gắn thêm ghi chú YouTube nếu cần rồi tách từng từ kèm khoảng trắng để phát dần về frontend.
    @Override
    public Stream<String> answer(ChatIntent intent, List<SearchHit> results, Consumer<String> onStage) {
        onStage.accept("template");
        List<SearchHit> hits = results == null ? List.of() : results;
        String text = CatalogReplies.withPlaybackNote(buildText(intent, hits), hits);
        return tokenize(text).stream();
    }

    // Chọn mẫu câu theo thứ tự ưu tiên: chào hỏi, hết kết quả, hỏi bộ phim, tóm tắt, câu hỏi nối tiếp rồi tới gợi ý.
    private String buildText(ChatIntent intent, List<SearchHit> hits) {
        if ("chitchat".equals(intent.mode())) {
            return CatalogReplies.chitchat(intent.semanticQuery());
        }
        if (hits.isEmpty()) {
            if (ChatFollowUp.parse(intent.semanticQuery()).filter(ChatFollowUp::nextScene).isPresent()) {
                return "Kho phim chưa có cảnh tiếp theo cho đoạn này.";
            }
            return CatalogReplies.noMatch(availableSeries.get());
        }
        if (intent.seriesInfo() != null) {
            return CatalogReplies.seriesReply(hits, intent.seriesInfo());
        }
        if (intent.summaryRequested()) {
            return CatalogReplies.summary(hits.getFirst());
        }
        if (ChatFollowUp.parse(intent.semanticQuery()).filter(followUp -> !followUp.nextScene()).isPresent()) {
            String details = CatalogReplies.episodeDetails(hits.getFirst(), intent.semanticQuery());
            if (!details.isEmpty()) {
                return details;
            }
        }
        if ("recommend".equals(intent.mode()) || hits.size() > 1) {
            return CatalogReplies.discovery(hits);
        }
        return CatalogReplies.catalogReply(hits, intent.mode());
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_STREAM_RE.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }
}
