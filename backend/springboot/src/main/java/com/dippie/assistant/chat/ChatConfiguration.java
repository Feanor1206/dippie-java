package com.dippie.assistant.chat;

import com.dippie.assistant.AssistantProperties;
import com.dippie.assistant.search.CatalogSearch;
import com.dippie.common.rating.RatingLookup;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Factory của Spring lo ghép bộ phân tích ý định, Strategy sinh câu trả lời và ChatService.
@Configuration
class ChatConfiguration {

    @Bean
    IntentParser intentParser() {
        return new IntentParser();
    }

    // Chỉ bật DeepSeekAnswerGenerator khi provider là "deepseek" và có sẵn apiKey, thiếu một trong hai thì chạy bản mẫu cho an toàn.
    @Bean
    @ConditionalOnMissingBean(AnswerGenerator.class)
    AnswerGenerator answerGenerator(
            AssistantProperties properties,
            ObjectMapper objectMapper,
            CatalogSearch catalogSearch,
            Clock clock
    ) {
        TemplateAnswerGenerator template = new TemplateAnswerGenerator(catalogSearch::availableSeriesTitles);
        AssistantProperties.Llm llm = properties.llm();
        if ("deepseek".equalsIgnoreCase(llm.provider()) && llm.hasApiKey()) {
            return new DeepSeekAnswerGenerator(llm, objectMapper, template, clock);
        }
        return template;
    }

    // Dùng lại đúng TemplateAnswerGenerator bên trong DeepSeek làm nhánh trả lời mẫu cho các câu không cần gọi LLM.
    @Bean
    ChatService chatService(
            CatalogSearch catalogSearch,
            IntentParser intentParser,
            AnswerGenerator answerGenerator,
            RatingLookup ratings
    ) {
        TemplateAnswerGenerator catalogAnswers = switch (answerGenerator) {
            case TemplateAnswerGenerator template -> template;
            case DeepSeekAnswerGenerator deepSeek -> deepSeek.fallback();
            default -> new TemplateAnswerGenerator(catalogSearch::availableSeriesTitles);
        };
        return new ChatService(catalogSearch, intentParser, answerGenerator, catalogAnswers, ratings);
    }
}
