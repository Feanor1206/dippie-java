package com.dippie.assistant;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

// Gom cấu hình CineBot dưới prefix dippie.assistant, chia hai nhánh là LLM và giới hạn endpoint /api/chat.
@ConfigurationProperties("dippie.assistant")
public record AssistantProperties(Llm llm, Chat chat) {

    private static final String DEFAULT_PROVIDER = "template";
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String DEFAULT_MODEL = "deepseek-chat";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);
    private static final int DEFAULT_MAX_CONCURRENT_LLM_CALLS = 4;
    private static final int DEFAULT_DAILY_CALL_BUDGET = 500;
    private static final int DEFAULT_CHAT_MAX_REQUEST_BYTES = 256 * 1024;
    private static final int DEFAULT_CHAT_MAX_CONCURRENT_STREAMS = 16;
    private static final int DEFAULT_CHAT_RATE_LIMIT_MAX_REQUESTS = 20;
    private static final Duration DEFAULT_CHAT_RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final int DEFAULT_CHAT_RATE_LIMIT_MAX_CLIENTS = 4096;

    public AssistantProperties(Llm llm) {
        this(llm, null);
    }

    // Nhồi giá trị mặc định nếu YAML bỏ trống khối llm hoặc chat để code phía sau khỏi phải check null.
    @ConstructorBinding
    public AssistantProperties {
        llm = llm == null ? new Llm(null, null, null, null, null, null, 0, 0) : llm;
        chat = chat == null ? new Chat(0, 0, null) : chat;
    }

    // Thông số kết nối LLM gồm provider, URL, model, apiKey, timeout, trần gọi song song và ngân sách ngày.
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

        // Chuẩn hóa chuỗi cấu hình và đưa các số âm hoặc bằng 0 về mặc định để tránh treo kết nối hay mở trần vô hạn.
        @ConstructorBinding
        public Llm {
            provider = provider == null || provider.isBlank() ? DEFAULT_PROVIDER : provider.trim();
            baseUrl = normalizeBaseUrl(baseUrl);
            model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
            apiKey = apiKey == null ? "" : apiKey.trim();
            timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? DEFAULT_TIMEOUT : timeout;
            connectTimeout = connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                    ? DEFAULT_CONNECT_TIMEOUT
                    : connectTimeout;
            maxConcurrentCalls = maxConcurrentCalls > 0 ? maxConcurrentCalls : DEFAULT_MAX_CONCURRENT_LLM_CALLS;
            dailyCallBudget = dailyCallBudget > 0 ? dailyCallBudget : DEFAULT_DAILY_CALL_BUDGET;
        }

        public boolean hasApiKey() {
            return !apiKey.isEmpty();
        }

        // Việt nhớ giữ override toString() này nhé, mình che apiKey thành "***" để log cấu hình không bao giờ làm lộ khóa thật.
        @Override
        public String toString() {
            return "Llm[provider=" + provider + ", baseUrl=" + baseUrl + ", model=" + model
                    + ", apiKey=" + (hasApiKey() ? "***" : "") + ", timeout=" + timeout
                    + ", connectTimeout=" + connectTimeout + ", maxConcurrentCalls=" + maxConcurrentCalls
                    + ", dailyCallBudget=" + dailyCallBudget + "]";
        }

        private static String normalizeBaseUrl(String rawUrl) {
            if (rawUrl == null || rawUrl.isBlank()) {
                return DEFAULT_BASE_URL;
            }
            String trimmed = rawUrl.trim();
            while (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            return trimmed;
        }
    }

    // Giới hạn kích thước body, số luồng SSE đồng thời và bộ đếm tần suất của /api/chat.
    public record Chat(int maxRequestBytes, int maxConcurrentStreams, ChatRateLimit rateLimit) {

        public Chat {
            maxRequestBytes = maxRequestBytes > 0 ? maxRequestBytes : DEFAULT_CHAT_MAX_REQUEST_BYTES;
            maxConcurrentStreams = maxConcurrentStreams > 0
                    ? maxConcurrentStreams
                    : DEFAULT_CHAT_MAX_CONCURRENT_STREAMS;
            rateLimit = rateLimit == null ? new ChatRateLimit(0, null, 0) : rateLimit;
        }
    }

    // Cửa sổ đếm tần suất gọi /api/chat theo từng client.
    public record ChatRateLimit(int maxRequests, Duration window, int maxClients) {

        public ChatRateLimit {
            maxRequests = maxRequests > 0 ? maxRequests : DEFAULT_CHAT_RATE_LIMIT_MAX_REQUESTS;
            window = window == null || window.isNegative() || window.isZero()
                    ? DEFAULT_CHAT_RATE_LIMIT_WINDOW
                    : window;
            maxClients = maxClients > 0 ? maxClients : DEFAULT_CHAT_RATE_LIMIT_MAX_CLIENTS;
        }
    }
}
