package com.dippie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.time.ZoneOffset;
import java.util.TimeZone;

// điểm chạy chính của backend Dippie, tắt UserDetailsServiceAutoConfiguration đi
// để Spring Security khỏi tự sinh user mặc định với mật khẩu ngẫu nhiên vì app dùng toàn bộ JWT
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class DippieApplication {

    // ghim JVM về UTC ngay lúc nạp class (chạy cả trong test lẫn IDE) vì mọi LocalDateTime trong app đều là giờ UTC
    // Việt đừng bỏ nha, máy để +07 là Hibernate với driver JDBC trừ thêm 7 tiếng lúc ghi, DB lệch so với app Admin
    static {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
    }

    public static void main(String[] args) {
        SpringApplication.run(DippieApplication.class, args);
    }
}
