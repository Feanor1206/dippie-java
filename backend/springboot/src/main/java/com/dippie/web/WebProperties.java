package com.dippie.web;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Đường dẫn tới thư mục dist đã build của frontend, đọc từ dippie.web.
@ConfigurationProperties("dippie.web")
final class WebProperties {

    private static final String DEFAULT_DIST = "../../frontend/dist";

    private final Path distDirectory;
    private final Path indexHtml;

    WebProperties(String dist) {
        String resolved = dist == null || dist.isBlank() ? DEFAULT_DIST : dist;
        this.distDirectory = Path.of(resolved).toAbsolutePath().normalize();
        this.indexHtml = this.distDirectory.resolve("index.html");
    }

    Path indexHtml() {
        return indexHtml;
    }

    Path subDirectory(String name) {
        return distDirectory.resolve(name);
    }
}
