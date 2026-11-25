package com.dippie.web;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebResourceConfig implements WebMvcConfigurer {

    private final WebProperties webProperties;

    public WebResourceConfig(WebProperties webProperties) {
        this.webProperties = webProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(toDirectoryLocation(webProperties.subDirectory("assets")))
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        registry.addResourceHandler("/illustrations/**")
                .addResourceLocations(toDirectoryLocation(webProperties.subDirectory("illustrations")))
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic());
    }

    private String toDirectoryLocation(Path directory) {
        String uri = directory.toAbsolutePath().normalize().toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }
}
