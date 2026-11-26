package com.dippie.config.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.time.Duration;

// Cấu hình phục vụ file tĩnh của frontend và thay thread pool bất đồng bộ mặc định bằng pool có giới hạn cho luồng SSE.
@Configuration
public class WebResourceConfig implements WebMvcConfigurer {

    private static final int ASYNC_CORE_POOL_SIZE = 8;
    private static final int ASYNC_MAX_POOL_SIZE = 32;
    private static final int ASYNC_QUEUE_CAPACITY = 64;
    private static final int ASYNC_AWAIT_TERMINATION_SECONDS = 15;

    private final WebProperties webProperties;

    public WebResourceConfig(WebProperties webProperties) {
        this.webProperties = webProperties;
    }

    // Spring mặc định dùng SimpleAsyncTaskExecutor tạo thread vô hạn; mình đặt pool có trần để mở nhiều luồng /api/chat không làm cạn thread.
    @Bean(name = "mvcAsyncTaskExecutor")
    public ThreadPoolTaskExecutor mvcAsyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(ASYNC_CORE_POOL_SIZE);
        executor.setMaxPoolSize(ASYNC_MAX_POOL_SIZE);
        executor.setQueueCapacity(ASYNC_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("dippie-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(ASYNC_AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncTaskExecutor());
    }

    // File trong /assets đã có hash tên nên cache bất biến 1 năm, còn /illustrations giữ cache 1 ngày.
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(toDirectoryLocation(webProperties.subDirectory("assets")))
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        registry.addResourceHandler("/illustrations/**")
                .addResourceLocations(toDirectoryLocation(webProperties.subDirectory("illustrations")))
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic());
    }

    private String toDirectoryLocation(Path directory) {
        String uri = directory.toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }
}
