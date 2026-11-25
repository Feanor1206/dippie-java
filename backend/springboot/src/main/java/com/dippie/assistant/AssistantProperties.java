package com.dippie.assistant;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dippie.assistant")
public record AssistantProperties(Llm llm) {

    private static final String DEFAULT_PROVIDER = "template";
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String DEFAULT_MODEL = "deepseek-chat";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    public AssistantProperties {
        llm = llm == null ? new Llm(null, null, null, null, null) : llm;
    }

    public record Llm(String provider, String baseUrl, String model, String apiKey, Duration timeout) {

        public Llm {
            provider = provider == null || provider.isBlank() ? DEFAULT_PROVIDER : provider.trim();
            baseUrl = normalizeBaseUrl(baseUrl);
            model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
            apiKey = apiKey == null ? "" : apiKey.trim();
            timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? DEFAULT_TIMEOUT : timeout;
        }

        public boolean hasApiKey() {
            return !apiKey.isEmpty();
        }

        @Override
        public String toString() {
            return "Llm[provider=" + provider + ", baseUrl=" + baseUrl + ", model=" + model
                    + ", apiKey=" + (hasApiKey() ? "***" : "") + ", timeout=" + timeout + "]";
        }

        private static String normalizeBaseUrl(String raw) {
            if (raw == null || raw.isBlank()) {
                return DEFAULT_BASE_URL;
            }
            String trimmed = raw.trim();
            while (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            return trimmed;
        }
    }
}
