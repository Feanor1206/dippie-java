package com.dippie.assistant.chat;

import com.dippie.assistant.search.SearchHit;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

// Strategy sinh câu trả lời cho CineBot, chọn DeepSeek hay Template ở ChatConfiguration.
public interface AnswerGenerator {

    // Sinh từng đoạn chữ trả lời và báo giai đoạn thực tế ("cloud" hay "template") qua onStage.
    Stream<String> answer(ChatIntent intent, List<SearchHit> results, Consumer<String> onStage);

    default Stream<String> answer(ChatIntent intent, List<SearchHit> results) {
        return answer(intent, results, stage -> {
        });
    }
}
