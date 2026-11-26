package com.dippie.common.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// cấp 1 bean Clock chạy UTC cho cả app để mấy chỗ dính thời gian (JWT, rate limit) dễ cắm đồng hồ giả lúc test
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
