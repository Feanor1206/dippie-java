package com.dippie.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

// trần dung lượng body HTTP theo từng nhóm route, đọc từ dippie.http.body-limit kẻo request khổng lồ ngốn RAM
@ConfigurationProperties("dippie.http.body-limit")
public record HttpBodyLimitProperties(
        long defaultMaxBytes,
        long chatMaxBytes,
        long catalogImportMaxBytes,
        long quizImportMaxBytes
) {

    public static final long DEFAULT_JSON_MAX_BYTES = 64L * 1024L;
    public static final long DEFAULT_CHAT_MAX_BYTES = 256L * 1024L;
    public static final long DEFAULT_CATALOG_IMPORT_MAX_BYTES = 25L * 1024L * 1024L;
    public static final long DEFAULT_QUIZ_IMPORT_MAX_BYTES = 1_572_864L;

    @ConstructorBinding
    public HttpBodyLimitProperties {
        defaultMaxBytes = defaultMaxBytes > 0 ? defaultMaxBytes : DEFAULT_JSON_MAX_BYTES;
        chatMaxBytes = chatMaxBytes > 0 ? chatMaxBytes : DEFAULT_CHAT_MAX_BYTES;
        catalogImportMaxBytes = catalogImportMaxBytes > 0 ? catalogImportMaxBytes : DEFAULT_CATALOG_IMPORT_MAX_BYTES;
        quizImportMaxBytes = quizImportMaxBytes > 0 ? quizImportMaxBytes : DEFAULT_QUIZ_IMPORT_MAX_BYTES;
    }

    public HttpBodyLimitProperties(long defaultMaxBytes, long chatMaxBytes, long catalogImportMaxBytes) {
        this(defaultMaxBytes, chatMaxBytes, catalogImportMaxBytes, DEFAULT_QUIZ_IMPORT_MAX_BYTES);
    }
}
