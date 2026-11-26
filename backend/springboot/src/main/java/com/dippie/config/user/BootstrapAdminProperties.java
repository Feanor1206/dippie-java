package com.dippie.config.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

// map nhóm dippie.bootstrap-admin từ biến môi trường để AdminBootstrap tạo hoặc đổi mật khẩu admin
@ConfigurationProperties("dippie.bootstrap-admin")
public record BootstrapAdminProperties(String username, String email, String password) {
}
