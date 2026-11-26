package com.dippie.common.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// Cung cấp một bean Clock chạy theo UTC cho toàn app để các chỗ phụ thuộc thời gian (JWT, rate limit) dễ thay đồng hồ giả khi viết test.
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
