package com.dippie.assistant.chat;

import com.dippie.assistant.search.SearchHit;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public interface AnswerGenerator {

    Stream<String> answer(ChatIntent intent, List<SearchHit> results, Consumer<String> conversationStageConsumer);

    default Stream<String> answer(ChatIntent intent, List<SearchHit> results) {
        return answer(intent, results, stage -> {
        });
    }
}
