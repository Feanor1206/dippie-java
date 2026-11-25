package com.dippie.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dippie.bootstrap-admin")
public record BootstrapAdminProperties(String username, String email, String password) {
}
