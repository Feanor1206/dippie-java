package com.dippie.config.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;

// gom cấu hình CineBot dưới prefix dippie.assistant, chia 2 nhánh LLM và giới hạn /api/chat
@ConfigurationProperties("dippie.assistant")
public record AssistantProperties(Llm llm, Chat chat) {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final int DEFAULT_MAX_CONCURRENT_LLM_CALLS = 4;
    private static final int DEFAULT_DAILY_CALL_BUDGET = 500;

    public AssistantProperties(Llm llm) {
        this(llm, null);
    }

    // nhồi mặc định nếu YAML bỏ trống khối llm hay chat để code phía sau khỏi check null
    @ConstructorBinding
    public AssistantProperties {
        llm = llm == null ? new Llm(null, null, null, null, null, null, 0, 0) : llm;
        chat = chat == null ? new Chat(0, 0, null) : chat;
    }

    // thông số gọi LLM gồm provider, URL, model, apiKey, timeout, trần gọi song song và ngân sách ngày
    public record Llm(
            String provider,
            String baseUrl,
            String model,
            String apiKey,
            Duration timeout,
            Duration connectTimeout,
            int maxConcurrentCalls,
            int dailyCallBudget
    ) {

        public Llm(String provider, String baseUrl, String model, String apiKey, Duration timeout) {
            this(provider, baseUrl, model, apiKey, timeout, DEFAULT_CONNECT_TIMEOUT,
                    DEFAULT_MAX_CONCURRENT_LLM_CALLS, DEFAULT_DAILY_CALL_BUDGET);
        }

        // chuẩn hóa chuỗi cấu hình rồi đưa số âm hoặc 0 về mặc định kẻo treo kết nối hay mở trần vô hạn
        @ConstructorBinding
        public Llm {
            provider = provider == null || provider.isBlank() ? "template" : provider.trim();
            baseUrl = normalizeBaseUrl(baseUrl);
            model = model == null || model.isBlank() ? "deepseek-chat" : model.trim();
            apiKey = apiKey == null ? "" : apiKey.trim();
            timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(20) : timeout;
            connectTimeout = connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                    ? DEFAULT_CONNECT_TIMEOUT
                    : connectTimeout;
            maxConcurrentCalls = maxConcurrentCalls > 0 ? maxConcurrentCalls : DEFAULT_MAX_CONCURRENT_LLM_CALLS;
            dailyCallBudget = dailyCallBudget > 0 ? dailyCallBudget : DEFAULT_DAILY_CALL_BUDGET;
        }

        public boolean hasApiKey() {
            return !apiKey.isEmpty();
        }

        // Việt nhớ giữ override toString() này nhé, che apiKey thành "***" để log cấu hình không bao giờ lộ khóa thật
        @Override
        public String toString() {
            return "Llm[provider=" + provider + ", baseUrl=" + baseUrl + ", model=" + model
                    + ", apiKey=" + (hasApiKey() ? "***" : "") + ", timeout=" + timeout
                    + ", connectTimeout=" + connectTimeout + ", maxConcurrentCalls=" + maxConcurrentCalls
                    + ", dailyCallBudget=" + dailyCallBudget + "]";
        }

        private static String normalizeBaseUrl(String rawUrl) {
            if (rawUrl == null || rawUrl.isBlank()) {
                return "https://api.deepseek.com";
            }
            String trimmed = rawUrl.trim();
            while (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            return trimmed;
        }
    }

    // giới hạn cỡ body, số luồng SSE song song và rate limit của /api/chat
    public record Chat(int maxRequestBytes, int maxConcurrentStreams, ChatRateLimit rateLimit) {

        public Chat {
            maxRequestBytes = maxRequestBytes > 0 ? maxRequestBytes : (256 * 1024);
            maxConcurrentStreams = maxConcurrentStreams > 0
                    ? maxConcurrentStreams
                    : 16;
            rateLimit = rateLimit == null ? new ChatRateLimit(0, null, 0) : rateLimit;
        }
    }

    // cửa sổ đếm tần suất gọi /api/chat theo từng client
    public record ChatRateLimit(int maxRequests, Duration window, int maxClients) {

        public ChatRateLimit {
            maxRequests = maxRequests > 0 ? maxRequests : 20;
            window = window == null || window.isNegative() || window.isZero()
                    ? Duration.ofMinutes(1)
                    : window;
            maxClients = maxClients > 0 ? maxClients : 4096;
        }
    }
}
