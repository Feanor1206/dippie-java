package com.dippie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Điểm khởi chạy backend Dippie. Mình tắt UserDetailsServiceAutoConfiguration để Spring Security
// không tự tạo tài khoản mặc định cùng mật khẩu ngẫu nhiên, vì app xác thực hoàn toàn bằng JWT.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class DippieApplication {

    public static void main(String[] args) {
        SpringApplication.run(DippieApplication.class, args);
    }
}
