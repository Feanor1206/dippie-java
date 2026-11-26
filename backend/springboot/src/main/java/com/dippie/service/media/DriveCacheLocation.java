package com.dippie.service.media;

import com.dippie.config.media.MediaProperties;
import com.dippie.model.media.MediaAsset;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

// nhánh Strategy tìm video đã kéo sẵn trong thư mục cache Drive dưới máy
// phải khớp đúng byteLength mới lấy kẻo phát nhầm file đang tải dở
@Component
public class DriveCacheLocation implements MediaLocationStrategy {

    private final Path cacheRoot;

    public DriveCacheLocation(MediaProperties properties) {
        String configured = properties == null ? null : properties.cacheRoot();
        this.cacheRoot = configured == null || configured.isBlank() ? null : Path.of(configured.trim());
    }

    // thiếu byteLength hay dung lượng trên đĩa bị lệch thì bỏ qua cho rơi xuống ổ Drive gắn kết
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
