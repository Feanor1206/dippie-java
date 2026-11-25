package com.dippie.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.stereotype.Component;

// Strategy tìm bản video đã kéo sẵn về thư mục cache Drive cục bộ.
// Phải khớp đúng kích thước byteLength thì mới dùng, tránh phát nhầm bản đang tải dở.
@Component
public class DriveCacheLocation implements MediaLocationStrategy {

    private final Path cacheRoot;

    public DriveCacheLocation(MediaProperties properties) {
        String configured = properties == null ? null : properties.cacheRoot();
        this.cacheRoot = configured == null || configured.isBlank() ? null : Path.of(configured.trim());
    }

    // Nếu thiếu byteLength hoặc dung lượng tệp trên đĩa lệch thì bỏ qua để rơi xuống ổ Drive gắn kết.
    @Override
    public Optional<Path> locate(MediaAsset asset) {
        if (cacheRoot == null || asset == null || asset.getByteLength() <= 0) {
            return Optional.empty();
        }
        Optional<Path> candidate = MediaPathPolicy.resolveRegularFile(cacheRoot, asset.getRelativePath());
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (Files.size(candidate.get()) == asset.getByteLength()) {
                return candidate;
            }
        } catch (IOException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
