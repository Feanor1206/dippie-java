package com.dippie.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dippie.media")
public record MediaProperties(String root, String cacheRoot, String driveMount) {
}
