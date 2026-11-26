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

// cấu hình phục vụ file tĩnh của frontend và thay async thread pool mặc định bằng pool có trần cho luồng SSE
@Configuration
public class WebResourceConfig implements WebMvcConfigurer {

    private final WebProperties webProperties;

    public WebResourceConfig(WebProperties webProperties) {
        this.webProperties = webProperties;
    }

    // Spring mặc định dùng SimpleAsyncTaskExecutor tạo thread vô hạn nên đặt pool có trần kẻo mở nhiều luồng /api/chat là cạn thread
    @Bean(name = "mvcAsyncTaskExecutor")
    public ThreadPoolTaskExecutor mvcAsyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("dippie-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncTaskExecutor());
    }

    // file trong /assets đã có hash tên nên cache bất biến 1 năm, còn /illustrations cache 1 ngày
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
