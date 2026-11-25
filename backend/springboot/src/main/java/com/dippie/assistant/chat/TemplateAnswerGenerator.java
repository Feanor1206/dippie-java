package com.dippie.assistant.chat;

import com.dippie.assistant.search.SearchHit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class TemplateAnswerGenerator implements AnswerGenerator {

    private static final Pattern TOKEN_STREAM_RE = Pattern.compile("\\S+\\s*");

    @Override
    public Stream<String> answer(ChatIntent intent, List<SearchHit> results, Consumer<String> conversationStageConsumer) {
        conversationStageConsumer.accept("template");
        String text = buildText(intent, results);
        return tokenize(text).stream();
    }

    private String buildText(ChatIntent intent, List<SearchHit> results) {
        List<SearchHit> safeResults = results == null ? List.of() : results;
        if (intent.seriesInfo() != null) {
            return CatalogReplies.seriesReply(safeResults, intent.seriesInfo());
        }
        if (intent.moodRecommendation() && !safeResults.isEmpty()) {
            return safeResults.size() == 1 ? "Bạn thử tập này nhé." : "Bạn thử mấy tập này nhé.";
        }
        return CatalogReplies.catalogReply(safeResults, intent.mode());
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
