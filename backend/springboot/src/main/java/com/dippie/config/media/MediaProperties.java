package com.dippie.config.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

// cấu hình dippie.media cho thư mục gốc, thư mục cache và ổ Drive gắn kết
@ConfigurationProperties("dippie.media")
public record MediaProperties(String root, String cacheRoot, String driveMount) {

    public MediaProperties {
        driveMount = defaultDriveMount(driveMount, System.getProperty("os.name", ""));
    }

    // chỉ gán mặc định ổ X: trên Windows để máy Linux hay macOS khỏi dò sai đường dẫn
    public static String defaultDriveMount(String configured, String osName) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return osName.startsWith("Windows") ? "X:" : "";
    }
}
