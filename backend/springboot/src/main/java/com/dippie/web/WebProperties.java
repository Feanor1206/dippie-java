package com.dippie.web;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dippie.web")
record WebProperties(String dist) {

    private static final String DEFAULT_DIST = "../../frontend/dist";

    WebProperties {
        if (dist == null || dist.isBlank()) {
            dist = DEFAULT_DIST;
        }
    }

    private Path distDirectory() {
        return Path.of(dist).toAbsolutePath().normalize();
    }

    Path indexHtml() {
        return distDirectory().resolve("index.html");
    }

    Path subDirectory(String name) {
        return distDirectory().resolve(name);
    }
}
