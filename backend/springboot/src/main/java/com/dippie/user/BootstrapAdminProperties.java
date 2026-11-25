package com.dippie.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Ánh xạ nhóm cấu hình dippie.bootstrap-admin từ biến môi trường để AdminBootstrap tạo hoặc đổi mật khẩu admin.
@ConfigurationProperties("dippie.bootstrap-admin")
public record BootstrapAdminProperties(String username, String email, String password) {
}
