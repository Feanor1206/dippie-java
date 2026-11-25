package com.dippie.web;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dippie.web")
public record WebProperties(String dist) {

    private static final String DEFAULT_DIST = "../../frontend/dist";

    public WebProperties {
        if (dist == null || dist.isBlank()) {
            dist = DEFAULT_DIST;
        }
    }

    public Path distDirectory() {
        return Path.of(dist).toAbsolutePath().normalize();
    }

    public Path indexHtml() {
        return distDirectory().resolve("index.html");
    }

    public Path subDirectory(String name) {
        return distDirectory().resolve(name);
    }
}
