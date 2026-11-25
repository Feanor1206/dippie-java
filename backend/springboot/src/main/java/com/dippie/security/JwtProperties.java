package com.dippie.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dippie.jwt")
public record JwtProperties(String secret, Duration expiration, Duration playbackGrantValidity) {

    public JwtProperties {
        if (expiration == null) {
            expiration = Duration.ofHours(24);
        }
        if (playbackGrantValidity == null) {
            playbackGrantValidity = Duration.ofMinutes(5);
        }
    }
}
