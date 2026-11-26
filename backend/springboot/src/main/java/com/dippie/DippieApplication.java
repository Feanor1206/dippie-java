package com.dippie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// điểm chạy chính của backend Dippie, tắt UserDetailsServiceAutoConfiguration đi
// để Spring Security khỏi tự sinh user mặc định với mật khẩu ngẫu nhiên vì app dùng toàn bộ JWT
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class DippieApplication {

    public static void main(String[] args) {
        SpringApplication.run(DippieApplication.class, args);
    }
}
