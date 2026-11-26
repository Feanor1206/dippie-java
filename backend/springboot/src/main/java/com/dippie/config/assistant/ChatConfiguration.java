package com.dippie.config.assistant;

import com.dippie.common.rating.RatingLookup;
import com.dippie.service.assistant.AnswerGenerator;
import com.dippie.service.assistant.CatalogSearch;
import com.dippie.service.assistant.ChatService;
import com.dippie.service.assistant.DeepSeekAnswerGenerator;
import com.dippie.service.assistant.IntentParser;
import com.dippie.service.assistant.TemplateAnswerGenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// factory bên Spring lo ghép bộ phân tích ý định, Strategy sinh câu trả lời và ChatService
@Configuration
public class ChatConfiguration {

    @Bean
    public IntentParser intentParser() {
        return new IntentParser();
    }

    // chỉ bật DeepSeekAnswerGenerator khi provider là "deepseek" và có apiKey, thiếu 1 trong 2 thì chạy bản mẫu cho chắc
    @Bean
    @ConditionalOnMissingBean(AnswerGenerator.class)
    public AnswerGenerator answerGenerator(
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

    // tái sử dụng luôn TemplateAnswerGenerator trong DeepSeek làm nhánh trả lời mẫu cho mấy câu không cần gọi LLM
    @Bean
    public ChatService chatService(
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
