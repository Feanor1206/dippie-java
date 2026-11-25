package com.dippie.assistant.chat;

import com.dippie.assistant.AssistantProperties;
import com.dippie.assistant.search.CatalogSearch;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ChatConfiguration {

    @Bean
    IntentParser intentParser() {
        return new IntentParser();
    }

    @Bean
    @ConditionalOnMissingBean(AnswerGenerator.class)
    AnswerGenerator answerGenerator(AssistantProperties properties, ObjectMapper objectMapper) {
        AssistantProperties.Llm llm = properties.llm();
        if ("deepseek".equalsIgnoreCase(llm.provider()) && llm.hasApiKey()) {
            return new DeepSeekAnswerGenerator(llm, objectMapper);
        }
        return new TemplateAnswerGenerator();
    }

    @Bean
    ChatService chatService(
            CatalogSearch catalogSearch,
            IntentParser intentParser,
            AnswerGenerator answerGenerator
    ) {
        return new ChatService(catalogSearch, intentParser, answerGenerator);
    }
}
