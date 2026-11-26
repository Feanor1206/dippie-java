package com.dippie.config.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

// đường dẫn tới thư mục dist đã build của frontend, đọc từ dippie.web
@ConfigurationProperties("dippie.web")
public final class WebProperties {

    private final Path distDirectory;
    private final Path indexHtml;

    public WebProperties(String dist) {
        String resolved = dist == null || dist.isBlank() ? "../../frontend/dist" : dist;
        this.distDirectory = Path.of(resolved).toAbsolutePath().normalize();
        this.indexHtml = this.distDirectory.resolve("index.html");
    }

    public Path indexHtml() {
        return indexHtml;
    }

    public Path subDirectory(String name) {
        return distDirectory.resolve(name);
    }
}
